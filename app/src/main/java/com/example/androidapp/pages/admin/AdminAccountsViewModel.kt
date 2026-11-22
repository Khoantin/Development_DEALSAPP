package com.example.androidapp.pages.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.AdminAccount
import com.example.androidapp.data.AdminAccountOptions
import com.example.androidapp.data.AdminAccountsLogic
import com.example.androidapp.data.AdminAccountsRepository
import com.example.androidapp.data.isStatusSavedButSignInPending
import com.example.androidapp.data.toAdminAccountsMessage
import com.google.firebase.auth.FirebaseAuth

/** A status change the admin has started but not yet confirmed or finished. */
data class PendingStatusChange(
    val account: AdminAccount,
    val toStatus: String
)

/**
 * A role change the admin has started. For an admin account the dialog
 * offers a choice of User or Support, so the target role is picked there.
 */
data class PendingRoleChange(
    val account: AdminAccount,
    val choices: List<String>
) {
    val isPromotion: Boolean get() = choices == listOf("admin")
}

data class AdminAccountsUiState(
    val currentUid: String? = null,
    val roleFilter: String? = null,
    val statusFilter: String? = null,
    val query: String = "",
    val accounts: List<AdminAccount> = emptyList(),
    val nextCursor: String? = null,
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val selectedUid: String? = null,
    val pending: PendingStatusChange? = null,
    val pendingRole: PendingRoleChange? = null,
    val isSaving: Boolean = false,
    val actionError: String? = null,
    val message: String? = null
) {
    val visibleAccounts: List<AdminAccount>
        get() = AdminAccountsLogic.filterAccounts(accounts, query)

    val selectedAccount: AdminAccount?
        get() = accounts.firstOrNull { it.uid == selectedUid }
}

