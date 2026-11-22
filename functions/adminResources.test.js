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

function setup({
  role = "admin",
  status = "active",
  exists = true,
  published = true,
} = {}) {
  const writes = [];

  const adminSnapshot = {
    exists: true,
    data: () => ({role, accountStatus: status}),
  };

  const resourceSnapshot = {
    id: "test-resource",
    exists,
    updateTime: {seconds: 100, nanoseconds: 25},
    data: () => ({
      name: "Demo resource",
      published,
      isDemo: true,
      contactMethods: ["PHONE"],
    }),
  };

  function reference(parts = []) {
    return {
      path: parts.join("/"),
      collection: (id) => reference([...parts, id]),
      doc: (id) => reference([...parts, id]),
      where() { return this; },
      limit() { return this; },
      async get() {
        if (parts[0] === "users") return adminSnapshot;
        return {size: 1, docs: [resourceSnapshot]};
      },
    };
  }

  const db = {
    collection: (id) => reference([id]),
    runTransaction: async (callback) => callback({
      get: async (ref) =>
        ref.path.startsWith("users/")
          ? adminSnapshot
          : resourceSnapshot,
      update: (ref, data) => {
        writes.push({path: ref.path, data});
      },
    }),
  };

  const sandbox = {
    exports: {},
    Buffer,
    URL,
    require: (name) => {
      if (name === "firebase-functions/v2/https") {
        return {
          onCall: (_options, handler) => handler,
          HttpsError: TestHttpsError,
        };
      }

      if (name === "firebase-admin/firestore") {
        return {
          getFirestore: () => db,
          FieldValue: {serverTimestamp: () => "SERVER_TIMESTAMP"},
        };
      }

      if (name === "firebase-functions/logger") {
        return {error: () => {}};
      }

      throw new Error(`Unexpected dependency: ${name}`);
    },
  };

  vm.runInNewContext(
    fs.readFileSync(
      path.join(__dirname, "adminResources.js"),
      "utf8"
    ),
    sandbox
  );

  return {handlers: sandbox.exports, writes};
}

function validRequest() {
  return {
    auth: {uid: "admin-test"},
    data: {
      regionId: "CA-AB",
      resourceId: "test-resource",
      expectedVersion: "100:25",
      fields: {
        name: "Updated demo resource",
        need: "GENERAL",
        summary: "Demo description",
        exampleProcess: "Contact this demo service.",
        contactMethods: ["PHONE"],
        websiteUrl: "",
        phoneNumber: "211",
        hoursNote: "",
        lastReviewed: "",
      },
    },
  };
}

async function expectDenied(config, modify, code) {
  const {handlers, writes} = setup(config);
  const request = validRequest();
  modify(request);

  await assert.rejects(
    handlers.updateAdminRegionalResource(request),
    (error) => error.code === code
  );

  assert.equal(writes.length, 0);
}

test("active admin can load resources", async () => {
  const {handlers} = setup();
  const result = await handlers.listAdminRegionalResources({
    auth: {uid: "admin-test"},
    data: {regionId: "CA-AB"},
  });

  assert.equal(result.regionId, "CA-AB");
  assert.equal(result.resources.length, 1);
  assert.equal(result.resources[0].version, "100:25");
});

test("regular user cannot load resources", async () => {
  const {handlers} = setup({role: "user"});

  await assert.rejects(
    handlers.listAdminRegionalResources({
      auth: {uid: "regular-user"},
      data: {regionId: "CA-AB"},
    }),
    (error) => error.code === "permission-denied"
  );
});

test("active admin can save only to the selected resource", async () => {
  const {handlers, writes} = setup();
  const result = await handlers.updateAdminRegionalResource(validRequest());

  assert.equal(result.saved, true);
  assert.equal(writes.length, 1);
  assert.equal(
    writes[0].path,
    "regionalResources/CA-AB/resources/test-resource"
  );
  assert.equal(writes[0].data.updatedBy, "admin-test");
  assert.equal(writes[0].data.name, "Updated demo resource");
  assert.equal("published" in writes[0].data, false);
  assert.equal("isDemo" in writes[0].data, false);
});

test("signed-out caller cannot save", () =>
  expectDenied({}, (r) => { delete r.auth; }, "unauthenticated")
);

test("regular user cannot save", () =>
  expectDenied({role: "user"}, () => {}, "permission-denied")
);

test("support account cannot save", () =>
  expectDenied({role: "support"}, () => {}, "permission-denied")
);

test("inactive admin cannot save", () =>
  expectDenied({status: "inactive"}, () => {}, "permission-denied")
);

test("unsupported region is rejected", () =>
  expectDenied({}, (r) => {
    r.data.regionId = "CA-ON";
  }, "invalid-argument")
);

test("resource ID cannot contain a path", () =>
  expectDenied({}, (r) => {
    r.data.resourceId = "other/resource";
  }, "invalid-argument")
);

test("blank name is rejected", () =>
  expectDenied({}, (r) => {
    r.data.fields.name = "   ";
  }, "invalid-argument")
);

test("invalid website is rejected", () =>
  expectDenied({}, (r) => {
    r.data.fields.websiteUrl = "javascript:alert(1)";
  }, "invalid-argument")
);

test("publication status cannot be changed through this editor", () =>
  expectDenied({}, (r) => {
    r.data.fields.published = false;
  }, "invalid-argument")
);

test("stale edits cannot overwrite a newer resource", () =>
  expectDenied({}, (r) => {
    r.data.expectedVersion = "99:25";
  }, "failed-precondition")
);

test("deleted resource cannot be recreated by saving", () =>
  expectDenied({exists: false}, () => {}, "not-found")
);

test("unpublished resource cannot be edited", () =>
  expectDenied({published: false}, () => {}, "failed-precondition")
);