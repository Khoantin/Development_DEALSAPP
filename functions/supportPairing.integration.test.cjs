const {test, beforeEach, after} = require("node:test");
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");

// Refuse to run against production.
if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8085") {
  throw new Error("Run this test through the configured Firestore emulator.");
}

const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");

const app = initializeApp({projectId: "demo-deals-rules"});
const db = getFirestore(app);

const {
  generateSupportPairingCode,
  redeemSupportPairingCode,
} = require("./supportPairing");

const USER = "pair-user";
const SUPPORT = "pair-support";
const OTHER_SUPPORT = "other-support";
const LINK = `supportLinks/${USER}_${SUPPORT}`;

function call(fn, uid, data) {
  return fn.run({
    auth: uid ? {uid, token: {sub: uid}} : undefined,
    data,
  });
}

function generate(uid = USER, data = {}) {
  return call(generateSupportPairingCode, uid, data);
}

function redeem(code, {
  uid = SUPPORT,
  requestId = randomUUID(),
} = {}) {
  return call(redeemSupportPairingCode, uid, {code, requestId});
}

async function denied(promise, code) {
  await assert.rejects(promise, (error) => error.code === code);
}

async function noLink() {
  assert.equal((await db.doc(LINK).get()).exists, false);
}

beforeEach(async () => {
  // These are emulator documents only. Run this suite separately
  // from the rules suite because both reset test data.
  for (const collection of [
    "users",
    "supportLinks",
    "supportPairingCodes",
    "supportPairingUsers",
    "supportPairingLimits",
    "supportPairingRedemptions",
  ]) {
    await db.recursiveDelete(db.collection(collection));
  }

  await Promise.all([
    db.doc(`users/${USER}`).set({
      role: "user",
      accountStatus: "active",
    }),
    db.doc(`users/${SUPPORT}`).set({
      role: "support",
      accountStatus: "active",
    }),
    db.doc(`users/${OTHER_SUPPORT}`).set({
      role: "support",
      accountStatus: "active",
    }),
  ]);
});

after(async () => {
  await db.terminate();
  await deleteApp(app);
});

test("generation returns six digits with a ten-minute expiry", async () => {
  const before = Date.now();
  const result = await generate();
  const after = Date.now();

  assert.match(result.code, /^[0-9]{6}$/);
  assert.ok(result.expiresAt >= before + 600000);
  assert.ok(result.expiresAt <= after + 600000);

  const reservation = await db.doc(
    `supportPairingCodes/${result.code}`
  ).get();
  const state = await db.doc(`supportPairingUsers/${USER}`).get();

  assert.equal(reservation.get("userId"), USER);
  assert.equal(reservation.get("status"), "available");
  assert.equal(
    reservation.get("generationId"),
    state.get("generationId")
  );
});

test("signed-out account cannot generate a code", async () => {
  await denied(generate(null), "unauthenticated");
});

test("Support cannot generate a User pairing code", async () => {
  await denied(generate(SUPPORT), "permission-denied");
});

test("inactive User cannot generate a code", async () => {
  await db.doc(`users/${USER}`).update({accountStatus: "inactive"});
  await denied(generate(), "permission-denied");
});

test("generation rejects a supplied account ID", async () => {
  await denied(generate(USER, {userId: "someone-else"}), "invalid-argument");
});

test("generation enforces its cooldown", async () => {
  await generate();
  await denied(generate(), "resource-exhausted");
});

test("valid redemption creates only a pending request", async () => {
  const {code} = await generate();
  const result = await redeem(code);

  assert.equal(result.status, "pending");
  assert.equal(result.duplicate, false);

  const link = await db.doc(LINK).get();
  assert.equal(link.get("userId"), USER);
  assert.equal(link.get("supportId"), SUPPORT);
  assert.equal(link.get("status"), "pending");

  const reservation = await db.doc(`supportPairingCodes/${code}`).get();
  assert.equal(reservation.get("status"), "redeemed");
  assert.equal(reservation.get("redeemedBy"), SUPPORT);
});

test("signed-out account cannot redeem", async () => {
  const {code} = await generate();
  await denied(redeem(code, {uid: null}), "unauthenticated");
  await noLink();
});

test("User account cannot redeem as Support", async () => {
  const {code} = await generate();
  await denied(redeem(code, {uid: USER}), "permission-denied");
  await noLink();
});

test("inactive Support cannot redeem", async () => {
  const {code} = await generate();
  await db.doc(`users/${SUPPORT}`).update({accountStatus: "inactive"});
  await denied(redeem(code), "permission-denied");
  await noLink();
});

test("invalid code format is rejected", async () => {
  await denied(redeem("12345"), "invalid-argument");
  await noLink();
});

