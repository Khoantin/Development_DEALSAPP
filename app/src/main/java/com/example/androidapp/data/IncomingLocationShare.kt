package com.example.androidapp.data

import com.google.firebase.Timestamp


data class IncomingLocationShare(
    val ownerId: String = "",
    val ownerDisplayName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val active: Boolean = false,
    val updatedAt: Timestamp? = null
)