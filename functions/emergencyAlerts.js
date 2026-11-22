const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

function validDocumentId(value) {
  return typeof value === "string" && value.length > 0 &&
    Buffer.byteLength(value, "utf8") <= 1500 &&
    !value.includes("/") && value !== "." && value !== "..";
}

function parseLocation(value) {
  if (value === undefined || value === null) return null;
  const fields = ["latitude", "longitude", "accuracyMeters", "capturedAtMillis"];
  if (typeof value !== "object" || Array.isArray(value) ||
      Object.keys(value).length !== fields.length ||
      !fields.every((key) => Object.hasOwn(value, key) &&
        typeof value[key] === "number" && Number.isFinite(value[key])) ||
      value.latitude < -90 || value.latitude > 90 ||
      value.longitude < -180 || value.longitude > 180 ||
      value.accuracyMeters < 0 || value.accuracyMeters > 100000 ||
      !Number.isSafeInteger(value.capturedAtMillis) || value.capturedAtMillis <= 0) {
    throw new HttpsError("invalid-argument", "Invalid location snapshot.");
  }
  // Canonical property order makes retry comparisons independent of input order.
  return Object.fromEntries(fields.map((key) => [key, value[key]]));
}

exports.sendEmergencyInAppAlert = onCall(
  {region: "us-central1", maxInstances: 3, timeoutSeconds: 60, memory: "256MiB"},
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Please sign in to send an alert.");
    }
    const uid = request.auth.uid;
    const input = request.data;
    if (!input || typeof input !== "object" || Array.isArray(input) ||
        Object.keys(input).some((key) => !["requestId", "contactIds", "location"].includes(key))) {
      throw new HttpsError("invalid-argument", "Invalid alert request.");
    }
    if (typeof input.requestId !== "string" ||
        !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(input.requestId)) {
      throw new HttpsError("invalid-argument", "Invalid request ID.");
    }
    if (!Array.isArray(input.contactIds) || input.contactIds.length < 1 ||
        input.contactIds.length > 10 || !input.contactIds.every(validDocumentId)) {
      throw new HttpsError("invalid-argument", "Select between 1 and 10 contacts.");
    }
    const contactIds = [...new Set(input.contactIds)].sort();
    const location = parseLocation(input.location);
    const db = getFirestore();
    const ownerRef = db.collection("users").doc(uid);
    const settingsRef = ownerRef.collection("emergencySettings").doc("preset");
    const eventId = `${uid}_${input.requestId.toLowerCase()}`;
    const eventRef = db.collection("emergencyEvents").doc(eventId);
    const limitRef = db.collection("emergencyRateLimits").doc(uid);

    try {
      return await db.runTransaction(async (transaction) => {
        const owner = await transaction.get(ownerRef);
        if (!owner.exists || owner.get("accountStatus") !== "active") {
          throw new HttpsError("permission-denied", "An active account is required.");
        }
        const existingEvent = await transaction.get(eventRef);
        if (existingEvent.exists) {
          const previous = existingEvent.data();
          if (previous.ownerId !== uid || previous.triggerType !== "emergency_button" ||
              JSON.stringify(previous.selectedContactIds) !== JSON.stringify(contactIds) ||
              JSON.stringify(parseLocation(previous.location)) !== JSON.stringify(location)) {
            throw new HttpsError("failed-precondition", "This request ID was already used for a different alert.");
          }
          return {eventId, state: previous.status,
            inAppRecordCount: previous.recipientIds.length, duplicate: true};
        }
        // A snapshot is device-reported, not proof of a verified current position.
        // Retrying may deliver an older snapshot; always retain its original time.
        if (location && location.capturedAtMillis > Date.now() + 60000) {
          throw new HttpsError("invalid-argument", "The snapshot time is in the future. Check the device clock.");
        }
        const settings = await transaction.get(settingsRef);
        const message = settings.get("message");
        if (typeof message !== "string" || !message.trim() || message.length > 1000) {
          throw new HttpsError("failed-precondition", "Save your emergency help message first.");
        }
        const limit = await transaction.get(limitRef);
        const lastCreatedAt = limit.get("lastCreatedAt");
        if (lastCreatedAt && Date.now() - lastCreatedAt.toMillis() < 30000) {
          throw new HttpsError("resource-exhausted", "An alert was recently created. Wait briefly before starting another.");
        }
        const recipientIds = new Set();
        for (const contactId of contactIds) {
          const contact = await transaction.get(ownerRef.collection("trustedContacts").doc(contactId));
          const linkedUid = contact.get("linkedUserId");
          if (!contact.exists || contact.get("emergencyContact") !== true ||
              contact.get("emergencyInAppOptIn") !== true || !validDocumentId(linkedUid) || linkedUid === uid) {
            throw new HttpsError("failed-precondition", "A selected contact is not configured for in-app alerts.");
          }
          recipientIds.add(linkedUid);
        }
        for (const recipientUid of recipientIds) {
          const recipientRef = db.collection("users").doc(recipientUid);
          const recipient = await transaction.get(recipientRef);
          const preferences = await transaction.get(recipientRef.collection("emergencySettings").doc("preset"));
          if (!recipient.exists || recipient.get("accountStatus") !== "active" ||
              preferences.get("receiveInApp") !== true) {
            throw new HttpsError("failed-precondition", "A selected recipient is unavailable for in-app alerts. No alert was created.");
          }
        }
        const timestamp = FieldValue.serverTimestamp();
        const recipients = [...recipientIds].sort();
        const optionalLocation = location ? {location} : {};
        transaction.create(eventRef, {
          ownerId: uid, recipientIds: recipients, selectedContactIds: contactIds,
          triggerType: "emergency_button", status: "active", message: message.trim(),
          startedAt: timestamp, updatedAt: timestamp, ...optionalLocation,
        });
        for (const recipientUid of recipients) {
          const notificationRef = db.collection("users").doc(recipientUid)
            .collection("emergencyNotifications").doc(eventId);
          transaction.create(notificationRef, {
            eventId, senderUid: uid, type: "emergency_button", message: message.trim(),
            createdAt: timestamp, read: false, ...optionalLocation,
          });
        }
        transaction.set(limitRef, {lastCreatedAt: timestamp});
        return {eventId, state: "active", inAppRecordCount: recipients.length, duplicate: false};
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logger.error("Emergency in-app alert failed.", {code: String(error.code || "unknown")});
      throw new HttpsError("internal", "Could not confirm the alert. Retry the same request.");
    }
  }
);
