package com.example.androidapp.pages.staffmessaging

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.AdminAccount
import com.example.androidapp.data.AdminAccountsRepository
import com.example.androidapp.data.OutgoingStaffMessage
import com.example.androidapp.data.StaffMessage
import com.example.androidapp.data.StaffMessagingLogic
import com.example.androidapp.data.StaffMessagingRepository
import com.example.androidapp.data.StaffThread
import com.example.androidapp.data.toAdminAccountsMessage
import com.example.androidapp.data.toStaffMessagingMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

// Listeners run only between start() and stop(). Screens call these from a
// DisposableEffect, so leaving a screen (including the sign-out/Quick Exit
// navigation, which clears the back stack) removes them. Signing out also
// stops them through the auth listener.

// -----------------------------------------------------------------
// THREAD LIST (admin inbox, admin dashboard badge, support dashboard badge)
// -----------------------------------------------------------------

data class StaffSupportPickerState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val accounts: List<AdminAccount> = emptyList(),
    val errorMessage: String? = null
)

data class StaffInboxUiState(
    val isLoading: Boolean = true,
    val threads: List<StaffThread> = emptyList(),
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val query: String = "",
    val picker: StaffSupportPickerState = StaffSupportPickerState()
)

class StaffInboxViewModel(
    private val repository: StaffMessagingRepository = StaffMessagingRepository(),
    private val accountsRepository: AdminAccountsRepository = AdminAccountsRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    var state by mutableStateOf(StaffInboxUiState())
        private set

    private var registration: ListenerRegistration? = null
    private var pickerVersion = 0

    private val authListener = FirebaseAuth.AuthStateListener {
        if (it.currentUser == null) stop()
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    /** Admins listen to every thread; support only to their own. */
    fun start(viewerRole: String) {
        if (registration != null) return
        val uid = auth.currentUser?.uid ?: run {
            state = state.copy(isLoading = false, errorMessage = "Please sign in again.")
            return
        }
        state = state.copy(isLoading = state.threads.isEmpty(), errorMessage = null)

        val onResult: (Result<com.example.androidapp.data.ListSnapshot<StaffThread>>) -> Unit =
            { result ->
                state = result.fold(
                    onSuccess = {
                        state.copy(
                            isLoading = false,
                            threads = it.items,
                            isOffline = it.fromCache,
                            errorMessage = null
                        )
                    },
                    onFailure = {
                        state.copy(isLoading = false, errorMessage = it.toStaffMessagingMessage())
                    }
                )
            }

        registration = if (viewerRole == StaffMessagingLogic.ADMIN) {
            repository.listenToThreads(onResult)
        } else {
            repository.listenToThread(uid, onResult)
        }
    }

    fun stop() {
        registration?.remove()
        registration = null
        pickerVersion++
        if (auth.currentUser == null) state = StaffInboxUiState(isLoading = false)
    }

    fun onQueryChange(query: String) {
        state = state.copy(query = query)
    }

    fun openPicker() {
        val version = ++pickerVersion
        state = state.copy(picker = StaffSupportPickerState(isOpen = true, isLoading = true))
        loadSupportPage(cursor = null, loaded = emptyList(), version = version)
    }

    fun closePicker() {
        pickerVersion++
        state = state.copy(picker = StaffSupportPickerState())
    }

    // Reuses listAdminAccounts. Support staff is a short list, so every page
    // is loaded and shown together.
    private fun loadSupportPage(cursor: String?, loaded: List<AdminAccount>, version: Int) {
        accountsRepository.listAccounts(role = "support", status = "active", cursor = cursor) { result ->
            if (version != pickerVersion) return@listAccounts
            result.fold(
                onSuccess = { page ->
                    val all = loaded + page.accounts
                    if (page.nextCursor != null) {
                        loadSupportPage(page.nextCursor, all, version)
                    } else {
                        state = state.copy(
                            picker = StaffSupportPickerState(
                                isOpen = true,
                                accounts = all.sortedBy { it.displayName.lowercase() }
                            )
                        )
                    }
                },
                onFailure = {
                    state = state.copy(
                        picker = StaffSupportPickerState(
                            isOpen = true,
                            errorMessage = it.toAdminAccountsMessage()
                        )
                    )
                }
            )
        }
    }

    override fun onCleared() {
        stop()
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}

// -----------------------------------------------------------------
// ONE THREAD (admin or support)
// -----------------------------------------------------------------

data class StaffThreadUiState(
    val viewerRole: String = StaffMessagingLogic.SUPPORT,
    val supportUid: String = "",
    val thread: StaffThread? = null,
    val messages: List<StaffMessage> = emptyList(),
    val outgoing: List<OutgoingStaffMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val errorMessage: String? = null
)

class StaffThreadViewModel(
    private val repository: StaffMessagingRepository = StaffMessagingRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    var state by mutableStateOf(StaffThreadUiState())
        private set

    private val registrations = mutableListOf<ListenerRegistration>()
    private var markingRead = false

    private val authListener = FirebaseAuth.AuthStateListener {
        if (it.currentUser == null) {
            stop()
            state = StaffThreadUiState(isLoading = false)
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    /** [supportUid] is required for admins; support always opens their own thread. */
    fun start(viewerRole: String, supportUid: String?) {
        if (registrations.isNotEmpty()) return
        val uid = auth.currentUser?.uid ?: run {
            state = state.copy(isLoading = false, errorMessage = "Please sign in again.")
            return
        }
        val threadId = if (viewerRole == StaffMessagingLogic.ADMIN) supportUid ?: return else uid
        state = state.copy(
            viewerRole = viewerRole,
            supportUid = threadId,
            isLoading = state.messages.isEmpty(),
            errorMessage = null
        )

        registrations += repository.listenToThread(threadId) { result ->
            result.fold(
                onSuccess = {
                    state = state.copy(thread = it.items.firstOrNull(), isOffline = it.fromCache)
                    markReadIfNeeded()
                },
                onFailure = { state = state.copy(isLoading = false, errorMessage = it.toStaffMessagingMessage()) }
            )
        }

        registrations += repository.listenToMessages(threadId) { result ->
            result.fold(
                onSuccess = { snapshot ->
                    val delivered = snapshot.items.mapTo(HashSet()) { it.id }
                    state = state.copy(
                        isLoading = false,
                        messages = snapshot.items,
                        // Drop sends the listener has now delivered.
                        outgoing = state.outgoing.filter { it.serverId !in delivered },
                        errorMessage = null
                    )
                },
                onFailure = { state = state.copy(isLoading = false, errorMessage = it.toStaffMessagingMessage()) }
            )
        }
    }

    fun stop() {
        registrations.forEach { it.remove() }
        registrations.clear()
    }

    /** Returns false (and keeps the draft) when the text can't be sent. */
    fun send(draft: String): Boolean {
        val text = StaffMessagingLogic.sendableText(draft) ?: return false
        if (state.supportUid.isEmpty()) return false
        val message = OutgoingStaffMessage(
            localId = UUID.randomUUID().toString(),
            text = text,
            createdAtMillis = System.currentTimeMillis()
        )
        state = state.copy(outgoing = state.outgoing + message)
        deliver(message)
        return true
    }

    fun retry(localId: String) {
        val message = state.outgoing.firstOrNull { it.localId == localId } ?: return
        val retrying = message.copy(errorMessage = null)
        replaceOutgoing(retrying)
        deliver(retrying)
    }

    fun discard(localId: String) {
        state = state.copy(outgoing = state.outgoing.filterNot { it.localId == localId })
    }

    private fun deliver(message: OutgoingStaffMessage) {
        val target = state.supportUid.takeIf { state.viewerRole == StaffMessagingLogic.ADMIN }
        repository.send(target, message.text) { result ->
            result.fold(
                onSuccess = { id ->
                    if (state.messages.any { it.id == id }) discard(message.localId)
                    else replaceOutgoing(message.copy(serverId = id))
                },
                onFailure = { replaceOutgoing(message.copy(errorMessage = it.toStaffMessagingMessage())) }
            )
        }
    }

    private fun replaceOutgoing(message: OutgoingStaffMessage) {
        state = state.copy(
            outgoing = state.outgoing.map { if (it.localId == message.localId) message else it }
        )
    }

    // Runs when the thread opens and whenever new messages raise our unread count.
    private fun markReadIfNeeded() {
        val thread = state.thread ?: return
        if (markingRead || registrations.isEmpty()) return
        if (StaffMessagingLogic.unreadFor(thread, state.viewerRole) <= 0) return

        markingRead = true
        val target = state.supportUid.takeIf { state.viewerRole == StaffMessagingLogic.ADMIN }
        repository.markRead(target) {
            markingRead = false
            // A failure leaves the badge as is; the next snapshot will try again.
        }
    }

    override fun onCleared() {
        stop()
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}
