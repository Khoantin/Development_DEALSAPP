
package com.example.androidapp.pages.supportlinking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.SupportLink
import com.example.androidapp.data.SupportLinkRepository
import com.google.firebase.firestore.ListenerRegistration

class IncomingSupportRequestsViewModel(
    private val repository: SupportLinkRepository =
        SupportLinkRepository()
) : ViewModel() {

    var requests by mutableStateOf<List<SupportLink>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    private var listener: ListenerRegistration? = null

    init {
        loadRequests()
    }

    private fun loadRequests() {
        listener = repository.listenToIncomingRequests { result ->
            isLoading = false

            result.onSuccess { links ->
                requests = links
                errorMessage = null
            }.onFailure { error ->
                errorMessage =
                    error.localizedMessage ?: "Unable to load requests."
            }
        }
    }

    fun respond(linkId: String, accept: Boolean) {
        if (isSubmitting) return

        isSubmitting = true
        errorMessage = null
        successMessage = null

        repository.respondToRequest(linkId, accept) { result ->
            isSubmitting = false

            result.onSuccess {
                successMessage = if (accept) {
                    "Connection approved."
                } else {
                    "Connection rejected."
                }
            }.onFailure { error ->
                errorMessage =
                    error.localizedMessage ?: "Unable to update request."
            }
        }
    }

    // ---------------------------------------------------------
    // DISCONNECT AN APPROVED SUPPORT ACCOUNT
    // ---------------------------------------------------------

    var removingLinkId by mutableStateOf<String?>(null)
        private set

    fun removeConnection(linkId: String) {

        if (removingLinkId != null) return

        removingLinkId = linkId
        errorMessage = null
        successMessage = null

        repository.removeConnection(linkId) { result ->

            removingLinkId = null

            result.onSuccess {
                successMessage = "Support connection removed."
            }.onFailure { error ->
                errorMessage =
                    error.localizedMessage
                        ?: "Unable to remove support connection."
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
