package com.example.androidapp.pages.guidance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.EmergencyGuide
import com.example.androidapp.data.EmergencyGuideContent
import com.example.androidapp.data.EmergencyGuidanceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException

data class EmergencyGuidanceState(
    val guides: List<EmergencyGuide> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val sessionVersion: Long = 0,
    val completedChanges: Long = 0
)

class EmergencyGuidanceViewModel(
    private val repository: EmergencyGuidanceRepository =
        EmergencyGuidanceRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    var state by mutableStateOf(EmergencyGuidanceState())
        private set

    private var adminMode = false
    private var started = false
    private var disposed = false
    private var generation = 0L

    private val authListener = FirebaseAuth.AuthStateListener {
        generation++

        // Clear content when authentication changes.
        state = EmergencyGuidanceState(
            sessionVersion = state.sessionVersion + 1
        )

        if (started) refresh()
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun start(asAdmin: Boolean) {
        if (started && adminMode == asAdmin) return

        generation++
        started = true
        adminMode = asAdmin

        state = EmergencyGuidanceState(
            sessionVersion = state.sessionVersion + 1
        )
        refresh()
    }

    fun refresh() {
        if (!started || state.isSaving) return

        val uid = auth.currentUser?.uid
        if (uid == null) {
            generation++
            state = state.copy(
                guides = emptyList(),
                isLoading = false,
                error = "Please sign in to view guidance.",
                success = null
            )
            return
        }

        val requestGeneration = ++generation

        state = state.copy(
            guides = emptyList(),
            isLoading = true,
            error = null
        )

        repository.load(adminMode) { result ->
            if (!isCurrent(uid, requestGeneration)) return@load

            result.fold(
                onSuccess = { guides ->
                    state = state.copy(
                        guides = guides,
                        isLoading = false,
                        error = null
                    )
                },
                onFailure = { error ->
                    state = state.copy(
                        guides = emptyList(),
                        isLoading = false,
                        error = messageFor(error)
                    )
                }
            )
        }
    }

    fun save(
        id: String,
        revision: Long,
        content: EmergencyGuideContent
    ) {
        change("Draft saved. Readers still see the published version.") {
                callback ->
            repository.save(id, revision, content, callback)
        }
    }

    // The screen must show a preview and require explicit confirmation
    // before calling this method.
    fun publish(guide: EmergencyGuide) {
        change("Guide published.") { callback ->
            repository.publish(
                guide.id,
                guide.revision,
                callback
            )
        }
    }

    fun withdraw(guide: EmergencyGuide) {
        change("Guide withdrawn from the published library.") { callback ->
            repository.withdraw(
                guide.id,
                guide.revision,
                callback
            )
        }
    }

    fun clearMessages() {
        state = state.copy(error = null, success = null)
    }

    private fun change(
        successMessage: String,
        operation: ((Result<Unit>) -> Unit) -> Unit
    ) {
        if (
            !started ||
            !adminMode ||
            state.isSaving ||
            state.isLoading
        ) return

        val uid = auth.currentUser?.uid ?: return
        val requestGeneration = ++generation

        state = state.copy(
            isSaving = true,
            error = null,
            success = null
        )

        operation { result ->
            if (isCurrent(uid, requestGeneration)) {
                result.fold(
                    onSuccess = {
                        state = state.copy(
                            isSaving = false,
                            success = successMessage,
                            completedChanges = state.completedChanges + 1
                        )
                        refresh()
                    },
                    onFailure = { error ->
                        state = state.copy(
                            isSaving = false,
                            error = messageFor(error)
                        )
                    }
                )
            }
        }
    }

    private fun isCurrent(
        uid: String,
        requestGeneration: Long
    ): Boolean =
        !disposed &&
                generation == requestGeneration &&
                auth.currentUser?.uid == uid

    private fun messageFor(error: Throwable): String {
        val functionsError = error as? FirebaseFunctionsException

        return when (functionsError?.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Please sign in again."

            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "Your account does not have access to this action."

            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                "The guide changed or cannot complete this action. " +
                        "Reload it and review the latest version."

            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                functionsError.message
                    ?: "Check the guide fields and try again."

            FirebaseFunctionsException.Code.NOT_FOUND ->
                "This guide was not found. Refresh the library."

            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                "The guide library has reached its current loading limit."

            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                "The result could not be confirmed. Check your connection " +
                        "and reload before repeating the action."

            else ->
                "Unable to complete the request. Reload and try again."
        }
    }

    override fun onCleared() {
        disposed = true
        generation++
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}