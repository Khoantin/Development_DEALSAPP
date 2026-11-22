package com.example.androidapp.pages.messagingpage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.ChatAttachment
import com.example.androidapp.data.ChatConversation
import com.example.androidapp.data.ChatMessage
import com.example.androidapp.data.ConversationDoc
import com.example.androidapp.data.MessagingRepository
import com.example.androidapp.data.PendingAttachment
import com.example.androidapp.data.SupportLink
import com.example.androidapp.data.UserRepository
import com.example.androidapp.data.attachmentError
import com.example.androidapp.data.buildInbox
import com.example.androidapp.data.canSend
import com.example.androidapp.data.toFriendlyFirestoreMessage
import com.example.androidapp.data.toFriendlyStorageMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.io.File
import java.util.UUID

data class InboxUiState(
    val isLoading: Boolean = true,
    val conversations: List<ChatConversation> = emptyList(),
    val isOffline: Boolean = false,
    val errorMessage: String? = null
)

data class ThreadUiState(
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val sendError: String? = null,
    val isUploading: Boolean = false,
    val openingPath: String? = null
)

class MessagingViewModel(
    private val repository: MessagingRepository = MessagingRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    var inbox by mutableStateOf(InboxUiState())
        private set

    var thread by mutableStateOf(ThreadUiState())
        private set

    var openConversationId by mutableStateOf<String?>(null)
        private set

    private var myUid: String? = null
    private var myName = ""
    private var sessionVersion = 0L
    private var threadVersion = 0L
    private var cleared = false
    private var accountInitialized = false

    private var userLinks: List<SupportLink> = emptyList()
    private var supportLinks: List<SupportLink> = emptyList()
    private var docs: List<ConversationDoc> = emptyList()

    private var userLinksLoaded = false
    private var supportLinksLoaded = false
    private var docsLoaded = false
    private var docsFromCache = false

    private var userLinksError: String? = null
    private var supportLinksError: String? = null
    private var docError: String? = null
    private var markReadInFlight = false

    private val listeners = mutableListOf<ListenerRegistration>()
    private var threadListener: ListenerRegistration? = null
    private var cancelUpload: (() -> Unit)? = null
    private var cancelDownload: (() -> Unit)? = null
    private val downloadedFiles = mutableSetOf<File>()

    private val authListener = FirebaseAuth.AuthStateListener {
        val uid = it.currentUser?.uid
        if (!cleared && (!accountInitialized || uid != myUid)) {
            accountInitialized = true
            switchAccount(uid)
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    private fun isCurrentSession(uid: String, version: Long): Boolean =
        !cleared &&
                sessionVersion == version &&
                myUid == uid &&
                auth.currentUser?.uid == uid

    private fun isCurrentThread(
        uid: String,
        session: Long,
        id: String,
        version: Long
    ): Boolean =
        isCurrentSession(uid, session) &&
                openConversationId == id &&
                threadVersion == version

    private fun switchAccount(uid: String?) {
        sessionVersion++
        closeConversation()

        listeners.forEach { it.remove() }
        listeners.clear()

        myUid = uid
        myName = ""
        userLinks = emptyList()
        supportLinks = emptyList()
        docs = emptyList()
        userLinksLoaded = false
        supportLinksLoaded = false
        docsLoaded = false
        docsFromCache = false
        userLinksError = null
        supportLinksError = null
        docError = null

        if (uid == null) {
            inbox = InboxUiState(
                isLoading = false,
                errorMessage = "Please sign in to view messages."
            )
            return
        }

        inbox = InboxUiState()
        val session = sessionVersion

        userRepository.getProfile(uid) { result ->
            if (isCurrentSession(uid, session)) {
                result.onSuccess {
                    myName = it?.displayName.orEmpty()
                }
            }
        }

        listeners += repository.listenToLinks("userId", uid) { result ->
            if (isCurrentSession(uid, session)) {
                userLinksLoaded = true
                result.onSuccess {
                    userLinks = it
                    userLinksError = null
                }.onFailure {
                    userLinks = emptyList()
                    userLinksError = it.toFriendlyFirestoreMessage()
                }
                refreshInbox()
            }
        }

        listeners += repository.listenToLinks("supportId", uid) { result ->
            if (isCurrentSession(uid, session)) {
                supportLinksLoaded = true
                result.onSuccess {
                    supportLinks = it
                    supportLinksError = null
                }.onFailure {
                    supportLinks = emptyList()
                    supportLinksError = it.toFriendlyFirestoreMessage()
                }
                refreshInbox()
            }
        }

        listeners += repository.listenToConversations(uid) { result ->
            if (isCurrentSession(uid, session)) {
                docsLoaded = true
                result.onSuccess {
                    docs = it.items
                    docsFromCache = it.fromCache
                    docError = null
                }.onFailure {
                    docs = emptyList()
                    docsFromCache = false
                    docError = it.toFriendlyFirestoreMessage()
                }
                refreshInbox()
            }
        }
    }

    private fun refreshInbox() {
        val uid = myUid ?: return
        val error = userLinksError ?: supportLinksError ?: docError

        inbox = InboxUiState(
            isLoading = !(userLinksLoaded && supportLinksLoaded && docsLoaded) &&
                    error == null,
            conversations = buildInbox(uid, userLinks + supportLinks, docs),
            isOffline = docsLoaded && docsFromCache,
            errorMessage = error
        )

        val id = openConversationId
        if (id != null && (error != null || linkFor(id) == null)) {
            closeConversation()
        } else {
            markReadIfNeeded()
        }
    }

    fun openConversation(id: String) {
        val uid = myUid ?: return
        val session = sessionVersion
        if (!isCurrentSession(uid, session)) return
        if (inbox.errorMessage != null || openConversationId == id) return
        val link = linkFor(id) ?: return

        closeConversation()
        openConversationId = id
        val version = threadVersion
        thread = ThreadUiState()

        threadListener = repository.listenToMessages(id, uid) { result ->
            if (isCurrentThread(uid, session, id, version)) {
                result.onSuccess {
                    thread = thread.copy(
                        isLoading = false,
                        messages = it.items,
                        isOffline = it.fromCache,
                        errorMessage = null
                    )
                    markReadIfNeeded()
                }.onFailure {
                    thread = thread.copy(
                        isLoading = false,
                        messages = emptyList(),
                        errorMessage = it.toFriendlyFirestoreMessage()
                    )
                }
            }
        }

        if (
            uid == link.supportId &&
            myName.isNotBlank() &&
            docs.firstOrNull { it.id == id }?.participantNames?.get(uid) != myName
        ) {
            repository.publishMyName(
                id, link.userId, link.supportId, uid, myName
            )
        }
    }

    fun closeConversation() {
        // Invalidate callbacks before cancelling their tasks.
        threadVersion++
        openConversationId = null

        threadListener?.remove()
        threadListener = null

        val upload = cancelUpload
        val download = cancelDownload
        cancelUpload = null
        cancelDownload = null
        upload?.invoke()
        download?.invoke()

        downloadedFiles.forEach { it.delete() }
        downloadedFiles.clear()

        markReadInFlight = false
        thread = ThreadUiState()
    }

    fun sendMessage(
        text: String,
        attachment: PendingAttachment? = null,
        onAccepted: () -> Unit = {}
    ) {
        val uid = myUid ?: return
        val id = openConversationId ?: return
        val session = sessionVersion
        val version = threadVersion

        if (!isCurrentThread(uid, session, id, version)) return
        if (!canSend(text, attachment != null) || thread.isUploading) return
        if (thread.errorMessage != null) return

        val link = linkFor(id)
        if (link == null) {
            thread = thread.copy(
                sendError = "This connection is no longer active."
            )
            return
        }

        thread = thread.copy(sendError = null)

        if (attachment == null) {
            commitMessage(id, link, uid, text, null, session, version)
            onAccepted()
            return
        }

        val problem = attachmentError(attachment)
        if (problem != null) {
            thread = thread.copy(sendError = problem)
            return
        }

        thread = thread.copy(isUploading = true)

        cancelUpload = repository.uploadAttachment(id, attachment) { result ->
            if (isCurrentThread(uid, session, id, version)) {
                cancelUpload = null
                thread = thread.copy(isUploading = false)

                result.onSuccess { uploaded ->
                    val currentLink = linkFor(id)
                    if (currentLink == null) {
                        thread = thread.copy(
                            sendError = "This connection is no longer active."
                        )
                    } else {
                        commitMessage(
                            id, currentLink, uid, text,
                            uploaded, session, version
                        )
                        onAccepted()
                    }
                }.onFailure {
                    thread = thread.copy(
                        sendError = it.toFriendlyStorageMessage()
                    )
                }
            }
        }
    }

    private fun commitMessage(
        id: String,
        link: SupportLink,
        uid: String,
        text: String,
        attachment: ChatAttachment?,
        session: Long,
        version: Long
    ) {
        if (!isCurrentThread(uid, session, id, version)) return

        val recipient = if (uid == link.userId) link.supportId else link.userId

        repository.sendMessage(
            conversationId = id,
            userId = link.userId,
            supportId = link.supportId,
            senderUid = uid,
            senderName = if (uid == link.supportId) myName else "",
            recipientUid = recipient,
            text = text,
            attachment = attachment
        ) { result ->
            if (isCurrentThread(uid, session, id, version)) {
                result.onFailure {
                    thread = thread.copy(
                        sendError = "A message could not be sent. " +
                                it.toFriendlyFirestoreMessage()
                    )
                }
            }
        }
    }

    fun openAttachment(
        attachment: ChatAttachment,
        cacheDir: File,
        onReady: (File, String) -> Unit
    ) {
        val uid = myUid ?: return
        val id = openConversationId ?: return
        val session = sessionVersion
        val version = threadVersion

        if (!isCurrentThread(uid, session, id, version)) return
        if (thread.openingPath != null || linkFor(id) == null) return
        if (thread.errorMessage != null) return

        val prefix = "message-files/$id/"
        val fileName = attachment.path.removePrefix(prefix)
        if (
            !attachment.path.startsWith(prefix) ||
            fileName.isBlank() ||
            fileName.contains("/") ||
            fileName.contains("\\") ||
            fileName == "." ||
            fileName == ".."
        ) {
            thread = thread.copy(sendError = "That file is not available.")
            return
        }

        val folder = File(cacheDir, "message_files")
        if (!folder.isDirectory && !folder.mkdirs()) {
            thread = thread.copy(sendError = "Unable to prepare the file.")
            return
        }

        // Unique paths prevent an old callback deleting a newer download.
        val destination = File(folder, UUID.randomUUID().toString())
        downloadedFiles.add(destination)
        thread = thread.copy(
            openingPath = attachment.path,
            sendError = null
        )

        cancelDownload = repository.downloadAttachment(
            attachment.path,
            destination
        ) { result ->
            if (!isCurrentThread(uid, session, id, version)) {
                destination.delete()
            } else {
                cancelDownload = null
                thread = thread.copy(openingPath = null)

                result.onSuccess {
                    onReady(it, attachment.contentType.ifBlank { "*/*" })
                }.onFailure {
                    destination.delete()
                    downloadedFiles.remove(destination)
                    thread = thread.copy(
                        sendError = it.toFriendlyStorageMessage()
                    )
                }
            }
        }
    }

    fun showThreadError(message: String) {
        thread = thread.copy(sendError = message)
    }

    fun clearSendError() {
        thread = thread.copy(sendError = null)
    }

    private fun markReadIfNeeded() {
        val uid = myUid ?: return
        val id = openConversationId ?: return
        val session = sessionVersion
        val version = threadVersion

        if (!isCurrentThread(uid, session, id, version)) return
        if (thread.isLoading || thread.errorMessage != null) return

        val unread = inbox.conversations
            .firstOrNull { it.id == id }?.unreadCount ?: 0

        if (unread > 0 && !markReadInFlight) {
            markReadInFlight = true
            repository.markRead(id, uid) {
                if (isCurrentThread(uid, session, id, version)) {
                    markReadInFlight = false
                }
            }
        }
    }

    private fun linkFor(id: String): SupportLink? =
        (userLinks + supportLinks).firstOrNull {
            it.id == id &&
                    it.status == "accepted" &&
                    (it.userId == myUid || it.supportId == myUid)
        }

    override fun onCleared() {
        cleared = true
        sessionVersion++
        auth.removeAuthStateListener(authListener)

        closeConversation()
        listeners.forEach { it.remove() }
        listeners.clear()

        super.onCleared()
    }
}