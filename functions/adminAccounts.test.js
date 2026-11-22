const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

class TestHttpsError extends Error {
  constructor(code, message, details) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

const SERVER_TIMESTAMP = {serverTimestamp: true};
const DOCUMENT_ID = "__name__";

function timestamp(millis) {
  return {toMillis: () => millis};
}

// -----------------------------------------------------------------
// In-memory Firestore and Auth fakes
// -----------------------------------------------------------------

function createFakes({
  users = {},
  docs = {},
  authFailures = 0,
  authMissing = false,
  activeAdminQueryOverride = null,
} = {}) {
  const store = new Map();
  for (const [id, data] of Object.entries(users)) {
    store.set(`users/${id}`, {...data});
  }
  for (const [docPath, data] of Object.entries(docs)) {
    store.set(docPath, {...data});
  }

  const log = {selects: [], commits: 0, authCalls: []};
  let autoId = 0;
  let remainingAuthFailures = authFailures;

  function snapshotFor(docPath, fields = null) {
    const data = store.get(docPath);
    const id = docPath.split("/").pop();
    const visible = data && fields
      ? Object.fromEntries(
        Object.entries(data).filter(([key]) => fields.includes(key))
      )
      : data;

    return {
      id,
      exists: data !== undefined,
      data: () => (visible ? {...visible} : undefined),
      get: (field) => (visible ? visible[field] : undefined),
    };
  }

  function docRef(docPath) {
    return {
      kind: "doc",
      path: docPath,
      id: docPath.split("/").pop(),
      get: async () => snapshotFor(docPath),
    };
  }

  function query(collectionPath, state = {
    filters: [], ordered: false, after: null, fields: null, max: null,
  }) {
    const next = (change) => query(collectionPath, {...state, ...change});

    return {
      kind: "query",
      collectionPath,
      state,
      where: (field, op, value) => {
        assert.equal(op, "==");
        return next({filters: [...state.filters, [field, value]]});
      },
      orderBy: (field) => {
        assert.equal(field, DOCUMENT_ID);
        return next({ordered: true});
      },
      startAfter: (id) => next({after: id}),
      select: (...fields) => {
        log.selects.push(fields);
        return next({fields});
      },
      limit: (max) => next({max}),
      get: async () => runQuery(collectionPath, state),
    };
  }

  function runQuery(collectionPath, state) {
    let ids = [...store.keys()]
      .filter((key) => key.startsWith(`${collectionPath}/`))
      .map((key) => key.slice(collectionPath.length + 1))
      .filter((id) => !id.includes("/"))
      .filter((id) => state.filters.every(
        ([field, value]) => store.get(`${collectionPath}/${id}`)[field] === value
      ))
      .sort();

    if (state.after !== null) ids = ids.filter((id) => id > state.after);
    if (state.max !== null) ids = ids.slice(0, state.max);

    const found = ids.map((id) => snapshotFor(`${collectionPath}/${id}`, state.fields));
    return {size: found.length, empty: found.length === 0, docs: found};
  }

  function collection(collectionPath) {
    const base = query(collectionPath);
    return {
      ...base,
      doc: (id) => docRef(`${collectionPath}/${id ?? `auto-${++autoId}`}`),
    };
  }

  function resolve(value) {
    return value === SERVER_TIMESTAMP ? "SERVER_TIMESTAMP" : value;
  }

  const db = {
    collection,
    runTransaction: async (callback) => {
      const pending = [];

      const transaction = {
        get: async (target) => {
          if (target.kind === "doc") return target.get();

          // Lets a test simulate the active-admin query returning only the
          // target, e.g. a concurrent change to the caller's own status.
          if (
            activeAdminQueryOverride &&
            target.state.filters.some(([f, v]) => f === "role" && v === "admin")
          ) {
            return activeAdminQueryOverride(target);
          }
          return target.get();
        },
        update: (ref, data) => pending.push({type: "update", ref, data}),
        create: (ref, data) => pending.push({type: "create", ref, data}),
      };

      const result = await callback(transaction);

      // Commit only after the callback succeeds, like a real transaction.
      for (const write of pending) {
        const current = store.get(write.ref.path);
        const data = Object.fromEntries(
          Object.entries(write.data).map(([k, v]) => [k, resolve(v)])
        );

        if (write.type === "create") {
          assert.equal(current, undefined, "create over an existing document");
          store.set(write.ref.path, data);
        } else {
          assert.notEqual(current, undefined, "update of a missing document");
          store.set(write.ref.path, {...current, ...data});
        }
      }

      log.commits++;
      return result;
    },
  };

  const auth = {
    updateUser: async (uid, change) => {
      log.authCalls.push(["updateUser", uid, change]);

      if (authMissing) {
        const error = new Error("missing");
        error.code = "auth/user-not-found";
        throw error;
      }
      if (remainingAuthFailures > 0) {
        remainingAuthFailures--;
        const error = new Error("Auth backend unavailable");
        error.code = "auth/internal-error";
        throw error;
      }
    },
    revokeRefreshTokens: async (uid) => {
      log.authCalls.push(["revokeRefreshTokens", uid]);
    },
  };

  return {db, auth, store, log};
}

function load(config) {
  const fakes = createFakes(config);

  const sandbox = {
    exports: {},
    require: (name) => {
      if (name === "firebase-functions/v2/https") {
        return {
          onCall: (_options, handler) => handler,
          HttpsError: TestHttpsError,
        };
      }
      if (name === "firebase-admin/firestore") {
        return {
          getFirestore: () => fakes.db,
          FieldValue: {serverTimestamp: () => SERVER_TIMESTAMP},
          FieldPath: {documentId: () => DOCUMENT_ID},
        };
      }
      if (name === "firebase-admin/auth") {
        return {getAuth: () => fakes.auth};
      }
      if (name === "firebase-functions/logger") {
        return {error: () => {}, warn: () => {}};
      }
      throw new Error(`Unexpected dependency: ${name}`);
    },
  };

  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "adminAccounts.js"), "utf8"),
    sandbox
  );

  return {handlers: sandbox.exports, ...fakes};
}

