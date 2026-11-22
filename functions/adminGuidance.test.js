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

function setup() {
  const documents = new Map();
  const DELETE = Symbol("delete");

  documents.set("users/admin", {
    role: "admin",
    accountStatus: "active",
  });
  documents.set("users/user", {
    role: "user",
    accountStatus: "active",
  });
  documents.set("users/support", {
    role: "support",
    accountStatus: "active",
  });
  documents.set("users/inactive", {
    role: "admin",
    accountStatus: "inactive",
  });

  function snapshot(documentPath) {
    const value = documents.get(documentPath);
    return {
      id: documentPath.split("/").at(-1),
      exists: value !== undefined,
      data: () => value,
    };
  }

  function collection(name, filters = [], maximum = Infinity) {
    return {
      name,
      filters,
      maximum,
      doc: (id) => ({path: `${name}/${id}`}),
      where: (field, operator, value) => {
        assert.equal(operator, "==");
        return collection(name, [...filters, [field, value]], maximum);
      },
      limit: (count) => collection(name, filters, count),
    };
  }

  const db = {
    collection,
    async runTransaction(callback) {
      const writes = [];
      const result = await callback({
        async get(reference) {
          if (reference.path) return snapshot(reference.path);

          const docs = [...documents.keys()]
            .filter((key) =>
              key.startsWith(`${reference.name}/`) &&
              key.split("/").length === 2
            )
            .filter((key) =>
              reference.filters.every(([field, value]) =>
                documents.get(key)[field] === value
              )
            )
            .slice(0, reference.maximum)
            .map(snapshot);

          return {docs, size: docs.length};
        },
        create(reference, value) {
          assert.equal(documents.has(reference.path), false);
          writes.push({reference, value, create: true});
        },
        update(reference, value) {
          assert.equal(documents.has(reference.path), true);
          writes.push({reference, value, create: false});
        },
      });

      for (const write of writes) {
        const next = write.create
          ? {}
          : {...documents.get(write.reference.path)};

        for (const [key, value] of Object.entries(write.value)) {
          if (value === DELETE) delete next[key];
          else next[key] = value;
        }
        documents.set(write.reference.path, next);
      }

      return result;
    },
  };

  const sandbox = {
    exports: {},
    require(name) {
      if (name === "firebase-functions/v2/https") {
        return {
          onCall: (_options, handler) => handler,
          HttpsError: TestHttpsError,
        };
      }
      if (name === "firebase-admin/firestore") {
        return {
          getFirestore: () => db,
          FieldValue: {
            serverTimestamp: () => ({toMillis: () => 1000}),
            delete: () => DELETE,
          },
        };
      }
      if (name === "firebase-functions/logger") {
        return {error() {}};
      }
      throw new Error(`Unexpected dependency: ${name}`);
    },
  };

  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "adminGuidance.js"), "utf8"),
    sandbox
  );

  function request(uid, data) {
    return {auth: uid ? {uid} : undefined, data};
  }

  return {
    documents,
    list: (uid, mode = "published") =>
      sandbox.exports.listEmergencyGuides(request(uid, {mode})),
    manage: (uid, data) =>
      sandbox.exports.manageEmergencyGuide(request(uid, data)),
  };
}

function fields(audience = "both", title = "Example guide") {
  return {
    title,
    summary: "Fictional test content.",
    audience,
    steps: ["Read the fictional example."],
    practiceScenario: "A fictional account previews this guide.",
  };
}

function saveRequest(expectedRevision = 0, content = fields()) {
  return {
    action: "save",
    guideId: "guide-one",
    expectedRevision,
    fields: content,
  };
}

function publishRequest(expectedRevision = 1) {
  return {
    action: "publish",
    guideId: "guide-one",
    expectedRevision,
    reviewConfirmed: true,
  };
}

async function publishedSetup(audience = "both") {
  const context = setup();
  await context.manage("admin", saveRequest(0, fields(audience)));
  await context.manage("admin", publishRequest());
  return context;
}

async function denied(promise, code) {
  await assert.rejects(promise, (error) => error.code === code);
}

test("signed-out accounts cannot list guides", async () => {
  const context = setup();
  await denied(context.list(null), "unauthenticated");
});

for (const uid of ["user", "support", "inactive"]) {
  test(`${uid} cannot edit guides or load Admin drafts`, async () => {
    const context = setup();

    await denied(
      context.manage(uid, saveRequest()),
      "permission-denied"
    );
    await denied(context.list(uid, "admin"), "permission-denied");
    assert.equal(context.documents.has("emergencyGuides/guide-one"), false);
  });
}

test("inactive accounts cannot read published guides", async () => {
  const context = await publishedSetup();
  await denied(context.list("inactive"), "permission-denied");
});

