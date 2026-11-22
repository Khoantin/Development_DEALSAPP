const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

const DAY_MILLIS = 24 * 60 * 60 * 1000;

// Anonymous emergency totals for the admin dashboard. Only count()
// aggregations run, so no event contents (owner, recipients, message,
// location) are ever read or returned.
exports.getAdminEmergencyStats = onCall(
  {region: "us-central1", maxInstances: 3, timeoutSeconds: 60, memory: "256MiB"},
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Please sign in to continue.");
    }

    const db = getFirestore();

    try {
      // Never trust a role supplied by the app.
      const profile = await db.collection("users").doc(request.auth.uid).get();
      if (!profile.exists || profile.get("role") !== "admin" ||
          profile.get("accountStatus") !== "active") {
        throw new HttpsError("permission-denied", "An active administrator account is required.");
      }

      const now = Date.now();
      const events = db.collection("emergencyEvents");
      const last30 = events.where("startedAt", ">=", Timestamp.fromMillis(now - 30 * DAY_MILLIS));
      const count = async (query) => (await query.count().get()).data().count;

      const [activeNow, startedLast7Days, startedLast30Days, cancelledLast30Days,
        emergencyButton, voiceDuress] = await Promise.all([
        count(events.where("status", "==", "active")),
        count(events.where("startedAt", ">=", Timestamp.fromMillis(now - 7 * DAY_MILLIS))),
        count(last30),
        // Voice duress events have no cancelledAt, so this counts alerts
        // started in the last 30 days that are now cancelled.
        count(last30.where("status", "==", "cancelled")),
        count(last30.where("triggerType", "==", "emergency_button")),
        count(last30.where("triggerType", "==", "voice_duress")),
      ]);

      return {
        activeNow,
        startedLast7Days,
        startedLast30Days,
        cancelledLast30Days,
        byTrigger: {emergency_button: emergencyButton, voice_duress: voiceDuress},
        generatedAtMillis: now,
      };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      // A missing composite index shows up here as FAILED_PRECONDITION with a link.
      logger.error("Admin emergency statistics failed.", error);
      throw new HttpsError("internal", "Unable to load emergency activity. Please try again.");
    }
  }
);
