// Security rules for the users/{uid} profile document.
//
// The profile holds role and accountStatus, which every other rule and
// Cloud Function trusts. An owner must never be able to change either one:
// not by editing, not by overwriting, and not by deleting the profile and
// creating it again.
//
// Run through the emulator, for example:
//   firebase emulators:exec --config firebase.rules-test.json --only firestore \
//     "node --test tests/firestore/user-profile.rules.test.cjs"

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
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  deleteField,
  serverTimestamp,
} = require("firebase/firestore");

let environment;

const ACTIVE_UID = "alice";
const SUSPENDED_UID = "sam";
const DISABLED_UID = "dana";
const NEW_UID = "newcomer";

function profile(uid, overrides = {}) {
  return {
    displayName: `${uid} display`,
    legalName: `${uid} legal`,
    email: `${uid}@example.com`,
    phoneNumber: "+17805550100",
    address: "1 Example St",
    role: "user",
    accountStatus: "active",
    regionId: "AB",
    uploadedDocuments: [],
    ...overrides,
  };
}

function ownerDb(uid) {
  return environment
    .authenticatedContext(uid, {email: `${uid}@example.com`})
    .firestore();
}

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

    await setDoc(
      doc(db, "users", ACTIVE_UID),
      {...profile(ACTIVE_UID), createdAt: new Date("2026-01-01")}
    );
    await setDoc(
      doc(db, "users", SUSPENDED_UID),
      {
        ...profile(SUSPENDED_UID, {accountStatus: "suspended"}),
        createdAt: new Date("2026-01-01"),
      }
    );
    await setDoc(
      doc(db, "users", DISABLED_UID),
      {
        ...profile(DISABLED_UID, {role: "support", accountStatus: "disabled"}),
        createdAt: new Date("2026-01-01"),
      }
    );
  });
});

after(async () => {
  if (environment) await environment.cleanup();
});

// -----------------------------------------------------------------
// Normal use keeps working
// -----------------------------------------------------------------

test("owner can read their own profile", async () => {
  await assertSucceeds(getDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID)));
});

test("owner can edit normal profile fields", async () => {
  await assertSucceeds(
    updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
      displayName: "Alice B",
      phoneNumber: "+17805550199",
      address: "2 New St",
    })
  );
});

test("owner can update uploadedDocuments", async () => {
  await assertSucceeds(
    updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
      uploadedDocuments: ["id.pdf"],
    })
  );
});

test("new user can create their own active user profile at sign-up", async () => {
  await assertSucceeds(
    setDoc(doc(ownerDb(NEW_UID), "users", NEW_UID), {
      ...profile(NEW_UID),
      createdAt: serverTimestamp(),
    })
  );
});

test("new user can create their own active support profile at sign-up", async () => {
  await assertSucceeds(
    setDoc(doc(ownerDb(NEW_UID), "users", NEW_UID), {
      ...profile(NEW_UID, {role: "support"}),
      createdAt: serverTimestamp(),
    })
  );
});

// -----------------------------------------------------------------
// Sign-up can't grant privileges
// -----------------------------------------------------------------

test("new user cannot create an admin profile", async () => {
  await assertFails(
    setDoc(doc(ownerDb(NEW_UID), "users", NEW_UID), {
      ...profile(NEW_UID, {role: "admin"}),
      createdAt: serverTimestamp(),
    })
  );
});

test("new user cannot create a profile for someone else", async () => {
  await assertFails(
    setDoc(doc(ownerDb(NEW_UID), "users", "someone-else"), {
      ...profile(NEW_UID),
      createdAt: serverTimestamp(),
    })
  );
});

// -----------------------------------------------------------------
// Profiles can't be deleted by their owner
// -----------------------------------------------------------------

for (const uid of [ACTIVE_UID, SUSPENDED_UID, DISABLED_UID]) {
  test(`owner (${uid}) cannot delete their own profile`, async () => {
    await assertFails(deleteDoc(doc(ownerDb(uid), "users", uid)));
  });
}

test("another user cannot delete someone's profile", async () => {
  await assertFails(deleteDoc(doc(ownerDb(ACTIVE_UID), "users", SUSPENDED_UID)));
});

// -----------------------------------------------------------------
// A suspended or disabled owner can't recreate their profile as active
// -----------------------------------------------------------------

test("suspended owner cannot delete then recreate their profile as active", async () => {
  const db = ownerDb(SUSPENDED_UID);
  const ref = doc(db, "users", SUSPENDED_UID);

  await assertFails(deleteDoc(ref));
  await assertFails(
    setDoc(ref, {...profile(SUSPENDED_UID), createdAt: serverTimestamp()})
  );

  // The stored profile is untouched.
  await environment.withSecurityRulesDisabled(async (context) => {
    const snapshot = await getDoc(
      doc(context.firestore(), "users", SUSPENDED_UID)
    );
    if (snapshot.get("accountStatus") !== "suspended") {
      throw new Error("Profile status changed.");
    }
  });
});

test("suspended owner cannot overwrite their profile as active", async () => {
  await assertFails(
    setDoc(doc(ownerDb(SUSPENDED_UID), "users", SUSPENDED_UID), {
      ...profile(SUSPENDED_UID),
      createdAt: new Date("2026-01-01"),
    })
  );
});

test("suspended owner cannot merge accountStatus: active", async () => {
  await assertFails(
    setDoc(
      doc(ownerDb(SUSPENDED_UID), "users", SUSPENDED_UID),
      {accountStatus: "active"},
      {merge: true}
    )
  );
});