// -----------------------------------------------------------------
// Test data
// -----------------------------------------------------------------

function account(role, accountStatus, extra = {}) {
  return {
    displayName: `${role} person`,
    legalName: "Private Legal Name",
    email: `${role}@example.com`,
    phoneNumber: "+17805550100",
    address: "1 Private St",
    regionId: "CA-AB",
    uploadedDocuments: ["passport.pdf"],
    role,
    accountStatus,
    createdAt: timestamp(1_700_000_000_000),
    ...extra,
  };
}

function baseUsers() {
  return {
    "admin-1": account("admin", "active"),
    "admin-2": account("admin", "active"),
    "user-1": account("user", "active"),
    "support-1": account("support", "active"),
    "admin-suspended": account("admin", "suspended"),
  };
}

function statusRequest(overrides = {}) {
  return {
    auth: {uid: "admin-1"},
    data: {
      targetUid: "user-1",
      newStatus: "suspended",
      reason: "Repeated abusive messages",
      ...overrides,
    },
  };
}

function auditRecords(store) {
  return [...store.entries()]
    .filter(([key]) => key.startsWith("adminAuditLog/"))
    .map(([, value]) => value);
}

// Objects created inside the vm sandbox come from another realm, so
// deepStrictEqual needs plain copies to compare them.
function plain(value) {
  return JSON.parse(JSON.stringify(value));
}

async function expectCode(promise, code) {
  await assert.rejects(promise, (error) => {
    assert.equal(error.code, code, error.message);
    return true;
  });
}

// =================================================================
// listAdminAccounts
// =================================================================

test("list: signed-out caller is rejected", async () => {
  const {handlers} = load({users: baseUsers()});
  await expectCode(handlers.listAdminAccounts({data: {}}), "unauthenticated");
});

for (const uid of ["user-1", "support-1", "admin-suspended"]) {
  test(`list: ${uid} is not an active admin and is denied`, async () => {
    const {handlers} = load({users: baseUsers()});
    await expectCode(
      handlers.listAdminAccounts({auth: {uid}, data: {}}),
      "permission-denied"
    );
  });
}

test("list: caller without a profile is denied", async () => {
  const {handlers} = load({users: baseUsers()});
  await expectCode(
    handlers.listAdminAccounts({auth: {uid: "ghost"}, data: {}}),
    "permission-denied"
  );
});

