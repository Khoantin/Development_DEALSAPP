const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

const options = {
  region: "us-central1",
  maxInstances: 3,
  timeoutSeconds: 60,
  memory: "256MiB",
};

const regions = new Set(["CA-AB", "CA-BC"]);
const needs = new Set([
  "GENERAL", "HOUSING", "CONVERSATION", "ESSENTIALS",
]);
const methods = new Set([
  "PHONE", "TEXT", "IN_PERSON", "WEBSITE",
]);

const editableFields = [
  "name",
  "need",
  "summary",
  "exampleProcess",
  "contactMethods",
  "websiteUrl",
  "phoneNumber",
  "hoursNote",
  "lastReviewed",
];

function invalid(message) {
  throw new HttpsError("invalid-argument", message);
}

function requireUid(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Please sign in.");
  }
  return request.auth.uid;
}

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

function checkRegion(regionId) {
  if (!regions.has(regionId)) {
    invalid("Choose Alberta or British Columbia.");
  }
  return regionId;
}

function checkId(id) {
  if (
    typeof id !== "string" ||
    id.length === 0 ||
    Buffer.byteLength(id, "utf8") > 1500 ||
    id.includes("/") ||
    id === "." ||
    id === ".."
  ) {
    invalid("Invalid resource ID.");
  }
  return id;
}

function readText(fields, key, maxLength, required = false) {
  const value = fields[key];

  if (typeof value !== "string") {
    invalid(`${key} must be text.`);
  }

  const cleaned = value.trim();

  if (required && !cleaned) {
    invalid(`${key} is required.`);
  }

  if (cleaned.length > maxLength) {
    invalid(`${key} must be at most ${maxLength} characters.`);
  }

  return cleaned;
}

function validateFields(fields) {
  if (!fields || typeof fields !== "object" || Array.isArray(fields)) {
    invalid("Resource fields are required.");
  }

  if (Object.keys(fields).some((key) => !editableFields.includes(key))) {
    invalid("The request contains an unsupported field.");
  }

  const clean = {
    name: readText(fields, "name", 200, true),
    need: readText(fields, "need", 30, true),
    summary: readText(fields, "summary", 2000, true),
    exampleProcess: readText(fields, "exampleProcess", 4000, true),
    websiteUrl: readText(fields, "websiteUrl", 2000),
    phoneNumber: readText(fields, "phoneNumber", 60),
    hoursNote: readText(fields, "hoursNote", 500),
    lastReviewed: readText(fields, "lastReviewed", 100),
  };

  if (!needs.has(clean.need)) {
    invalid("Choose a valid resource category.");
  }

  if (
    !Array.isArray(fields.contactMethods) ||
    fields.contactMethods.length < 1 ||
    fields.contactMethods.length > methods.size ||
    fields.contactMethods.some((method) => !methods.has(method))
  ) {
    invalid("Choose at least one valid contact method.");
  }

  clean.contactMethods = [...new Set(fields.contactMethods)];

  if (clean.websiteUrl) {
    let url;
    try {
      url = new URL(clean.websiteUrl);
    } catch {
      invalid("Enter a valid HTTPS website address.");
    }

    if (
      !/^https:\/\//i.test(clean.websiteUrl) ||
      /[\s\\]/.test(clean.websiteUrl) ||
      url.protocol !== "https:" ||
      !url.hostname ||
      url.username ||
      url.password ||
      (url.port && url.port !== "443")
    ) {
      invalid("Use an HTTPS website without login details or a custom port.");
    }
  }

  if (clean.phoneNumber) {
    const normalized = clean.phoneNumber.replace(/[() .-]/g, "");

    if (
      !/^[+0-9() .-]+$/.test(clean.phoneNumber) ||
      !/^\+?[0-9]{3,15}$/.test(normalized)
    ) {
      invalid("Enter a valid phone number.");
    }

    clean.phoneNumber = normalized;
  }

  return clean;
}

// Exact document version, including nanoseconds.
function versionOf(snapshot) {
  return `${snapshot.updateTime.seconds}:${snapshot.updateTime.nanoseconds}`;
}

function resourceForClient(snapshot) {
  const data = snapshot.data();
  const fields = {};

  for (const key of editableFields) {
    if (key === "contactMethods") {
      fields[key] = Array.isArray(data[key])
        ? data[key].filter((value) => typeof value === "string")
        : [];
    } else {
      fields[key] = typeof data[key] === "string" ? data[key] : "";
    }
  }

  return {
    id: snapshot.id,
    version: versionOf(snapshot),
    isDemo: data.isDemo === true,
    ...fields,
  };
}

function handleError(error, operation) {
  if (error instanceof HttpsError) throw error;

  logger.error(`Admin resources ${operation} failed.`, error);

  throw new HttpsError(
    "internal",
    "Unable to complete the request. Please try again."
  );
}

exports.listAdminRegionalResources = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);
    const regionId = checkRegion(request.data?.regionId);
    const db = getFirestore();

    checkAdmin(await db.collection("users").doc(uid).get());

    const snapshot = await db
      .collection("regionalResources")
      .doc(regionId)
      .collection("resources")
      .where("published", "==", true)
      .limit(501)
      .get();

    if (snapshot.size > 500) {
      throw new HttpsError(
        "resource-exhausted",
        "This region requires pagination before it can be edited."
      );
    }

    return {
      regionId,
      resources: snapshot.docs
        .map(resourceForClient)
        .sort((a, b) => a.name.localeCompare(b.name)),
    };
  } catch (error) {
    handleError(error, "load");
  }
});

exports.updateAdminRegionalResource = onCall(options, async (request) => {
  try {
    const uid = requireUid(request);
    const regionId = checkRegion(request.data?.regionId);
    const resourceId = checkId(request.data?.resourceId);
    const expectedVersion = request.data?.expectedVersion;

    if (
      typeof expectedVersion !== "string" ||
      !/^\d+:\d+$/.test(expectedVersion)
    ) {
      invalid("Reload this resource before saving.");
    }

    const fields = validateFields(request.data?.fields);
    const db = getFirestore();
    const adminRef = db.collection("users").doc(uid);
    const resourceRef = db
      .collection("regionalResources")
      .doc(regionId)
      .collection("resources")
      .doc(resourceId);

    await db.runTransaction(async (transaction) => {
      // Authorization and resource checks belong to the same transaction.
      const adminSnapshot = await transaction.get(adminRef);
      checkAdmin(adminSnapshot);

      const resourceSnapshot = await transaction.get(resourceRef);

      if (!resourceSnapshot.exists) {
        throw new HttpsError("not-found", "This resource no longer exists.");
      }

      const existing = resourceSnapshot.data();

      if (
        existing.published !== true ||
        typeof existing.isDemo !== "boolean"
      ) {
        throw new HttpsError(
          "failed-precondition",
          "This listing is unpublished or needs its DEMO status corrected."
        );
      }

      if (versionOf(resourceSnapshot) !== expectedVersion) {
        throw new HttpsError(
          "failed-precondition",
          "This resource changed since you opened it. Reload before saving."
        );
      }

      transaction.update(resourceRef, {
        ...fields,
        updatedAt: FieldValue.serverTimestamp(),
        updatedBy: uid,
      });
    });

    return {saved: true, regionId, resourceId};
  } catch (error) {
    handleError(error, "save");
  }
});