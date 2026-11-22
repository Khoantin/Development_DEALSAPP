// adminAuditLog is written only by the setAccountStatus Cloud Function.
// No client, including an active admin, may read, list or write it.
//
// Run through the emulator, for example:
//   firebase emulators:exec --config firebase.rules-test.json --only firestore \
//     "node --test tests/firestore/admin-audit-log.rules.test.cjs"

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
  addDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
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
      await setDoc(doc(db, "users", role), {role, accountStatus: "active"});
    }

    await setDoc(doc(db, "adminAuditLog", "existing-entry"), {
      action: "setAccountStatus",
      targetUid: "user",
      fromStatus: "active",
      toStatus: "suspended",
      reason: "Example reason",
      adminUid: "admin",
      createdAt: new Date("2026-06-01"),
    });
  });
});

after(async () => {
  if (environment) await environment.cleanup();
});

function databaseFor(role) {
  return role === "signed-out"
    ? environment.unauthenticatedContext().firestore()
    : environment.authenticatedContext(role).firestore();
}

const entry = {
  action: "setAccountStatus",
  targetUid: "user",
  fromStatus: "suspended",
  toStatus: "active",
  reason: "Forged entry",
  adminUid: "admin",
  createdAt: new Date(),
};

for (const role of ["admin", "user", "support", "signed-out"]) {
  test(`${role} cannot read or list the audit log`, async () => {
    const db = databaseFor(role);

    await assertFails(getDoc(doc(db, "adminAuditLog", "existing-entry")));
    await assertFails(getDocs(collection(db, "adminAuditLog")));
    await assertFails(
      getDocs(query(collection(db, "adminAuditLog"), where("targetUid", "==", "user")))
    );
  });

  test(`${role} cannot write the audit log`, async () => {
    const db = databaseFor(role);

    await assertFails(setDoc(doc(db, "adminAuditLog", "forged"), entry));
    await assertFails(addDoc(collection(db, "adminAuditLog"), entry));
    await assertFails(
      updateDoc(doc(db, "adminAuditLog", "existing-entry"), {reason: "Edited"})
    );
    await assertFails(deleteDoc(doc(db, "adminAuditLog", "existing-entry")));
  });
}
