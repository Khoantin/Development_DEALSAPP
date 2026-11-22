package com.example.androidapp.data

// Pure logic for the admin accounts screen, kept free of Firebase and
// Android types so it can be unit tested (see AdminAccountsLogicTest).

object AdminAccountsLogic {

    /** Case-insensitive match on display name or email, over loaded rows only. */
    fun filterAccounts(
        accounts: List<AdminAccount>,
        query: String
    ): List<AdminAccount> {
        val needle = query.trim()
        if (needle.isEmpty()) return accounts

        return accounts.filter { account ->
            account.displayName.contains(needle, ignoreCase = true) ||
                    account.email.contains(needle, ignoreCase = true)
        }
    }

    /**
     * Statuses an admin can move an account to from [currentStatus]:
     * every settable status except the one it already has.
     * Order: Reactivate, Suspend, Disable.
     */
    fun availableTargets(currentStatus: String): List<String> =
        listOf("active", "suspended", "disabled").filter { it != currentStatus }

    /**
     * Roles an admin can move another account to: a user or support account
     * can be made an admin, and an admin can be moved to user or support.
     * The admin's own account never has role actions.
     */
    fun roleTargets(currentRole: String, isSelf: Boolean): List<String> = when {
        isSelf -> emptyList()
        currentRole == "admin" -> listOf("user", "support")
        else -> listOf("admin")
    }

    /**
     * Applies a saved role change to the loaded list. If a role filter is
     * active and the account no longer matches it, the row is removed.
     */
    fun applyRoleChange(
        accounts: List<AdminAccount>,
        targetUid: String,
        toRole: String,
        roleFilter: String?,
        nowMillis: Long
    ): List<AdminAccount> =
        accounts.mapNotNull { account ->
            when {
                account.uid != targetUid -> account
                roleFilter != null && roleFilter != toRole -> null
                else -> account.copy(role = toRole, roleUpdatedAtMillis = nowMillis)
            }
        }

    /** Matches the backend: trimmed, 5 to 500 characters. */
    fun isValidReason(reason: String): Boolean =
        reason.trim().length in
                AdminAccountOptions.REASON_MIN..AdminAccountOptions.REASON_MAX

    /**
     * Applies a saved status change to the loaded list. If a status filter
     * is active and the account no longer matches it, the row is removed.
     */
    fun applyStatusChange(
        accounts: List<AdminAccount>,
        targetUid: String,
        toStatus: String,
        statusFilter: String?,
        nowMillis: Long
    ): List<AdminAccount> =
        accounts.mapNotNull { account ->
            when {
                account.uid != targetUid -> account
                statusFilter != null && statusFilter != toStatus -> null
                account.accountStatus == toStatus -> account
                else -> account.copy(
                    accountStatus = toStatus,
                    statusUpdatedAtMillis = nowMillis
                )
            }
        }

    /** Appends a page, dropping any rows already loaded (defensive against overlap). */
    fun appendPage(
        existing: List<AdminAccount>,
        page: List<AdminAccount>
    ): List<AdminAccount> {
        val seen = existing.mapTo(HashSet()) { it.uid }
        return existing + page.filter { seen.add(it.uid) }
    }
}
