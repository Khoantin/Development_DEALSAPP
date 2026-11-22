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
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  Timestamp,
} = require("firebase/firestore");

let env;

const eventPath = "emergencyEvents/button-test";
const voicePath = "emergencyEvents/voice-test";
const noticePath =
  "users/recipient/emergencyNotifications/button-test";

function database(uid) {
  return uid
    ? env.authenticatedContext(uid).firestore()
    : env.unauthenticatedContext().firestore();
}

function reference(uid, documentPath) {
  return doc(database(uid), documentPath);
}

function cancel(uid, documentPath) {
  return updateDoc(reference(uid, documentPath), {
    status: "cancelled",
    updatedAt: serverTimestamp(),
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
    const timestamp = Timestamp.fromMillis(1000);

    const event = {
      ownerId: "sender",
      recipientIds: ["recipient"],
      triggerType: "emergency_button",
      status: "active",
      message: "TEST ONLY",
      startedAt: timestamp,
      updatedAt: timestamp,
    };

    await setDoc(doc(db, eventPath), event);

    await setDoc(doc(db, voicePath), {
      ownerId: "sender",
      recipientIds: ["recipient"],
      triggerType: "voice_duress",
      status: "active",
      startedAt: timestamp,
      updatedAt: timestamp,
    });

    await setDoc(doc(db, noticePath), {
      eventId: "button-test",
      senderUid: "sender",
      type: "emergency_button",
      message: "TEST ONLY",
      createdAt: timestamp,
      read: false,
    });
  });
});

after(async () => {
  if (env) await env.cleanup();
});

test("sender can read their event", async () => {
  await assertSucceeds(getDoc(reference("sender", eventPath)));
});

test("recipient can read their event", async () => {
  await assertSucceeds(getDoc(reference("recipient", eventPath)));
});

test("unrelated user cannot read the event", async () => {
  await assertFails(getDoc(reference("stranger", eventPath)));
});

test("signed-out user cannot read the event", async () => {
  await assertFails(getDoc(reference(null, eventPath)));
});

test("sender cannot directly cancel a button alert", async () => {
  await assertFails(cancel("sender", eventPath));
});

test("recipient cannot cancel the event", async () => {
  await assertFails(cancel("recipient", eventPath));
});

test("sender can still cancel a voice-duress event", async () => {
  await assertSucceeds(cancel("sender", voicePath));
});

test("recipient cannot cancel a voice-duress event", async () => {
  await assertFails(cancel("recipient", voicePath));
});

test("sender cannot change event recipients", async () => {
  await assertFails(updateDoc(reference("sender", eventPath), {
    recipientIds: ["stranger"],
  }));
});

test("sender cannot delete an event", async () => {
  await assertFails(deleteDoc(reference("sender", eventPath)));
});

test("client cannot create a button alert", async () => {
  await assertFails(setDoc(
    reference("sender", "emergencyEvents/forged"),
    {
      ownerId: "sender",
      recipientIds: ["recipient"],
      triggerType: "emergency_button",
      status: "active",
      startedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }
  ));
});

test("recipient can read their notification", async () => {
  await assertSucceeds(getDoc(reference("recipient", noticePath)));
});

test("sender cannot read the recipient's private inbox", async () => {
  await assertFails(getDoc(reference("sender", noticePath)));
});

test("unrelated user cannot read the notification", async () => {
  await assertFails(getDoc(reference("stranger", noticePath)));
});

test("signed-out user cannot read the notification", async () => {
  await assertFails(getDoc(reference(null, noticePath)));
});

test("recipient can mark a notification read", async () => {
  await assertSucceeds(updateDoc(reference("recipient", noticePath), {
    read: true,
  }));
});

test("recipient cannot rewrite the notification message", async () => {
  await assertFails(updateDoc(reference("recipient", noticePath), {
    message: "Changed",
    read: true,
  }));
});

test("recipient cannot mark a notification cancelled", async () => {
  await assertFails(updateDoc(reference("recipient", noticePath), {
    status: "cancelled",
  }));
});

test("recipient cannot create a notification", async () => {
  await assertFails(setDoc(
    reference("recipient", "users/recipient/emergencyNotifications/forged"),
    {message: "Forged", read: false}
  ));
});

test("recipient cannot delete a notification", async () => {
  await assertFails(deleteDoc(reference("recipient", noticePath)));
});

// SUPPORT LINK APPROVAL TESTS

const supportLinkPath = "supportLinks/pair-user_pair-support";

