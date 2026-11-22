const {test, before, beforeEach, after} = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertFails,
} = require("@firebase/rules-unit-testing");

const {
  doc,
  collection,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
} = require("firebase/firestore");

let environment;

before(async () => {
  const address = process.env.FIRESTORE_EMULATOR_HOST;

  if (!address) {
    throw new Error("Run this test through the Firestore emulator.");
  }

  const [host, port] = address.split(":");

  environment = await initializeTestEnvironment({
    projectId: "demo-deals-rules",
    firestore: {
      host,
      port: Number(port),
      rules: fs.readFileSync(
        path.resolve(__dirname, "../../firestore.rules"),
        "utf8"
      ),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();

  await environment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    for (const role of ["admin", "user", "support"]) {
      await setDoc(doc(db, "users", role), {
        role,
        accountStatus: "active",
      });
    }

    await setDoc(doc(db, "emergencyGuides", "existing-guide"), {
      revision: 1,
      isPublished: true,
      draft: {
        title: "Private draft",
      },
      published: {
        title: "Published example",
        audience: "both",
      },
    });
  });
});

after(async () => {
  if (environment) await environment.cleanup();
});

for (const role of ["admin", "user", "support", "signed-out"]) {
  test(`${role} cannot directly read or list guides`, async () => {
    const context = role === "signed-out"
      ? environment.unauthenticatedContext()
      : environment.authenticatedContext(role);

    const db = context.firestore();

    await assertFails(
      getDoc(doc(db, "emergencyGuides", "existing-guide"))
    );

    await assertFails(
      getDocs(collection(db, "emergencyGuides"))
    );
  });

  test(`${role} cannot directly create, edit, or delete guides`, async () => {
    const context = role === "signed-out"
      ? environment.unauthenticatedContext()
      : environment.authenticatedContext(role);

    const db = context.firestore();

    await assertFails(
      setDoc(doc(db, "emergencyGuides", "new-guide"), {
        isPublished: true,
        title: "Unauthorized guide",
      })
    );

    await assertFails(
      updateDoc(doc(db, "emergencyGuides", "existing-guide"), {
        isPublished: false,
      })
    );

    await assertFails(
      deleteDoc(doc(db, "emergencyGuides", "existing-guide"))
    );
  });
}