test("list: returns only the allowed fields", async () => {
  const users = baseUsers();
  users["user-1"].statusUpdatedAt = timestamp(1_800_000_000_000);
  users["user-1"].statusUpdatedBy = "admin-2";
  users["user-1"].roleUpdatedAt = timestamp(1_850_000_000_000);
  users["user-1"].roleUpdatedBy = "admin-2";
  users["user-1"].locations = [{lat: 1, lng: 2}];

  const {handlers, log} = load({users});
  const result = await handlers.listAdminAccounts({auth: {uid: "admin-1"}, data: {}});

  const allowed = [
    "uid", "displayName", "email", "role",
    "accountStatus", "createdAt", "statusUpdatedAt", "roleUpdatedAt",
  ];

  for (const item of result.accounts) {
    assert.deepEqual(Object.keys(item).sort(), [...allowed].sort());
  }

  const user = result.accounts.find((a) => a.uid === "user-1");
  assert.equal(user.email, "user@example.com");
  assert.equal(user.createdAt, 1_700_000_000_000);
  assert.equal(user.statusUpdatedAt, 1_800_000_000_000);
  assert.equal(user.roleUpdatedAt, 1_850_000_000_000);

  const serialized = JSON.stringify(result);
  for (const secret of ["Private St", "+1780", "passport", "Legal", "lat", "statusUpdatedBy", "roleUpdatedBy"]) {
    assert.equal(serialized.includes(secret), false, `leaked ${secret}`);
  }

  // The query itself only reads the listed fields.
  assert.deepEqual(plain(log.selects), [[
    "displayName", "email", "role", "accountStatus", "createdAt",
    "statusUpdatedAt", "roleUpdatedAt",
  ]]);
});

test("list: missing timestamps become null", async () => {
  const users = baseUsers();
  delete users["user-1"].createdAt;

  const {handlers} = load({users});
  const result = await handlers.listAdminAccounts({auth: {uid: "admin-1"}, data: {}});
  const user = result.accounts.find((a) => a.uid === "user-1");

  assert.equal(user.createdAt, null);
  assert.equal(user.statusUpdatedAt, null);
  assert.equal(user.roleUpdatedAt, null);
});

test("list: role and status filters are applied", async () => {
  const {handlers} = load({users: baseUsers()});

  const admins = await handlers.listAdminAccounts({
    auth: {uid: "admin-1"}, data: {role: "admin"},
  });
  assert.deepEqual(admins.accounts.map((a) => a.uid), ["admin-1", "admin-2", "admin-suspended"]);

  const suspendedAdmins = await handlers.listAdminAccounts({
    auth: {uid: "admin-1"}, data: {role: "admin", status: "suspended"},
  });
  assert.deepEqual(suspendedAdmins.accounts.map((a) => a.uid), ["admin-suspended"]);
});

for (const data of [
  {role: "superuser"},
  {status: "deleted"},
  {role: 7},
  {cursor: "a/b"},
  {extra: true},
]) {
  test(`list: invalid request ${JSON.stringify(data)} is rejected`, async () => {
    const {handlers} = load({users: baseUsers()});
    await expectCode(
      handlers.listAdminAccounts({auth: {uid: "admin-1"}, data}),
      "invalid-argument"
    );
  });
}

test("list: paginates 25 at a time with a cursor", async () => {
  const users = {"admin-1": account("admin", "active")};
  for (let i = 0; i < 30; i++) {
    users[`user-${String(i).padStart(2, "0")}`] = account("user", "active");
  }

  const {handlers} = load({users});

  const first = await handlers.listAdminAccounts({
    auth: {uid: "admin-1"}, data: {role: "user"},
  });
  assert.equal(first.accounts.length, 25);
  assert.equal(first.nextCursor, "user-24");

  const second = await handlers.listAdminAccounts({
    auth: {uid: "admin-1"}, data: {role: "user", cursor: first.nextCursor},
  });
  assert.equal(second.accounts.length, 5);
  assert.equal(second.nextCursor, null);

  const all = [...first.accounts, ...second.accounts].map((a) => a.uid);
  assert.equal(new Set(all).size, 30);
});

test("list: exactly one page has no next cursor", async () => {
  const users = {"admin-1": account("admin", "active")};
  for (let i = 0; i < 24; i++) users[`user-${i}`] = account("user", "active");

  const {handlers} = load({users});
  const result = await handlers.listAdminAccounts({auth: {uid: "admin-1"}, data: {}});

  assert.equal(result.accounts.length, 25);
  assert.equal(result.nextCursor, null);
});

// =================================================================
// setAccountStatus: access
// =================================================================

test("status: signed-out caller is rejected", async () => {
  const {handlers, log} = load({users: baseUsers()});
  const request = statusRequest();
  delete request.auth;

  await expectCode(handlers.setAccountStatus(request), "unauthenticated");
  assert.equal(log.authCalls.length, 0);
});

