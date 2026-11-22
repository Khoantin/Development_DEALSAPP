package com.example.androidapp.data

data class TrustedContact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    val accessGiven: Boolean = false,
    val linkedUserId: String? = null,
    val privateNotes: String = "",
    val safeToStay: Boolean = false,
    val email: String = "",
    val emergencyContact: Boolean = false,
    val emergencyEmailOptIn: Boolean = false,
    val emergencyInAppOptIn: Boolean = false,
    val emergencySmsRecipient: Boolean = false,
    val categories: List<String> = listOf("TRUSTED"),
    val contactCreatedAtMillis: Long = 0L,
    val contactUpdatedAtMillis: Long = 0L
)
