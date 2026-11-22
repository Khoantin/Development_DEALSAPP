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

const SERVER_TIMESTAMP = {serverTimestamp: true};

function timestamp(millis) {
  return {toMillis: () => millis};
}

// -----------------------------------------------------------------
// In-memory Firestore fake (documents, subcollections, transactions)
// -----------------------------------------------------------------

function load({users = {}, docs = {}} = {}) {
  const store = new Map();
  for (const [id, data] of Object.entries(users)) store.set(`users/${id}`, data);
  for (const [docPath, data] of Object.entries(docs)) store.set(docPath, data);

  const log = {commits: 0};
  let autoId = 0;

  function snapshot(docPath) {
    const data = store.get(docPath);
    return {
      exists: data !== undefined,
      data: () => (data ? {...data} : undefined),
      get: (field) => (data ? data[field] : undefined),
    };
  }

  function collection(collectionPath) {
    return {
      doc: (id) => docRef(`${collectionPath}/${id ?? `auto-${++autoId}`}`),
    };
  }

  function docRef(docPath) {
    return {
      path: docPath,
      id: docPath.split("/").pop(),
      collection: (name) => collection(`${docPath}/${name}`),
    };
  }

  const resolve = (v) => (v === SERVER_TIMESTAMP ? "SERVER_TIMESTAMP" : v);

  const db = {
    collection,
    runTransaction: async (callback) => {
      const pending = [];
      let wrote = false;
      const write = (type) => (ref, data) => {
        wrote = true;
        pending.push({type, ref, data});
      };

      const result = await callback({
        get: async (ref) => {
          // Firestore requires all reads before any write.
          assert.equal(wrote, false, "transaction read after a write");
          return snapshot(ref.path);
        },
        set: write("set"),
        update: write("update"),
        create: write("create"),
      });

      for (const {type, ref, data} of pending) {
        const current = store.get(ref.path);
        const resolved = Object.fromEntries(
          Object.entries(data).map(([k, v]) => [k, resolve(v)])
        );
        if (type === "create") {
          assert.equal(current, undefined, "create over an existing document");
        }
        if (type === "update") {
          assert.notEqual(current, undefined, "update of a missing document");
        }
        store.set(ref.path, type === "update" ? {...current, ...resolved} : resolved);
      }
      log.commits++;
      return result;
    },
  };

  const sandbox = {
    exports: {},
    require: (name) => {
      if (name === "firebase-functions/v2/https") {
        return {onCall: (_o, handler) => handler, HttpsError: TestHttpsError};
      }
      if (name === "firebase-admin/firestore") {
        return {
          getFirestore: () => db,
          FieldValue: {serverTimestamp: () => SERVER_TIMESTAMP},
          Timestamp: {fromMillis: timestamp},
        };
      }
      if (name === "firebase-functions/logger") return {error: () => {}};
      throw new Error(`Unexpected dependency: ${name}`);
    },
  };

  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "staffMessaging.js"), "utf8"),
    sandbox
  );

  return {handlers: sandbox.exports, store, log};
}

// -----------------------------------------------------------------
// Test data and helpers
// -----------------------------------------------------------------

function account(role, accountStatus, displayName = `${role} name`) {
  return {role, accountStatus, displayName, email: `${role}@example.com`};
}

function baseUsers() {
  return {
    "admin-1": account("admin", "active", "Alice Admin"),
    "admin-2": account("admin", "active", "Bob Admin"),
    "admin-suspended": account("admin", "suspended"),
    "support-1": account("support", "active", "Sam Support"),
    "support-2": account("support", "active", "Pat Support"),
    "support-suspended": account("support", "suspended"),
    "support-disabled": account("support", "disabled"),
    "user-1": account("user", "active"),
  };
}

function setup() {
  return load({users: baseUsers()});
}

const send = (handlers, uid, data) =>
  handlers.sendStaffMessage({auth: {uid}, data});
const markRead = (handlers, uid, data = {}) =>
  handlers.markStaffConversationRead({auth: {uid}, data});

function under(store, prefix) {
  return [...store.entries()]
    .filter(([key]) => key.startsWith(prefix))
    .map(([, value]) => value);
}

const messages = (store, supportUid) =>
  under(store, `staffConversations/${supportUid}/messages/`);

async function expectCode(promise, code) {
  await assert.rejects(promise, (error) => {
    assert.equal(error.code, code, error.message);
    return true;
  });
}

// =================================================================
// Access control
// =================================================================

test("signed-out callers are rejected", async () => {
  const {handlers} = setup();
  await expectCode(handlers.sendStaffMessage({data: {text: "hi"}}), "unauthenticated");
  await expectCode(handlers.markStaffConversationRead({data: {}}), "unauthenticated");
});

