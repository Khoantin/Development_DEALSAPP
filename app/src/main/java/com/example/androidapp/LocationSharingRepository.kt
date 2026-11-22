package com.example.androidapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions


class LocationSharingRepository(
    private val db: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private fun settingsDocument(uid: String) =
        db.collection("users")
            .document(uid)
            .collection("locationSharing")
            .document("settings")


    private fun liveShareDocument(uid: String) =
        db.collection("locationShares")
            .document(uid)


    // ---------------------------------------------------------
    // SAVE CONTACT SELECTION
    // ---------------------------------------------------------

    fun saveSelectedContacts(
        uid: String,
        contactIds: List<String>,
        onResult: (Result<Unit>) -> Unit
    ) {

        val data =
            mapOf(
                "selectedContactIds" to contactIds
            )


        settingsDocument(uid)
            .set(
                data,
                SetOptions.merge()
            )
            .addOnSuccessListener {

                onResult(
                    Result.success(Unit)
                )
            }
            .addOnFailureListener { exception ->

                onResult(
                    Result.failure(exception)
                )
            }
    }


    // ---------------------------------------------------------
    // LOAD CONTACT SELECTION
    // ---------------------------------------------------------

    fun getSelectedContacts(
        uid: String,
        onResult: (Result<List<String>>) -> Unit
    ) {

        settingsDocument(uid)
            .get()
            .addOnSuccessListener { snapshot ->

                val ids =
                    snapshot.get(
                        "selectedContactIds"
                    ) as? List<*>


                val contactIds =
                    ids
                        ?.filterIsInstance<String>()
                        ?: emptyList()


                onResult(
                    Result.success(
                        contactIds
                    )
                )
            }
            .addOnFailureListener { exception ->

                onResult(
                    Result.failure(exception)
                )
            }
    }


    // ---------------------------------------------------------
    // START LOCATION SHARING
    // ---------------------------------------------------------

    fun startLocationShare(
        uid: String,
        ownerDisplayName: String,
        selectedContactIds: List<String>,
        recipientIds: List<String>,
        latitude: Double,
        longitude: Double,
        onResult: (Result<Unit>) -> Unit
    ) {

        val data =
            hashMapOf<String, Any>(
                "ownerId" to uid,
                "ownerDisplayName" to ownerDisplayName,
                "selectedContactIds" to selectedContactIds,
                "recipientIds" to recipientIds,
                "latitude" to latitude,
                "longitude" to longitude,
                "active" to true,
                "startedAt" to
                        FieldValue.serverTimestamp(),
                "updatedAt" to
                        FieldValue.serverTimestamp()
            )


        liveShareDocument(uid)
            .set(data)
            .addOnSuccessListener {

                onResult(
                    Result.success(Unit)
                )
            }
            .addOnFailureListener { exception ->

                onResult(
                    Result.failure(exception)
                )
            }
    }


    // ---------------------------------------------------------
    // UPDATE LIVE LOCATION
    // ---------------------------------------------------------

    fun updateLiveLocation(
        uid: String,
        latitude: Double,
        longitude: Double,
        onResult: (Result<Unit>) -> Unit
    ) {

        liveShareDocument(uid)
            .update(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                onResult(
                    Result.success(Unit)
                )
            }
            .addOnFailureListener { exception ->

                onResult(
                    Result.failure(exception)
                )
            }
    }


    // ---------------------------------------------------------
    // STOP LOCATION SHARING
    // ---------------------------------------------------------

    fun stopLocationShare(
        uid: String,
        onResult: (Result<Unit>) -> Unit
    ) {

        liveShareDocument(uid)
            .update(
                mapOf(
                    "active" to false,
                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                onResult(
                    Result.success(Unit)
                )
            }
            .addOnFailureListener { exception ->

                onResult(
                    Result.failure(exception)
                )
            }
    }


    // ---------------------------------------------------------
    // CHECK CURRENT SHARING STATUS
    // ---------------------------------------------------------

    fun getSharingStatus(
        uid: String,
        onResult: (Result<Boolean>) -> Unit
    ) {

        liveShareDocument(uid)
            .get()
            .addOnSuccessListener { snapshot ->

                val active =
                    snapshot.getBoolean(
                        "active"
                    ) ?: false


                onResult(
                    Result.success(active)
                )
            }
            .addOnFailureListener { exception ->

                onResult(
                    Result.failure(exception)
                )
            }
    }
}