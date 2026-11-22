package com.example.androidapp.pages.locationsharing

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.DeviceLocationRepository
import com.example.androidapp.data.LocationSharingRepository
import com.example.androidapp.data.TrustedContact
import com.example.androidapp.data.TrustedContactRepository
import com.example.androidapp.data.UserRepository
import com.example.androidapp.data.toFriendlyFirestoreMessage


data class LocationSharingUiState(

    val contacts: List<TrustedContact> =
        emptyList(),

    val selectedContactIds: Set<String> =
        emptySet(),

    val isLoading: Boolean =
        true,

    val isSaving: Boolean =
        false,

    val message: String? =
        null,

    val errorMessage: String? =
        null,

    val isGettingLocation: Boolean =
        false,

    val latitude: Double? =
        null,

    val longitude: Double? =
        null,

    val locationError: String? =
        null,

    val isStartingShare: Boolean =
        false,

    val isStoppingShare: Boolean =
        false,

    val sharingActive: Boolean =
        false
)


class LocationSharingViewModel(
    application: Application
) : AndroidViewModel(application) {


    private val authRepository =
        AuthRepository()


    private val userRepository =
        UserRepository()


    private val trustedContactRepository =
        TrustedContactRepository()


    private val locationSharingRepository =
        LocationSharingRepository()


    private val deviceLocationRepository =
        DeviceLocationRepository(
            application.applicationContext
        )


    var uiState by
    mutableStateOf(
        LocationSharingUiState()
    )
        private set


    init {
        loadContacts()
    }


    // ---------------------------------------------------------
    // LOAD CONTACTS + SAVED SELECTION
    // ---------------------------------------------------------

    private fun loadContacts() {

        val uid =
            authRepository
                .currentUser
                ?.uid


        if (uid == null) {

            uiState =
                uiState.copy(
                    isLoading = false,
                    errorMessage =
                        "You must be signed in."
                )

            return
        }


        trustedContactRepository
            .getContacts(uid) { contactsResult ->

                contactsResult
                    .onSuccess { contacts ->

                        val eligibleContacts =
                            contacts.filter {
                                it.accessGiven
                            }


                        locationSharingRepository
                            .getSelectedContacts(uid) { selectionResult ->

                                selectionResult
                                    .onSuccess { selected ->

                                        uiState =
                                            uiState.copy(
                                                contacts =
                                                    eligibleContacts,

                                                selectedContactIds =
                                                    selected.toSet(),

                                                isLoading =
                                                    false,

                                                errorMessage =
                                                    null
                                            )


                                        loadSharingStatus(
                                            uid
                                        )
                                    }
                                    .onFailure { exception ->

                                        uiState =
                                            uiState.copy(
                                                contacts =
                                                    eligibleContacts,

                                                isLoading =
                                                    false,

                                                errorMessage =
                                                    exception
                                                        .toFriendlyFirestoreMessage()
                                            )
                                    }
                            }
                    }
                    .onFailure { exception ->

                        uiState =
                            uiState.copy(
                                isLoading =
                                    false,

                                errorMessage =
                                    exception
                                        .toFriendlyFirestoreMessage()
                            )
                    }
            }
    }


    // ---------------------------------------------------------
    // LOAD CURRENT SHARING STATUS
    // ---------------------------------------------------------

    private fun loadSharingStatus(
        uid: String
    ) {

        locationSharingRepository
            .getSharingStatus(uid) { result ->

                result
                    .onSuccess { active ->

                        uiState =
                            uiState.copy(
                                sharingActive =
                                    active
                            )


                        if (active) {

                            startLiveLocationUpdates(
                                uid
                            )
                        }
                    }
                    .onFailure { exception ->

                        uiState =
                            uiState.copy(
                                errorMessage =
                                    exception
                                        .toFriendlyFirestoreMessage()
                            )
                    }
            }
    }


    // ---------------------------------------------------------
    // SELECT / UNSELECT CONTACT
    // ---------------------------------------------------------

    fun toggleContact(
        contactId: String
    ) {

        if (uiState.sharingActive) {
            return
        }


        val selected =
            uiState
                .selectedContactIds
                .toMutableSet()


        if (
            selected.contains(
                contactId
            )
        ) {

            selected.remove(
                contactId
            )

        } else {

            selected.add(
                contactId
            )
        }


        uiState =
            uiState.copy(
                selectedContactIds =
                    selected,

                message =
                    null,

                errorMessage =
                    null
            )
    }


    // ---------------------------------------------------------
    // SAVE CONTACT SELECTION
    // ---------------------------------------------------------

    fun saveSelection() {

        val uid =
            authRepository
                .currentUser
                ?.uid
                ?: return


        uiState =
            uiState.copy(
                isSaving =
                    true,

                message =
                    null,

                errorMessage =
                    null
            )


        locationSharingRepository
            .saveSelectedContacts(
                uid =
                    uid,

                contactIds =
                    uiState
                        .selectedContactIds
                        .toList()
            ) { result ->

                result
                    .onSuccess {

                        uiState =
                            uiState.copy(
                                isSaving =
                                    false,

                                message =
                                    "Location sharing contacts saved."
                            )
                    }
                    .onFailure { exception ->

                        uiState =
                            uiState.copy(
                                isSaving =
                                    false,

                                errorMessage =
                                    exception
                                        .toFriendlyFirestoreMessage()
                            )
                    }
            }
    }


    // ---------------------------------------------------------
    // LIVE LOCATION UPDATES
    // ---------------------------------------------------------

    private fun startLiveLocationUpdates(
        uid: String
    ) {

        deviceLocationRepository
            .startLocationUpdates(

                onLocationUpdate = { location ->

                    uiState =
                        uiState.copy(
                            latitude =
                                location.latitude,

                            longitude =
                                location.longitude
                        )


                    locationSharingRepository
                        .updateLiveLocation(
                            uid =
                                uid,

                            latitude =
                                location.latitude,

                            longitude =
                                location.longitude
                        ) { result ->

                            result.onFailure { exception ->

                                uiState =
                                    uiState.copy(
                                        errorMessage =
                                            exception
                                                .toFriendlyFirestoreMessage()
                                    )
                            }
                        }
                },

                onError = { exception ->

                    uiState =
                        uiState.copy(
                            locationError =
                                exception.message
                                    ?: "Live location updates failed."
                        )
                }
            )
    }


    // ---------------------------------------------------------
    // GET DEVICE LOCATION
    // ---------------------------------------------------------

    fun getCurrentLocation() {

        uiState =
            uiState.copy(
                isGettingLocation =
                    true,

                locationError =
                    null,

                message =
                    null
            )


        deviceLocationRepository
            .getCurrentLocation { result ->

                result
                    .onSuccess { location ->

                        uiState =
                            uiState.copy(
                                isGettingLocation =
                                    false,

                                latitude =
                                    location.latitude,

                                longitude =
                                    location.longitude,

                                locationError =
                                    null
                            )
                    }
                    .onFailure { exception ->

                        uiState =
                            uiState.copy(
                                isGettingLocation =
                                    false,

                                locationError =
                                    exception.message
                                        ?: "Unable to get current location."
                            )
                    }
            }
    }


    fun locationPermissionDenied() {

        uiState =
            uiState.copy(
                isGettingLocation =
                    false,

                locationError =
                    "Location permission is required to share your location."
            )
    }


    // ---------------------------------------------------------
    // START SHARING
    // ---------------------------------------------------------

    fun startSharing() {

        val uid =
            authRepository
                .currentUser
                ?.uid


        if (uid == null) {

            uiState =
                uiState.copy(
                    errorMessage =
                        "You must be signed in."
                )

            return
        }


        if (
            uiState
                .selectedContactIds
                .isEmpty()
        ) {

            uiState =
                uiState.copy(
                    errorMessage =
                        "Select at least one trusted contact."
                )

            return
        }


        val latitude =
            uiState.latitude


        val longitude =
            uiState.longitude


        if (
            latitude == null ||
            longitude == null
        ) {

            uiState =
                uiState.copy(
                    locationError =
                        "Get your current location before starting location sharing."
                )

            return
        }


        val recipientIds =
            uiState.contacts
                .filter {
                    uiState
                        .selectedContactIds
                        .contains(
                            it.id
                        )
                }
                .mapNotNull {
                    it.linkedUserId
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()


        uiState =
            uiState.copy(
                isStartingShare =
                    true,

                message =
                    null,

                errorMessage =
                    null,

                locationError =
                    null
            )


        // -----------------------------------------------------
        // GET SENDER NAME FIRST
        // -----------------------------------------------------

        userRepository
            .getProfile(uid) { profileResult ->

                profileResult
                    .onSuccess { profile ->

                        val senderName =
                            profile
                                ?.displayName
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "User"


                        locationSharingRepository
                            .startLocationShare(
                                uid =
                                    uid,

                                ownerDisplayName =
                                    senderName,

                                selectedContactIds =
                                    uiState
                                        .selectedContactIds
                                        .toList(),

                                recipientIds =
                                    recipientIds,

                                latitude =
                                    latitude,

                                longitude =
                                    longitude
                            ) { result ->

                                result
                                    .onSuccess {

                                        uiState =
                                            uiState.copy(
                                                isStartingShare =
                                                    false,

                                                sharingActive =
                                                    true,

                                                message =
                                                    "Live location sharing started."
                                            )


                                        startLiveLocationUpdates(
                                            uid
                                        )
                                    }
                                    .onFailure { exception ->

                                        uiState =
                                            uiState.copy(
                                                isStartingShare =
                                                    false,

                                                errorMessage =
                                                    exception
                                                        .toFriendlyFirestoreMessage()
                                            )
                                    }
                            }
                    }
                    .onFailure { exception ->

                        uiState =
                            uiState.copy(
                                isStartingShare =
                                    false,

                                errorMessage =
                                    exception
                                        .toFriendlyFirestoreMessage()
                            )
                    }
            }
    }


    // ---------------------------------------------------------
    // STOP SHARING
    // ---------------------------------------------------------

    fun stopSharing() {

        val uid =
            authRepository
                .currentUser
                ?.uid
                ?: return


        deviceLocationRepository
            .stopLocationUpdates()


        uiState =
            uiState.copy(
                isStoppingShare =
                    true,

                message =
                    null,

                errorMessage =
                    null
            )


        locationSharingRepository
            .stopLocationShare(
                uid
            ) { result ->

                result
                    .onSuccess {

                        uiState =
                            uiState.copy(
                                isStoppingShare =
                                    false,

                                sharingActive =
                                    false,

                                message =
                                    "Live location sharing stopped."
                            )
                    }
                    .onFailure { exception ->

                        uiState =
                            uiState.copy(
                                isStoppingShare =
                                    false,

                                errorMessage =
                                    exception
                                        .toFriendlyFirestoreMessage()
                            )
                    }
            }
    }


    // ---------------------------------------------------------
    // CLEAN UP LOCATION UPDATES
    // ---------------------------------------------------------

    override fun onCleared() {

        deviceLocationRepository
            .stopLocationUpdates()

        super.onCleared()
    }
}