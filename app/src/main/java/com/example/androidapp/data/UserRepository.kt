package com.example.androidapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val users = db.collection("users")

    // CREATE: called once, right after registration.
    // role and accountStatus are forced here, so callers can't choose them.
    fun createProfile(
        uid: String,
        profile: UserProfile,
        onResult: (Result<Unit>) -> Unit
    ) {
        val data = hashMapOf<String, Any>(
            "displayName" to profile.displayName,
            "legalName" to profile.legalName,
            "email" to profile.email,
            "phoneNumber" to profile.phoneNumber,
            "address" to profile.address,
            "regionId" to profile.regionId,
            "role" to profile.role,
            "accountStatus" to "active",
            "uploadedDocuments" to profile.uploadedDocuments,
            "createdAt" to FieldValue.serverTimestamp()
        )
        users.document(uid).set(data)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // READ: returns null inside the Result if the document doesn't exist.
    fun getProfile(uid: String, onResult: (Result<UserProfile?>) -> Unit) {
        users.document(uid).get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.toObject(UserProfile::class.java)))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // UPDATE: only send the fields that changed, e.g. mapOf("displayName" to "New Name").
    // The app should only pass editable fields. The rules enforce this as well.
    fun updateFields(
        uid: String,
        fields: Map<String, Any>,
        onResult: (Result<Unit>) -> Unit
    ) {
        users.document(uid).update(fields)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // There is deliberately no delete here: firestore.rules denies owners
    // deleting their profile, so a suspended or disabled account can't
    // delete it and create a new "active" one. Account deletion must go
    // through a trusted backend (a callable Cloud Function).
}

// Safe messages for the UI, the same idea as the auth ones.
fun Throwable.toFriendlyFirestoreMessage(): String =
    if (this is FirebaseFirestoreException) {
        when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have permission to do that."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "No connection. Please try again."
            FirebaseFirestoreException.Code.NOT_FOUND -> "That record doesn't exist."
            else -> "Something went wrong. Please try again."
        }
    } else {
        "Something went wrong. Please try again."
    }