package com.example.androidapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class SupportHotline(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val notes: String
)

class SupportHotlineRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun collection(uid: String) =
        db.collection("users")
            .document(uid)
            .collection("supportHotlines")

    fun observeHotlines(
        uid: String,
        onResult: (Result<List<SupportHotline>>) -> Unit
    ): ListenerRegistration {
        require(uid.isNotBlank())

        return collection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
                return@addSnapshotListener
            }

            if (snapshot == null) {
                onResult(
                    Result.failure(
                        IllegalStateException("No response received.")
                    )
                )
                return@addSnapshotListener
            }

            val hotlines = snapshot.documents.mapNotNull { document ->
                val name = document.getString("name")
                    ?: return@mapNotNull null
                val phone = document.getString("phoneNumber")
                    ?: return@mapNotNull null

                SupportHotline(
                    id = document.id,
                    name = name,
                    phoneNumber = phone,
                    notes = document.getString("notes").orEmpty()
                )
            }.sortedBy { it.name.lowercase() }

            onResult(Result.success(hotlines))
        }
    }

    fun addHotline(
        uid: String,
        name: String,
        phoneNumber: String,
        notes: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanName = name.trim()
        val cleanNotes = notes.trim()
        val cleanPhone = normalizePhone(phoneNumber)

        val validationError = when {
            uid.isBlank() -> "Please sign in again."
            cleanName.isBlank() -> "Enter a hotline name."
            cleanName.length > 100 ->
                "Keep the name within 100 characters."
            cleanPhone == null ->
                "Enter a valid phone number with 3–15 digits."
            cleanNotes.length > 1000 ->
                "Keep notes within 1,000 characters."
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

        val data = hashMapOf<String, Any>(
            "name" to cleanName,
            "phoneNumber" to requireNotNull(cleanPhone),
            "notes" to cleanNotes,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        collection(uid).document().set(data)
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun updateHotline(
        uid: String,
        hotlineId: String,
        name: String,
        phoneNumber: String,
        notes: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanName = name.trim()
        val cleanNotes = notes.trim()
        val cleanPhone = normalizePhone(phoneNumber)

        val validationError = when {
            uid.isBlank() -> "Please sign in again."
            hotlineId.isBlank() -> "This hotline could not be found."
            cleanName.isBlank() -> "Enter a hotline name."
            cleanName.length > 100 ->
                "Keep the name within 100 characters."
            cleanPhone == null ->
                "Enter a valid phone number with 3–15 digits."
            cleanNotes.length > 1000 ->
                "Keep notes within 1,000 characters."
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

        // Preserve createdAt and update only the editable fields.
        val updates = mapOf<String, Any>(
            "name" to cleanName,
            "phoneNumber" to requireNotNull(cleanPhone),
            "notes" to cleanNotes,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        collection(uid).document(hotlineId)
            .update(updates)
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun deleteHotline(
        uid: String,
        hotlineId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (uid.isBlank() || hotlineId.isBlank()) {
            onResult(
                Result.failure(
                    IllegalArgumentException(
                        "This hotline could not be deleted."
                    )
                )
            )
            return
        }

        collection(uid).document(hotlineId)
            .delete()
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    private fun normalizePhone(value: String): String? {
        val trimmed = value.trim()

        if (!trimmed.matches(Regex("[+0-9() .-]+"))) {
            return null
        }

        val normalized = trimmed.filter {
            it in '0'..'9' || it == '+'
        }

        return normalized.takeIf {
            it.matches(Regex("[+]?[0-9]{3,15}"))
        }
    }
}