for (const uid of ["user-1", "ghost"]) {
  test(`${uid} (not staff) is denied`, async () => {
    const {handlers, store} = setup();
    await expectCode(send(handlers, uid, {text: "hi"}), "permission-denied");
    await expectCode(
      send(handlers, uid, {supportUid: "support-1", text: "hi"}),
      "permission-denied"
    );
    await expectCode(markRead(handlers, uid), "permission-denied");
    assert.equal(under(store, "staffConversations/").length, 0);
  });
}

for (const uid of ["admin-suspended", "support-suspended", "support-disabled"]) {
  test(`${uid} is denied`, async () => {
    const {handlers, store} = setup();
    await expectCode(
      send(handlers, uid, {supportUid: "support-1", text: "hi"}),
      "permission-denied"
    );
    await expectCode(markRead(handlers, uid, {supportUid: "support-1"}), "permission-denied");
    assert.equal(under(store, "staffConversations/").length, 0);
  });
}

test("support can't target another support thread", async () => {
  const {handlers, store} = setup();
  await expectCode(
    send(handlers, "support-1", {supportUid: "support-2", text: "hi"}),
    "permission-denied"
  );
  await expectCode(
    markRead(handlers, "support-1", {supportUid: "support-2"}),
    "permission-denied"
  );
  assert.equal(under(store, "staffConversations/").length, 0);
});

test("support may pass their own uid or omit it", async () => {
  const {handlers} = setup();
  const a = await send(handlers, "support-1", {text: "one"});
  const b = await send(handlers, "support-1", {supportUid: "support-1", text: "two"});
  assert.equal(a.conversationId, "support-1");
  assert.equal(b.conversationId, "support-1");
});

test("admin must name a support account", async () => {
  const {handlers} = setup();
  await expectCode(send(handlers, "admin-1", {text: "hi"}), "invalid-argument");
  await expectCode(markRead(handlers, "admin-1"), "invalid-argument");
});

for (const target of ["user-1", "admin-2", "support-suspended", "support-disabled", "ghost"]) {
  test(`admin can't target ${target}`, async () => {
    const {handlers, store} = setup();
    await expectCode(
      send(handlers, "admin-1", {supportUid: target, text: "hi"}),
      "failed-precondition"
    );
    await expectCode(
      markRead(handlers, "admin-1", {supportUid: target}),
      "failed-precondition"
    );
    assert.equal(under(store, "staffConversations/").length, 0);
  });
}

// =================================================================
// Input validation
// =================================================================

for (const [label, data] of [
  ["missing text", {supportUid: "support-1"}],
  ["non-string text", {supportUid: "support-1", text: 5}],
  ["blank text", {supportUid: "support-1", text: "   \n "}],
  ["2001 characters", {supportUid: "support-1", text: "x".repeat(2001)}],
  ["unknown field", {supportUid: "support-1", text: "hi", senderRole: "support"}],
  ["bad supportUid", {supportUid: "a/b", text: "hi"}],
  ["empty supportUid", {supportUid: "", text: "hi"}],
]) {
  test(`send rejects ${label}`, async () => {
    const {handlers, store} = setup();
    await expectCode(send(handlers, "admin-1", data), "invalid-argument");
    assert.equal(under(store, "staffConversations/").length, 0);
  });
}

test("text is trimmed and 2000 characters is allowed", async () => {
  const {handlers, store} = setup();
  await send(handlers, "admin-1", {supportUid: "support-1", text: "  hello  "});
  await send(handlers, "admin-1", {supportUid: "support-1", text: "y".repeat(2000)});
  const texts = messages(store, "support-1").map((m) => m.text).sort();
  assert.deepEqual(texts, ["hello", "y".repeat(2000)]);
});

test("preview is capped at 120 characters", async () => {
  const {handlers, store} = setup();
  await send(handlers, "admin-1", {supportUid: "support-1", text: "z".repeat(500)});
  assert.equal(store.get("staffConversations/support-1").lastMessagePreview, "z".repeat(120));
});

// =================================================================
// Transaction contents and anonymity
// =================================================================

test("first admin message writes conversation, message and audit in one commit", async () => {
  const {handlers, store, log} = setup();
  const result = await send(handlers, "admin-1", {supportUid: "support-1", text: "Hello"});

  assert.equal(log.commits, 1);
  assert.equal(result.conversationId, "support-1");

  assert.deepEqual({...store.get("staffConversations/support-1")}, {
    supportUid: "support-1",
    supportDisplayName: "Sam Support",
    createdAt: "SERVER_TIMESTAMP",
    lastMessageAt: "SERVER_TIMESTAMP",
    lastMessagePreview: "Hello",
    lastSenderRole: "admin",
    supportUnread: 1,
    adminUnread: 0,
  });

  const message = store.get(`staffConversations/support-1/messages/${result.messageId}`);
  assert.deepEqual({...message}, {
    senderRole: "admin",
    text: "Hello",
    createdAt: "SERVER_TIMESTAMP",
  });

  const audit = under(store, "staffMessageAudit/");
  assert.equal(audit.length, 1);
  assert.deepEqual({...audit[0]}, {
    conversationId: "support-1",
    messageId: result.messageId,
    senderUid: "admin-1",
    senderRole: "admin",
    createdAt: "SERVER_TIMESTAMP",
  });
});

