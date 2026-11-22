const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {
  getFirestore,
  FieldValue,
  FieldPath,
} = require("firebase-admin/firestore");
const {getAuth} = require("firebase-admin/auth");
const logger = require("firebase-functions/logger");

const options = {
  region: "us-central1",
  maxInstances: 3,
  timeoutSeconds: 60,
  memory: "256MiB",
};

const PAGE_SIZE = 25;

const roles = new Set(["user", "support", "admin"]);
const filterStatuses = new Set(["active", "inactive", "suspended", "disabled"]);

// Statuses an admin can set. "inactive" exists in data but isn't set here.
const settableStatuses = new Set(["active", "suspended", "disabled"]);

// The only profile fields this module ever returns. No addresses, phone
// numbers, documents, locations or anything else leave the backend.
const listedFields = [
  "displayName",
  "email",
  "role",
  "accountStatus",
  "createdAt",
  "statusUpdatedAt",
  "roleUpdatedAt",
];

const roleLabels = {user: "User", support: "Support", admin: "Admin"};

const REASON_MIN = 5;
const REASON_MAX = 500;

function invalid(message) {
  throw new HttpsError("invalid-argument", message);
}

function requireUid(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Please sign in.");
  }
  return request.auth.uid;
}

// Never trust a role or status supplied by the app: always read the
// caller's own profile.
function checkAdmin(snapshot) {
  const profile = snapshot.data();

  if (
    !snapshot.exists ||
    profile?.role !== "admin" ||
    profile?.accountStatus !== "active"
  ) {
    throw new HttpsError(
      "permission-denied",
      "An active administrator account is required."
    );
  }
}

function checkObject(value, allowedKeys) {
  if (value === undefined || value === null) return {};

  if (typeof value !== "object" || Array.isArray(value)) {
    invalid("Invalid request.");
  }

  if (Object.keys(value).some((key) => !allowedKeys.includes(key))) {
    invalid("The request contains an unsupported field.");
  }

  return value;
}

function checkUid(value, label) {
  if (
    typeof value !== "string" ||
    value.length === 0 ||
    value.length > 128 ||
    value.includes("/") ||
    value === "." ||
    value === ".."
  ) {
    invalid(`Invalid ${label}.`);
  }
  return value;
}

function optionalFilter(value, allowed, label) {
  if (value === undefined || value === null || value === "") return null;

  if (typeof value !== "string" || !allowed.has(value)) {
    invalid(`Invalid ${label} filter.`);
  }
  return value;
}

function checkReason(value) {
  if (typeof value !== "string") {
    invalid("A reason is required.");
  }

  const reason = value.trim();

  if (reason.length < REASON_MIN || reason.length > REASON_MAX) {
    invalid(
      `The reason must be between ${REASON_MIN} and ${REASON_MAX} characters.`
    );
  }
  return reason;
}

function millisOrNull(value) {
  return value && typeof value.toMillis === "function"
    ? value.toMillis()
    : null;
}

function textOrEmpty(value) {
  return typeof value === "string" ? value : "";
}

function accountForClient(snapshot) {
  const data = snapshot.data() || {};

  return {
    uid: snapshot.id,
    displayName: textOrEmpty(data.displayName),
    email: textOrEmpty(data.email),
    role: textOrEmpty(data.role),
    accountStatus: textOrEmpty(data.accountStatus),
    createdAt: millisOrNull(data.createdAt),
    statusUpdatedAt: millisOrNull(data.statusUpdatedAt),
    roleUpdatedAt: millisOrNull(data.roleUpdatedAt),
  };
}

// True when at least one active admin other than [excludeUid] exists.
// Runs inside the caller's transaction so the read is part of it.
async function hasOtherActiveAdmin(transaction, users, excludeUid) {
  const activeAdmins = await transaction.get(
    users
      .where("role", "==", "admin")
      .where("accountStatus", "==", "active")
      .limit(2)
  );

  return activeAdmins.docs.some((doc) => doc.id !== excludeUid);
}

function handleError(error, operation) {
  if (error instanceof HttpsError) throw error;

  logger.error(`Admin accounts ${operation} failed.`, error);

  throw new HttpsError(
    "internal",
    "Unable to complete the request. Please try again."
  );
}

// -----------------------------------------------------------------
// listAdminAccounts
// -----------------------------------------------------------------