for (const uid of ["user-1", "support-1", "admin-suspended"]) {
  test(`status: ${uid} is not an active admin and changes nothing`, async () => {
    const {handlers, store, log} = load({users: baseUsers()});

    await expectCode(
      handlers.setAccountStatus({...statusRequest({targetUid: "admin-2"}), auth: {uid}}),
      "permission-denied"
    );

    assert.equal(store.get("users/admin-2").accountStatus, "active");
    assert.equal(auditRecords(store).length, 0);
    assert.equal(log.authCalls.length, 0);
  });
}

test("status: admin cannot change their own account", async () => {
  const {handlers, store, log} = load({users: baseUsers()});

  await expectCode(
    handlers.setAccountStatus(statusRequest({targetUid: "admin-1"})),
    "failed-precondition"
  );

  assert.equal(store.get("users/admin-1").accountStatus, "active");
  assert.equal(auditRecords(store).length, 0);
  assert.equal(log.authCalls.length, 0);
});

test("status: missing target account is rejected", async () => {
  const {handlers, store, log} = load({users: baseUsers()});

  await expectCode(
    handlers.setAccountStatus(statusRequest({targetUid: "nobody"})),
    "not-found"
  );

  assert.equal(store.has("users/nobody"), false);
  assert.equal(auditRecords(store).length, 0);
  assert.equal(log.authCalls.length, 0);
});

// =================================================================
// setAccountStatus: validation
// =================================================================

for (const newStatus of ["inactive", "admin", "", undefined, 3]) {
  test(`status: newStatus ${JSON.stringify(newStatus)} is rejected`, async () => {
    const {handlers, store} = load({users: baseUsers()});
    await expectCode(
      handlers.setAccountStatus(statusRequest({newStatus})),
      "invalid-argument"
    );
    assert.equal(auditRecords(store).length, 0);
  });
}

for (const [label, reason] of [
  ["missing", undefined],
  ["not text", 42],
  ["too short", "abcd"],
  ["short after trimming", "   ab    "],
  ["too long", "x".repeat(501)],
]) {
  test(`status: reason ${label} is rejected`, async () => {
    const {handlers, store, log} = load({users: baseUsers()});
    await expectCode(
      handlers.setAccountStatus(statusRequest({reason})),
      "invalid-argument"
    );
    assert.equal(store.get("users/user-1").accountStatus, "active");
    assert.equal(auditRecords(store).length, 0);
    assert.equal(log.authCalls.length, 0);
  });
}

test("status: reason at the limits is accepted and stored trimmed", async () => {
  const {handlers, store} = load({users: baseUsers()});

  await handlers.setAccountStatus(statusRequest({reason: "   spam!   "}));
  assert.equal(auditRecords(store)[0].reason, "spam!");

  await handlers.setAccountStatus(
    statusRequest({newStatus: "active", reason: "y".repeat(500)})
  );
  assert.equal(auditRecords(store)[1].reason.length, 500);
});

test("status: unsupported request fields are rejected", async () => {
  const {handlers, store} = load({users: baseUsers()});
  const request = statusRequest();
  request.data.role = "admin";

  await expectCode(handlers.setAccountStatus(request), "invalid-argument");
  assert.equal(store.get("users/user-1").role, "user");
});

// =================================================================
// setAccountStatus: effects
// =================================================================

test("status: suspending updates the profile, writes an audit record, and disables sign-in", async () => {
  const {handlers, store, log} = load({users: baseUsers()});

  const result = await handlers.setAccountStatus(statusRequest());

  assert.deepEqual(plain(result), {
    saved: true,
    targetUid: "user-1",
    fromStatus: "active",
    toStatus: "suspended",
    changed: true,
    authUserFound: true,
  });

  const profile = store.get("users/user-1");
  assert.equal(profile.accountStatus, "suspended");
  assert.equal(profile.statusUpdatedAt, "SERVER_TIMESTAMP");
  assert.equal(profile.statusUpdatedBy, "admin-1");
  assert.equal(profile.role, "user");

  const audits = auditRecords(store);
  assert.equal(audits.length, 1);
  assert.deepEqual(audits[0], {
    action: "status_change",
    targetUid: "user-1",
    fromStatus: "active",
    toStatus: "suspended",
    reason: "Repeated abusive messages",
    adminUid: "admin-1",
    createdAt: "SERVER_TIMESTAMP",
  });

  // Profile and audit record were committed together.
  assert.equal(log.commits, 1);

  assert.deepEqual(plain(log.authCalls), [
    ["updateUser", "user-1", {disabled: true}],
    ["revokeRefreshTokens", "user-1"],
  ]);
});

