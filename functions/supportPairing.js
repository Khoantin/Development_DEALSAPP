const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {randomInt, randomUUID} = require("node:crypto");
const logger = require("firebase-functions/logger");

const callableOptions = {
  region: "us-central1",
  maxInstances: 3,
  timeoutSeconds: 60,
  memory: "256MiB",
};

const CODE_LIFETIME_MS = 10 * 60 * 1000;
const GENERATION_COOLDOWN_MS = 30 * 1000;

exports.generateSupportPairingCode = onCall(
  callableOptions,
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to generate a pairing code."
      );
    }

    const input = request.data;

    if (
      input != null &&
      (
        typeof input !== "object" ||
        Array.isArray(input) ||
        Object.keys(input).length !== 0
      )
    ) {
      throw new HttpsError(
        "invalid-argument",
        "This operation does not accept account details."
      );
    }

    const uid = request.auth.uid;
    const db = getFirestore();
    const userRef = db.collection("users").doc(uid);
    const stateRef = db.collection("supportPairingUsers").doc(uid);

    try {
      // Retry with another random code if a live code already owns it.
      for (let attempt = 0; attempt < 10; attempt++) {
        const code = String(randomInt(0, 1000000)).padStart(6, "0");
        const generationId = randomUUID();
        const codeRef = db.collection("supportPairingCodes").doc(code);

        const result = await db.runTransaction(async (transaction) => {
          // All reads happen before any writes.
          const user = await transaction.get(userRef);
          const state = await transaction.get(stateRef);
          const existingCode = await transaction.get(codeRef);

          if (
            !user.exists ||
            user.get("role") !== "user" ||
            user.get("accountStatus") !== "active"
          ) {
            throw new HttpsError(
              "permission-denied",
              "An active User account is required."
            );
          }

          const now = Date.now();
          const lastGeneratedAt = state.get("lastGeneratedAt");

          if (
            lastGeneratedAt &&
            now - lastGeneratedAt.toMillis() < GENERATION_COOLDOWN_MS
          ) {
            throw new HttpsError(
              "resource-exhausted",
              "Wait 30 seconds before generating another code."
            );
          }

          const existingExpiry = existingCode.get("expiresAt");

          // Do not overwrite an unexpired reservation, even if redeemed.
          if (
            existingCode.exists &&
            (!existingExpiry || existingExpiry.toMillis() > now)
          ) {
            return null;
          }

          // Ensure regeneration visibly changes the previous code.
          if (state.get("code") === code) {
            return null;
          }

          const createdAt = Timestamp.fromMillis(now);
          const expiresAtMillis = now + CODE_LIFETIME_MS;
          const expiresAt = Timestamp.fromMillis(expiresAtMillis);

          transaction.set(codeRef, {
            userId: uid,
            generationId,
            status: "available",
            createdAt,
            expiresAt,
          });

          // Redemption must check this generationId against the code.
          // Replacing it invalidates any previously generated code.
          transaction.set(stateRef, {
            code,
            generationId,
            lastGeneratedAt: createdAt,
            expiresAt,
          });

          return {
            code,
            expiresAt: expiresAtMillis,
          };
        });

        if (result !== null) {
          return result;
        }
      }

      throw new HttpsError(
        "resource-exhausted",
        "Unable to reserve a code. Please try again shortly."
      );
    } catch (error) {
      if (error instanceof HttpsError) {
        throw error;
      }

      // Never log pairing codes or account details.
      logger.error("Support pairing code generation failed.", {
        code: String(error.code || "unknown"),
      });

      throw new HttpsError(
        "internal",
        "Unable to generate a pairing code. Please try again."
      );
    }
  }
);
const REDEMPTION_WINDOW_MS = 10 * 60 * 1000;
const MAX_REDEMPTION_ATTEMPTS = 5;

