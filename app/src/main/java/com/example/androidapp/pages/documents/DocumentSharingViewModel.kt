package com.example.androidapp.pages.documents

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.data.DocumentRecipient
import com.example.androidapp.data.DocumentSharingRepository
import com.example.androidapp.data.toFriendlyFirestoreMessage
import com.example.androidapp.repository.UserDocument
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class DocumentSharingViewModel(
    private val repository: DocumentSharingRepository =
        DocumentSharingRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    var ownDocuments by mutableStateOf<List<UserDocument>>(emptyList())
        private set

    var sharedDocuments by mutableStateOf<List<UserDocument>>(emptyList())
        private set

    var recipients by mutableStateOf<List<DocumentRecipient>>(emptyList())
        private set

    var selectedDocument by mutableStateOf<UserDocument?>(null)
        private set

    var sharedRecipientIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var expiredSupportRecipientIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isUploading by mutableStateOf(false)
        private set

    var isLoadingPermissions by mutableStateOf(false)
        private set

    var changingRecipientUid by mutableStateOf<String?>(null)
        private set

    var openingDocumentId by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var permissionsError by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    var currentUid by mutableStateOf(auth.currentUser?.uid)
        private set

    private var accountGeneration = 0
    private var permissionGeneration = 0

    private var refreshJob: Job? = null
    private var permissionsJob: Job? = null

    private val authListener = FirebaseAuth.AuthStateListener {
        val nextUid = it.currentUser?.uid

        if (nextUid != currentUid) {
            accountGeneration++
            permissionGeneration++

            refreshJob?.cancel()
            permissionsJob?.cancel()

            currentUid = nextUid
            clearAccountState()

            if (nextUid != null) {
                refresh()
            }
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        refresh()
    }

    private fun clearAccountState() {
        ownDocuments = emptyList()
        sharedDocuments = emptyList()
        recipients = emptyList()
        selectedDocument = null
        sharedRecipientIds = emptySet()
        expiredSupportRecipientIds = emptySet()

        isLoading = false
        isUploading = false
        isLoadingPermissions = false
        changingRecipientUid = null
        openingDocumentId = null

        errorMessage = null
        permissionsError = null
        successMessage = null
    }

    private fun isCurrentAccount(
        uid: String,
        generation: Int
    ): Boolean =
        accountGeneration == generation &&
                currentUid == uid &&
                auth.currentUser?.uid == uid

    fun refresh() {
        val uid = currentUid ?: run {
            clearAccountState()
            return
        }

        val generation = accountGeneration

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null

            // Clear old results rather than retaining possibly revoked access.
            ownDocuments = emptyList()
            sharedDocuments = emptyList()
            recipients = emptyList()

            try {
                val own = repository.loadOwnDocuments().getOrThrow()
                val shared = repository.loadSharedDocuments().getOrThrow()
                val contacts = repository.loadRecipients().getOrThrow()

                if (isCurrentAccount(uid, generation)) {
                    ownDocuments = own
                    sharedDocuments = shared
                    recipients = contacts
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAccount(uid, generation)) {
                    errorMessage = friendlyMessage(error)
                }
            } finally {
                if (isCurrentAccount(uid, generation)) {
                    isLoading = false
                }
            }
        }
    }

    fun uploadDocument(
        context: Context,
        uri: Uri,
        category: String
    ) {
        if (isUploading) return

        val uid = currentUid ?: return
        val generation = accountGeneration

        isUploading = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            try {
                repository.uploadDocument(
                    context.applicationContext,
                    uri,
                    category
                ).getOrThrow()

                if (isCurrentAccount(uid, generation)) {
                    successMessage = "Document uploaded."
                    refresh()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAccount(uid, generation)) {
                    errorMessage = friendlyMessage(error)
                }
            } finally {
                if (isCurrentAccount(uid, generation)) {
                    isUploading = false
                }
            }
        }
    }

    fun selectDocumentForSharing(document: UserDocument) {
        val uid = currentUid ?: return

        if (document.ownerUid != uid) {
            errorMessage = "Only the owner can share this document."
            return
        }

        // Do not switch documents while a permission change is running.
        if (changingRecipientUid != null) return

        selectedDocument = document
        sharedRecipientIds = emptySet()
        expiredSupportRecipientIds = emptySet()
        permissionsError = null

        loadSelectedDocumentPermissions()
    }

    fun loadSelectedDocumentPermissions() {
        val document = selectedDocument ?: return
        val uid = currentUid ?: return
        val generation = accountGeneration

        permissionGeneration++
        val requestGeneration = permissionGeneration

        permissionsJob?.cancel()
        isLoadingPermissions = true
        permissionsError = null

        permissionsJob = viewModelScope.launch {
            try {
                val contacts = repository.loadRecipients().getOrThrow()
                val sharedIds = repository
                    .loadSharedRecipientIds(document.id)
                    .getOrThrow()
                val expiredIds = repository
                    .loadExpiredSupportRecipientIds(document.id)
                    .getOrThrow()

                if (
                    isCurrentAccount(uid, generation) &&
                    permissionGeneration == requestGeneration &&
                    selectedDocument?.id == document.id
                ) {
                    recipients = contacts
                    sharedRecipientIds = sharedIds
                    expiredSupportRecipientIds = expiredIds
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (
                    isCurrentAccount(uid, generation) &&
                    permissionGeneration == requestGeneration
                ) {
                    permissionsError = friendlyMessage(error)
                }
            } finally {
                if (
                    isCurrentAccount(uid, generation) &&
                    permissionGeneration == requestGeneration
                ) {
                    isLoadingPermissions = false
                }
            }
        }
    }

    fun closeSharing() {
        if (changingRecipientUid != null) return

        permissionGeneration++
        permissionsJob?.cancel()

        selectedDocument = null
        sharedRecipientIds = emptySet()
        expiredSupportRecipientIds = emptySet()
        isLoadingPermissions = false
        permissionsError = null
    }

    fun setSharing(
        recipient: DocumentRecipient,
        enabled: Boolean
    ) {
        val document = selectedDocument ?: return
        val uid = currentUid ?: return

        if (
            isLoadingPermissions ||
            changingRecipientUid != null ||
            permissionsError != null
        ) {
            return
        }

        val generation = accountGeneration

        changingRecipientUid = recipient.uid
        successMessage = null

        viewModelScope.launch {
            try {
                if (enabled) {
                    repository.shareDocument(
                        document.id,
                        recipient
                    ).getOrThrow()
                } else {
                    repository.revokeShare(
                        document.id,
                        recipient.uid
                    ).getOrThrow()
                }

                if (
                    isCurrentAccount(uid, generation) &&
                    selectedDocument?.id == document.id
                ) {
                    sharedRecipientIds =
                        if (enabled) {
                            sharedRecipientIds + recipient.uid
                        } else {
                            sharedRecipientIds - recipient.uid
                        }
                    expiredSupportRecipientIds =
                        expiredSupportRecipientIds - recipient.uid

                    successMessage =
                        if (enabled) {
                            "Document shared with ${recipient.name}."
                        } else {
                            "Sharing removed for ${recipient.name}."
                        }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAccount(uid, generation)) {
                    permissionsError = friendlyMessage(error)
                }
            } finally {
                if (isCurrentAccount(uid, generation)) {
                    changingRecipientUid = null
                }
            }
        }
    }

    fun openDocument(
        context: Context,
        document: UserDocument,
        onReady: (File) -> Unit
    ) {
        if (openingDocumentId != null) return

        val uid = currentUid ?: return
        val generation = accountGeneration

        openingDocumentId = document.id
        errorMessage = null

        viewModelScope.launch {
            try {
                val file = repository.downloadForViewing(
                    context.applicationContext,
                    document
                ).getOrThrow()

                if (isCurrentAccount(uid, generation)) {
                    onReady(file)
                } else {
                    file.delete()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAccount(uid, generation)) {
                    errorMessage = friendlyMessage(error)
                }
            } finally {
                if (isCurrentAccount(uid, generation)) {
                    openingDocumentId = null
                }
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String =
        when (error) {
            is IllegalArgumentException,
            is IllegalStateException ->
                error.message ?: "Please try again."

            else -> error.toFriendlyFirestoreMessage()
        }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}