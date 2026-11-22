package com.example.androidapp.data

import com.google.firebase.Timestamp

// Mirrors the fields of the users/{uid} document.
// Every property has a default value: Firestore needs that to rebuild the
// object when reading. The document ID (the UID) is not stored as a field.
data class UserProfile(
    val displayName: String = "",
    val legalName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val role: String = "user",
    val accountStatus: String = "active",
    val regionId: String = "",
    val uploadedDocuments: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)