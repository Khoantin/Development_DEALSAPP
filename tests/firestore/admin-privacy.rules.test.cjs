// Admin privacy (backlog 8-1): an ACTIVE admin who isn't a participant
// can't read any of a user's private data, by get, list or query. Each
// denial is paired with the owner (or chosen recipient) reading the same
// seeded data, so a pass proves the rules deny the admin, not that the
// data is missing.
//
// Run through the emulator, for example:
//   firebase emulators:exec --config firebase.rules-test.json --only firestore \
//     "node --test tests/firestore/admin-privacy.rules.test.cjs"

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
  collectionGroup,
  query,
  where,
  getDoc,
  getDocs,
  setDoc,
} = require("firebase/firestore");

let environment;

const OWNER = "owner";
const CONTACT = "contact"; // the owner's chosen trusted contact / recipient
const SUPPORT = "support"; // the owner's linked support worker
const ADMIN = "admin"; // active admin, not a participant in anything
const LINK = `${OWNER}_${SUPPORT}`;
const when = new Date("2026-06-01");

const seed = {
  [`users/${OWNER}`]: {role: "user", accountStatus: "active", displayName: "Owner",
    email: "owner@example.com", phoneNumber: "+15555550100", address: "1 Private St"},
  [`users/${CONTACT}`]: {role: "user", accountStatus: "active"},
  [`users/${SUPPORT}`]: {role: "support", accountStatus: "active"},
  [`users/${ADMIN}`]: {role: "admin", accountStatus: "active"},

  [`users/${OWNER}/trustedContacts/c1`]: {name: "Contact", linkedUserId: CONTACT,
    accessGiven: true, emergencyContact: true, emergencyInAppOptIn: true},
  [`users/${OWNER}/locationSharing/settings`]: {enabled: true, selectedContactIds: ["c1"]},
  [`locationShares/${OWNER}`]: {ownerId: OWNER, ownerDisplayName: "Owner",
    selectedContactIds: ["c1"], recipientIds: [CONTACT], latitude: 53.5461,
    longitude: -113.4938, active: true, startedAt: when, updatedAt: when},
  [`users/${OWNER}/emergencySettings/preset`]: {message: "I need help", receiveInApp: true},
  [`users/${OWNER}/emergencyNotifications/${CONTACT}_req`]: {eventId: `${CONTACT}_req`,
    senderUid: CONTACT, type: "emergency_button", message: "Help", createdAt: when,
    read: false, location: {latitude: 53.5, longitude: -113.5}},
  ["emergencyEvents/e1"]: {ownerId: OWNER, recipientIds: [CONTACT],
    selectedContactIds: ["c1"], triggerType: "emergency_button", status: "active",
    message: "I need help", startedAt: when, updatedAt: when,
    location: {latitude: 53.5, longitude: -113.5, accuracyMeters: 10, capturedAtMillis: 1}},
  ["documentFiles/doc1"]: {ownerUid: OWNER, name: "id.pdf", type: "ID",
    cloudStoragePath: `protectedDocuments/${OWNER}/doc1/content`,
    contentType: "application/pdf", sizeBytes: 100, createdAt: when},
  [`documentShares/doc1_${CONTACT}`]: {documentId: "doc1", ownerUid: OWNER,
    recipientUid: CONTACT, contactId: "c1", createdAt: when},
  [`supportLinks/${LINK}`]: {userId: OWNER, supportId: SUPPORT, status: "accepted",
    createdAt: when},
  [`conversations/${LINK}`]: {participants: [OWNER, SUPPORT], userId: OWNER,
    supportId: SUPPORT, participantNames: {[SUPPORT]: "Support"}, lastMessage: "Hi",
    lastMessageAt: when, lastSenderId: OWNER, unreadCounts: {[SUPPORT]: 1}},
  [`conversations/${LINK}/messages/m1`]: {senderId: OWNER, text: "Hi", createdAt: when},
};

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
    for (const [docPath, data] of Object.entries(seed)) await setDoc(doc(db, docPath), data);
  });
});

after(async () => {
  if (environment) await environment.cleanup();
});

const dbFor = (uid) => environment.authenticatedContext(uid).firestore();

