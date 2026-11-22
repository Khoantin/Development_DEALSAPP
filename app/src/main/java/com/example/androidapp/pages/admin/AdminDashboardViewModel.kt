package com.example.androidapp.pages.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.AdminDashboardRepository
import com.example.androidapp.data.AdminDashboardStats
import com.example.androidapp.data.AdminEmergencyStats
import com.example.androidapp.data.toAdminDashboardMessage
import com.example.androidapp.data.toAdminEmergencyStatsMessage
import com.google.firebase.auth.FirebaseAuth

data class AdminDashboardUiState(
    val isLoading: Boolean = false,
    val stats: AdminDashboardStats? = null,
    val errorMessage: String? = null
)

data class AdminEmergencyUiState(
    val isLoading: Boolean = false,
    val stats: AdminEmergencyStats? = null,
    val errorMessage: String? = null
)

class AdminDashboardViewModel(
    private val repository: AdminDashboardRepository =
        AdminDashboardRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    var state by mutableStateOf(AdminDashboardUiState())
        private set

    // Loads separately, so one section failing never blocks the other.
    var emergencyState by mutableStateOf(AdminEmergencyUiState())
        private set

    private var requestVersion = 0
    private var emergencyVersion = 0
    private var currentUid: String? = auth.currentUser?.uid

    // Discard displayed data and pending responses when accounts change.
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val newUid = firebaseAuth.currentUser?.uid

        if (newUid != currentUid) {
            currentUid = newUid
            requestVersion++
            emergencyVersion++
            state = AdminDashboardUiState()
            emergencyState = AdminEmergencyUiState()
            refresh()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        refresh()
    }

    /** Pull-to-refresh: reloads both sections. */
    fun refresh() {
        refreshAccounts()
        refreshEmergency()
    }

    fun refreshAccounts() {
        if (state.isLoading) return

        val uid = auth.currentUser?.uid
        if (uid == null) {
            state = AdminDashboardUiState(
                errorMessage = "Please sign in again."
            )
            return
        }

        val version = ++requestVersion

        // Clear old counts so a failed refresh never looks current.
        state = AdminDashboardUiState(isLoading = true)

        repository.loadStats { result ->
            if (
                version == requestVersion &&
                auth.currentUser?.uid == uid
            ) {
                state = result.fold(
                    onSuccess = { stats ->
                        AdminDashboardUiState(stats = stats)
                    },
                    onFailure = { error ->
                        AdminDashboardUiState(
                            errorMessage = error.toAdminDashboardMessage()
                        )
                    }
                )
            }
        }
    }

    fun refreshEmergency() {
        if (emergencyState.isLoading) return

        val uid = auth.currentUser?.uid
        if (uid == null) {
            emergencyState = AdminEmergencyUiState(
                errorMessage = "Please sign in again."
            )
            return
        }

        val version = ++emergencyVersion
        emergencyState = AdminEmergencyUiState(isLoading = true)

        repository.loadEmergencyStats { result ->
            if (
                version == emergencyVersion &&
                auth.currentUser?.uid == uid
            ) {
                emergencyState = result.fold(
                    onSuccess = { AdminEmergencyUiState(stats = it) },
                    onFailure = {
                        AdminEmergencyUiState(
                            errorMessage = it.toAdminEmergencyStatsMessage()
                        )
                    }
                )
            }
        }
    }

    override fun onCleared() {
        requestVersion++
        emergencyVersion++
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}