exports.redeemSupportPairingCode = onCall(
  callableOptions,
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to request a connection."
      );
    }

    const input = request.data;

    if (
      !input ||
      typeof input !== "object" ||
      Array.isArray(input) ||
      Object.keys(input).some(
        (key) => !["code", "requestId"].includes(key)
      ) ||
      typeof input.code !== "string" ||
      !/^[0-9]{6}$/.test(input.code) ||
      typeof input.requestId !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
        .test(input.requestId)
    ) {
      throw new HttpsError(
        "invalid-argument",
        "Enter a six-digit code and a valid request ID."
      );
    }

    const supportId = request.auth.uid;
    const code = input.code;
    const requestId = input.requestId.toLowerCase();
    const db = getFirestore();

    const supportRef = db.collection("users").doc(supportId);
    const codeRef = db.collection("supportPairingCodes").doc(code);
    const limitRef = db.collection("supportPairingLimits").doc(supportId);
    const receiptRef = db.collection("supportPairingRedemptions")
      .doc(`${supportId}_${requestId}`);

    try {
      const result = await db.runTransaction(async (transaction) => {
        const support = await transaction.get(supportRef);

        if (
          !support.exists ||
          support.get("role") !== "support" ||
          support.get("accountStatus") !== "active"
        ) {
          throw new HttpsError(
            "permission-denied",
            "An active Support account is required."
          );
        }

        const receipt = await transaction.get(receiptRef);

        // A retry returns the existing outcome without recreating a link.
        if (receipt.exists) {
          if (
            receipt.get("supportId") !== supportId ||
            receipt.get("code") !== code
          ) {
            throw new HttpsError(
              "failed-precondition",
              "This request ID was already used for another request."
            );
          }

          const linkId = receipt.get("linkId");
          const link = await transaction.get(
            db.collection("supportLinks").doc(linkId)
          );

          return {
            success: true,
            linkId,
            status: link.exists ? link.get("status") : "removed",
            duplicate: true,
          };
        }

        const limit = await transaction.get(limitRef);
        const now = Date.now();
        const previousWindow = limit.get("windowStartedAt");

        const withinWindow =
          previousWindow &&
          now - previousWindow.toMillis() < REDEMPTION_WINDOW_MS;

        const attempts = withinWindow
          ? limit.get("attempts")
          : 0;

        if (
          !Number.isInteger(attempts) ||
          attempts < 0 ||
          attempts >= MAX_REDEMPTION_ATTEMPTS
        ) {
          throw new HttpsError(
            "resource-exhausted",
            "Too many pairing attempts. Try again later."
          );
        }

        const nextLimit = {
          windowStartedAt: withinWindow
            ? previousWindow
            : Timestamp.fromMillis(now),
          attempts: attempts + 1,
        };

        // Return a failure instead of throwing inside the transaction,
        // so unsuccessful code attempts still update the rate limit.
        function unavailable() {
          transaction.set(limitRef, nextLimit);

          return {
            failure: "failed-precondition",
            message:
              "This code is unavailable. Ask the User for a new code.",
          };
        }

        const pairingCode = await transaction.get(codeRef);
        const expiresAt = pairingCode.get("expiresAt");
        const userId = pairingCode.get("userId");

        if (
          !pairingCode.exists ||
          pairingCode.get("status") !== "available" ||
          !expiresAt ||
          expiresAt.toMillis() <= now ||
          typeof userId !== "string" ||
          userId.length === 0 ||
          userId.includes("/") ||
          userId === supportId
        ) {
          return unavailable();
        }

        const userRef = db.collection("users").doc(userId);
        const stateRef = db.collection("supportPairingUsers").doc(userId);

        const user = await transaction.get(userRef);
        const state = await transaction.get(stateRef);

        if (
          !user.exists ||
          user.get("role") !== "user" ||
          user.get("accountStatus") !== "active" ||
          !state.exists ||
          state.get("code") !== code ||
          typeof pairingCode.get("generationId") !== "string" ||
          state.get("generationId") !== pairingCode.get("generationId")
        ) {
          return unavailable();
        }

        const linkId = `${userId}_${supportId}`;
        const linkRef = db.collection("supportLinks").doc(linkId);
        const existingLink = await transaction.get(linkRef);

        // Never overwrite pending, accepted, or rejected connections.
        if (existingLink.exists) {
          transaction.set(limitRef, nextLimit);

          return {
            failure: "already-exists",
            message:
              "A connection or request already exists. Check your connections.",
          };
        }

        // All reads are complete. These writes commit together.
        const timestamp = Timestamp.fromMillis(now);

        transaction.create(linkRef, {
          userId,
          supportId,
          status: "pending",
          createdAt: timestamp,
        });

        transaction.update(codeRef, {
          status: "redeemed",
          redeemedBy: supportId,
          redeemedAt: timestamp,
        });

        transaction.create(receiptRef, {
          supportId,
          code,
          generationId: pairingCode.get("generationId"),
          linkId,
          createdAt: timestamp,
        });

        transaction.set(limitRef, nextLimit);

        return {
          success: true,
          linkId,
          status: "pending",
          duplicate: false,
        };
      });

      // Throw only after the failed attempt's counter has committed.
      if (result.failure) {
        throw new HttpsError(result.failure, result.message);
      }

      return result;
    } catch (error) {
      if (error instanceof HttpsError) {
        throw error;
      }

      logger.error("Support pairing redemption failed.", {
        code: String(error.code || "unknown"),
      });

      throw new HttpsError(
        "internal",
        "Could not confirm the request. Retry using the same request ID."
      );
    }
  }
);