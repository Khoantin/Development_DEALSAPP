const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

class TestHttpsError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

const NOW = 1_800_000_000_000;
const DAY = 24 * 60 * 60 * 1000;
const timestamp = (millis) => ({toMillis: () => millis});

// Fake Firestore: profiles can be read with get(), but event queries only
// support count(). Reading event documents would throw and fail the test.
function load({users = {}, events = []} = {}) {
  const aggregations = [];

  function query(filters) {
    return {
      where: (field, op, value) => query([...filters, {field, op, value}]),
      count: () => ({
        get: async () => {
          aggregations.push(filters);
          const matches = events.filter((event) => filters.every(({field, op, value}) => {
            const actual = event[field];
            if (op === "==") return actual === value;
            if (op === ">=") return actual !== undefined && actual.toMillis() >= value.toMillis();
            throw new Error(`Unexpected operator ${op}`);
          }));
          return {data: () => ({count: matches.length})};
        },
      }),
    };
  }

  const db = {
    collection: (name) => {
      if (name === "users") {
        return {doc: (id) => ({get: async () => {
          const data = users[id];
          return {exists: data !== undefined, get: (field) => data?.[field]};
        }})};
      }
      assert.equal(name, "emergencyEvents");
      return query([]);
    },
  };

  const sandbox = {
    exports: {},
    Date: {now: () => NOW},
    require: (name) => {
      if (name === "firebase-functions/v2/https") {
        return {onCall: (_o, handler) => handler, HttpsError: TestHttpsError};
      }
      if (name === "firebase-admin/firestore") {
        return {getFirestore: () => db, Timestamp: {fromMillis: timestamp}};
      }
      if (name === "firebase-functions/logger") return {error: () => {}};
      throw new Error(`Unexpected dependency: ${name}`);
    },
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "adminEmergencyStats.js"), "utf8"), sandbox
  );
  return {call: (uid) => sandbox.exports.getAdminEmergencyStats(uid ? {auth: {uid}, data: {}} : {data: {}}),
    aggregations};
}

const ADMIN = {admin: {role: "admin", accountStatus: "active"}};

function event(daysAgo, triggerType, status, extra = {}) {
  return {startedAt: timestamp(NOW - daysAgo * DAY), triggerType, status,
    ownerId: "owner-secret", recipientIds: ["recipient-secret"], message: "help-secret",
    location: {latitude: 53.5, longitude: -113.5}, ...extra};
}

async function rejectsWith(promise, code) {
  await assert.rejects(promise, (error) => error.code === code);
}

test("signed-out callers are rejected", async () => {
  await rejectsWith(load({users: ADMIN}).call(null), "unauthenticated");
});

test("non-admins and missing profiles are denied", async () => {
  const users = {
    user: {role: "user", accountStatus: "active"},
    support: {role: "support", accountStatus: "active"},
  };
  for (const uid of ["user", "support", "nobody"]) {
    const {call, aggregations} = load({users, events: [event(1, "voice_duress", "active")]});
    await rejectsWith(call(uid), "permission-denied");
    assert.equal(aggregations.length, 0, `${uid} ran no queries`);
  }
});

test("suspended and disabled admins are denied", async () => {
  for (const accountStatus of ["suspended", "disabled", "inactive"]) {
    const {call, aggregations} = load({users: {a: {role: "admin", accountStatus}}});
    await rejectsWith(call("a"), "permission-denied");
    assert.equal(aggregations.length, 0);
  }
});

test("counts events by status, window and trigger", async () => {
  const {call} = load({users: ADMIN, events: [
    event(0, "emergency_button", "active"),
    event(3, "voice_duress", "active"),
    event(6.9, "emergency_button", "cancelled"),
    event(10, "voice_duress", "cancelled"),
    event(29.9, "emergency_button", "active"),
    event(31, "voice_duress", "active"), // still active, but older than 30 days
    event(45, "emergency_button", "cancelled"),
    {triggerType: "voice_duress", status: "active"}, // no startedAt yet
  ]});

  // JSON round-trip: the result object comes from the vm realm.
  assert.deepEqual(JSON.parse(JSON.stringify(await call("admin"))), {
    activeNow: 5,
    startedLast7Days: 3,
    startedLast30Days: 5,
    cancelledLast30Days: 2,
    byTrigger: {emergency_button: 3, voice_duress: 2},
    generatedAtMillis: NOW,
  });
});

test("an empty collection returns zeros", async () => {
  const result = await load({users: ADMIN}).call("admin");
  assert.equal(result.activeNow + result.startedLast7Days + result.startedLast30Days +
    result.cancelledLast30Days + result.byTrigger.emergency_button + result.byTrigger.voice_duress, 0);
});

test("returns only counts, never event details", async () => {
  const {call, aggregations} = load({users: ADMIN, events: [event(1, "emergency_button", "active")]});
  const result = await call("admin");

  // JSON round-trip: the result object comes from the vm realm.
  const plain = JSON.parse(JSON.stringify(result));
  assert.deepEqual(Object.keys(plain).sort(), ["activeNow", "byTrigger", "cancelledLast30Days",
    "generatedAtMillis", "startedLast30Days", "startedLast7Days"]);
  assert.deepEqual(Object.keys(plain.byTrigger).sort(), ["emergency_button", "voice_duress"]);
  for (const value of [...Object.values(plain), ...Object.values(plain.byTrigger)]) {
    if (typeof value === "object") continue;
    assert.equal(typeof value, "number");
  }
  const serialized = JSON.stringify(plain);
  for (const secret of ["owner-secret", "recipient-secret", "help-secret", "53.5"]) {
    assert.ok(!serialized.includes(secret), `leaked ${secret}`);
  }
  assert.equal(aggregations.length, 6, "six count() aggregations, no document reads");
});

test("query failures become a generic internal error", async () => {
  const {call} = load({users: ADMIN, events: [{startedAt: "not a timestamp"}]});
  await rejectsWith(call("admin"), "internal");
});