test("status: disabling also disables sign-in and revokes tokens", async () => {
  const {handlers, store, log} = load({users: baseUsers()});

  await handlers.setAccountStatus(statusRequest({targetUid: "support-1", newStatus: "disabled"}));

  assert.equal(store.get("users/support-1").accountStatus, "disabled");
  assert.deepEqual(plain(log.authCalls), [
    ["updateUser", "support-1", {disabled: true}],
    ["revokeRefreshTokens", "support-1"],
  ]);
});

test("status: reactivating re-enables sign-in without revoking", async () => {
  const users = baseUsers();
  users["user-1"].accountStatus = "suspended";

  const {handlers, store, log} = load({users});
  const result = await handlers.setAccountStatus(
    statusRequest({newStatus: "active", reason: "Appeal approved"})
  );

  assert.equal(result.fromStatus, "suspended");
  assert.equal(store.get("users/user-1").accountStatus, "active");
  assert.deepEqual(auditRecords(store)[0].toStatus, "active");
  assert.deepEqual(plain(log.authCalls), [["updateUser", "user-1", {disabled: false}]]);
});

test("status: an inactive account can be reactivated", async () => {
  const users = baseUsers();
  users["user-1"].accountStatus = "inactive";

  const {handlers, store} = load({users});
  await handlers.setAccountStatus(statusRequest({newStatus: "active"}));

  assert.equal(store.get("users/user-1").accountStatus, "active");
  assert.equal(auditRecords(store)[0].fromStatus, "inactive");
});

// =================================================================
// setAccountStatus: last active admin
// =================================================================

test("status: an admin can suspend another admin while active admins remain", async () => {
  const {handlers, store} = load({users: baseUsers()});

  await handlers.setAccountStatus(statusRequest({targetUid: "admin-2"}));
  assert.equal(store.get("users/admin-2").accountStatus, "suspended");
});

test("status: refuses to deactivate the last active admin", async () => {
  // Simulates the active-admin query seeing only the target, e.g. the
  // caller's own status changing at the same moment.
  const {handlers, store, log} = load({
    users: baseUsers(),
    activeAdminQueryOverride: () => ({
      size: 1,
      docs: [{id: "admin-2", exists: true, data: () => ({}), get: () => undefined}],
    }),
  });

  await expectCode(
    handlers.setAccountStatus(statusRequest({targetUid: "admin-2", newStatus: "disabled"})),
    "failed-precondition"
  );

  assert.equal(store.get("users/admin-2").accountStatus, "active");
  assert.equal(auditRecords(store).length, 0);
  assert.equal(log.authCalls.length, 0);
});

test("status: last-admin check is skipped when reactivating an admin", async () => {
  let queried = false;
  const {handlers, store} = load({
    users: baseUsers(),
    activeAdminQueryOverride: () => {
      queried = true;
      return {size: 0, docs: []};
    },
  });

  await handlers.setAccountStatus(
    statusRequest({targetUid: "admin-suspended", newStatus: "active"})
  );

  assert.equal(queried, false);
  assert.equal(store.get("users/admin-suspended").accountStatus, "active");
});

// =================================================================
// setAccountStatus: Auth failures, retry and idempotency
// =================================================================

test("status: Auth failure reports that the status was saved, and a retry finishes it", async () => {
  const {handlers, store, log} = load({users: baseUsers(), authFailures: 1});

  await assert.rejects(
    handlers.setAccountStatus(statusRequest()),
    (error) => {
      assert.equal(error.code, "unavailable");
      assert.match(error.message, /status was saved/);
      assert.match(error.message, /again/);
      assert.equal(error.details.statusSaved, true);
      return true;
    }
  );

  // The status change and audit record are kept.
  assert.equal(store.get("users/user-1").accountStatus, "suspended");
  assert.equal(auditRecords(store).length, 1);

  // Retrying the same change re-applies Auth without a duplicate record.
  const retry = await handlers.setAccountStatus(statusRequest());

  assert.equal(retry.changed, false);
  assert.equal(retry.fromStatus, "suspended");
  assert.equal(auditRecords(store).length, 1);
  assert.deepEqual(plain(log.authCalls), [
    ["updateUser", "user-1", {disabled: true}],
    ["updateUser", "user-1", {disabled: true}],
    ["revokeRefreshTokens", "user-1"],
  ]);
});