test("no admin uid or name appears outside the audit log", async () => {
  const {handlers, store} = setup();
  await send(handlers, "admin-1", {supportUid: "support-1", text: "a"});
  await send(handlers, "admin-2", {supportUid: "support-1", text: "b"});
  await send(handlers, "support-1", {text: "c"});
  await markRead(handlers, "admin-2", {supportUid: "support-1"});

  const visible = JSON.stringify(
    [...store.entries()].filter(([key]) => key.startsWith("staffConversations/"))
  );
  for (const secret of ["admin-1", "admin-2", "Alice Admin", "Bob Admin"]) {
    assert.ok(!visible.includes(secret), `${secret} leaked into a staff thread`);
  }
  for (const message of messages(store, "support-1")) {
    assert.deepEqual(Object.keys(message).sort(), ["createdAt", "senderRole", "text"]);
  }
  assert.deepEqual(
    under(store, "staffMessageAudit/").map((a) => a.senderUid).sort(),
    ["admin-1", "admin-2", "support-1"]
  );
});

test("support's first message creates their own thread", async () => {
  const {handlers, store} = setup();
  await send(handlers, "support-1", {text: "Question"});
  const conversation = store.get("staffConversations/support-1");
  assert.equal(conversation.lastSenderRole, "support");
  assert.equal(conversation.adminUnread, 1);
  assert.equal(conversation.supportUnread, 0);
});

// =================================================================
// Unread counts
// =================================================================

test("unread counts increment the other side and reset on read", async () => {
  const {handlers, store} = setup();
  const thread = () => store.get("staffConversations/support-1");

  await send(handlers, "admin-1", {supportUid: "support-1", text: "1"});
  await send(handlers, "admin-2", {supportUid: "support-1", text: "2"});
  assert.equal(thread().supportUnread, 2);
  assert.equal(thread().adminUnread, 0);

  await send(handlers, "support-1", {text: "3"});
  assert.equal(thread().supportUnread, 2);
  assert.equal(thread().adminUnread, 1);
  assert.equal(thread().lastSenderRole, "support");

  await markRead(handlers, "support-1");
  assert.equal(thread().supportUnread, 0);
  assert.equal(thread().adminUnread, 1);

  await markRead(handlers, "admin-1", {supportUid: "support-1"});
  assert.equal(thread().adminUnread, 0);

  // Creation fields don't change after the first message.
  assert.equal(thread().supportDisplayName, "Sam Support");
  assert.equal(messages(store, "support-1").length, 3);
});

test("mark read on a missing thread writes nothing", async () => {
  const {handlers, store} = setup();
  const result = await markRead(handlers, "support-1");
  assert.equal(result.conversationId, "support-1");
  assert.equal(store.has("staffConversations/support-1"), false);
});

// =================================================================
// Rate limit
// =================================================================

test("21st message in a minute is rejected and writes nothing", async () => {
  const {handlers, store} = setup();
  for (let i = 0; i < 20; i++) {
    await send(handlers, "support-1", {text: `m${i}`});
  }
  await expectCode(send(handlers, "support-1", {text: "too many"}), "resource-exhausted");

  assert.equal(messages(store, "support-1").length, 20);
  assert.equal(under(store, "staffMessageAudit/").length, 20);
  assert.equal(store.get("staffConversations/support-1").adminUnread, 20);
  assert.equal(store.get("staffMessageLimits/support-1").count, 20);
});

test("rate limit is per sender", async () => {
  const {handlers} = load({
    users: baseUsers(),
    docs: {"staffMessageLimits/admin-1": {windowStartedAt: timestamp(Date.now()), count: 20}},
  });
  await expectCode(
    send(handlers, "admin-1", {supportUid: "support-1", text: "hi"}),
    "resource-exhausted"
  );
  await send(handlers, "admin-2", {supportUid: "support-1", text: "hi"});
});

test("an expired window resets the counter", async () => {
  const {handlers, store} = load({
    users: baseUsers(),
    docs: {
      "staffMessageLimits/support-1": {
        windowStartedAt: timestamp(Date.now() - 61_000),
        count: 20,
      },
    },
  });
  await send(handlers, "support-1", {text: "hi"});
  assert.equal(store.get("staffMessageLimits/support-1").count, 1);
});
