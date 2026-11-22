const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const REQUEST_ID = "12345678-1234-4123-8123-123456789abc";
const EVENT_PATH = `emergencyEvents/sender_${REQUEST_ID}`;
const NOTIFICATION_PATH = `users/recipient/emergencyNotifications/sender_${REQUEST_ID}`;
class HttpsError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function fixture() {
  const records = new Map([
    ["users/sender", {accountStatus: "active"}],
    ["users/sender/emergencySettings/preset", {message: "TEST: Please check on me.", receiveInApp: false}],
    ["users/sender/trustedContacts/contact1", {linkedUserId: "recipient", emergencyContact: true, emergencyInAppOptIn: true}],
    ["users/recipient", {accountStatus: "active"}],
    ["users/recipient/emergencySettings/preset", {message: "My preset", receiveInApp: true}],
  ]);
  function reference(documentPath) {
    return {path: documentPath, collection: (name) => ({doc: (id) => reference(`${documentPath}/${name}/${id}`)})};
  }
  const db = {
    collection: (name) => ({doc: (id) => reference(`${name}/${id}`)}),
    async runTransaction(callback) {
      const pending = [];
      const transaction = {
        async get(ref) {
          assert.equal(pending.length, 0, "Firestore requires reads before writes");
          const value = records.get(ref.path);
          return {exists: records.has(ref.path), data: () => value, get: (field) => value?.[field]};
        },
        create(ref, value) { pending.push({kind: "create", path: ref.path, value}); },
        set(ref, value) { pending.push({kind: "set", path: ref.path, value}); },
      };
      const result = await callback(transaction);
      for (const write of pending) {
        if (write.kind === "create" && records.has(write.path)) throw new Error("Document already exists.");
      }
      for (const write of pending) records.set(write.path, write.value);
      return result;
    },
  };
  const sandbox = {exports: {}, Buffer, require(name) {
    if (name === "firebase-functions/v2/https") return {onCall: (_options, handler) => handler, HttpsError};
    if (name === "firebase-admin/firestore") return {getFirestore: () => db,
      FieldValue: {serverTimestamp: () => ({toMillis: () => Date.now()})}};
    if (name === "firebase-functions/logger") return {error() {}};
    throw new Error(`Unexpected import: ${name}`);
  }};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, "emergencyAlerts.js"), "utf8"), sandbox, {filename: "emergencyAlerts.js"});
  return {records, send: (overrides = {}) => sandbox.exports.sendEmergencyInAppAlert({
    auth: {uid: "sender"}, data: {requestId: REQUEST_ID, contactIds: ["contact1"]}, ...overrides,
  })};
}
const location = () => ({latitude: 53.5461, longitude: -113.4938, accuracyMeters: 25, capturedAtMillis: Date.now() - 1000});
const dataWith = (snapshot) => ({data: {requestId: REQUEST_ID, contactIds: ["contact1"], location: snapshot}});
async function expectDenied(f, code, overrides = {}) {
  const before = [...f.records.entries()];
  await assert.rejects(() => f.send(overrides), (error) => error.code === code);
  assert.deepEqual([...f.records.entries()], before);
}

test("valid no-location request creates an event and private notification", async () => {
  const f = fixture(); const result = await f.send();
  assert.equal(result.inAppRecordCount, 1); assert.equal(result.duplicate, false);
  const event = f.records.get(EVENT_PATH); const notice = f.records.get(NOTIFICATION_PATH);
  assert.equal(event.ownerId, "sender"); assert.equal(event.triggerType, "emergency_button");
  assert.equal(event.message, "TEST: Please check on me."); assert.equal(notice.read, false);
  assert.equal(notice.senderUid, "sender"); assert.equal(notice.message, event.message);
  assert.equal(Object.hasOwn(event, "location"), false); assert.equal(Object.hasOwn(notice, "location"), false);
});
test("signed-out sender denied", async () => expectDenied(fixture(), "unauthenticated", {auth: null}));
test("inactive sender denied", async () => { const f = fixture(); f.records.set("users/sender", {accountStatus: "inactive"}); await expectDenied(f, "permission-denied"); });
test("contact outside sender list denied", async () => expectDenied(fixture(), "failed-precondition", {data: {requestId: REQUEST_ID, contactIds: ["someoneElse"]}}));
test("emergency designation required", async () => { const f = fixture(); f.records.get("users/sender/trustedContacts/contact1").emergencyContact = false; await expectDenied(f, "failed-precondition"); });
test("sender in-app opt-in required", async () => { const f = fixture(); f.records.get("users/sender/trustedContacts/contact1").emergencyInAppOptIn = false; await expectDenied(f, "failed-precondition"); });
test("recipient receiving opt-in required", async () => { const f = fixture(); f.records.get("users/recipient/emergencySettings/preset").receiveInApp = false; await expectDenied(f, "failed-precondition"); });
test("inactive recipient denied", async () => { const f = fixture(); f.records.set("users/recipient", {accountStatus: "inactive"}); await expectDenied(f, "failed-precondition"); });
test("blank message denied", async () => { const f = fixture(); f.records.get("users/sender/emergencySettings/preset").message = " "; await expectDenied(f, "failed-precondition"); });
test("same request retry creates no extra records", async () => {
  const f = fixture(); await f.send(); const before = [...f.records.entries()];
  assert.equal((await f.send()).duplicate, true); assert.deepEqual([...f.records.entries()], before);
});
test("different contacts with same ID denied", async () => { const f = fixture(); await f.send(); await expectDenied(f, "failed-precondition", {data: {requestId: REQUEST_ID, contactIds: ["differentContact"]}}); });
test("new alert during cooldown denied", async () => { const f = fixture(); await f.send(); await expectDenied(f, "resource-exhausted", {data: {requestId: "22345678-1234-4123-8123-123456789abc", contactIds: ["contact1"]}}); });
test("multiple contacts linked to one recipient deduplicated", async () => {
  const f = fixture(); f.records.set("users/sender/trustedContacts/contact2", {linkedUserId: "recipient", emergencyContact: true, emergencyInAppOptIn: true});
  assert.equal((await f.send({data: {requestId: REQUEST_ID, contactIds: ["contact1", "contact2"]}})).inAppRecordCount, 1);
});
test("ineligible contact prevents partial creation", async () => expectDenied(fixture(), "failed-precondition", {data: {requestId: REQUEST_ID, contactIds: ["contact1", "missing"]}}));
test("arbitrary destination rejected", async () => expectDenied(fixture(), "invalid-argument", {data: {requestId: REQUEST_ID, contactIds: ["contact1"], recipientIds: ["arbitrary"]}}));

