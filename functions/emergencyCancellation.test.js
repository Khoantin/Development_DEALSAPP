const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const EVENT_ID = "sender_12345678-1234-4123-8123-123456789abc";
const EVENT = `emergencyEvents/${EVENT_ID}`;
const NOTICE = `users/recipient/emergencyNotifications/${EVENT_ID}`;
class HttpsError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function fixture() {
  const records = new Map([
    [EVENT, {ownerId: "sender", recipientIds: ["recipient"], triggerType: "emergency_button",
      status: "active", message: "TEST", location: {latitude: 53, longitude: -113}, startedAt: "original"}],
    [NOTICE, {eventId: EVENT_ID, senderUid: "sender", type: "emergency_button", message: "TEST",
      read: true, createdAt: "original", location: {latitude: 53, longitude: -113}}],
  ]);
  let writes = 0;
  function ref(p) { return {path: p, collection: (name) => ({doc: (id) => ref(`${p}/${name}/${id}`)})}; }
  const db = {
    collection: (name) => ({doc: (id) => ref(`${name}/${id}`)}),
    async runTransaction(callback) {
      const pending = [];
      const result = await callback({
        async get(reference) {
          assert.equal(pending.length, 0, "All reads must precede writes");
          const data = records.get(reference.path);
          return {exists: !!data, get: (key) => data?.[key]};
        },
        update(reference, data) { pending.push([reference.path, data]); },
      });
      for (const [p] of pending) assert.ok(records.has(p));
      for (const [p, data] of pending) { records.set(p, {...records.get(p), ...data}); writes++; }
      return result;
    },
  };
  const sandbox = {exports: {}, Buffer, require(name) {
    if (name === "firebase-functions/v2/https") return {onCall: (_, handler) => handler, HttpsError};
    if (name === "firebase-admin/firestore") return {getFirestore: () => db, FieldValue: {serverTimestamp: () => "cancel-time"}};
    if (name === "firebase-functions/logger") return {error() {}};
    throw new Error(name);
  }};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, "emergencyCancellation.js"), "utf8"), sandbox);
  return {records, writes: () => writes, cancel: (overrides = {}) => sandbox.exports.cancelEmergencyInAppAlert({
    auth: {uid: "sender"}, data: {eventId: EVENT_ID}, ...overrides,
  })};
}
async function denied(f, code, overrides) {
  const before = JSON.stringify([...f.records]);
  await assert.rejects(() => f.cancel(overrides), (error) => error.code === code);
  assert.equal(JSON.stringify([...f.records]), before);
  assert.equal(f.writes(), 0);
}
test("owner cancellation updates event and notification, preserving content and read state", async () => {
  const f = fixture(); const result = await f.cancel();
  assert.equal(result.state, "cancelled"); assert.equal(result.alreadyCancelled, false);
  assert.equal(result.inAppRecordCount, 1);
  for (const p of [EVENT, NOTICE]) {
    assert.equal(f.records.get(p).status, "cancelled");
    assert.equal(f.records.get(p).message, "TEST");
    assert.deepEqual(f.records.get(p).location, {latitude: 53, longitude: -113});
    assert.equal(f.records.get(p).cancelledAt, "cancel-time");
  }
  assert.equal(f.records.get(NOTICE).read, true);
  assert.equal(f.records.get(NOTICE).createdAt, "original");
  assert.equal(f.records.get(EVENT).startedAt, "original");
});
test("repeated cancellation is idempotent and does not write again", async () => {
  const f = fixture(); await f.cancel(); const writes = f.writes(); const before = JSON.stringify([...f.records]);
  assert.equal((await f.cancel()).alreadyCancelled, true);
  assert.equal(f.writes(), writes); assert.equal(JSON.stringify([...f.records]), before);
});
test("signed-out caller denied", async () => denied(fixture(), "unauthenticated", {auth: null}));
test("recipient cannot cancel sender's event", async () => denied(fixture(), "permission-denied", {auth: {uid: "recipient"}}));
test("unrelated account cannot cancel", async () => denied(fixture(), "permission-denied", {auth: {uid: "stranger"}}));
test("missing event denied without revealing existence", async () => denied(fixture(), "permission-denied", {data: {eventId: "missing"}}));
test("arbitrary owner and recipients in payload rejected", async () => denied(fixture(), "invalid-argument", {data: {eventId: EVENT_ID, ownerId: "sender", recipientIds: ["stranger"]}}));
test("path traversal rejected", async () => denied(fixture(), "invalid-argument", {data: {eventId: "a/b"}}));
test("voice duress events not modified", async () => { const f = fixture(); f.records.get(EVENT).triggerType = "voice_duress"; await denied(f, "failed-precondition"); });
test("unsupported event state rejected", async () => { const f = fixture(); f.records.get(EVENT).status = "resolved"; await denied(f, "failed-precondition"); });
test("missing notification prevents partial cancellation", async () => { const f = fixture(); f.records.delete(NOTICE); await denied(f, "failed-precondition"); });
test("notification belonging to another sender prevents writes", async () => { const f = fixture(); f.records.get(NOTICE).senderUid = "other"; await denied(f, "failed-precondition"); });
test("all original recipients are updated", async () => {
  const f = fixture(); const second = `users/recipient2/emergencyNotifications/${EVENT_ID}`;
  f.records.get(EVENT).recipientIds.push("recipient2"); f.records.set(second, {...f.records.get(NOTICE), read: false});
  assert.equal((await f.cancel()).inAppRecordCount, 2);
  assert.equal(f.records.get(second).status, "cancelled"); assert.equal(f.records.get(second).read, false);
});
test("legacy cancelled event repairs notifications without changing its cancellation timestamp", async () => {
  const f = fixture(); f.records.get(EVENT).status = "cancelled"; f.records.get(EVENT).cancelledAt = "earlier";
  assert.equal((await f.cancel()).alreadyCancelled, true);
  assert.equal(f.records.get(NOTICE).status, "cancelled"); assert.equal(f.records.get(NOTICE).cancelledAt, "earlier");
});
test("invalid recipient list prevents writes", async () => { const f = fixture(); f.records.get(EVENT).recipientIds = ["recipient", "recipient"]; await denied(f, "failed-precondition"); });
test("cancellation works without current contact or receiving-preference records", async () => {
  const f = fixture(); assert.equal(f.records.has("users/sender/trustedContacts/contact1"), false);
  assert.equal((await f.cancel()).state, "cancelled");
});