test("Admin can save a draft that readers cannot see", async () => {
  const context = setup();
  const result = await context.manage("admin", saveRequest());

  assert.equal(result.revision, 1);
  assert.equal((await context.list("user")).guides.length, 0);
  assert.equal((await context.list("support")).guides.length, 0);

  const adminResult = await context.list("admin", "admin");
  assert.equal(adminResult.guides[0].draft.title, "Example guide");
  assert.equal(adminResult.guides[0].isPublished, false);
});

test("publishing exposes content without drafts or Admin identifiers", async () => {
  const context = await publishedSetup();
  const guide = (await context.list("user")).guides[0];

  assert.equal(guide.title, "Example guide");
  assert.equal(guide.lastReviewedAtMillis, 1000);
  assert.equal("draft" in guide, false);
  assert.equal("updatedBy" in guide, false);
  assert.equal("publishedBy" in guide, false);
});

for (const audience of ["user", "support"]) {
  test(`${audience}-only guides are hidden from the other role`, async () => {
    const context = await publishedSetup(audience);
    const other = audience === "user" ? "support" : "user";

    assert.equal((await context.list(audience)).guides.length, 1);
    assert.equal((await context.list(other)).guides.length, 0);
  });
}

test("editing a draft preserves the published version until republishing", async () => {
  const context = await publishedSetup();

  await context.manage(
    "admin",
    saveRequest(2, fields("both", "Revised guide"))
  );

  assert.equal(
    (await context.list("user")).guides[0].title,
    "Example guide"
  );

  await context.manage("admin", publishRequest(3));

  assert.equal(
    (await context.list("user")).guides[0].title,
    "Revised guide"
  );
});

test("stale edits cannot overwrite a newer draft", async () => {
  const context = setup();
  await context.manage("admin", saveRequest());

  await denied(
    context.manage("admin", saveRequest(0, fields("both", "Stale"))),
    "failed-precondition"
  );

  assert.equal(
    context.documents.get("emergencyGuides/guide-one").draft.title,
    "Example guide"
  );
});

test("stale preview cannot publish a newer revision", async () => {
  const context = setup();
  await context.manage("admin", saveRequest());
  await context.manage("admin", saveRequest(1, fields("both", "New draft")));

  await denied(
    context.manage("admin", publishRequest(1)),
    "failed-precondition"
  );

  assert.equal((await context.list("user")).guides.length, 0);
});

test("publishing requires explicit review confirmation", async () => {
  const context = setup();
  await context.manage("admin", saveRequest());

  await denied(
    context.manage("admin", {
      ...publishRequest(),
      reviewConfirmed: false,
    }),
    "invalid-argument"
  );

  assert.equal((await context.list("user")).guides.length, 0);
});

test("incomplete drafts can be saved but cannot be published", async () => {
  const context = setup();
  const incomplete = {
    ...fields(),
    summary: "",
    steps: [],
    practiceScenario: "",
  };

  await context.manage("admin", saveRequest(0, incomplete));

  await denied(
    context.manage("admin", publishRequest()),
    "invalid-argument"
  );

  assert.equal((await context.list("user")).guides.length, 0);
});

test("withdrawal hides the guide and preserves its editable draft", async () => {
  const context = await publishedSetup();

  await context.manage("admin", {
    action: "withdraw",
    guideId: "guide-one",
    expectedRevision: 2,
  });

  assert.equal((await context.list("user")).guides.length, 0);
  assert.equal((await context.list("support")).guides.length, 0);

  const guide = (await context.list("admin", "admin")).guides[0];
  assert.equal(guide.draft.title, "Example guide");
  assert.equal(guide.published, null);
  assert.equal(guide.lastReviewedAtMillis, null);
});

test("User and Support cannot publish or withdraw guides", async () => {
  const context = await publishedSetup();

  for (const uid of ["user", "support"]) {
    for (const action of ["publish", "withdraw"]) {
      const data = {
        action,
        guideId: "guide-one",
        expectedRevision: 2,
      };
      if (action === "publish") data.reviewConfirmed = true;

      await denied(context.manage(uid, data), "permission-denied");
    }
  }

  assert.equal((await context.list("user")).guides.length, 1);
});

test("Admin access is checked again after account suspension", async () => {
  const context = setup();
  await context.manage("admin", saveRequest());

  context.documents.set("users/admin", {
    role: "admin",
    accountStatus: "suspended",
  });

  await denied(
    context.manage("admin", publishRequest()),
    "permission-denied"
  );
});

test("guide IDs cannot target another collection", async () => {
  const context = setup();

  await denied(
    context.manage("admin", {
      ...saveRequest(),
      guideId: "../users/admin",
    }),
    "invalid-argument"
  );
});

test("unsupported fields cannot be injected into a guide", async () => {
  const context = setup();

  await denied(
    context.manage("admin", saveRequest(0, {
      ...fields(),
      isPublished: true,
    })),
    "invalid-argument"
  );
});