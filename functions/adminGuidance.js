const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

const options = {
  region: "us-central1",
  maxInstances: 3,
  timeoutSeconds: 60,
  memory: "256MiB",
};

function fail(code, message) {
  throw new HttpsError(code, message);
}

function requireUid(request) {
  if (!request.auth) {
    fail("unauthenticated", "Please sign in.");
  }
  return request.auth.uid;
}

function checkAccount(snapshot, adminOnly = false) {
  const profile = snapshot.data();

  if (
    !snapshot.exists ||
    profile?.accountStatus !== "active" ||
    !["user", "support", "admin"].includes(profile?.role) ||
    (adminOnly && profile.role !== "admin")
  ) {
    fail(
      "permission-denied",
      adminOnly
        ? "An active administrator account is required."
        : "An active account is required."
    );
  }

  return profile.role;
}

function checkObject(value, allowedKeys) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    fail("invalid-argument", "Invalid request.");
  }

  if (Object.keys(value).some((key) => !allowedKeys.includes(key))) {
    fail("invalid-argument", "The request contains an unsupported field.");
  }
}

function readText(value, name, maxLength, required = false) {
  if (typeof value !== "string") {
    fail("invalid-argument", `${name} must be text.`);
  }

  const cleaned = value.trim();

  if (cleaned.length > maxLength || (required && !cleaned)) {
    fail(
      "invalid-argument",
      `${name} ${required ? "is required and " : ""}` +
        `must be at most ${maxLength} characters.`
    );
  }

  return cleaned;
}

function validateFields(fields, publishing = false) {
  checkObject(fields, [
    "title",
    "summary",
    "audience",
    "steps",
    "practiceScenario",
  ]);

  const title = readText(fields.title, "Title", 120, true);
  const summary = readText(fields.summary, "Summary", 1000, publishing);
  const practiceScenario = readText(
    fields.practiceScenario,
    "Fictional practice scenario",
    2000,
    publishing
  );

  if (!["user", "support", "both"].includes(fields.audience)) {
    fail("invalid-argument", "Choose User, Support, or both.");
  }

  if (!Array.isArray(fields.steps) || fields.steps.length > 12) {
    fail("invalid-argument", "A guide can contain up to 12 steps.");
  }

  const steps = fields.steps.map((step) =>
    readText(step, "Step", 1000, true)
  );

  if (publishing && steps.length === 0) {
    fail("invalid-argument", "Add at least one step before publishing.");
  }

  return {
    title,
    summary,
    audience: fields.audience,
    steps,
    practiceScenario,
  };
}

function checkId(value) {
  if (
    typeof value !== "string" ||
    !/^[a-zA-Z0-9-]{1,80}$/.test(value)
  ) {
    fail("invalid-argument", "Invalid guide ID.");
  }
  return value;
}

function checkRevision(value) {
  if (!Number.isSafeInteger(value) || value < 0) {
    fail("invalid-argument", "Reload the guide before continuing.");
  }
  return value;
}

function revisionOf(snapshot) {
  if (!snapshot.exists) return 0;

  const revision = snapshot.data().revision;

  if (!Number.isSafeInteger(revision) || revision < 1) {
    fail("failed-precondition", "This guide needs its version corrected.");
  }

  return revision;
}

function timeMillis(value) {
  return value && typeof value.toMillis === "function"
    ? value.toMillis()
    : null;
}

function handleError(error, operation) {
  if (error instanceof HttpsError) throw error;

  // Do not log guide content or request data.
  logger.error(`Emergency guidance ${operation} failed.`, {
    code: String(error.code || "unknown"),
  });

  fail("internal", "Unable to complete the request. Refresh and try again.");
}