test("disabled owner cannot overwrite their profile as active", async () => {
  await assertFails(
    setDoc(doc(ownerDb(DISABLED_UID), "users", DISABLED_UID), {
      ...profile(DISABLED_UID, {role: "support"}),
      createdAt: new Date("2026-01-01"),
    })
  );
});

// -----------------------------------------------------------------
// role and accountStatus can't be changed by editing
// -----------------------------------------------------------------

for (const role of ["admin", "support", "other"]) {
  test(`owner cannot change role to ${role}`, async () => {
    await assertFails(
      updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {role})
    );
  });
}

for (const accountStatus of ["inactive", "suspended", "disabled"]) {
  test(`active owner cannot change accountStatus to ${accountStatus}`, async () => {
    await assertFails(
      updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {accountStatus})
    );
  });
}

test("suspended owner cannot change accountStatus to active", async () => {
  await assertFails(
    updateDoc(doc(ownerDb(SUSPENDED_UID), "users", SUSPENDED_UID), {
      accountStatus: "active",
    })
  );
});

test("owner cannot remove role or accountStatus", async () => {
  const ref = doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID);
  await assertFails(updateDoc(ref, {role: deleteField()}));
  await assertFails(updateDoc(ref, {accountStatus: deleteField()}));
});

test("owner cannot sneak a role change into a normal edit", async () => {
  await assertFails(
    updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
      displayName: "Alice B",
      role: "admin",
    })
  );
});

test("owner cannot change createdAt", async () => {
  await assertFails(
    updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
      createdAt: serverTimestamp(),
    })
  );
});

// -----------------------------------------------------------------
// statusUpdatedAt / statusUpdatedBy are admin-only (setAccountStatus)
// -----------------------------------------------------------------

for (const field of ["statusUpdatedAt", "statusUpdatedBy"]) {
  test(`new profile cannot include ${field}`, async () => {
    await assertFails(
      setDoc(doc(ownerDb(NEW_UID), "users", NEW_UID), {
        ...profile(NEW_UID),
        createdAt: serverTimestamp(),
        [field]: field === "statusUpdatedBy" ? "admin-x" : serverTimestamp(),
      })
    );
  });

  test(`owner cannot add ${field} to their profile`, async () => {
    await assertFails(
      updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
        [field]: field === "statusUpdatedBy" ? ACTIVE_UID : serverTimestamp(),
      })
    );
  });
}

test("owner cannot change or remove existing status audit fields", async () => {
  await environment.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "users", SUSPENDED_UID), {
      statusUpdatedAt: new Date("2026-06-01"),
      statusUpdatedBy: "admin-x",
    });
  });

  const ref = doc(ownerDb(SUSPENDED_UID), "users", SUSPENDED_UID);

  await assertFails(updateDoc(ref, {statusUpdatedBy: SUSPENDED_UID}));
  await assertFails(updateDoc(ref, {statusUpdatedAt: serverTimestamp()}));
  await assertFails(updateDoc(ref, {statusUpdatedBy: deleteField()}));
  await assertFails(updateDoc(ref, {statusUpdatedAt: deleteField()}));
});

test("normal edits still work on a profile that has status audit fields", async () => {
  await environment.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "users", ACTIVE_UID), {
      statusUpdatedAt: new Date("2026-06-01"),
      statusUpdatedBy: "admin-x",
    });
  });

  await assertSucceeds(
    updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
      displayName: "Alice C",
    })
  );
});

// -----------------------------------------------------------------
// roleUpdatedAt / roleUpdatedBy are admin-only (setAccountRole)
// -----------------------------------------------------------------

for (const field of ["roleUpdatedAt", "roleUpdatedBy"]) {
  test(`new profile cannot include ${field}`, async () => {
    await assertFails(
      setDoc(doc(ownerDb(NEW_UID), "users", NEW_UID), {
        ...profile(NEW_UID),
        createdAt: serverTimestamp(),
        [field]: field === "roleUpdatedBy" ? "admin-x" : serverTimestamp(),
      })
    );
  });

  test(`owner cannot add ${field} to their profile`, async () => {
    await assertFails(
      updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
        [field]: field === "roleUpdatedBy" ? ACTIVE_UID : serverTimestamp(),
      })
    );
  });
}

test("owner cannot change or remove existing role audit fields", async () => {
  await environment.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "users", ACTIVE_UID), {
      roleUpdatedAt: new Date("2026-06-01"),
      roleUpdatedBy: "admin-x",
    });
  });

  const ref = doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID);

  await assertFails(updateDoc(ref, {roleUpdatedBy: ACTIVE_UID}));
  await assertFails(updateDoc(ref, {roleUpdatedAt: serverTimestamp()}));
  await assertFails(updateDoc(ref, {roleUpdatedBy: deleteField()}));
  await assertFails(updateDoc(ref, {roleUpdatedAt: deleteField()}));
});

test("owner cannot change role together with a forged role audit trail", async () => {
  await assertFails(
    updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
      role: "admin",
      roleUpdatedAt: serverTimestamp(),
      roleUpdatedBy: "admin-x",
    })
  );
});

test("normal edits still work on a profile that has role audit fields", async () => {
  await environment.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "users", ACTIVE_UID), {
      roleUpdatedAt: new Date("2026-06-01"),
      roleUpdatedBy: "admin-x",
    });
  });

  await assertSucceeds(
    updateDoc(doc(ownerDb(ACTIVE_UID), "users", ACTIVE_UID), {
      phoneNumber: "+17805550123",
    })
  );
});
