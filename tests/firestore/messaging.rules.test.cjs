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
  collection,
  getDoc,
  getDocs,
  query,
  where,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
  Timestamp,
  increment,
} = require("firebase/firestore");

let env;

const LINK = "user_support";
const CONVERSATION = `conversations/${LINK}`;
const MESSAGE = `${CONVERSATION}/messages/existing`;

function database(uid) {
  return uid
    ? env.authenticatedContext(uid).firestore()
    : env.unauthenticatedContext().firestore();
}

function messageData(uid, extra = {}) {
  return {
    senderId: uid,
    text: "TEST ONLY",
    createdAt: serverTimestamp(),
    ...extra,
  };
}

function attachment(extra = {}) {
  return {
    name: "test.pdf",
    path: `message-files/${LINK}/test.pdf`,
    size: 10,
    contentType: "application/pdf",
    ...extra,
  };
}

function send(uid, extra = {}) {
  return setDoc(
    doc(database(uid), `${CONVERSATION}/messages/new`),
    messageData(uid, extra)
  );
}

async function changeLink(status) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), "supportLinks", LINK), {
      status,
    });
  });
}

// Mirrors the Android repository's message + summary batch.
function sendBatch(uid, includeUserName = false) {
  const db = database(uid);
  const batch = writeBatch(db);
  const recipient = uid === "user" ? "support" : "user";

  const summary = {
    participants: ["user", "support"],
    userId: "user",
    supportId: "support",
    lastMessage: "TEST ONLY",
    lastMessageAt: serverTimestamp(),
    lastSenderId: uid,
    unreadCounts: {[recipient]: increment(1)},
  };

  if (uid === "support") {
    summary.participantNames = {support: "Support Test"};
  }

  if (includeUserName) {
    summary.participantNames = {user: "Private User Name"};
  }

  batch.set(doc(db, CONVERSATION), summary, {merge: true});
  batch.set(
    doc(db, `${CONVERSATION}/messages/batched`),
    messageData(uid)
  );

  return batch.commit();
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

  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const timestamp = Timestamp.fromMillis(1000);

    await setDoc(doc(db, "supportLinks", LINK), {
      userId: "user",
      supportId: "support",
      status: "accepted",
      createdAt: timestamp,
    });

    await setDoc(doc(db, CONVERSATION), {
      participants: ["user", "support"],
      userId: "user",
      supportId: "support",
      participantNames: {support: "Support Test"},
      lastMessage: "TEST ONLY",
      lastMessageAt: timestamp,
      lastSenderId: "user",
      unreadCounts: {user: 0, support: 1},
    });

    await setDoc(doc(db, MESSAGE), {
      senderId: "user",
      text: "TEST ONLY",
      createdAt: timestamp,
    });

    await setDoc(doc(db, "users/user"), {
      role: "user",
      accountStatus: "active",
      displayName: "Private User Name",
    });
  });
});

after(async () => {
  if (env) await env.cleanup();
});

for (const uid of ["user", "support"]) {
  test(`${uid} can read messages on an accepted link`, async () => {
    await assertSucceeds(getDoc(doc(database(uid), MESSAGE)));
  });

  test(`${uid} can query their inbox`, async () => {
    const db = database(uid);
    await assertSucceeds(getDocs(query(
      collection(db, "conversations"),
      where("participants", "array-contains", uid)
    )));
  });

  test(`${uid} can send a message with its summary`, async () => {
    await assertSucceeds(sendBatch(uid));
  });

  test(`${uid} can create the first conversation by sending`, async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), CONVERSATION));
    });

    await assertSucceeds(sendBatch(uid));
  });
}

for (const uid of ["stranger", null]) {
  const label = uid || "signed-out account";

  test(`${label} cannot read messages`, async () => {
    await assertFails(getDoc(doc(database(uid), MESSAGE)));
  });

  test(`${label} cannot send messages`, async () => {
    await assertFails(send(uid));
  });

  test(`${label} cannot read the conversation summary`, async () => {
    await assertFails(getDoc(doc(database(uid), CONVERSATION)));
  });
}

for (const status of ["pending", "rejected"]) {
  test(`${status} link blocks reading and sending messages`, async () => {
    await changeLink(status);

    for (const uid of ["user", "support"]) {
      await assertFails(getDoc(doc(database(uid), MESSAGE)));
      await assertFails(send(uid));
    }
  });
}

test("disconnect blocks reading and sending messages", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await deleteDoc(doc(ctx.firestore(), "supportLinks", LINK));
  });

  for (const uid of ["user", "support"]) {
    await assertFails(getDoc(doc(database(uid), MESSAGE)));
    await assertFails(send(uid));
  }
});

test("User name cannot be added when sending", async () => {
  await assertFails(sendBatch("user", true));
});

test("Support cannot store the User name either", async () => {
  await assertFails(updateDoc(doc(database("support"), CONVERSATION), {
    "participantNames.user": "Private User Name",
  }));
});

test("Support cannot read the User's private profile", async () => {
  await assertFails(getDoc(doc(database("support"), "users/user")));
});

test("sender identity cannot be forged", async () => {
  await assertFails(send("user", {senderId: "support"}));
});

test("empty message without an attachment is denied", async () => {
  await assertFails(send("user", {text: ""}));
});

test("message longer than 2000 characters is denied", async () => {
  await assertFails(send("user", {text: "x".repeat(2001)}));
});

test("attachment-only message is allowed", async () => {
  await assertSucceeds(send("user", {
    text: "",
    attachment: attachment(),
  }));
});

test("attachment from another conversation is denied", async () => {
  await assertFails(send("user", {
    attachment: attachment({
      path: "message-files/other_support/test.pdf",
    }),
  }));
});

test("client-supplied old timestamp is denied", async () => {
  await assertFails(send("user", {
    createdAt: Timestamp.fromMillis(1000),
  }));
});

test("existing message cannot be edited", async () => {
  await assertFails(updateDoc(doc(database("user"), MESSAGE), {
    text: "Changed",
  }));
});

test("existing message cannot be deleted", async () => {
  await assertFails(deleteDoc(doc(database("user"), MESSAGE)));
});

test("conversation participants cannot be changed", async () => {
  await assertFails(updateDoc(doc(database("user"), CONVERSATION), {
    participants: ["user", "stranger"],
  }));
});

test("recipient can reset their unread count", async () => {
  await assertSucceeds(updateDoc(
    doc(database("support"), CONVERSATION),
    {"unreadCounts.support": 0}
  ));
});