// Admin mode returns drafts and the current published copy.
// Published mode returns only content suitable for the caller's role.
exports.listEmergencyGuides = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);
    checkObject(request.data, ["mode"]);

    const mode = request.data.mode;
    if (!["admin", "published"].includes(mode)) {
      fail("invalid-argument", "Invalid guide list mode.");
    }

    const db = getFirestore();

    return await db.runTransaction(async (transaction) => {
      const profile = await transaction.get(
        db.collection("users").doc(uid)
      );
      const role = checkAccount(profile, mode === "admin");

      let query = db.collection("emergencyGuides");

      if (mode === "published") {
        query = query.where("isPublished", "==", true);
      }

      const snapshot = await transaction.get(query.limit(101));

      if (snapshot.size > 100) {
        fail(
          "resource-exhausted",
          "The guide library needs pagination before more guides can be loaded."
        );
      }

      const guides = [];

      for (const document of snapshot.docs) {
        const data = document.data();

        if (mode === "admin") {
          guides.push({
            id: document.id,
            revision: revisionOf(document),
            draft: data.draft,
            published: data.isPublished === true
              ? data.published
              : null,
            isPublished: data.isPublished === true,
            hasUnpublishedChanges: data.hasUnpublishedChanges === true,
            updatedAtMillis: timeMillis(data.updatedAt),
            lastReviewedAtMillis: data.isPublished === true
              ? timeMillis(data.lastReviewedAt)
              : null,
          });
        } else {
          const published = data.published;
          if (!published) continue;

          if (
            role !== "admin" &&
            published.audience !== "both" &&
            published.audience !== role
          ) {
            continue;
          }

          // Explicit projection: no draft or Admin identifiers.
          guides.push({
            id: document.id,
            title: published.title,
            summary: published.summary,
            audience: published.audience,
            steps: published.steps,
            practiceScenario: published.practiceScenario,
            lastReviewedAtMillis: timeMillis(data.lastReviewedAt),
          });
        }
      }

      guides.sort((a, b) => {
        const first = mode === "admin" ? a.draft.title : a.title;
        const second = mode === "admin" ? b.draft.title : b.title;
        return first.localeCompare(second);
      });

      return {guides};
    });
  } catch (error) {
    handleError(error, "load");
  }
});

// Every mutation checks Admin access and the exact expected revision
// in the same transaction as the write.
exports.manageEmergencyGuide = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);

    checkObject(request.data, [
      "action",
      "guideId",
      "expectedRevision",
      "fields",
      "reviewConfirmed",
    ]);

    const {
      action,
      fields,
      reviewConfirmed,
    } = request.data;

    if (!["save", "publish", "withdraw"].includes(action)) {
      fail("invalid-argument", "Invalid guide action.");
    }

    if (
      (action !== "save" && fields !== undefined) ||
      (action !== "publish" && reviewConfirmed !== undefined)
    ) {
      fail("invalid-argument", "Unsupported fields for this action.");
    }

    const guideId = checkId(request.data.guideId);
    const expectedRevision = checkRevision(
      request.data.expectedRevision
    );

    const draft = action === "save"
      ? validateFields(fields)
      : null;

    if (action === "publish" && reviewConfirmed !== true) {
      fail(
        "invalid-argument",
        "Review the saved guide and confirm before publishing."
      );
    }

    const db = getFirestore();
    const guideRef = db.collection("emergencyGuides").doc(guideId);

    return await db.runTransaction(async (transaction) => {
      const profile = await transaction.get(
        db.collection("users").doc(uid)
      );
      checkAccount(profile, true);

      const snapshot = await transaction.get(guideRef);
      const currentRevision = revisionOf(snapshot);

      if (currentRevision !== expectedRevision) {
        fail(
          "failed-precondition",
          "This guide changed. Reload it before continuing."
        );
      }

      if (!snapshot.exists && action !== "save") {
        fail("not-found", "Save the guide before publishing it.");
      }

      if (currentRevision >= Number.MAX_SAFE_INTEGER) {
        fail("failed-precondition", "This guide needs version maintenance.");
      }

      const revision = currentRevision + 1;
      const timestamp = FieldValue.serverTimestamp();
      const common = {
        revision,
        updatedAt: timestamp,
        updatedBy: uid,
      };

      if (action === "save") {
        if (!snapshot.exists) {
          transaction.create(guideRef, {
            ...common,
            draft,
            createdAt: timestamp,
            createdBy: uid,
            isPublished: false,
            hasUnpublishedChanges: true,
          });
        } else {
          // Preserve the published copy while editing the draft.
          transaction.update(guideRef, {
            ...common,
            draft,
            hasUnpublishedChanges: true,
          });
        }
      } else if (action === "publish") {
        const published = validateFields(snapshot.data().draft, true);

        transaction.update(guideRef, {
          ...common,
          published,
          isPublished: true,
          hasUnpublishedChanges: false,
          lastReviewedAt: timestamp,
          publishedBy: uid,
        });
      } else {
        if (snapshot.data().isPublished !== true) {
          fail("failed-precondition", "This guide is already unpublished.");
        }

        transaction.update(guideRef, {
          ...common,
          isPublished: false,
          published: FieldValue.delete(),
          lastReviewedAt: FieldValue.delete(),
          publishedBy: FieldValue.delete(),
          hasUnpublishedChanges: true,
        });
      }

      return {saved: true, guideId, revision, action};
    });
  } catch (error) {
    handleError(error, "update");
  }
});