test("missing code is rejected and counts as an attempt", async () => {
  await denied(redeem("999999"), "failed-precondition");

  const limit = await db.doc(`supportPairingLimits/${SUPPORT}`).get();
  assert.equal(limit.get("attempts"), 1);
  await noLink();
});

test("expired code cannot create a request", async () => {
  const {code} = await generate();
  await db.doc(`supportPairingCodes/${code}`).update({
    expiresAt: Timestamp.fromMillis(Date.now() - 1000),
  });

  await denied(redeem(code), "failed-precondition");
  await noLink();
});

test("regeneration invalidates the previous code", async () => {
  const first = await generate();

  // Move the generation timestamp back instead of waiting 30 seconds.
  await db.doc(`supportPairingUsers/${USER}`).update({
    lastGeneratedAt: Timestamp.fromMillis(Date.now() - 31000),
  });

  const second = await generate();
  assert.notEqual(first.code, second.code);

  await denied(redeem(first.code), "failed-precondition");
  await noLink();

  const result = await redeem(second.code);
  assert.equal(result.status, "pending");
});

test("User becoming inactive invalidates redemption", async () => {
  const {code} = await generate();
  await db.doc(`users/${USER}`).update({accountStatus: "inactive"});

  await denied(redeem(code), "failed-precondition");
  await noLink();
});

test("successful retry does not create another request or attempt", async () => {
  const {code} = await generate();
  const requestId = randomUUID();

  const first = await redeem(code, {requestId});
  const retry = await redeem(code, {requestId});

  assert.equal(retry.linkId, first.linkId);
  assert.equal(retry.duplicate, true);
  assert.equal((await db.collection("supportLinks").get()).size, 1);

  const limit = await db.doc(`supportPairingLimits/${SUPPORT}`).get();
  assert.equal(limit.get("attempts"), 1);
});

test("retry after removal does not recreate the connection", async () => {
  const {code} = await generate();
  const requestId = randomUUID();

  await redeem(code, {requestId});
  await db.doc(LINK).delete();

  const retry = await redeem(code, {requestId});
  assert.equal(retry.status, "removed");
  assert.equal(retry.duplicate, true);
  await noLink();
});

test("request ID cannot be reused for another code", async () => {
  const {code} = await generate();
  const requestId = randomUUID();
  await redeem(code, {requestId});

  const otherCode = code === "000000" ? "000001" : "000000";
  await denied(
    redeem(otherCode, {requestId}),
    "failed-precondition"
  );
});

test("a redeemed code cannot be used by another Support account", async () => {
  const {code} = await generate();
  await redeem(code);

  await denied(
    redeem(code, {uid: OTHER_SUPPORT}),
    "failed-precondition"
  );

  assert.equal((await db.collection("supportLinks").get()).size, 1);
});

for (const status of ["pending", "accepted", "rejected"]) {
  test(`redemption does not overwrite an existing ${status} link`, async () => {
    const {code} = await generate();

    await db.doc(LINK).set({
      userId: USER,
      supportId: SUPPORT,
      status,
      createdAt: Timestamp.fromMillis(1000),
    });

    await denied(redeem(code), "already-exists");

    const link = await db.doc(LINK).get();
    assert.equal(link.get("status"), status);
    assert.equal(link.get("createdAt").toMillis(), 1000);
  });
}

test("five failed attempts block the sixth attempt", async () => {
  for (let attempt = 0; attempt < 5; attempt++) {
    await denied(redeem("999999"), "failed-precondition");
  }

  await denied(redeem("999999"), "resource-exhausted");

  const limit = await db.doc(`supportPairingLimits/${SUPPORT}`).get();
  assert.equal(limit.get("attempts"), 5);
  await noLink();
});

test("attempts are allowed again after the rate-limit window", async () => {
  await db.doc(`supportPairingLimits/${SUPPORT}`).set({
    attempts: 5,
    windowStartedAt: Timestamp.fromMillis(Date.now() - 601000),
  });

  const {code} = await generate();
  const result = await redeem(code);

  assert.equal(result.status, "pending");

  const limit = await db.doc(`supportPairingLimits/${SUPPORT}`).get();
  assert.equal(limit.get("attempts"), 1);
});

test("two competing Support accounts cannot both redeem one code", async () => {
  const {code} = await generate();

  const results = await Promise.allSettled([
    redeem(code),
    redeem(code, {uid: OTHER_SUPPORT}),
  ]);

  assert.equal(
    results.filter((result) => result.status === "fulfilled").length,
    1
  );

  const failure = results.find((result) => result.status === "rejected");
  assert.equal(failure.reason.code, "failed-precondition");
  assert.equal((await db.collection("supportLinks").get()).size, 1);
});