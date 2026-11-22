package com.example.androidapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class VoiceEmergencyRepository(
    private val db: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    /**
     * Creates a duress event and its initial location share together.
     * The caller must supply freshly checked, eligible contacts.
     * Returns the created event ID on success.
     */
    fun startDuress(
        uid: String,
        ownerDisplayName: String,
        selectedContactIds: List<String>,
        recipientIds: List<String>,
        latitude: Double,
        longitude: Double,
        onResult: (Result<String>) -> Unit
    ) {
        val contacts = selectedContactIds
            .filter { it.isNotBlank() }
            .distinct()

        val recipients = recipientIds
            .filter { it.isNotBlank() && it != uid }
            .distinct()

        val validationError = when {
            uid.isBlank() ->
                "You must be signed in."

            contacts.isEmpty() || recipients.isEmpty() ->
                "Select at least one eligible linked trusted contact."

            !latitude.isFinite() ||
                    !longitude.isFinite() ||
                    latitude !in -90.0..90.0 ||
                    longitude !in -180.0..180.0 ->
                "A valid location is required."

            else -> null
        }

        if (validationError != null) {
            onResult(
                Result.failure(
                    IllegalArgumentException(validationError)
                )
            )
            return
        }

        val shareDocument =
            db.collection("locationShares").document(uid)

        val eventDocument =
            db.collection("emergencyEvents").document()

        val shareData = mapOf(
            "ownerId" to uid,
            "ownerDisplayName" to
                    ownerDisplayName.ifBlank { "User" },
            "selectedContactIds" to contacts,
            "recipientIds" to recipients,
            "latitude" to latitude,
            "longitude" to longitude,
            "active" to true,
            "startedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val eventData = mapOf(
            "ownerId" to uid,
            "recipientIds" to recipients,
            "triggerType" to "voice_duress",
            "status" to "active",
            "startedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.runTransaction { transaction ->
            val existingShare =
                transaction.get(shareDocument)

            // Avoid replacing an existing sharing session.
            if (existingShare.getBoolean("active") == true) {
                throw IllegalStateException(
                    "Location sharing is already active."
                )
            }

            transaction.set(shareDocument, shareData)
            transaction.set(eventDocument, eventData)

            eventDocument.id
        }
            .addOnSuccessListener { eventId ->
                onResult(Result.success(eventId))
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }

    fun cancelDuress(
        uid: String,
        eventId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (
            uid.isBlank() ||
            eventId.isBlank() ||
            uid.contains("/") ||
            eventId.contains("/")
        ) {
            onResult(
                Result.failure(
                    IllegalArgumentException(
                        "A valid user and emergency event are required."
                    )
                )
            )
            return
        }

        val eventDocument =
            db.collection("emergencyEvents").document(eventId)

        val shareDocument =
            db.collection("locationShares").document(uid)

        db.runTransaction { transaction ->
            val event = transaction.get(eventDocument)

            if (
                !event.exists() ||
                event.getString("ownerId") != uid
            ) {
                throw IllegalStateException(
                    "This emergency event does not belong to you."
                )
            }

            val status = event.getString("status")

            if (status == "cancelled") {
                return@runTransaction Unit
            }

            if (status != "active") {
                throw IllegalStateException(
                    "This emergency event is not active."
                )
            }

            // Read before making any transaction writes.
            val share = transaction.get(shareDocument)

            val eventStartedAt =
                event.getTimestamp("startedAt")

            val shareStartedAt =
                share.getTimestamp("startedAt")

            // Both timestamps were created in the same transaction.
            // Do not stop a newer, unrelated sharing session.
            val belongsToThisEvent =
                eventStartedAt != null &&
                        shareStartedAt == eventStartedAt &&
                        share.getString("ownerId") == uid

            transaction.update(
                eventDocument,
                mapOf(
                    "status" to "cancelled",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (
                belongsToThisEvent &&
                share.getBoolean("active") == true
            ) {
                transaction.update(
                    shareDocument,
                    mapOf(
                        "active" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            Unit
        }
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }
}