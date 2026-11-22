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
  getDoc,
  updateDoc,
  deleteDoc,
  Timestamp,
  serverTimestamp,
} = require("firebase/firestore");

let env;

const linkPath = "supportLinks/user_support";
const filePath = "documentFiles/document-one";
const sharePath = "documentShares/document-one_support";
const connectionTime = Timestamp.fromMillis(1000);

function database(uid) {
  return uid
    ? env.authenticatedContext(uid).firestore()
    : env.unauthenticatedContext().firestore();
}

function reference(uid, documentPath) {
  return doc(database(uid), documentPath);
}

function grant(overrides = {}) {
  return {
    documentId: "document-one",
    ownerUid: "user",
    recipientUid: "support",
    recipientType: "support",
    supportLinkId: "user_support",
    supportLinkCreatedAt: connectionTime,
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

async function createGrant() {
  await assertSucceeds(
    setDoc(reference("user", sharePath), grant())
  );
}

async function changeLink(fields) {
  await env.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), linkPath), fields);
  });
}

before(async () => {
  if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8085") {
    throw new Error("Run through the configured Firestore emulator.");
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
  });
});

beforeEach(async () => {
  await env.clearFirestore();

  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "users/user"), {
      role: "user",
      accountStatus: "active",
    });

    await setDoc(doc(db, "users/support"), {
      role: "support",
      accountStatus: "active",
    });

    await setDoc(doc(db, linkPath), {
      userId: "user",
      supportId: "support",
      status: "accepted",
      createdAt: connectionTime,
    });

    for (const documentId of ["document-one", "document-two"]) {
      await setDoc(doc(db, `documentFiles/${documentId}`), {
        ownerUid: "user",
        name: `${documentId}.pdf`,
        type: "Identification",
        cloudStoragePath:
          `protectedDocuments/user/${documentId}/content`,
        contentType: "application/pdf",
        sizeBytes: 100,
        createdAt: connectionTime,
      });
    }
  });
});

after(async () => {
  if (env) await env.cleanup();
});

test("owner can grant one document to approved Support", async () => {
  await createGrant();
  await assertSucceeds(getDoc(reference("support", filePath)));
});

test("accepted connection alone does not grant document access", async () => {
  await assertFails(getDoc(reference("support", filePath)));
});

test("sharing one document does not expose another", async () => {
  await createGrant();
  await assertFails(
    getDoc(reference("support", "documentFiles/document-two"))
  );
});

test("unrelated account cannot read a shared document", async () => {
  await createGrant();
  await assertFails(getDoc(reference("stranger", filePath)));
});

test("signed-out account cannot read a shared document", async () => {
  await createGrant();
  await assertFails(getDoc(reference(null, filePath)));
});

test("Support cannot grant itself access", async () => {
  await assertFails(
    setDoc(reference("support", sharePath), grant())
  );
});

test("pending connection cannot receive a grant", async () => {
  await changeLink({ status: "pending" });
  await assertFails(
    setDoc(reference("user", sharePath), grant())
  );
});

test("rejected connection cannot receive a grant", async () => {
  await changeLink({ status: "rejected" });
  await assertFails(
    setDoc(reference("user", sharePath), grant())
  );
});

test("forged connection timestamp is rejected", async () => {
  await assertFails(
    setDoc(
      reference("user", sharePath),
      grant({ supportLinkCreatedAt: Timestamp.fromMillis(999) })
    )
  );
});

test("forged connection ID is rejected", async () => {
  await assertFails(
    setDoc(
      reference("user", sharePath),
      grant({ supportLinkId: "user_stranger" })
    )
  );
});

test("owner revocation blocks subsequent document reads", async () => {
  await createGrant();
  await assertSucceeds(deleteDoc(reference("user", sharePath)));
  await assertFails(getDoc(reference("support", filePath)));
});

test("Support cannot modify a grant", async () => {
  await createGrant();
  await assertFails(
    updateDoc(reference("support", sharePath), {
      recipientUid: "stranger",
    })
  );
});

test("Support cannot delete the owner's grant", async () => {
  await createGrant();
  await assertFails(deleteDoc(reference("support", sharePath)));
});

test("disconnect immediately blocks subsequent reads", async () => {
  await createGrant();
  await assertSucceeds(deleteDoc(reference("support", linkPath)));
  await assertFails(getDoc(reference("support", filePath)));
});

test("pairing again does not reactivate the old grant", async () => {
  await createGrant();
  await assertSucceeds(deleteDoc(reference("support", linkPath)));

  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), linkPath), {
      userId: "user",
      supportId: "support",
      status: "accepted",
      createdAt: Timestamp.fromMillis(2000),
    });
  });

  await assertFails(getDoc(reference("support", filePath)));

  // The owner must explicitly remove and replace the old grant.
  await assertSucceeds(deleteDoc(reference("user", sharePath)));
  await assertSucceeds(
    setDoc(
      reference("user", sharePath),
      grant({ supportLinkCreatedAt: Timestamp.fromMillis(2000) })
    )
  );
  await assertSucceeds(getDoc(reference("support", filePath)));
});

test("owner can revoke a grant after disconnecting", async () => {
  await createGrant();
  await assertSucceeds(deleteDoc(reference("user", linkPath)));
  await assertSucceeds(deleteDoc(reference("user", sharePath)));
  await assertSucceeds(getDoc(reference("user", filePath)));
});