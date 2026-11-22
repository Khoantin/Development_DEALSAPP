Test readme for project
Test

## Administrator features

Admins sign in like everyone else and land on the **Admin dashboard**. Every admin action goes through a callable Cloud Function that re-reads the caller's `users/{uid}` profile and requires `role: "admin"` and `accountStatus: "active"`.

| Feature | What it does |
|---|---|
| **Dashboard stats** | Account totals by role and by status (`getAdminDashboardStats`). |
| **Emergency activity** | Anonymous counts: alerts active now, started in the last 7 and 30 days, cancelled (30 days), and the split between emergency button and voice duress (`getAdminEmergencyStats`). It uses `count()` queries only, so no alert is ever read. |
| **Regional resources** | Edit and publish the support listings users see (`listAdminRegionalResources`, `updateAdminRegionalResource`). |
| **Emergency guidance** | Write, review and publish emergency guides (`listEmergencyGuides`, `manageEmergencyGuide`). |
| **Accounts** | List, filter and search accounts. **Suspend**, **Disable** or **Reactivate** an account with a reason. Suspending or disabling also blocks Firebase sign-in (`listAdminAccounts`, `setAccountStatus`). |
| **Roles** | **Make admin** or **Remove admin rights** (back to User or Support), with a reason (`setAccountRole`). |
| **Staff messaging** | Admins and support staff message each other. Support staff see every admin as **"D.E.A.L.S. Administrator"**: messages store only the sender's role, and the sender's uid is kept only in a server-only audit log (`sendStaffMessage`, `markStaffConversationRead`). |

Every account and role change is written to `adminAuditLog`, which no client can read.

### Privacy design

Admins see **counts and published content only**. They can never read a user's location, emergency alerts, trusted contacts, documents or messages. The Firestore rules give the admin role no access to that data, and the admin functions return totals only.

This is proven by `tests/firestore/admin-privacy.rules.test.cjs`. An active admin is denied every get, list and query of a user's profile, contacts, location settings and live location, emergency settings, notifications and events, documents and shares, support links, conversations and messages. The owner, or the contact they chose, can read the same data, so the test proves the rules deny the admin rather than the data being missing.

### Administrator accounts

Nobody can choose the admin role when signing up. The sign-up flow and `firestore.rules` only allow `user` or `support`. The **first** admin on a new Firebase project has to be set up by hand in the Firebase console. After that, every admin change goes through the app.

#### Creating the first admin on a new Firebase project

1. **Create a normal account.** In the app, sign up the person who will be the first admin. Use a fresh account that has never been linked to a support worker or a user. Admins must not have support connections, and the console doesn't check for them.
2. **Find their user ID.** In the Firebase console, open **Authentication → Users**, find their email and copy the **User UID**.
3. **Make them an admin.** Open **Firestore Database → `users` → that UID** and set these two fields (both are strings):
   - `role`: `"admin"`
   - `accountStatus`: `"active"`

   Leave every other field as it is.
4. **Open the app again.** They need to fully close and reopen the app, or sign out and back in. After unlocking, they'll land on the **Admin dashboard**.

The admin features also need the Cloud Functions, indexes and Firestore rules deployed to the project (see [Deployment](#deployment)).

#### All later admin changes go through the Accounts screen

Once one admin exists, use **Admin dashboard → Accounts** for everything:
- **Make admin**, or **Remove admin rights** (back to User or Support)
- **Suspend**, **Disable** or **Reactivate** an account

Each change asks for a reason and is recorded in the `adminAuditLog` collection. The app also enforces these safety rules:
- an admin can't change their own account
- the last active admin can't be removed or deactivated
- only active accounts can be made admins
- a user or support account with support connections must be unlinked before its role can change
- suspending or disabling an account also blocks Firebase sign-in

**Don't edit `role` or `accountStatus` in the console after the first admin exists.** Console edits skip the audit log and every one of the safety rules above, and they don't update Firebase sign-in. Keep at least two active admins so one can always fix the other's account.

### Running the tests

**Cloud Functions unit tests** use an in-memory Firestore fake, so they need no emulator:
```
cd functions
node --test *.test.js
```

**Emulator tests** (the Firestore and Storage rules tests and the support-pairing integration test) need the Firebase emulator, which needs **Java on the PATH**. In Git Bash, Android Studio's bundled Java works:
```
export PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"
cd tests/firestore && npm install && cd ../..
```

Run **one rules file at a time**. Every file clears the emulator database before each test, so files that run in parallel wipe each other's data and fail at random:
```
firebase emulators:exec --config firebase.rules-test.json --only firestore "node --test tests/firestore/admin-privacy.rules.test.cjs"
firebase emulators:exec --config firebase.rules-test.json --only firestore,storage "node --test tests/firestore/messaging.storage.test.cjs"
firebase emulators:exec --config firebase.rules-test.json --only firestore "node --test functions/supportPairing.integration.test.cjs"
```
To run them all in one go, add `--test-concurrency=1`:
```
firebase emulators:exec --config firebase.rules-test.json --only firestore,storage "node --test --test-concurrency=1 tests/firestore/*.test.cjs"
```

**Android:**
```
./gradlew assembleDebug testDebugUnitTest
```

### Deployment

The main `firebase.json` only configures Cloud Functions.

**Admin Cloud Functions** (region `us-central1`):
```
firebase deploy --only functions:getAdminDashboardStats,functions:getAdminEmergencyStats,functions:listAdminRegionalResources,functions:updateAdminRegionalResource,functions:listEmergencyGuides,functions:manageEmergencyGuide,functions:listAdminAccounts,functions:setAccountStatus,functions:setAccountRole,functions:sendStaffMessage,functions:markStaffConversationRead
```

**Composite indexes.** `getAdminEmergencyStats` needs two composite indexes on `emergencyEvents`:
- `status` + `startedAt`
- `triggerType` + `startedAt`

They're defined in `firestore.indexes.json`. That file documents the indexes but isn't wired into `firebase.json`, so `firebase deploy` doesn't create them. Create them once per project, either in the console (**Firestore → Indexes → Composite → Add index**, both fields ascending) or with gcloud:
```
gcloud firestore indexes composite create --project=<project-id> --collection-group=emergencyEvents --query-scope=COLLECTION --field-config=field-path=status,order=ascending --field-config=field-path=startedAt,order=ascending
gcloud firestore indexes composite create --project=<project-id> --collection-group=emergencyEvents --query-scope=COLLECTION --field-config=field-path=triggerType,order=ascending --field-config=field-path=startedAt,order=ascending
```
Until both show **Enabled**, the dashboard shows "Unable to load emergency activity".

**Firestore rules.** Publish `firestore.rules` with the rules-test config:
```
firebase deploy --only firestore:rules --config firebase.rules-test.json
```
To check that the published rules match the repo, open **Firestore → Rules** in the console and compare them with `firestore.rules`.
