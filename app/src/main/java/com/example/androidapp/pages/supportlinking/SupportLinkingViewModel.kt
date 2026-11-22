package com.example.androidapp.pages.supportlinking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.SupportLink
import com.example.androidapp.data.SupportLinkRepository
import com.example.androidapp.data.SupportPairingRepository
import com.example.androidapp.data.toSupportPairingMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.UUID

class SupportLinkingViewModel(
    private val repository: SupportLinkRepository =
        SupportLinkRepository(),
    private val pairingRepository: SupportPairingRepository =
        SupportPairingRepository()
) : ViewModel() {

    var pairingCode by mutableStateOf("")
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var needsRetry by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    var connections by mutableStateOf<List<SupportLink>>(emptyList())
        private set

    var isLoadingConnections by mutableStateOf(true)
        private set

    var connectionsError by mutableStateOf<String?>(null)
        private set

    var removingLinkId by mutableStateOf<String?>(null)
        private set

    private val auth = FirebaseAuth.getInstance()
    private var ownerUid: String? = null
    private var accountVersion = 0
    private var pendingRequestId: String? = null
    private var connectionsListener: ListenerRegistration? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid

        if (uid != ownerUid) {
            accountVersion++
            ownerUid = uid
            connectionsListener?.remove()
            connectionsListener = null

            pairingCode = ""
            pendingRequestId = null
            isSubmitting = false
            needsRetry = false
            errorMessage = null
            successMessage = null
            connections = emptyList()
            connectionsError = null
            removingLinkId = null

            if (uid == null) {
                isLoadingConnections = false
                connectionsError = "Please sign in again."
            } else {
                loadConnections()
            }
        } else if (uid == null) {
            isLoadingConnections = false
            connectionsError = "Please sign in again."
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    private fun loadConnections() {
        connectionsListener?.remove()
        isLoadingConnections = true
        connectionsError = null

        val version = accountVersion
        val uid = ownerUid

        connectionsListener = repository.listenToSupportConnections { result ->
            if (
                version != accountVersion ||
                auth.currentUser?.uid != uid
            ) {
                return@listenToSupportConnections
            }

            isLoadingConnections = false

            result.onSuccess { links ->
                connections = links
                connectionsError = null
            }.onFailure {
                connections = emptyList()
                connectionsError =
                    "Unable to load connections. Check your connection."
            }
        }
    }

    fun updatePairingCode(value: String) {
        if (isSubmitting || needsRetry) return

        val cleaned = value.filter { it in '0'..'9' }.take(6)

        if (cleaned != pairingCode) {
            pairingCode = cleaned
            pendingRequestId = null
            errorMessage = null
            successMessage = null
        }
    }

    fun sendLinkRequest() {
        if (isSubmitting || removingLinkId != null) return

        if (pairingCode.length != 6) {
            errorMessage = "Please enter a six-digit pairing code."
            return
        }

        val uid = auth.currentUser?.uid

        if (uid == null || uid != ownerUid) {
            errorMessage = "Please sign in again."
            return
        }

        // Keep the same ID after an uncertain response.
        val requestId = pendingRequestId
            ?: UUID.randomUUID().toString().also {
                pendingRequestId = it
            }

        val version = accountVersion
        val submittedCode = pairingCode

        isSubmitting = true
        errorMessage = null
        successMessage = null

        pairingRepository.redeemCode(
            code = submittedCode,
            requestId = requestId
        ) { result ->
            if (
                version != accountVersion ||
                auth.currentUser?.uid != uid
            ) {
                return@redeemCode
            }

            isSubmitting = false

            result.onSuccess { response ->
                pendingRequestId = null
                needsRetry = false
                pairingCode = ""

                successMessage = when (response.status) {
                    "pending" ->
                        "Request confirmed. Waiting for the User's approval."

                    "accepted" ->
                        "This connection is already accepted."

                    "rejected" ->
                        "The User declined this request."

                    "removed" ->
                        "This request was removed. It was not sent again."

                    else ->
                        "Request status received."
                }
            }.onFailure { error ->
                val code = (error as? FirebaseFunctionsException)?.code

                val definiteFailure = code in setOf(
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                    FirebaseFunctionsException.Code.ALREADY_EXISTS,
                    FirebaseFunctionsException.Code.PERMISSION_DENIED,
                    FirebaseFunctionsException.Code.UNAUTHENTICATED
                )

                needsRetry = !definiteFailure

                if (definiteFailure) {
                    pendingRequestId = null
                }

                errorMessage = error.toSupportPairingMessage()
            }
        }
    }

    fun removeConnection(linkId: String) {
        if (
            removingLinkId != null ||
            isSubmitting ||
            needsRetry
        ) {
            return
        }

        val uid = auth.currentUser?.uid

        if (uid == null || uid != ownerUid) {
            errorMessage = "Please sign in again."
            return
        }

        val version = accountVersion

        removingLinkId = linkId
        errorMessage = null
        successMessage = null

        repository.removeConnection(linkId) { result ->
            if (
                version != accountVersion ||
                auth.currentUser?.uid != uid
            ) {
                return@removeConnection
            }

            removingLinkId = null

            result.onSuccess {
                successMessage = "Connection removed successfully."
            }.onFailure {
                errorMessage =
                    "Could not confirm removal. Check your connections before retrying."
            }
        }
    }

    override fun onCleared() {
        accountVersion++
        connectionsListener?.remove()
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}