test("status: repeating the current status changes no data but re-applies Auth", async () => {
  const users = baseUsers();
  users["user-1"].accountStatus = "suspended";
  users["user-1"].statusUpdatedBy = "admin-2";

  const {handlers, store, log} = load({users});
  const result = await handlers.setAccountStatus(statusRequest());

  assert.equal(result.changed, false);
  assert.equal(store.get("users/user-1").statusUpdatedBy, "admin-2");
  assert.equal(auditRecords(store).length, 0);
  assert.deepEqual(plain(log.authCalls), [
    ["updateUser", "user-1", {disabled: true}],
    ["revokeRefreshTokens", "user-1"],
  ]);
});

test("status: a profile with no Auth user is still updated", async () => {
  const {handlers, store} = load({users: baseUsers(), authMissing: true});

  const result = await handlers.setAccountStatus(statusRequest());

  assert.equal(result.saved, true);
  assert.equal(result.authUserFound, false);
  assert.equal(store.get("users/user-1").accountStatus, "suspended");
  assert.equal(auditRecords(store).length, 1);
});

// =================================================================
// setAccountRole
// =================================================================

function roleRequest(overrides = {}) {
  return {
    auth: {uid: "admin-1"},
    data: {
      targetUid: "user-1",
      newRole: "admin",
      reason: "New team lead for moderation",
      ...overrides,
    },
  };
}

function link(userId, supportId, status) {
  return {userId, supportId, status, createdAt: timestamp(1_700_000_000_000)};
}

test("role: signed-out caller is rejected", async () => {
  const {handlers, store} = load({users: baseUsers()});
  const request = roleRequest();
  delete request.auth;

  await expectCode(handlers.setAccountRole(request), "unauthenticated");
  assert.equal(store.get("users/user-1").role, "user");
});

for (const uid of ["user-1", "support-1", "admin-suspended"]) {
  test(`role: ${uid} is not an active admin and changes nothing`, async () => {
    const {handlers, store} = load({users: baseUsers()});

    await expectCode(
      handlers.setAccountRole({
        ...roleRequest({targetUid: "admin-2", newRole: "user"}),
        auth: {uid},
      }),
      "permission-denied"
    );

    assert.equal(store.get("users/admin-2").role, "admin");
    assert.equal(auditRecords(store).length, 0);
  });
}

test("role: a user cannot promote themselves", async () => {
  const {handlers, store} = load({users: baseUsers()});

  // The self-change check runs first, so a non-admin targeting themselves
  // is refused before any read.
  await expectCode(
    handlers.setAccountRole({...roleRequest({targetUid: "user-1"}), auth: {uid: "user-1"}}),
    "failed-precondition"
  );
  assert.equal(store.get("users/user-1").role, "user");
  assert.equal(auditRecords(store).length, 0);
});

test("role: admin cannot change their own role", async () => {
  const {handlers, store} = load({users: baseUsers()});

  await assert.rejects(
    handlers.setAccountRole(roleRequest({targetUid: "admin-1", newRole: "user"})),
    (error) => {
      assert.equal(error.code, "failed-precondition");
      assert.match(error.message, /your own account/);
      return true;
    }
  );
  assert.equal(store.get("users/admin-1").role, "admin");
  assert.equal(auditRecords(store).length, 0);
});

test("role: missing target account is rejected", async () => {
  const {handlers, store} = load({users: baseUsers()});

  await expectCode(
    handlers.setAccountRole(roleRequest({targetUid: "nobody"})),
    "not-found"
  );
  assert.equal(store.has("users/nobody"), false);
  assert.equal(auditRecords(store).length, 0);
});

for (const newRole of ["owner", "", undefined, 1, "Admin"]) {
  test(`role: newRole ${JSON.stringify(newRole)} is rejected`, async () => {
    const {handlers, store} = load({users: baseUsers()});
    await expectCode(
      handlers.setAccountRole(roleRequest({newRole})),
      "invalid-argument"
    );
    assert.equal(store.get("users/user-1").role, "user");
  });
}

for (const [label, reason] of [
  ["missing", undefined],
  ["not text", 42],
  ["too short", "abcd"],
  ["short after trimming", "  ab   "],
  ["too long", "x".repeat(501)],
]) {
  test(`role: reason ${label} is rejected`, async () => {
    const {handlers, store} = load({users: baseUsers()});
    await expectCode(
      handlers.setAccountRole(roleRequest({reason})),
      "invalid-argument"
    );
    assert.equal(store.get("users/user-1").role, "user");
    assert.equal(auditRecords(store).length, 0);
  });
}

