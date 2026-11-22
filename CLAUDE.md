# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal-safety Android app (Kotlin, Jetpack Compose, single `:app` module, package `com.example.androidapp`) backed by Firebase (Auth, Firestore, Storage, Cloud Functions). The app **disguises itself as a weather app**: the public face is a working weather screen, and the real app is unlocked by a local passcode or voice phrase. The privacy and disguise behaviour is the core product requirement, so don't weaken it by accident.

Firebase project: `dealsapp-40972` (`.firebaserc`). The Maps key comes from `secrets.properties` (gitignored) through the secrets-gradle-plugin, with `local.defaults.properties` as the fallback.

## Commands

Android (use `gradlew.bat` from PowerShell, `./gradlew` from bash):

```
./gradlew assembleDebug
./gradlew testDebugUnitTest                                   # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.example.androidapp.data.MessagingLogicTest"
./gradlew connectedDebugAndroidTest                           # instrumented tests, needs a device/emulator
./gradlew lint
```

Cloud Functions (`functions/`, Node 22). The unit tests load the function sources into a `vm` with mocked Firestore, so they don't need an emulator:

```
cd functions && node --test adminGuidance.test.js            # or run every *.test.js
```

Security-rules tests (`tests/firestore/*.cjs`) and `functions/supportPairing.integration.test.cjs` **refuse to run unless the emulator env vars are set** (Firestore `127.0.0.1:8085`, Storage `127.0.0.1:9199`). Use the separate emulator config `firebase.rules-test.json`. The main `firebase.json` only configures functions.

```
cd tests/firestore && npm install
firebase emulators:exec --config firebase.rules-test.json --only firestore,storage "node --test tests/firestore/messaging.rules.test.cjs"
```

`functions/package.json` has no test script, and the one in `tests/firestore/package.json` is a placeholder that exits 1, so call `node --test` directly.

Test-running notes:
- **Run rules files one at a time** (one `emulators:exec` per file), or pass `--test-concurrency=1` when running them all. Every file clears the emulator database in `beforeEach`, so parallel files wipe each other's data and fail at random.
- The emulator needs **Java on the PATH**. In Git Bash: `export PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"`.
- Run the functions unit tests as `node --test *.test.js` inside `functions/`. A bare `node --test` also picks up `supportPairing.integration.test.cjs`, which fails without the emulator.
- 11 failures in the two `*.storage.test.cjs` files are pre-existing (a `storage.rules` null-value error at line 99). Don't treat them as regressions.
- The tests inside the `vm` sandbox return objects from another realm, so compare them as JSON (`JSON.parse(JSON.stringify(x))`) before calling `deepEqual`.

## Git workflow

All PRs merge into `development`, which is the integration branch and holds the current code. `main` is stale. Pull from `origin/development`, branch from it (`feature/...`, `fix/...`), and target PRs at it.

## Architecture

**Navigation is centralized in `navigation/AppNavigation.kt`** (about 2000 lines). Every route is a string `composable("...")` in a single NavHost, and most screen wiring happens inline there, including repository construction, ViewModel scoping and post-action navigation. Read this file first for any flow change. The key flow:

- Start destination `weather`. If disguise or Extreme Privacy is on (`SettingsRepository`), `WeatherScreen` is shown. Otherwise the app routes straight on to `roleHome`, `createPasscode` or `accountSetup`, depending on whether a local passcode (`LocalPasswordRepository`) exists and whether the user is signed in.
- `passcode` then `roleHome` loads `users/{uid}` and dispatches on `role` (`user` goes to `dashboard`, `support` to `supportDashboard`, `admin` to `adminDashboard`). It also requires `accountStatus == "active"`.
- There is a nested account-setup graph (`accountSetup/...`) near the end of the file.

**Privacy layer (`security/`).** `PrivacyRoutePolicy` sorts routes into PUBLIC (`weather`, `weatherContact`), CRITICAL, or PROTECTED (the default). `AppNavigation` uses this level for the inactivity timeout (`PrivacyInactivityTimer`, fed by `MainActivity.onUserInteraction`), the focus-loss and privacy shield behaviour, and returning to the weather cover. **A new route is PROTECTED by default. Add it to the policy if it needs a different level.** Quick Exit (`pages/quickexit`, `repository/QuickExitRepository`) and voice duress/access (`security/voice`) also hook in at the NavHost level.