async function seedSupportLink({
  userStatus = "active",
  supportStatus = "active",
  linkStatus = "pending",
} = {}) {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "users/pair-user"), {
      role: "user",
      accountStatus: userStatus,
    });

    await setDoc(doc(db, "users/pair-support"), {
      role: "support",
      accountStatus: supportStatus,
    });

    await setDoc(doc(db, supportLinkPath), {
      userId: "pair-user",
      supportId: "pair-support",
      status: linkStatus,
      createdAt: Timestamp.fromMillis(1000),
    });
  });
}

for (const status of ["accepted", "rejected"]) {
  test(`User can set a pending support request to ${status}`, async () => {
    await seedSupportLink();

    await assertSucceeds(
      updateDoc(reference("pair-user", supportLinkPath), {status})
    );
  });

  test(`Support cannot set its own request to ${status}`, async () => {
    await seedSupportLink();

    await assertFails(
      updateDoc(reference("pair-support", supportLinkPath), {status})
    );
  });
}

test("unrelated account cannot approve a support request", async () => {
  await seedSupportLink();

  await assertFails(
    updateDoc(reference("stranger", supportLinkPath), {
      status: "accepted",
    })
  );
});

test("signed-out account cannot approve a support request", async () => {
  await seedSupportLink();

  await assertFails(
    updateDoc(reference(null, supportLinkPath), {
      status: "accepted",
    })
  );
});

test("inactive User cannot approve a support request", async () => {
  await seedSupportLink({userStatus: "inactive"});

  await assertFails(
    updateDoc(reference("pair-user", supportLinkPath), {
      status: "accepted",
    })
  );
});

test("User cannot approve a request from inactive Support", async () => {
  await seedSupportLink({supportStatus: "inactive"});

  await assertFails(
    updateDoc(reference("pair-user", supportLinkPath), {
      status: "accepted",
    })
  );
});

test("User cannot change an already accepted request", async () => {
  await seedSupportLink({linkStatus: "accepted"});

  await assertFails(
    updateDoc(reference("pair-user", supportLinkPath), {
      status: "rejected",
    })
  );
});

test("User cannot change the Support ID while approving", async () => {
  await seedSupportLink();

  // Make the replacement a valid active Support account so that
  // this test checks participant immutability, not a missing profile.
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "users/other-support"), {
      role: "support",
      accountStatus: "active",
    });
  });

  await assertFails(
    updateDoc(reference("pair-user", supportLinkPath), {
      status: "accepted",
      supportId: "other-support",
    })
  );
});

// CLIENTS MUST NOT BYPASS PAIRING

for (const uid of ["pair-user", "pair-support"]) {
  test(`${uid} cannot directly create a support request`, async () => {
    await seedSupportLink();

    await env.withSecurityRulesDisabled(async (context) => {
      await deleteDoc(doc(context.firestore(), supportLinkPath));
    });

    await assertFails(
      setDoc(reference(uid, supportLinkPath), {
        userId: "pair-user",
        supportId: "pair-support",
        status: "pending",
        createdAt: serverTimestamp(),
      })
    );
  });
}

// PAIRING COLLECTIONS ARE BACKEND-ONLY

const privatePairingPaths = [
  "supportPairingCodes/123456",
  "supportPairingUsers/pair-user",
  "supportPairingLimits/pair-support",
  "supportPairingRedemptions/pair-support_test-request",
];

for (const documentPath of privatePairingPaths) {
  for (const uid of ["pair-user", "pair-support", null]) {
    const actor = uid || "signed-out account";

    test(`${actor} cannot read ${documentPath}`, async () => {
      await seedSupportLink();

      await env.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), documentPath), {
          marker: "private",
        });
      });

      await assertFails(getDoc(reference(uid, documentPath)));
    });

    test(`${actor} cannot create ${documentPath}`, async () => {
      await seedSupportLink();

      await assertFails(
        setDoc(reference(uid, documentPath), {
          marker: "forged",
        })
      );
    });

    test(`${actor} cannot update ${documentPath}`, async () => {
      await seedSupportLink();

      await env.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), documentPath), {
          marker: "private",
        });
      });

      await assertFails(
        updateDoc(reference(uid, documentPath), {
          marker: "changed",
        })
      );
    });

    test(`${actor} cannot delete ${documentPath}`, async () => {
      await seedSupportLink();

      await env.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), documentPath), {
          marker: "private",
        });
      });

      await assertFails(deleteDoc(reference(uid, documentPath)));
    });
  }
}