class AdminAccountsViewModel(
    private val repository: AdminAccountsRepository = AdminAccountsRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    var state by mutableStateOf(AdminAccountsUiState(currentUid = auth.currentUser?.uid))
        private set

    private var requestVersion = 0
    private var currentUid: String? = auth.currentUser?.uid

    // Discard displayed data and pending responses when accounts change.
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val newUid = firebaseAuth.currentUser?.uid

        if (newUid != currentUid) {
            currentUid = newUid
            requestVersion++
            state = AdminAccountsUiState(currentUid = newUid)
            refresh()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        refresh()
    }

    // -------------------------------------------------------------
    // Filters and search
    // -------------------------------------------------------------

    fun setRoleFilter(role: String?) {
        if (role == state.roleFilter || state.isSaving) return
        state = state.copy(roleFilter = role)
        refresh()
    }

    fun setStatusFilter(status: String?) {
        if (status == state.statusFilter || state.isSaving) return
        state = state.copy(statusFilter = status)
        refresh()
    }

    /** Search filters the rows already loaded; it doesn't call the backend. */
    fun setQuery(query: String) {
        state = state.copy(query = query.take(100))
    }

    // -------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------

    fun refresh() {
        if (state.isSaving) return

        val uid = auth.currentUser?.uid
        if (uid == null) {
            state = state.copy(
                accounts = emptyList(),
                nextCursor = null,
                isLoading = false,
                isLoadingMore = false,
                error = "Please sign in again."
            )
            return
        }

        val version = ++requestVersion
        val role = state.roleFilter
        val status = state.statusFilter

        // Clear old rows so a failed refresh never looks current.
        state = state.copy(
            accounts = emptyList(),
            nextCursor = null,
            isLoading = true,
            isLoadingMore = false,
            error = null,
            loadMoreError = null,
            selectedUid = null
        )

        repository.listAccounts(role, status, cursor = null) { result ->
            if (version != requestVersion || auth.currentUser?.uid != uid) return@listAccounts

            state = result.fold(
                onSuccess = { page ->
                    state.copy(
                        accounts = page.accounts,
                        nextCursor = page.nextCursor,
                        hasLoaded = true,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    state.copy(
                        isLoading = false,
                        error = error.toAdminAccountsMessage()
                    )
                }
            )
        }
    }

    fun loadMore() {
        val cursor = state.nextCursor ?: return
        if (state.isLoading || state.isLoadingMore || state.isSaving) return

        val uid = auth.currentUser?.uid ?: return
        val version = ++requestVersion

        state = state.copy(isLoadingMore = true, loadMoreError = null)

        repository.listAccounts(state.roleFilter, state.statusFilter, cursor) { result ->
            if (version != requestVersion || auth.currentUser?.uid != uid) return@listAccounts

            state = result.fold(
                onSuccess = { page ->
                    state.copy(
                        accounts = AdminAccountsLogic.appendPage(state.accounts, page.accounts),
                        nextCursor = page.nextCursor,
                        isLoadingMore = false
                    )
                },
                onFailure = { error ->
                    state.copy(
                        isLoadingMore = false,
                        loadMoreError = error.toAdminAccountsMessage()
                    )
                }
            )
        }
    }

    // -------------------------------------------------------------
    // Details sheet and status changes
    // -------------------------------------------------------------

    fun select(uid: String) {
        // The admin's own row has no actions.
        if (uid == state.currentUid || state.isSaving) return
        state = state.copy(selectedUid = uid, message = null)
    }

    fun dismissDetails() {
        if (state.isSaving) return
        state = state.copy(selectedUid = null)
    }

    fun startStatusChange(toStatus: String) {
        val account = state.selectedAccount ?: return
        if (state.isSaving || toStatus !in AdminAccountsLogic.availableTargets(account.accountStatus)) return

        state = state.copy(
            pending = PendingStatusChange(account, toStatus),
            pendingRole = null,
            actionError = null
        )
    }

    fun cancelStatusChange() {
        if (state.isSaving) return
        state = state.copy(pending = null, actionError = null)
    }

    /** Opens the role dialog: "Make admin", or "Remove admin rights". */
    fun startRoleChange() {
        val account = state.selectedAccount ?: return
        val choices = AdminAccountsLogic.roleTargets(
            account.role,
            isSelf = account.uid == state.currentUid
        )
        if (state.isSaving || choices.isEmpty()) return

        state = state.copy(
            pendingRole = PendingRoleChange(account, choices),
            pending = null,
            actionError = null
        )
    }

    fun cancelRoleChange() {
        if (state.isSaving) return
        state = state.copy(pendingRole = null, actionError = null)
    }

    /**
     * Sends the role change. Errors such as existing support links, the last
     * admin, or an unchanged role come back with a message from the backend,
     * shown in the dialog. The dialog stays open with the reason kept.
     */
    fun confirmRoleChange(toRole: String, reason: String) {
        val pending = state.pendingRole ?: return
        if (
            state.isSaving ||
            toRole !in pending.choices ||
            !AdminAccountsLogic.isValidReason(reason)
        ) return

        val uid = auth.currentUser?.uid ?: return

        state = state.copy(isSaving = true, actionError = null)

        repository.setAccountRole(
            targetUid = pending.account.uid,
            newRole = toRole,
            reason = reason.trim()
        ) { result ->
            if (auth.currentUser?.uid != uid) return@setAccountRole

            state = result.fold(
                onSuccess = { change ->
                    state.copy(
                        accounts = AdminAccountsLogic.applyRoleChange(
                            state.accounts,
                            change.targetUid,
                            change.toRole,
                            state.roleFilter,
                            clock()
                        ),
                        isSaving = false,
                        pendingRole = null,
                        selectedUid = null,
                        message = roleSuccessMessage(pending.account, change.toRole)
                    )
                },
                onFailure = { error ->
                    state.copy(
                        isSaving = false,
                        actionError = error.toAdminAccountsMessage()
                    )
                }
            )
        }
    }

    /**
     * Sends the change. If the status saved but the sign-in step failed, the
     * dialog stays open with the same reason so "Try again" repeats the exact
     * change, which the backend treats as a retry of the sign-in step.
     */
    fun confirmStatusChange(reason: String) {
        val pending = state.pending ?: return
        if (state.isSaving || !AdminAccountsLogic.isValidReason(reason)) return

        val uid = auth.currentUser?.uid ?: return
        val version = requestVersion

        state = state.copy(isSaving = true, actionError = null)

        repository.setAccountStatus(
            targetUid = pending.account.uid,
            newStatus = pending.toStatus,
            reason = reason.trim()
        ) { result ->
            if (auth.currentUser?.uid != uid) return@setAccountStatus

            result.onSuccess { change ->
                state = state.copy(
                    accounts = AdminAccountsLogic.applyStatusChange(
                        state.accounts,
                        change.targetUid,
                        change.toStatus,
                        state.statusFilter,
                        clock()
                    ),
                    isSaving = false,
                    pending = null,
                    selectedUid = null,
                    message = successMessage(pending, change.authUserFound)
                )
            }.onFailure { error ->
                val savedButPending = error.isStatusSavedButSignInPending()

                state = state.copy(
                    // The status itself was saved; show it in the list now.
                    accounts = if (savedButPending && version == requestVersion) {
                        AdminAccountsLogic.applyStatusChange(
                            state.accounts,
                            pending.account.uid,
                            pending.toStatus,
                            statusFilter = null,
                            nowMillis = clock()
                        )
                    } else {
                        state.accounts
                    },
                    isSaving = false,
                    actionError = error.toAdminAccountsMessage()
                )
            }
        }
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    private fun roleSuccessMessage(account: AdminAccount, toRole: String): String {
        val name = account.displayName.ifBlank { account.email }
        return if (toRole == "admin") {
            "$name is now an admin."
        } else {
            "$name's admin rights were removed. " +
                    "They now have the ${AdminAccountOptions.roleLabel(toRole)} role."
        }
    }

    private fun successMessage(pending: PendingStatusChange, authUserFound: Boolean): String {
        val name = pending.account.displayName.ifBlank { pending.account.email }
        val base = when (pending.toStatus) {
            "active" -> "$name was reactivated."
            "suspended" -> "$name was suspended."
            else -> "$name was disabled."
        }
        return if (authUserFound) base else "$base No sign-in account exists for this profile."
    }

    override fun onCleared() {
        requestVersion++
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}
