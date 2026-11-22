const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();

exports.getAdminDashboardStats = onCall(
  {
    region: "us-central1",
    maxInstances: 3,
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to continue."
      );
    }

    try {
      // Never trust a role or user ID supplied by the app.
      const adminProfile = await db
        .collection("users")
        .doc(request.auth.uid)
        .get();

      const admin = adminProfile.data();

      if (
        !adminProfile.exists ||
        admin.role !== "admin" ||
        admin.accountStatus !== "active"
      ) {
        throw new HttpsError(
          "permission-denied",
          "An active administrator account is required."
        );
      }

      // Read only the fields needed for statistics.
      const profiles = await db
        .collection("users")
        .select("role", "accountStatus")
        .get();

      const stats = {
        totalAccounts: profiles.size,
        roles: {
          user: 0,
          support: 0,
          admin: 0,
          other: 0,
        },
        statuses: {
          active: 0,
          inactive: 0,
          suspended: 0,
          disabled: 0,
          other: 0,
        },
        generatedAtMillis: Date.now(),
      };

      for (const document of profiles.docs) {
        const profile = document.data();

        switch (profile.role) {
          case "user":
          case "support":
          case "admin":
            stats.roles[profile.role] += 1;
            break;
          default:
            stats.roles.other += 1;
        }

        switch (profile.accountStatus) {
          case "active":
          case "inactive":
          case "suspended":
          case "disabled":
            stats.statuses[profile.accountStatus] += 1;
            break;
          default:
            stats.statuses.other += 1;
        }
      }

      // Return totals only—no names, emails, documents, or locations.
      return stats;
    } catch (error) {
      if (error instanceof HttpsError) {
        throw error;
      }

      logger.error("Admin dashboard statistics failed.", error);

      throw new HttpsError(
        "internal",
        "Unable to load dashboard statistics. Please try again."
      );
    }
  }
);
const adminResources = require("./adminResources");

  exports.listAdminRegionalResources =
    adminResources.listAdminRegionalResources;

  exports.updateAdminRegionalResource =
    adminResources.updateAdminRegionalResource;

const emergencyAlerts = require("./emergencyAlerts");

exports.sendEmergencyInAppAlert =
  emergencyAlerts.sendEmergencyInAppAlert;

const emergencyCancellation = require("./emergencyCancellation");

exports.cancelEmergencyInAppAlert =
  emergencyCancellation.cancelEmergencyInAppAlert;

const supportPairing = require("./supportPairing");

exports.generateSupportPairingCode =
  supportPairing.generateSupportPairingCode;

exports.redeemSupportPairingCode =
  supportPairing.redeemSupportPairingCode;

const adminGuidance = require("./adminGuidance");

exports.listEmergencyGuides =
  adminGuidance.listEmergencyGuides;

exports.manageEmergencyGuide =
  adminGuidance.manageEmergencyGuide;

const adminAccounts = require("./adminAccounts");

exports.listAdminAccounts =
  adminAccounts.listAdminAccounts;

exports.setAccountStatus =
  adminAccounts.setAccountStatus;

exports.setAccountRole =
  adminAccounts.setAccountRole;

const staffMessaging = require("./staffMessaging");

exports.sendStaffMessage =
  staffMessaging.sendStaffMessage;

exports.markStaffConversationRead =
  staffMessaging.markStaffConversationRead;

const adminEmergencyStats = require("./adminEmergencyStats");

exports.getAdminEmergencyStats =
  adminEmergencyStats.getAdminEmergencyStats;