test("location is attached identically to event and private notification", async () => {
  const f = fixture(); const snapshot = location(); await f.send(dataWith(snapshot));
  assert.equal(JSON.stringify(f.records.get(EVENT_PATH).location), JSON.stringify(snapshot));
  assert.equal(JSON.stringify(f.records.get(NOTIFICATION_PATH).location), JSON.stringify(snapshot));
  assert.equal(f.records.has(`users/sender/emergencyNotifications/sender_${REQUEST_ID}`), false);
});
test("zero coordinates and zero accuracy are valid", async () => {
  const f = fixture(); await f.send(dataWith({...location(), latitude: 0, longitude: 0, accuracyMeters: 0}));
  assert.equal(f.records.get(EVENT_PATH).location.latitude, 0);
});
test("reordered location properties still retry idempotently", async () => {
  const f = fixture(); const loc = location(); await f.send(dataWith(loc)); const before = [...f.records.entries()];
  const reordered = {capturedAtMillis: loc.capturedAtMillis, accuracyMeters: loc.accuracyMeters, longitude: loc.longitude, latitude: loc.latitude};
  assert.equal((await f.send(dataWith(reordered))).duplicate, true);
  assert.deepEqual([...f.records.entries()], before);
});
test("changed coordinates cannot overwrite an existing alert", async () => {
  const f = fixture(); const loc = location(); await f.send(dataWith(loc));
  await expectDenied(f, "failed-precondition", dataWith({...loc, latitude: 54}));
});
test("changed capture time cannot overwrite an existing alert", async () => {
  const f = fixture(); const loc = location(); await f.send(dataWith(loc));
  await expectDenied(f, "failed-precondition", dataWith({...loc, capturedAtMillis: loc.capturedAtMillis - 1000}));
});
test("location cannot be removed on retry", async () => { const f = fixture(); await f.send(dataWith(location())); await expectDenied(f, "failed-precondition"); });
test("location cannot be added to legacy no-location alert", async () => { const f = fixture(); await f.send(); await expectDenied(f, "failed-precondition", dataWith(location())); });
test("explicit null remains compatible with legacy alerts", async () => { const f = fixture(); await f.send(); assert.equal((await f.send(dataWith(null))).duplicate, true); });
test("an older pending snapshot retains original time rather than appearing current", async () => {
  const f = fixture(); const loc = {...location(), capturedAtMillis: Date.now() - 86400000};
  await f.send(dataWith(loc)); assert.equal(f.records.get(NOTIFICATION_PATH).location.capturedAtMillis, loc.capturedAtMillis);
});
test("location does not bypass recipient consent", async () => {
  const f = fixture(); f.records.get("users/recipient/emergencySettings/preset").receiveInApp = false;
  await expectDenied(f, "failed-precondition", dataWith(location()));
});
for (const [label, patch] of [
  ["latitude range", {latitude: 91}], ["longitude range", {longitude: -181}],
  ["NaN", {latitude: NaN}], ["infinity", {longitude: Infinity}],
  ["numeric string", {latitude: "53"}], ["negative accuracy", {accuracyMeters: -1}],
  ["unbounded accuracy", {accuracyMeters: 100001}], ["fractional timestamp", {capturedAtMillis: 1.5}],
  ["zero timestamp", {capturedAtMillis: 0}], ["future timestamp", {capturedAtMillis: Date.now() + 3600000}],
  ["unknown property", {recipientUid: "other"}],
]) {
  test(`rejects ${label} without writes`, async () => expectDenied(fixture(), "invalid-argument", dataWith({...location(), ...patch})));
}
test("missing location field rejected", async () => { const loc = location(); delete loc.accuracyMeters; await expectDenied(fixture(), "invalid-argument", dataWith(loc)); });
test("array location rejected", async () => expectDenied(fixture(), "invalid-argument", dataWith([53, -113])));