exports.listAdminAccounts = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);
    const data = checkObject(request.data, ["role", "status", "cursor"]);

    const role = optionalFilter(data.role, roles, "role");
    const status = optionalFilter(data.status, filterStatuses, "status");
    const cursor = data.cursor === undefined || data.cursor === null
      ? null
      : checkUid(data.cursor, "page cursor");

    const db = getFirestore();

    checkAdmin(await db.collection("users").doc(uid).get());

    // Equality filters plus document-ID order are served by Firestore's
    // automatic single-field indexes, so no composite index is needed.
    let query = db.collection("users");

    if (role) query = query.where("role", "==", role);
    if (status) query = query.where("accountStatus", "==", status);

    query = query.orderBy(FieldPath.documentId());

    if (cursor) query = query.startAfter(cursor);

    const snapshot = await query
      .select(...listedFields)
      .limit(PAGE_SIZE + 1)
      .get();

    const page = snapshot.docs.slice(0, PAGE_SIZE);
    const hasMore = snapshot.docs.length > PAGE_SIZE;

    return {
      accounts: page.map(accountForClient),
      nextCursor: hasMore ? page[page.length - 1].id : null,
    };
  } catch (error) {
    handleError(error, "list");
  }
});

// -----------------------------------------------------------------
// setAccountStatus
// -----------------------------------------------------------------

function isAuthNotFound(error) {
  return error?.code === "auth/user-not-found";
}

// Disabling blocks new sign-ins and token refreshes. Revoking refresh tokens
// ends existing sessions once their current ID token expires (up to 1 hour).
async function syncAuth(targetUid, newStatus) {
  const auth = getAuth();

  if (newStatus === "active") {
    await auth.updateUser(targetUid, {disabled: false});
  } else {
    await auth.updateUser(targetUid, {disabled: true});
    await auth.revokeRefreshTokens(targetUid);
  }
}

exports.setAccountStatus = onCall(options, async (request) => {
  let statusSaved = false;

  try {
    const uid = requireUid(request);
    const data = checkObject(request.data, ["targetUid", "newStatus", "reason"]);

    const targetUid = checkUid(data.targetUid, "account");

    if (typeof data.newStatus !== "string" || !settableStatuses.has(data.newStatus)) {
      invalid("Choose active, suspended or disabled.");
    }
    const newStatus = data.newStatus;
    const reason = checkReason(data.reason);

    if (targetUid === uid) {
      throw new HttpsError(
        "failed-precondition",
        "You can't change the status of your own account."
      );
    }

    const db = getFirestore();
    const users = db.collection("users");
    const adminRef = users.doc(uid);
    const targetRef = users.doc(targetUid);

    const result = await db.runTransaction(async (transaction) => {
      // Authorization is checked inside the same transaction as the write.
      checkAdmin(await transaction.get(adminRef));

      const target = await transaction.get(targetRef);

      if (!target.exists) {
        throw new HttpsError("not-found", "This account no longer exists.");
      }

      const fromStatus = typeof target.get("accountStatus") === "string"
        ? target.get("accountStatus")
        : null;

      // Never leave the app without an active administrator. The caller is
      // an active admin and can't target themselves, but checking here also
      // covers any future path, and the query is part of the transaction.
      if (
        target.get("role") === "admin" &&
        fromStatus === "active" &&
        newStatus !== "active"
      ) {
        if (!(await hasOtherActiveAdmin(transaction, users, targetUid))) {
          throw new HttpsError(
            "failed-precondition",
            "This is the last active administrator and can't be deactivated."
          );
        }
      }

      // Same status: nothing to record. The Auth state is still re-applied
      // below, so a call that failed at the Auth step can simply be retried.
      if (fromStatus === newStatus) {
        return {fromStatus, changed: false};
      }

      const timestamp = FieldValue.serverTimestamp();

      transaction.update(targetRef, {
        accountStatus: newStatus,
        statusUpdatedAt: timestamp,
        statusUpdatedBy: uid,
      });

      transaction.create(db.collection("adminAuditLog").doc(), {
        action: "status_change",
        targetUid,
        fromStatus,
        toStatus: newStatus,
        reason,
        adminUid: uid,
        createdAt: timestamp,
      });

      return {fromStatus, changed: true};
    });

    statusSaved = true;

    let authUserFound = true;

    try {
      await syncAuth(targetUid, newStatus);
    } catch (error) {
      if (isAuthNotFound(error)) {
        // A profile without a sign-in account: nothing to enable or disable.
        logger.warn("Account status saved; no Auth user exists.", {targetUid});
        authUserFound = false;
      } else {
        logger.error("Account status saved but Auth sync failed.", error);

        throw new HttpsError(
          "unavailable",
          "The account status was saved, but updating sign-in access " +
            "failed. Try the same change again to finish it.",
          {statusSaved: true, targetUid, toStatus: newStatus}
        );
      }
    }

    return {
      saved: true,
      targetUid,
      fromStatus: result.fromStatus,
      toStatus: newStatus,
      changed: result.changed,
      authUserFound,
    };
  } catch (error) {
    if (error instanceof HttpsError) throw error;

    logger.error("Admin accounts status change failed.", {statusSaved, error});

    throw new HttpsError(
      "internal",
      statusSaved
        ? "The account status was saved, but updating sign-in access " +
          "failed. Try the same change again to finish it."
        : "Unable to complete the request. Please try again.",
      statusSaved ? {statusSaved: true} : undefined
    );
  }
});

