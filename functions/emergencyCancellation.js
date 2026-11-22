const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

function validDocumentId(value) {
  return typeof value === "string" && value.length > 0 &&
    Buffer.byteLength(value, "utf8") <= 1500 &&
    !value.includes("/") && value !== "." && value !== "..";
}

exports.cancelEmergencyInAppAlert = onCall(
  {region: "us-central1", maxInstances: 3, timeoutSeconds: 60, memory: "256MiB"},
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Please sign in to cancel your alert.");
    }
    const input = request.data;
    if (!input || typeof input !== "object" || Array.isArray(input) ||
        Object.keys(input).length !== 1 || !validDocumentId(input.eventId)) {
      throw new HttpsError("invalid-argument", "A valid event ID is required.");
    }
    const uid = request.auth.uid;
    const eventId = input.eventId;
    const db = getFirestore();
    const eventRef = db.collection("emergencyEvents").doc(eventId);
    try {
      return await db.runTransaction(async (transaction) => {
        const event = await transaction.get(eventRef);
        // Use the same denial for absent and non-owned events.
        if (!event.exists || event.get("ownerId") !== uid) {
          throw new HttpsError("permission-denied", "You can only cancel your own alert.");
        }
        if (event.get("triggerType") !== "emergency_button") {
          throw new HttpsError("failed-precondition", "This service only cancels emergency-button alerts.");
        }
        const status = event.get("status");
        if (status !== "active" && status !== "cancelled") {
          throw new HttpsError("failed-precondition", "This alert cannot be cancelled in its current state.");
        }
        const recipients = event.get("recipientIds");
        if (!Array.isArray(recipients) || recipients.length < 1 || recipients.length > 10 ||
            !recipients.every(validDocumentId) || new Set(recipients).size !== recipients.length ||
            recipients.includes(uid)) {
          throw new HttpsError("failed-precondition", "The alert's recipient records need review.");
        }
        // Use only the immutable recipients recorded on this event, not current contacts.
        // Cancellation remains possible if a contact or receiving preference has changed.
        const notifications = [];
        for (const recipientUid of recipients) {
          const ref = db.collection("users").doc(recipientUid)
            .collection("emergencyNotifications").doc(eventId);
          const notification = await transaction.get(ref);
          if (!notification.exists || notification.get("eventId") !== eventId ||
              notification.get("senderUid") !== uid || notification.get("type") !== "emergency_button") {
            throw new HttpsError("failed-precondition", "The alert's notification records need review. No changes were saved.");
          }
          notifications.push({ref, status: notification.get("status")});
        }
        const timestamp = FieldValue.serverTimestamp();
        const cancelledAt = event.get("cancelledAt") || timestamp;
        if (status !== "cancelled" || !event.get("cancelledAt")) {
          transaction.update(eventRef, {
            status: "cancelled", cancelledAt, updatedAt: timestamp,
          });
        }
        // Also repair an event previously cancelled through legacy client rules.
        // Preserve message, location, original creation time, and read state.
        for (const notification of notifications) {
          if (notification.status !== "cancelled") {
            transaction.update(notification.ref, {status: "cancelled", cancelledAt});
          }
        }
        return {
          eventId, state: "cancelled", inAppRecordCount: recipients.length,
          alreadyCancelled: status === "cancelled",
        };
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logger.error("Emergency alert cancellation failed.", {code: String(error.code || "unknown")});
      throw new HttpsError("internal", "Could not confirm cancellation. Retry cancelling the same alert.");
    }
  }
);