test("role: unsupported request fields are rejected", async () => {
  const {handlers, store} = load({users: baseUsers()});
  const request = roleRequest();
  request.data.accountStatus = "active";

  await expectCode(handlers.setAccountRole(request), "invalid-argument");
  assert.equal(store.get("users/user-1").role, "user");
});

test("role: setting the current role is rejected with a clear message", async () => {
  const {handlers, store} = load({users: baseUsers()});

  await assert.rejects(
    handlers.setAccountRole(roleRequest({targetUid: "support-1", newRole: "support"})),
    (error) => {
      assert.equal(error.code, "failed-precondition");
      assert.match(error.message, /already has the Support role/);
      return true;
    }
  );
  assert.equal(auditRecords(store).length, 0);
});

// ---------------- promote ----------------

test("role: promoting an active user changes only role fields and writes an audit record", async () => {
  const {handlers, store, log} = load({users: baseUsers()});
  const before = {...store.get("users/user-1")};

  const result = await handlers.setAccountRole(roleRequest({reason: "  New team lead  "}));

  assert.deepEqual(plain(result), {
    saved: true,
    targetUid: "user-1",
    fromRole: "user",
    toRole: "admin",
  });

  const after = store.get("users/user-1");
  const changed = [...new Set([...Object.keys(before), ...Object.keys(after)])]
    .filter((key) => JSON.stringify(after[key]) !== JSON.stringify(before[key]))
    .sort();
  assert.deepEqual(changed, ["role", "roleUpdatedAt", "roleUpdatedBy"]);
  assert.equal(after.role, "admin");
  assert.equal(after.roleUpdatedAt, "SERVER_TIMESTAMP");
  assert.equal(after.roleUpdatedBy, "admin-1");

  const audits = auditRecords(store);
  assert.equal(audits.length, 1);
  assert.deepEqual(audits[0], {
    action: "role_change",
    targetUid: "user-1",
    fromRole: "user",
    toRole: "admin",
    reason: "New team lead",
    adminUid: "admin-1",
    createdAt: "SERVER_TIMESTAMP",
  });

  // Role changes never touch Firebase Auth.
  assert.equal(log.authCalls.length, 0);
  assert.equal(log.commits, 1);
});

for (const status of ["suspended", "disabled", "inactive"]) {
  test(`role: a ${status} account cannot be made an admin`, async () => {
    const users = baseUsers();
    users["user-1"].accountStatus = status;

    const {handlers, store} = load({users});

    await assert.rejects(
      handlers.setAccountRole(roleRequest()),
      (error) => {
        assert.equal(error.code, "failed-precondition");
        assert.match(error.message, /Only active accounts/);
        return true;
      }
    );
    assert.equal(store.get("users/user-1").role, "user");
    assert.equal(auditRecords(store).length, 0);
  });
}

for (const [label, docs, target] of [
  ["accepted link as the user", {"supportLinks/user-1_support-1": link("user-1", "support-1", "accepted")}, "user-1"],
  ["pending link as the user", {"supportLinks/user-1_support-1": link("user-1", "support-1", "pending")}, "user-1"],
  ["rejected link as the user", {"supportLinks/user-1_support-1": link("user-1", "support-1", "rejected")}, "user-1"],
  ["accepted link as support", {"supportLinks/user-1_support-1": link("user-1", "support-1", "accepted")}, "support-1"],
  ["rejected link as support", {"supportLinks/user-9_support-1": link("user-9", "support-1", "rejected")}, "support-1"],
]) {
  test(`role: promotion is blocked by a ${label}`, async () => {
    const {handlers, store} = load({users: baseUsers(), docs});

    await assert.rejects(
      handlers.setAccountRole(roleRequest({targetUid: target})),
      (error) => {
        assert.equal(error.code, "failed-precondition");
        assert.match(error.message, /must be unlinked before its role can change/);
        return true;
      }
    );
    assert.notEqual(store.get(`users/${target}`).role, "admin");
    assert.equal(auditRecords(store).length, 0);
  });
}