// Each target: the document, the reads an admin tries (all must fail), and
// the reads the rightful reader makes on the same data (all must succeed).
const targets = [
  {
    name: "user profile",
    path: `users/${OWNER}`,
    adminReads: (db) => [getDocs(collection(db, "users")),
      getDocs(query(collection(db, "users"), where("role", "==", "user")))],
    allowed: [[OWNER, (db) => getDoc(doc(db, "users", OWNER))]],
  },
  {
    name: "trustedContacts",
    path: `users/${OWNER}/trustedContacts/c1`,
    adminReads: (db) => [getDocs(collection(db, "users", OWNER, "trustedContacts")),
      getDocs(collectionGroup(db, "trustedContacts"))],
    allowed: [[OWNER, (db) => getDocs(collection(db, "users", OWNER, "trustedContacts"))]],
  },
  {
    name: "locationSharing settings",
    path: `users/${OWNER}/locationSharing/settings`,
    adminReads: (db) => [getDocs(collection(db, "users", OWNER, "locationSharing")),
      getDocs(collectionGroup(db, "locationSharing"))],
    allowed: [[OWNER, (db) => getDocs(collection(db, "users", OWNER, "locationSharing"))]],
  },
  {
    name: "locationShares (live location)",
    path: `locationShares/${OWNER}`,
    adminReads: (db) => [getDocs(collection(db, "locationShares")),
      getDocs(query(collection(db, "locationShares"), where("ownerId", "==", OWNER))),
      getDocs(query(collection(db, "locationShares"), where("active", "==", true)))],
    // The owner and the recipient the owner chose.
    allowed: [[OWNER, (db) => getDoc(doc(db, "locationShares", OWNER))],
      [CONTACT, (db) => getDoc(doc(db, "locationShares", OWNER))],
      [CONTACT, (db) => getDocs(query(collection(db, "locationShares"),
        where("recipientIds", "array-contains", CONTACT)))]],
  },
  {
    name: "emergencySettings",
    path: `users/${OWNER}/emergencySettings/preset`,
    adminReads: (db) => [getDocs(collection(db, "users", OWNER, "emergencySettings")),
      getDocs(collectionGroup(db, "emergencySettings"))],
    // The rule only allows the single "preset" doc, so the owner reads it by id.
    allowed: [[OWNER, (db) => getDoc(doc(db, "users", OWNER, "emergencySettings", "preset"))]],
  },
  {
    name: "emergencyNotifications",
    path: `users/${OWNER}/emergencyNotifications/${CONTACT}_req`,
    adminReads: (db) => [getDocs(collection(db, "users", OWNER, "emergencyNotifications")),
      getDocs(collectionGroup(db, "emergencyNotifications"))],
    allowed: [[OWNER, (db) => getDocs(collection(db, "users", OWNER, "emergencyNotifications"))]],
  },
  {
    name: "emergencyEvents",
    path: "emergencyEvents/e1",
    adminReads: (db) => [getDocs(collection(db, "emergencyEvents")),
      getDocs(query(collection(db, "emergencyEvents"), where("ownerId", "==", OWNER))),
      getDocs(query(collection(db, "emergencyEvents"), where("status", "==", "active")))],
    allowed: [[OWNER, (db) => getDocs(query(collection(db, "emergencyEvents"),
      where("ownerId", "==", OWNER)))],
    [CONTACT, (db) => getDoc(doc(db, "emergencyEvents", "e1"))]],
  },
  {
    name: "documentFiles",
    path: "documentFiles/doc1",
    adminReads: (db) => [getDocs(collection(db, "documentFiles")),
      getDocs(query(collection(db, "documentFiles"), where("ownerUid", "==", OWNER)))],
    allowed: [[OWNER, (db) => getDocs(query(collection(db, "documentFiles"),
      where("ownerUid", "==", OWNER)))],
    [CONTACT, (db) => getDoc(doc(db, "documentFiles", "doc1"))]],
  },
  {
    name: "documentShares",
    path: `documentShares/doc1_${CONTACT}`,
    adminReads: (db) => [getDocs(collection(db, "documentShares")),
      getDocs(query(collection(db, "documentShares"), where("ownerUid", "==", OWNER)))],
    allowed: [[OWNER, (db) => getDocs(query(collection(db, "documentShares"),
      where("ownerUid", "==", OWNER)))],
    [CONTACT, (db) => getDoc(doc(db, "documentShares", `doc1_${CONTACT}`))]],
  },
  {
    name: "supportLinks",
    path: `supportLinks/${LINK}`,
    adminReads: (db) => [getDocs(collection(db, "supportLinks")),
      getDocs(query(collection(db, "supportLinks"), where("userId", "==", OWNER)))],
    allowed: [[OWNER, (db) => getDocs(query(collection(db, "supportLinks"),
      where("userId", "==", OWNER)))]],
  },
  {
    name: "conversations",
    path: `conversations/${LINK}`,
    adminReads: (db) => [getDocs(collection(db, "conversations")),
      getDocs(query(collection(db, "conversations"),
        where("participants", "array-contains", OWNER)))],
    allowed: [[OWNER, (db) => getDocs(query(collection(db, "conversations"),
      where("participants", "array-contains", OWNER)))],
    [SUPPORT, (db) => getDoc(doc(db, "conversations", LINK))]],
  },
  {
    name: "conversation messages",
    path: `conversations/${LINK}/messages/m1`,
    adminReads: (db) => [getDocs(collection(db, "conversations", LINK, "messages")),
      getDocs(collectionGroup(db, "messages"))],
    allowed: [[OWNER, (db) => getDocs(collection(db, "conversations", LINK, "messages"))],
      [SUPPORT, (db) => getDoc(doc(db, "conversations", LINK, "messages", "m1"))]],
  },
];

test("the admin account really is an active admin", async () => {
  // Positive control: rules grant this account admin-only reads, so the
  // denials below aren't caused by a broken admin profile.
  await assertSucceeds(getDocs(collection(dbFor(ADMIN), "staffConversations")));
});

for (const target of targets) {
  test(`active admin can't read ${target.name}`, async () => {
    const db = dbFor(ADMIN);
    await assertFails(getDoc(doc(db, target.path)));
    for (const read of target.adminReads(db)) await assertFails(read);
  });

  test(`owner or chosen recipient can read ${target.name}`, async () => {
    const owner = dbFor(OWNER);
    // Every target is the owner's own data, so the owner can always get it.
    await assertSucceeds(getDoc(doc(owner, target.path)));
    for (const [uid, read] of target.allowed) {
      const result = await assertSucceeds(read(dbFor(uid)));
      // A list or query must actually return the seeded data.
      if (result.size === 0 || result.exists === false) {
        throw new Error(`${uid} read of ${target.name} returned no data`);
      }
    }
  });
}
