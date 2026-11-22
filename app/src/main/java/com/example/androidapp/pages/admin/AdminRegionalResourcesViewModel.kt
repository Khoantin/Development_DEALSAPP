package com.example.androidapp.pages.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.AdminRegionalResource
import com.example.androidapp.data.AdminRegionalResourceRepository
import com.example.androidapp.data.AdminResourceOptions
import com.example.androidapp.data.toAdminResourceMessage
import com.google.firebase.auth.FirebaseAuth

data class AdminRegionalResourcesUiState(
    val regionId: String = "CA-AB",
    val resources: List<AdminRegionalResource> = emptyList(),
    val original: AdminRegionalResource? = null,
    val draft: AdminRegionalResource? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val hasUnsavedChanges: Boolean
        get() = draft != null && draft != original
}

class AdminRegionalResourcesViewModel(
    private val repository: AdminRegionalResourceRepository =
        AdminRegionalResourceRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    var state by mutableStateOf(AdminRegionalResourcesUiState())
        private set

    private var requestVersion = 0
    private var currentUid = auth.currentUser?.uid

    private val authListener = FirebaseAuth.AuthStateListener {
        val uid = it.currentUser?.uid

        if (uid != currentUid) {
            currentUid = uid
            requestVersion++
            state = AdminRegionalResourcesUiState()
            refresh()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        refresh()
    }

    fun selectRegion(regionId: String) {
        if (
            state.isSaving ||
            state.draft != null ||
            regionId !in AdminResourceOptions.regions
        ) return

        state = AdminRegionalResourcesUiState(regionId = regionId)
        refresh()
    }

    fun refresh() {
        if (state.isSaving || state.draft != null) return

        val uid = auth.currentUser?.uid
        if (uid == null) {
            state = AdminRegionalResourcesUiState(
                regionId = state.regionId,
                error = "Please sign in again."
            )
            return
        }

        val regionId = state.regionId
        val version = ++requestVersion

        state = state.copy(
            resources = emptyList(),
            isLoading = true,
            error = null
        )

        repository.loadResources(regionId) { result ->
            if (
                version == requestVersion &&
                auth.currentUser?.uid == uid
            ) {
                result.onSuccess { resources ->
                    state = state.copy(
                        resources = resources,
                        isLoading = false
                    )
                }.onFailure { error ->
                    state = state.copy(
                        isLoading = false,
                        error = error.toAdminResourceMessage()
                    )
                }
            }
        }
    }

    fun edit(resource: AdminRegionalResource) {
        if (state.isLoading || state.isSaving) return

        state = state.copy(
            original = resource,
            draft = resource,
            error = null,
            message = null
        )
    }

    fun updateDraft(resource: AdminRegionalResource) {
        val current = state.draft ?: return

        if (
            state.isSaving ||
            resource.id != current.id ||
            resource.version != current.version
        ) return

        state = state.copy(draft = resource, error = null)
    }

    fun closeEditor() {
        if (state.isSaving) return

        state = state.copy(
            draft = null,
            original = null,
            error = null
        )
        refresh()
    }

    fun save() {
        val draft = state.draft ?: return
        if (state.isSaving || !state.hasUnsavedChanges) return

        if (
            draft.name.isBlank() ||
            draft.summary.isBlank() ||
            draft.exampleProcess.isBlank() ||
            draft.need !in AdminResourceOptions.needs ||
            draft.contactMethods.isEmpty() ||
            draft.contactMethods.any {
                it !in AdminResourceOptions.contactMethods
            }
        ) {
            state = state.copy(
                error = "Enter the required details, category, and contact methods."
            )
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            state = state.copy(error = "Please sign in again.")
            return
        }

        val regionId = state.regionId
        val version = ++requestVersion
        state = state.copy(isSaving = true, error = null, message = null)

        repository.saveResource(regionId, draft) { result ->
            if (
                version == requestVersion &&
                auth.currentUser?.uid == uid
            ) {
                result.onSuccess {
                    state = state.copy(
                        isSaving = false,
                        original = null,
                        draft = null,
                        message = "Resource saved."
                    )
                    // Fetch fresh versions after every successful save.
                    refresh()
                }.onFailure { error ->
                    // Preserve the draft so the admin does not lose their edits.
                    state = state.copy(
                        isSaving = false,
                        error = error.toAdminResourceMessage()
                    )
                }
            }
        }
    }

    override fun onCleared() {
        requestVersion++
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}