// User <-> support switches need the same unlinking as promotion.
for (const [label, docs, target, newRole, currentRole] of [
  ["user to support, accepted link as the user", {"supportLinks/user-1_support-1": link("user-1", "support-1", "accepted")}, "user-1", "support", "user"],
  ["user to support, pending link as the user", {"supportLinks/user-1_support-1": link("user-1", "support-1", "pending")}, "user-1", "support", "user"],
  ["support to user, accepted link as support", {"supportLinks/user-1_support-1": link("user-1", "support-1", "accepted")}, "support-1", "user", "support"],
  ["support to user, rejected link as support", {"supportLinks/user-9_support-1": link("user-9", "support-1", "rejected")}, "support-1", "user", "support"],
]) {
  test(`role: ${label} is blocked`, async () => {
    const {handlers, store} = load({users: baseUsers(), docs});

    await assert.rejects(
      handlers.setAccountRole(roleRequest({targetUid: target, newRole})),
      (error) => {
        assert.equal(error.code, "failed-precondition");
        assert.match(error.message, /must be unlinked before its role can change/);
        return true;
      }
    );
    assert.equal(store.get(`users/${target}`).role, currentRole);
    assert.equal(auditRecords(store).length, 0);
  });
}

test("role: an account with an unknown role is still link-checked before promotion", async () => {
  const users = baseUsers();
  users["user-1"].role = "legacy";

  const {handlers, store} = load({
    users,
    docs: {"supportLinks/user-1_support-1": link("user-1", "support-1", "accepted")},
  });

  await expectCode(handlers.setAccountRole(roleRequest()), "failed-precondition");
  assert.equal(store.get("users/user-1").role, "legacy");
});

test("role: links belonging to other accounts don't block promotion", async () => {
  const {handlers, store} = load({
    users: baseUsers(),
    docs: {"supportLinks/user-9_support-9": link("user-9", "support-9", "accepted")},
  });

  await handlers.setAccountRole(roleRequest({targetUid: "support-1"}));
  assert.equal(store.get("users/support-1").role, "admin");
});

for (const [target, newRole] of [["user-1", "support"], ["support-1", "user"]]) {
  test(`role: ${target} can switch to ${newRole} when it has no links`, async () => {
    const {handlers, store} = load({
      users: baseUsers(),
      docs: {"supportLinks/user-9_support-9": link("user-9", "support-9", "accepted")},
    });

    await handlers.setAccountRole(roleRequest({targetUid: target, newRole}));
    assert.equal(store.get(`users/${target}`).role, newRole);
    assert.equal(auditRecords(store)[0].toRole, newRole);
  });
}

test("role: removing admin rights skips the link check", async () => {
  // Admins can't normally hold links; seed one anyway to show the check
  // doesn't apply to admin -> user/support.
  const {handlers, store} = load({
    users: baseUsers(),
    docs: {"supportLinks/admin-2_support-1": link("admin-2", "support-1", "accepted")},
  });

  await handlers.setAccountRole(roleRequest({targetUid: "admin-2", newRole: "user"}));
  assert.equal(store.get("users/admin-2").role, "user");
});

// ---------------- remove admin ----------------

for (const newRole of ["user", "support"]) {
  test(`role: removing admin rights (to ${newRole}) works while another admin remains`, async () => {
    const {handlers, store} = load({users: baseUsers()});

    const result = await handlers.setAccountRole(
      roleRequest({targetUid: "admin-2", newRole})
    );

    assert.equal(result.fromRole, "admin");
    assert.equal(store.get("users/admin-2").role, newRole);
    assert.equal(store.get("users/admin-2").accountStatus, "active");
    assert.equal(auditRecords(store)[0].fromRole, "admin");
  });
}

test("role: refuses to remove the last active admin", async () => {
  // Simulates the active-admin query seeing only the target, e.g. the
  // caller's own status changing at the same moment.
  const {handlers, store} = load({
    users: baseUsers(),
    activeAdminQueryOverride: () => ({
      size: 1,
      empty: false,
      docs: [{id: "admin-2", exists: true, data: () => ({}), get: () => undefined}],
    }),
  });

  await assert.rejects(
    handlers.setAccountRole(roleRequest({targetUid: "admin-2", newRole: "user"})),
    (error) => {
      assert.equal(error.code, "failed-precondition");
      assert.match(error.message, /last active administrator/);
      return true;
    }
  );
  assert.equal(store.get("users/admin-2").role, "admin");
  assert.equal(auditRecords(store).length, 0);
});

test("role: a suspended admin can be demoted without the last-admin check", async () => {
  let queried = false;
  const {handlers, store} = load({
    users: baseUsers(),
    activeAdminQueryOverride: () => {
      queried = true;
      return {size: 0, empty: true, docs: []};
    },
  });

  await handlers.setAccountRole(roleRequest({targetUid: "admin-suspended", newRole: "user"}));

  assert.equal(queried, false);
  assert.equal(store.get("users/admin-suspended").role, "user");
});
