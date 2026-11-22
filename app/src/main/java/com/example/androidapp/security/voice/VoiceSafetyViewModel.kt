package com.example.androidapp.security.voice

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.androidapp.data.DeviceLocationRepository
import com.example.androidapp.data.LocationSharingRepository
import com.example.androidapp.data.TrustedContactRepository
import com.example.androidapp.data.UserRepository
import com.example.androidapp.data.VoiceEmergencyRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Foreground-only location session. Scope this ViewModel to the Activity. */
class VoiceSafetyViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val contacts = TrustedContactRepository()
    private val sharing = LocationSharingRepository()
    private val users = UserRepository()
    private val emergency = VoiceEmergencyRepository()
    private val locations = DeviceLocationRepository(application.applicationContext)

    private val sessionPrefs =
        application.getSharedPreferences(
            "voice_safety_session",
            android.content.Context.MODE_PRIVATE
        )

    private fun saveSession(uid: String, id: String) {
        sessionPrefs.edit()
            .putString("event_$uid", id)
            .apply()
    }

    private fun clearSavedSession(uid: String) {
        sessionPrefs.edit()
            .remove("event_$uid")
            .apply()
    }

    var duressEnabled by mutableStateOf(false)
        private set

    fun updateDuressEnabled(enabled: Boolean) {
        duressEnabled = enabled
    }

    var isBusy by mutableStateOf(false)
        private set
    var eventId by mutableStateOf<String?>(null)
        private set
    // Show detailed status only inside the protected settings UI.
    var status by mutableStateOf("No voice-triggered session in this app session.")
        private set

    private var ownerUid: String? = null
    private var generation = 0
    private var foreground = false
    private var committing = false
    private var cancelRequested = false


    private var cleared = false
    private var observedUid = auth.currentUser?.uid

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid
        if (uid != observedUid) {
            observedUid = uid
            duressEnabled = false
            generation++
            stopUpdates()
            ownerUid = null
            eventId = null
            isBusy = false
            committing = false
            cancelRequested = false
            status = "Account changed. Check sharing status after signing in."
            if (foreground && uid != null) {
                restoreSavedSession()
            }
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    private fun restoreSavedSession() {
        if (cleared || isBusy || eventId != null) return

        val uid = auth.currentUser?.uid ?: return
        val savedId =
            sessionPrefs.getString("event_$uid", null)
                ?: return

        if (savedId.isBlank() || savedId.contains("/")) {
            clearSavedSession(uid)
            return
        }

        val operation = generation
        isBusy = true
        status = "Checking your saved voice session."

        val eventRef =
            db.collection("emergencyEvents").document(savedId)

        // A transaction checks the server rather than trusting
        // a possibly outdated local event status.
        db.runTransaction { transaction ->
            val event = transaction.get(eventRef)

            if (
                !event.exists() ||
                event.getString("ownerId") != uid
            ) {
                throw IllegalStateException(
                    "Saved session could not be verified."
                )
            }

            when (event.getString("status")) {
                "active" -> true
                "cancelled" -> false
                else -> throw IllegalStateException(
                    "Unexpected emergency event status."
                )
            }
        }
            .addOnSuccessListener { active ->
                if (
                    cleared ||
                    generation != operation ||
                    auth.currentUser?.uid != uid
                ) {
                    return@addOnSuccessListener
                }

                isBusy = false

                if (active) {
                    ownerUid = uid
                    eventId = savedId
                    cancelRequested = false
                    status = "Previous voice session restored."

                    // Existing update code verifies that the
                    // location share still belongs to this event.
                    resumeUpdates()
                } else {
                    clearSavedSession(uid)
                    status = "Previous voice session was cancelled."
                }
            }
            .addOnFailureListener {
                if (
                    cleared ||
                    generation != operation ||
                    auth.currentUser?.uid != uid
                ) {
                    return@addOnFailureListener
                }

                isBusy = false
                status =
                    "Could not restore the voice session. " +
                            "Check your connection, then leave and reopen the app."

                // Keep the saved ID so recovery can be retried.
            }
    }

    fun setForeground(active: Boolean) {
        foreground = active

        if (active) {
            if (eventId == null) {
                restoreSavedSession()
            } else {
                resumeUpdates()
            }
        }

        // The service continues when the app goes into the background.
    }

    fun startDuress() {
        if (!duressEnabled) {
            status = "Enable duress sharing in Voice Safety settings first."
            return
        }
        if (isBusy) return
        if (eventId != null) {
            status = "A voice-triggered session is already active."
            return
        }
        if (!foreground) {
            status = "Open the app before starting voice safety."
            return
        }
        val uid = auth.currentUser?.uid
        if (uid == null) {
            status = "Sign in before starting voice safety."
            return
        }
        generation++
        val operation = generation
        ownerUid = uid
        isBusy = true
        cancelRequested = false
        status = "Checking saved recipients."

        fun current() = !cleared && generation == operation &&
                auth.currentUser?.uid == uid
        fun fail(message: String) {
            if (!current()) return
            isBusy = false
            committing = false
            ownerUid = null
            status = message
        }

        sharing.getSelectedContacts(uid) selection@{ selectionResult ->
            if (!current()) return@selection
            val selected = selectionResult.getOrNull()
            if (selected == null) {
                fail("Could not load saved recipient selection.")
                return@selection
            }
            contacts.getContacts(uid) contactLoad@{ contactResult ->
                if (!current()) return@contactLoad
                val loaded = contactResult.getOrNull()
                if (loaded == null) {
                    fail("Could not load trusted contacts.")
                    return@contactLoad
                }
                val eligible = loaded.filter {
                    it.id in selected && it.accessGiven &&
                            !it.linkedUserId.isNullOrBlank() && it.linkedUserId != uid
                }
                val recipients = eligible.mapNotNull { it.linkedUserId }.distinct()
                if (recipients.isEmpty()) {
                    fail("Save at least one eligible linked contact in Location Sharing.")
                    return@contactLoad
                }
                users.getProfile(uid) profileLoad@{ profileResult ->
                    if (!current()) return@profileLoad
                    if (profileResult.isFailure) {
                        fail("Could not load your profile.")
                        return@profileLoad
                    }
                    val name = profileResult.getOrNull()?.displayName
                        ?.takeIf { it.isNotBlank() } ?: "User"
                    status = "Getting current location."
                    locations.getCurrentLocation locationLoad@{ locationResult ->
                        if (!current()) return@locationLoad
                        val location = locationResult.getOrNull()
                        if (location == null) {
                            fail("Location unavailable. Check location permission and device location settings.")
                            return@locationLoad
                        }
                        // Do not initiate a new share after the app leaves the foreground.
                        if (!foreground) {
                            fail("Start cancelled because the app left the foreground.")
                            return@locationLoad
                        }
                        committing = true
                        status = "Starting voice-triggered sharing."
                        emergency.startDuress(
                            uid, name, eligible.map { it.id }, recipients,
                            location.latitude, location.longitude
                        ) commit@{ result ->
                            if (!current()) return@commit
                            committing = false
                            isBusy = false
                            val id = result.getOrNull()
                            if (id == null) {
                                fail("Could not start duress sharing. Check connectivity, database permissions, and existing sharing sessions.")
                                return@commit
                            }
                            eventId = id
                            saveSession(uid, id)
                            status = "Duress event saved."
                            if (cancelRequested) cancelDuress() else resumeUpdates()
                        }
                    }
                }
            }
        }
    }

    fun cancelDuress() {

        if (isBusy && eventId == null) {
            if (committing) {
                cancelRequested = true
                status = "Waiting for the start operation, then cancelling."
            } else {
                generation++
                isBusy = false
                ownerUid = null
                status = "Start cancelled. No emergency event was created."
            }
            return
        }
        if (isBusy) return
        val id = eventId ?: run {
            status = "No voice-triggered session to cancel in this app session."
            return
        }
        val uid = ownerUid ?: return
        if (auth.currentUser?.uid != uid) return
        val operation = generation
        stopUpdates()
        isBusy = true
        status = "Cancelling duress sharing."
        emergency.cancelDuress(uid, id) completion@{ result ->

            if (cleared || generation != operation) return@completion
            isBusy = false
            if (result.isSuccess) {
                clearSavedSession(uid)
                generation++
                eventId = null
                ownerUid = null
                cancelRequested = false
                status = "Duress session cancelled."
            } else {
                // Keep the event ID so cancellation can be retried.
                status = "Cancellation not confirmed. GPS updates paused; reconnect and retry cancellation."
                cancelRequested = true
            }
        }
    }

    private fun resumeUpdates() {
        if (!foreground || isBusy || cancelRequested || cleared) return

        val id = eventId ?: return
        val uid = ownerUid ?: return

        if (auth.currentUser?.uid != uid) return

        val app = getApplication<Application>()

        val intent = android.content.Intent(
            app,
            VoiceLocationService::class.java
        ).apply {
            putExtra(VoiceLocationService.EXTRA_UID, uid)
            putExtra(VoiceLocationService.EXTRA_EVENT_ID, id)
        }

        try {
            androidx.core.content.ContextCompat.startForegroundService(
                app,
                intent
            )

            status =
                "Location service requested. Check the recipient for updates."
        } catch (exception: Exception) {
            status =
                "Could not start the location service. " +
                        "Keep the app open, check location permission, and retry."
        }
    }


    private fun stopUpdates() {
        locations.stopLocationUpdates()

        val app = getApplication<Application>()

        app.stopService(
            android.content.Intent(
                app,
                VoiceLocationService::class.java
            )
        )
    }

    override fun onCleared() {
        cleared = true
        generation++

        // Release this ViewModel's location callbacks.
        // Do not stop the independently running service here.
        locations.stopLocationUpdates()

        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}
