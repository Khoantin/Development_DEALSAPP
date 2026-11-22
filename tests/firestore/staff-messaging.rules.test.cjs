// Staff threads are written only by Cloud Functions. Clients may read:
// active support -> their own thread; active admin -> every thread.
//
// Run through the emulator, for example:
//   firebase emulators:exec --config firebase.rules-test.json --only firestore \
//     "node --test tests/firestore/staff-messaging.rules.test.cjs"

const {test, before, beforeEach, after} = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
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
} = require("firebase/firestore");

let environment;

const accounts = {
  "admin": {role: "admin", accountStatus: "active"},
  "admin-suspended": {role: "admin", accountStatus: "suspended"},
  "support-1": {role: "support", accountStatus: "active"},
  "support-2": {role: "support", accountStatus: "active"},
  "support-suspended": {role: "support", accountStatus: "suspended"},
  "user": {role: "user", accountStatus: "active"},
};

const thread = (supportUid) => ({
  supportUid,
  supportDisplayName: "Support",
  createdAt: new Date("2026-06-01"),
  lastMessageAt: new Date("2026-06-01"),
  lastMessagePreview: "Hello",
  lastSenderRole: "admin",
  supportUnread: 1,
  adminUnread: 0,
});

const message = {senderRole: "admin", text: "Hello", createdAt: new Date("2026-06-01")};

before(async () => {
  const address = process.env.FIRESTORE_EMULATOR_HOST;
  if (!address) throw new Error("Run this test through the Firestore emulator.");

  const [host, port] = address.split(":");

  environment = await initializeTestEnvironment({
    projectId: "demo-deals-rules",
    firestore: {
      host,
      port: Number(port),
      rules: fs.readFileSync(path.resolve(__dirname, "../../firestore.rules"), "utf8"),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();

  await environment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    for (const [uid, profile] of Object.entries(accounts)) {
      await setDoc(doc(db, "users", uid), profile);
    }
    for (const supportUid of ["support-1", "support-2", "support-suspended"]) {
      await setDoc(doc(db, "staffConversations", supportUid), thread(supportUid));
      await setDoc(doc(db, "staffConversations", supportUid, "messages", "m1"), message);
    }
    await setDoc(doc(db, "staffMessageAudit", "a1"), {
      conversationId: "support-1",
      messageId: "m1",
      senderUid: "admin",
      senderRole: "admin",
      createdAt: new Date("2026-06-01"),
    });
    await setDoc(doc(db, "staffMessageLimits", "admin"), {
      windowStartedAt: new Date("2026-06-01"),
      count: 1,
    });
  });
});

after(async () => {
  if (environment) await environment.cleanup();
});

function databaseFor(uid) {
  return uid === "signed-out"
    ? environment.unauthenticatedContext().firestore()
    : environment.authenticatedContext(uid).firestore();
}

const threadDoc = (db, s) => doc(db, "staffConversations", s);
const messageDoc = (db, s) => doc(db, "staffConversations", s, "messages", "m1");
const messageList = (db, s) => collection(db, "staffConversations", s, "messages");

async function canRead(db, supportUid) {
  await assertSucceeds(getDoc(threadDoc(db, supportUid)));
  await assertSucceeds(getDoc(messageDoc(db, supportUid)));
  await assertSucceeds(getDocs(messageList(db, supportUid)));
}

async function cannotRead(db, supportUid) {
  await assertFails(getDoc(threadDoc(db, supportUid)));
  await assertFails(getDoc(messageDoc(db, supportUid)));
  await assertFails(getDocs(messageList(db, supportUid)));
}

// -----------------------------------------------------------------
// Reads
// -----------------------------------------------------------------

for (const uid of ["user", "signed-out"]) {
  test(`${uid} can't read any staff thread`, async () => {
    const db = databaseFor(uid);
    for (const s of ["support-1", "support-2"]) await cannotRead(db, s);
    await assertFails(getDocs(collection(db, "staffConversations")));
  });
}

test("support can read only their own thread", async () => {
  const db = databaseFor("support-1");
  await canRead(db, "support-1");
  await cannotRead(db, "support-2");
  await assertFails(getDocs(collection(db, "staffConversations")));
});

test("admin can read and list all threads", async () => {
  const db = databaseFor("admin");
  for (const s of ["support-1", "support-2", "support-suspended"]) await canRead(db, s);
  const all = await assertSucceeds(getDocs(collection(db, "staffConversations")));
  if (all.size !== 3) throw new Error(`expected 3 threads, got ${all.size}`);
});

test("suspended support can't read their own thread", async () => {
  await cannotRead(databaseFor("support-suspended"), "support-suspended");
});

test("suspended admin can't read any thread", async () => {
  const db = databaseFor("admin-suspended");
  await cannotRead(db, "support-1");
  await assertFails(getDocs(collection(db, "staffConversations")));
});

// -----------------------------------------------------------------
// Writes
// -----------------------------------------------------------------

for (const uid of ["admin", "support-1", "user", "signed-out"]) {
  test(`${uid} can't write staff threads or messages`, async () => {
    const db = databaseFor(uid);

    await assertFails(setDoc(threadDoc(db, "support-1"), thread("support-1")));
    await assertFails(setDoc(threadDoc(db, "new-thread"), thread("new-thread")));
    await assertFails(updateDoc(threadDoc(db, "support-1"), {adminUnread: 0}));
    await assertFails(updateDoc(threadDoc(db, "support-1"), {supportUnread: 0}));
    await assertFails(deleteDoc(threadDoc(db, "support-1")));

    await assertFails(addDoc(messageList(db, "support-1"), message));
    await assertFails(setDoc(messageDoc(db, "support-1"), message));
    await assertFails(updateDoc(messageDoc(db, "support-1"), {text: "Edited"}));
    await assertFails(deleteDoc(messageDoc(db, "support-1")));
  });

  test(`${uid} can't access the staff audit log or rate limits`, async () => {
    const db = databaseFor(uid);

    await assertFails(getDoc(doc(db, "staffMessageAudit", "a1")));
    await assertFails(getDocs(collection(db, "staffMessageAudit")));
    await assertFails(addDoc(collection(db, "staffMessageAudit"), {senderUid: "x"}));
    await assertFails(deleteDoc(doc(db, "staffMessageAudit", "a1")));

    await assertFails(getDoc(doc(db, "staffMessageLimits", "admin")));
    await assertFails(getDocs(collection(db, "staffMessageLimits")));
    await assertFails(setDoc(doc(db, "staffMessageLimits", "admin"), {count: 0}));
    await assertFails(deleteDoc(doc(db, "staffMessageLimits", "admin")));
  });
}
