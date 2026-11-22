const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {
  getFirestore,
  FieldValue,
  Timestamp,
} = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

const options = {
  region: "us-central1",
  maxInstances: 3,
  timeoutSeconds: 60,
  memory: "256MiB",
};

const TEXT_MAX = 2000;
const PREVIEW_MAX = 120;
const RATE_LIMIT = 20;
const RATE_WINDOW_MS = 60 * 1000;

// Anonymity: message documents and conversation summaries store only the
// sender's ROLE. The admin's uid is kept only in staffMessageAudit, which
// no client can read.

function invalid(message) {
  throw new HttpsError("invalid-argument", message);
}

function denied(message) {
  throw new HttpsError("permission-denied", message);
}

function requireUid(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Please sign in.");
  }
  return request.auth.uid;
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

function optionalUid(value) {
  if (value === undefined || value === null) return null;

  if (
    typeof value !== "string" ||
    value.length === 0 ||
    value.length > 128 ||
    value.includes("/") ||
    value === "." ||
    value === ".."
  ) {
    invalid("Invalid support account.");
  }
  return value;
}

function checkText(value) {
  if (typeof value !== "string") invalid("A message is required.");

  const text = value.trim();

  if (text.length < 1 || text.length > TEXT_MAX) {
    invalid(`Messages must be between 1 and ${TEXT_MAX} characters.`);
  }
  return text;
}

function isActive(snapshot, role) {
  return snapshot.exists &&
    snapshot.get("role") === role &&
    snapshot.get("accountStatus") === "active";
}

// Never trust a role supplied by the app: read the caller's own profile,
// and the target's, inside the caller's transaction.
async function resolveThread(transaction, users, uid, requestedSupportUid) {
  const caller = await transaction.get(users.doc(uid));

  if (caller.get("accountStatus") !== "active") {
    denied("An active account is required.");
  }

  if (caller.get("role") === "support") {
    if (requestedSupportUid !== null && requestedSupportUid !== uid) {
      denied("Support accounts can only use their own conversation.");
    }
    return {role: "support", supportUid: uid, support: caller};
  }

  if (caller.get("role") !== "admin") {
    denied("Only administrators and support accounts can use staff messaging.");
  }

  if (requestedSupportUid === null) invalid("Choose a support account.");

  const support = await transaction.get(users.doc(requestedSupportUid));

  if (!isActive(support, "support")) {
    throw new HttpsError(
      "failed-precondition",
      "That account isn't an active support account."
    );
  }
  return {role: "admin", supportUid: requestedSupportUid, support};
}

function handleError(error, operation) {
  if (error instanceof HttpsError) throw error;

  logger.error(`Staff messaging ${operation} failed.`, error);

  throw new HttpsError(
    "internal",
    "Unable to complete the request. Please try again."
  );
}

// -----------------------------------------------------------------
// sendStaffMessage
// -----------------------------------------------------------------

exports.sendStaffMessage = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);
    const data = checkObject(request.data, ["supportUid", "text"]);
    const requested = optionalUid(data.supportUid);
    const text = checkText(data.text);

    const db = getFirestore();
    const users = db.collection("users");
    const limitRef = db.collection("staffMessageLimits").doc(uid);

    return await db.runTransaction(async (transaction) => {
      const {role, supportUid, support} =
        await resolveThread(transaction, users, uid, requested);

      const conversationRef =
        db.collection("staffConversations").doc(supportUid);
      const [conversation, limit] = await Promise.all([
        transaction.get(conversationRef),
        transaction.get(limitRef),
      ]);

      // Fixed one-minute window per sender, like supportPairingLimits.
      const now = Date.now();
      const windowStart = limit.get("windowStartedAt");
      const withinWindow = windowStart &&
        now - windowStart.toMillis() < RATE_WINDOW_MS;
      const count = withinWindow ? limit.get("count") : 0;

      if (!Number.isInteger(count) || count < 0 || count >= RATE_LIMIT) {
        throw new HttpsError(
          "resource-exhausted",
          "You're sending messages too quickly. Wait a minute and try again."
        );
      }

      const timestamp = FieldValue.serverTimestamp();
      const messageRef = conversationRef.collection("messages").doc();
      const otherUnread = role === "admin" ? "supportUnread" : "adminUnread";
      const currentUnread = conversation.get(otherUnread);

      transaction.set(limitRef, {
        windowStartedAt: withinWindow ? windowStart : Timestamp.fromMillis(now),
        count: count + 1,
      });

      const summary = {
        lastMessageAt: timestamp,
        lastMessagePreview: Array.from(text).slice(0, PREVIEW_MAX).join(""),
        lastSenderRole: role,
        [otherUnread]: (Number.isInteger(currentUnread) ? currentUnread : 0) + 1,
      };

      if (conversation.exists) {
        transaction.update(conversationRef, summary);
      } else {
        const name = support.get("displayName");
        transaction.create(conversationRef, {
          supportUid,
          supportDisplayName: typeof name === "string" ? name : "",
          createdAt: timestamp,
          supportUnread: 0,
          adminUnread: 0,
          ...summary,
        });
      }

      transaction.create(messageRef, {
        senderRole: role,
        text,
        createdAt: timestamp,
      });

      transaction.create(db.collection("staffMessageAudit").doc(), {
        conversationId: supportUid,
        messageId: messageRef.id,
        senderUid: uid,
        senderRole: role,
        createdAt: timestamp,
      });

      return {conversationId: supportUid, messageId: messageRef.id};
    });
  } catch (error) {
    handleError(error, "send");
  }
});

// -----------------------------------------------------------------
// markStaffConversationRead
// -----------------------------------------------------------------

exports.markStaffConversationRead = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);
    const data = checkObject(request.data, ["supportUid"]);
    const requested = optionalUid(data.supportUid);

    const db = getFirestore();
    const users = db.collection("users");

    return await db.runTransaction(async (transaction) => {
      const {role, supportUid} =
        await resolveThread(transaction, users, uid, requested);

      const conversationRef =
        db.collection("staffConversations").doc(supportUid);
      const conversation = await transaction.get(conversationRef);
      const field = role === "admin" ? "adminUnread" : "supportUnread";

      // No thread yet, or already read: nothing to write.
      if (conversation.exists && conversation.get(field) !== 0) {
        transaction.update(conversationRef, {[field]: 0});
      }

      return {conversationId: supportUid};
    });
  } catch (error) {
    handleError(error, "mark read");
  }
});
