const { test, before, beforeEach, after } = require("node:test");
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
  deleteDoc,
  updateDoc,
  Timestamp,
  serverTimestamp,
} = require("firebase/firestore");

const {
  ref,
  uploadBytes,
  getBytes,
  deleteObject,
} = require("firebase/storage");

let env;

const bucket = "gs://demo-deals-rules.appspot.com";
const linkPath = "supportLinks/user_support";
const sharePath = "documentShares/document-one_support";
const filePath = "protectedDocuments/user/document-one/content";
const otherFilePath = "protectedDocuments/user/document-two/content";
const connectionTime = Timestamp.fromMillis(1000);

function context(uid) {
  return uid
    ? env.authenticatedContext(uid)
    : env.unauthenticatedContext();
}

function fileReference(uid, file = filePath) {
  return ref(context(uid).storage(bucket), file);
}

function documentReference(uid, documentPath) {
  return doc(context(uid).firestore(), documentPath);
}

async function createGrant(timestamp = connectionTime) {
  await assertSucceeds(
    setDoc(documentReference("user", sharePath), {
      documentId: "document-one",
      ownerUid: "user",
      recipientUid: "support",
      recipientType: "support",
      supportLinkId: "user_support",
      supportLinkCreatedAt: timestamp,
      createdAt: serverTimestamp(),
    })
  );
}

async function changeLink(fields) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), linkPath), fields);
  });
}

before(async () => {
  if (
    process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8085" ||
    process.env.FIREBASE_STORAGE_EMULATOR_HOST !== "127.0.0.1:9199"
  ) {
    throw new Error("Run through the Firestore and Storage emulators.");
  }

  env = await initializeTestEnvironment({
    projectId: "demo-deals-rules",
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

    await setDoc(doc(db, linkPath), {
      userId: "user",
      supportId: "support",
      status: "accepted",
      createdAt: connectionTime,
    });

    for (const documentId of ["document-one", "document-two"]) {
      const storagePath =
        `protectedDocuments/user/${documentId}/content`;

      await setDoc(doc(db, `documentFiles/${documentId}`), {
        ownerUid: "user",
        name: `${documentId}.txt`,
        type: "Other",
        cloudStoragePath: storagePath,
        contentType: "text/plain",
        sizeBytes: 4,
        createdAt: connectionTime,
      });

      await uploadBytes(
        ref(ctx.storage(bucket), storagePath),
        new Uint8Array([84, 69, 83, 84]),
        { contentType: "text/plain" }
      );
    }
  });
});

after(async () => {
  if (env) await env.cleanup();
});

test("owner can download their document", async () => {
  await assertSucceeds(getBytes(fileReference("user")));
});

test("approved Support with a grant can download", async () => {
  await createGrant();
  await assertSucceeds(getBytes(fileReference("support")));
});

test("accepted connection without a grant cannot download", async () => {
  await assertFails(getBytes(fileReference("support")));
});

test("grant does not expose another document", async () => {
  await createGrant();
  await assertFails(
    getBytes(fileReference("support", otherFilePath))
  );
});

test("unrelated account cannot download", async () => {
  await createGrant();
  await assertFails(getBytes(fileReference("stranger")));
});

test("signed-out account cannot download", async () => {
  await createGrant();
  await assertFails(getBytes(fileReference(null)));
});

test("revoking the grant blocks downloads", async () => {
  await createGrant();
  await assertSucceeds(getBytes(fileReference("support")));

  await assertSucceeds(
    deleteDoc(documentReference("user", sharePath))
  );

  await assertFails(getBytes(fileReference("support")));
});

test("disconnecting blocks downloads", async () => {
  await createGrant();
  await assertSucceeds(getBytes(fileReference("support")));

  await assertSucceeds(
    deleteDoc(documentReference("support", linkPath))
  );

  await assertFails(getBytes(fileReference("support")));
});

test("pending connection blocks an existing grant", async () => {
  await createGrant();
  await changeLink({ status: "pending" });
  await assertFails(getBytes(fileReference("support")));
});

test("rejected connection blocks an existing grant", async () => {
  await createGrant();
  await changeLink({ status: "rejected" });
  await assertFails(getBytes(fileReference("support")));
});

test("pairing again requires a fresh document grant", async () => {
  await createGrant();

  await assertSucceeds(
    deleteDoc(documentReference("support", linkPath))
  );

  const newConnectionTime = Timestamp.fromMillis(2000);

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), linkPath), {
      userId: "user",
      supportId: "support",
      status: "accepted",
      createdAt: newConnectionTime,
    });
  });

  await assertFails(getBytes(fileReference("support")));

  await assertSucceeds(
    deleteDoc(documentReference("user", sharePath))
  );

  await createGrant(newConnectionTime);
  await assertSucceeds(getBytes(fileReference("support")));
});

test("Support cannot replace a shared document", async () => {
  await createGrant();

  await assertFails(
    uploadBytes(
      fileReference("support"),
      new Uint8Array([1, 2, 3]),
      { contentType: "text/plain" }
    )
  );
});

test("Support cannot delete a shared document", async () => {
  await createGrant();
  await assertFails(deleteObject(fileReference("support")));
});