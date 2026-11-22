# Admin features: demo script (about 10 minutes)

## Test accounts to prepare

| Account | Role / status | Used for |
|---|---|---|
| **Admin A** | admin, active | Presenter, on device 1 |
| **Admin B** | admin, active | A second admin, so "Remove admin rights" isn't blocked by the last-admin rule |
| **Sam Support** | support, active, display name set, **linked** to Uma | Staff messaging on device 2; shows the blocked role change |
| **Uma User** | user, active, **linked** to Sam | Shows "blocked by support links" |
| **Nia New** | user, active, **no** support links | Suspend/reactivate and make admin/remove admin |

Add a few emergency events before the demo (Uma presses the emergency button and then cancels it, or triggers voice duress), so the emergency counts aren't all 0.

## Pre-demo checklist

- [ ] **Functions deployed:** every function in the README's Deployment list shows in **Firebase console → Functions**, including `getAdminEmergencyStats`, `sendStaffMessage` and `markStaffConversationRead`.
- [ ] **Indexes enabled:** **Firestore → Indexes → Composite** shows both `emergencyEvents` indexes (`status`+`startedAt`, `triggerType`+`startedAt`) as **Enabled**.
- [ ] **Rules published:** **Firestore → Rules** matches `firestore.rules` in the repo, including the `staffConversations` block.
- [ ] **Test accounts ready:** every account in the table above signs in, Nia is active with no links, and Sam and Uma are linked.
- [ ] **Two devices or emulators:** device 1 signed in as Admin A, device 2 as Sam. Both are past the passcode and on their dashboards.
- [ ] **Staff thread warmed up:** one earlier message exists, so the inbox isn't empty.
- [ ] **Laptop:** a terminal in the repo with Java on the PATH and `tests/firestore` dependencies installed. Run the privacy test once beforehand so the emulator is downloaded.
- [ ] The Firebase console is open in a browser tab (`users`, `adminAuditLog`).

## Script

### 1. Dashboard and emergency counts (≈2 min)

**Do:**
1. On device 1, show the **Admin dashboard**.
2. Point at **Overview** (account totals), then scroll to **Emergency activity**.
3. Pull down to refresh.

**Say:**
- "Admins get a dashboard of totals, never individual records."
- "Emergency activity shows how many alerts are active now, how many started in the last 7 and 30 days, how many were cancelled, and the split between the emergency button and voice duress."
- "These numbers come from server-side count queries. The server never reads an alert, so it can't send one to the admin."
- Read the note on screen: "Counts only. Administrators can't see who sent an alert or where they are."
- Tap **Emergency guidance**: "What admins *can* manage is published content, like this emergency guidance and the regional resources." Go back.

### 2. Accounts: suspend and reactivate (≈2 min)

**Do:**
1. Open **Accounts**. Show the filters and search, and point out your own row marked **You**.
2. Tap **Nia New** → **Suspend**. Type "abc" to show the button stays disabled, then type a real reason and confirm.
3. Show the **Suspended** badge.
4. Optional: in the console, show `users/Nia` (`accountStatus: "suspended"`) and the new `adminAuditLog` entry.
5. Open Nia again → **Reactivate** with a reason.

**Say:**
- "Every change needs a reason and is written to an audit log that no app user, not even an admin, can read or edit."
- "Suspending also disables Firebase sign-in."
- "Admins can't change their own account, and the last active admin can't be deactivated."

### 3. Make admin and remove admin (≈2 min)

**Do:**
1. Tap **Uma User** → **Make admin**, with a reason. Show the error that she has support connections and must be unlinked first, and that nothing changed.
2. Optional: do the same with **Sam Support**. His link as the support side blocks it too.
3. Tap **Nia New** → **Make admin**, with a reason. Show "Nia is now an admin."
4. Open Nia → **Remove admin rights** → choose **User**, with a reason, and confirm.

**Say:**
- "An admin can't keep links to clients, so an account with support connections has to be unlinked before its role can change. This includes pending and declined requests."
- "Only active accounts can be promoted, and the last active admin can't be removed."
- "A removed admin loses access straight away: every admin function re-checks the role on each call."

### 4. Staff messaging with the anonymous admin (≈2 min)

**Do:**
1. On device 1, go back to the dashboard and open **Staff messages**.
2. Point out the hint: "Support staff see your messages as 'D.E.A.L.S. Administrator'. Don't include your name."
3. Open Sam's thread, or use **New message** → Sam, and send "Can you check in with your clients today?"
4. On device 2 (Sam), show the badge on the **Message administration** tile. Open it: the message is from **D.E.A.L.S. Administrator**.
5. Sam replies. On device 1, the reply appears live, and the unread badge clears.

**Say:**
- "Support staff can always reach the admin team, but they never learn which admin they're talking to."
- "The message itself stores only the sender's role. The admin's identity is kept in a server-only audit log for accountability."
- "Messages go through Cloud Functions with a rate limit. No app can write to these collections directly."

### 5. Privacy proof (≈2 min)

**Do:** on the laptop, run:
```
firebase emulators:exec --config firebase.rules-test.json --only firestore "node --test tests/firestore/admin-privacy.rules.test.cjs"
```
Scroll through the 25 passing tests.

**Say:**
- "This is the proof that admins can't see private data. We seed a user with a profile, contacts, live location, emergency alerts, documents and messages."
- "Then an active admin tries every way to read them: direct reads, lists, filtered queries and cross-collection queries. Every one is denied by the security rules."
- "Each denial is paired with the owner, or the contact they chose, reading the same data successfully. So this shows the rules block the admin, not that the data is missing."
- "Location is visible only to the people the user chooses. The admin app has no location feature at all."

## If something goes wrong

| Symptom | Likely cause |
|---|---|
| "Unable to load emergency activity" | The indexes are still building. Show the Overview section and move on. |
| Staff messages show "isn't active" | The rules aren't published, or Sam's account isn't active support. |
| Accounts shows "could not be found" | `listAdminAccounts` isn't deployed. |
| The emulator won't start | Java isn't on the PATH. Use the README command. |