// -----------------------------------------------------------------
// setAccountRole
// -----------------------------------------------------------------

// No Firebase Auth change is needed: every admin function re-reads the
// caller's role from users/{uid} on each call, so a removed admin loses
// access on their next request.
exports.setAccountRole = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);
    const data = checkObject(request.data, ["targetUid", "newRole", "reason"]);

    const targetUid = checkUid(data.targetUid, "account");

    if (typeof data.newRole !== "string" || !roles.has(data.newRole)) {
      invalid("Choose user, support or admin.");
    }
    const newRole = data.newRole;
    const reason = checkReason(data.reason);

    if (targetUid === uid) {
      throw new HttpsError(
        "failed-precondition",
        "You can't change the role of your own account."
      );
    }

    const db = getFirestore();
    const users = db.collection("users");
    const adminRef = users.doc(uid);
    const targetRef = users.doc(targetUid);
    const links = db.collection("supportLinks");

    const fromRole = await db.runTransaction(async (transaction) => {
      // Authorization is checked inside the same transaction as the write.
      checkAdmin(await transaction.get(adminRef));

      const target = await transaction.get(targetRef);

      if (!target.exists) {
        throw new HttpsError("not-found", "This account no longer exists.");
      }

      const currentRole = typeof target.get("role") === "string"
        ? target.get("role")
        : null;
      const status = target.get("accountStatus");

      if (currentRole === newRole) {
        throw new HttpsError(
          "failed-precondition",
          `This account already has the ${roleLabels[newRole]} role.`
        );
      }

      if (newRole === "admin" && status !== "active") {
        throw new HttpsError(
          "failed-precondition",
          "Only active accounts can be made admins. Reactivate the " +
            "account first."
        );
      }

      // A support link, whatever its status, is tied to both sides' roles
      // (user and support). Changing either side's role would leave the link
      // in an inconsistent state, so any role change for a non-admin account
      // requires it to be unlinked first. Admins can't hold support links
      // (they can't create pairing codes or redeem them), so removing admin
      // rights skips this check. Checking every non-admin role, not only
      // user and support, keeps promotion to admin always link-checked.
      if (currentRole !== "admin") {
        const [asUser, asSupport] = await Promise.all([
          transaction.get(links.where("userId", "==", targetUid).limit(1)),
          transaction.get(links.where("supportId", "==", targetUid).limit(1)),
        ]);

        if (!asUser.empty || !asSupport.empty) {
          throw new HttpsError(
            "failed-precondition",
            "This account has support connections (including pending or " +
              "declined requests). They must be unlinked before its role " +
              "can change."
          );
        }
      }

      // Never leave the app without an active administrator.
      if (
        currentRole === "admin" &&
        status === "active" &&
        !(await hasOtherActiveAdmin(transaction, users, targetUid))
      ) {
        throw new HttpsError(
          "failed-precondition",
          "This is the last active administrator. Make another account an " +
            "admin before removing these admin rights."
        );
      }

      const timestamp = FieldValue.serverTimestamp();

      transaction.update(targetRef, {
        role: newRole,
        roleUpdatedAt: timestamp,
        roleUpdatedBy: uid,
      });

      transaction.create(db.collection("adminAuditLog").doc(), {
        action: "role_change",
        targetUid,
        fromRole: currentRole,
        toRole: newRole,
        reason,
        adminUid: uid,
        createdAt: timestamp,
      });

      return currentRole;
    });

    return {saved: true, targetUid, fromRole, toRole: newRole};
  } catch (error) {
    handleError(error, "role change");
  }
});