**Layering.** `pages/<feature>/` holds Compose screens and their ViewModels. `data/` holds the Firebase-backed repositories and models. `repository/` holds device-local storage (passcode, settings, weather prefs and location, quick exit) plus the weather API. Repositories are created directly; there is no DI framework. Pure logic is pulled out so it can be unit tested without Firebase (for example `data/MessagingLogic.kt` and `utils/AttachmentUtils.kt`). Follow that pattern when adding testable logic.

**Trust boundary.** Privileged operations go through callable Cloud Functions, not direct client writes: admin stats and regional resources, emergency alerts and cancellation, support pairing codes, and emergency guidance management Each feature's functions live in their own module (`functions/adminResources.js`, `emergencyAlerts.js`, `supportPairing.js`, and so on) with a sibling `*.test.js`, and `functions/index.js` only re-exports them. A new callable must be exported from `index.js`, and the client calls it by that export name through `getHttpsCallable("...")` in `data/`. Functions re-read the caller's role from `users/{uid}` and never trust client-supplied roles or UIDs. `firestore.rules` and `storage.rules` enforce the rest, and the final `match /{document=**}` denies everything else. When you add a collection or field, update the rules **and** the matching tests in `tests/firestore/` (`*.rules.test.cjs` for Firestore, `*.storage.test.cjs` for Storage).

Main Firestore areas: `users/{uid}` (with subcollections `trustedContacts`, `supportHotlines`, `emergencySettings`, `emergencyNotifications`, `locationSharing`), `locationShares`, `supportLinks` and the `supportPairing*` collections (written only by functions), `conversations/{id}/messages`, `emergencyEvents`, `regionalResources/{region}/resources`, `documentFiles`, `documentShares`, and `emergencyGuides`.

## Admin features

**The privacy rule: admins never read individual user data.** Admins get counts and published content only. Never add an admin path, rule or function that returns a user's location, alerts, contacts, documents or messages. `tests/firestore/admin-privacy.rules.test.cjs` proves this. When you add a user-data collection, add it to that test.

- **Screens:**
  - `pages/admin/`: `AdminDashboardPage` (stats, emergency activity, tool cards), `AdminAccountsPage`, `AdminRegionalResourcesPage`, and the shared styles in `AdminUi.kt`.
  - `pages/staffmessaging/`: the staff inbox and thread screens.
  - `pages/guidance/`: emergency guidance.
- **Data:** `data/AdminDashboardRepository.kt`, `AdminAccountsRepository.kt` with `AdminAccountsLogic.kt`, `AdminRegionalResourceRepository.kt`, and `StaffMessagingRepository.kt` with `StaffMessagingLogic.kt`.
- **Admin navigation:** tool screens return with `returnToAdminDashboard(route)` in `AppNavigation.kt`. The staff messaging routes are CRITICAL in `PrivacyRoutePolicy`.
- **Functions:**

  | Function | Module |
  |---|---|
  | `getAdminDashboardStats` | `index.js` |
  | `getAdminEmergencyStats` | `adminEmergencyStats.js` (`count()` only; needs the two composite indexes in `firestore.indexes.json`) |
  | `listAdminRegionalResources`, `updateAdminRegionalResource` | `adminResources.js` |
  | `listEmergencyGuides`, `manageEmergencyGuide` | `adminGuidance.js` |
  | `listAdminAccounts`, `setAccountStatus`, `setAccountRole` | `adminAccounts.js` |
  | `sendStaffMessage`, `markStaffConversationRead` | `staffMessaging.js` |

  Every one re-reads the caller's profile and requires an active admin (the staff messaging functions also accept active support).
- **Collections:**

  | Collection | Access |
  |---|---|
  | `adminAuditLog` | server-only; status and role changes |
  | `staffConversations/{supportUid}` and `/messages` | read by that support account and active admins; written only by functions; stores `senderRole`, never an admin uid or name |
  | `staffMessageAudit`, `staffMessageLimits` | server-only |
  | `emergencyGuides` | server-only |

  Owners can't change `role`, `accountStatus`, `statusUpdated*` or `roleUpdated*` on `users/{uid}`.
- **Rules helpers:** `isActiveAdmin()`, `isActiveSupport(uid)` and `canReadStaffThread()` in `firestore.rules`.

## Gotchas

- There are two contacts packages. `AppNavigation` wires `pages/contacts/`, not `pages/contactpage/`.
- `LocationSharingRepository.kt` sits in the root package rather than in `data/`.
- The untracked `.txt` files in the repo root (`*-review.txt`, `*-source.txt`, `messaging-current-*.txt`, `admin-guidance-setup.txt`) and `firestore-debug.log` are local scratch, not project documentation.
- `README.md` documents the admin features, first-admin setup, test commands and deployment. The rest of it is still a placeholder.
