const {randomUUID} = require("node:crypto");
const {test, before, beforeEach, after} = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");

const {
  doc,
  setDoc,
  updateDoc,
  deleteDoc,
} = require("firebase/firestore");

const {
  ref,
  uploadBytes,
  getBytes,
  deleteObject,
  updateMetadata,
  listAll,
} = require("firebase/storage");

const PROJECT = "demo-deals-rules";
const BUCKET = `gs://${PROJECT}.appspot.com`;
const LINK = "user_support";
const EXISTING = `message-files/${LINK}/existing.pdf`;
const DOCUMENT = "protectedDocuments/user/doc1/content";

let env;

function context(uid) {
  return uid
    ? env.authenticatedContext(uid)
    : env.unauthenticatedContext();
}

function objectRef(uid, objectPath) {
  return ref(context(uid).storage(BUCKET), objectPath);
}

function upload(
  uid,
  objectPath = `message-files/${LINK}/${randomUUID()}.pdf`,
  size = 10,
  contentType = "application/pdf"
) {
  return uploadBytes(
    objectRef(uid, objectPath),
    new Uint8Array(size),
    {contentType}
  );
}

async function changeLink(status) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), "supportLinks", LINK), {
      status,
    });
  });
}

before(async () => {
  if (
    process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8085" ||
    process.env.FIREBASE_STORAGE_EMULATOR_HOST !== "127.0.0.1:9199"
  ) {
    throw new Error("Run through the configured local emulators.");
  }

  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: {
      host: "127.0.0.1",
      port: 8085,
      rules: fs.readFileSync(
        path.resolve(__dirname, "../../firestore.rules"),
        "utf8"
      ),
    },
    storage: {
      host: "127.0.0.1",
      port: 9199,
      rules: fs.readFileSync(
        path.resolve(__dirname, "../../storage.rules"),
        "utf8"
      ),
    },
  });
});

beforeEach(async () => {
  await env.clearStorage();
  await env.clearFirestore();

  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const storage = ctx.storage(BUCKET);

    await setDoc(doc(db, "supportLinks", LINK), {
      userId: "user",
      supportId: "support",
      status: "accepted",
    });

    await uploadBytes(
      ref(storage, EXISTING),
      new Uint8Array([1, 2, 3]),
      {contentType: "application/pdf"}
    );

    await setDoc(doc(db, "documentShares", "doc1_recipient"), {
      documentId: "doc1",
      ownerUid: "user",
      recipientUid: "recipient",
      contactId: "contact1",
    });

    await setDoc(doc(db, "users/user/trustedContacts/contact1"), {
      linkedUserId: "recipient",
      accessGiven: true,
    });

    await uploadBytes(
      ref(storage, DOCUMENT),
      new Uint8Array([4, 5, 6]),
      {contentType: "application/pdf"}
    );
  });
});

after(async () => {
  if (env) await env.cleanup();
});

for (const uid of ["user", "support"]) {
  test(`${uid} can upload to an accepted conversation`, async () => {
    await assertSucceeds(upload(uid));
  });

  test(`${uid} can download from an accepted conversation`, async () => {
    await assertSucceeds(getBytes(objectRef(uid, EXISTING)));
  });
}

for (const uid of ["stranger", null]) {
  const label = uid || "signed-out account";

  test(`${label} cannot upload`, async () => {
    await assertFails(upload(uid));
  });

  test(`${label} cannot download`, async () => {
    await assertFails(getBytes(objectRef(uid, EXISTING)));
  });
}

for (const status of ["pending", "rejected"]) {
  test(`${status} link blocks uploads and downloads`, async () => {
    await changeLink(status);

    await assertFails(upload("user"));
    await assertFails(getBytes(objectRef("support", EXISTING)));
  });
}

test("disconnect blocks both participants", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await deleteDoc(doc(ctx.firestore(), "supportLinks", LINK));
  });

  for (const uid of ["user", "support"]) {
    await assertFails(upload(uid));
    await assertFails(getBytes(objectRef(uid, EXISTING)));
  }
});

test("participant cannot upload into another conversation", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "supportLinks", "other_support"), {
      userId: "other",
      supportId: "support",
      status: "accepted",
    });
  });

  await assertFails(
    upload("user", "message-files/other_support/new.pdf")
  );
});

test("10 MB file is allowed", async () => {
  await assertSucceeds(
    upload("user", undefined, 10 * 1024 * 1024)
  );
});

test("file larger than 10 MB is denied", async () => {
  await assertFails(
    upload("user", undefined, 10 * 1024 * 1024 + 1)
  );
});

test("empty file is denied", async () => {
  await assertFails(upload("user", undefined, 0));
});

test("unsupported content type is denied", async () => {
  await assertFails(
    upload("user", undefined, 10, "application/octet-stream")
  );
});

test("supported image content type is allowed", async () => {
  await assertSucceeds(
    upload("user", `message-files/${LINK}/photo.png`, 10, "image/png")
  );
});

test("existing attachment cannot be overwritten", async () => {
  await assertFails(upload("user", EXISTING));
});

test("attachment metadata cannot be changed", async () => {
  await assertFails(
    updateMetadata(objectRef("support", EXISTING), {
      contentType: "text/plain",
    })
  );
});

test("attachment cannot be deleted by a participant", async () => {
  await assertFails(deleteObject(objectRef("user", EXISTING)));
});

test("conversation file listing is denied", async () => {
  await assertFails(
    listAll(objectRef("user", `message-files/${LINK}`))
  );
});

test("document owner retains access", async () => {
  await assertSucceeds(getBytes(objectRef("user", DOCUMENT)));
});

test("authorized document recipient can download", async () => {
  await assertSucceeds(getBytes(objectRef("recipient", DOCUMENT)));
});

test("revoking accessGiven blocks document download", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(
      doc(ctx.firestore(), "users/user/trustedContacts/contact1"),
      {accessGiven: false}
    );
  });

  await assertFails(getBytes(objectRef("recipient", DOCUMENT)));
});

test("unrelated account cannot download a protected document", async () => {
  await assertFails(getBytes(objectRef("stranger", DOCUMENT)));
});