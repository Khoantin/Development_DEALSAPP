package com.example.androidapp.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration


class IncomingLocationSharingRepository(
    private val db: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    fun listenForIncomingShares(
        recipientUid: String,
        onResult: (Result<List<IncomingLocationShare>>) -> Unit
    ): ListenerRegistration {

        return db.collection("locationShares")
            .whereArrayContains(
                "recipientIds",
                recipientUid
            )
            .addSnapshotListener { snapshot, exception ->

                if (exception != null) {

                    onResult(
                        Result.failure(exception)
                    )

                    return@addSnapshotListener
                }


                val shares =
                    snapshot
                        ?.documents
                        ?.mapNotNull { document ->

                            try {

                                IncomingLocationShare(

                                    ownerId =
                                        document.getString(
                                            "ownerId"
                                        ) ?: "",

                                    ownerDisplayName =
                                        document.getString(
                                            "ownerDisplayName"
                                        ) ?: "User",

                                    latitude =
                                        document.getDouble(
                                            "latitude"
                                        ) ?: 0.0,

                                    longitude =
                                        document.getDouble(
                                            "longitude"
                                        ) ?: 0.0,

                                    active =
                                        document.getBoolean(
                                            "active"
                                        ) ?: false,

                                    updatedAt =
                                        document.getTimestamp(
                                            "updatedAt"
                                        )
                                )

                            } catch (exception: Exception) {

                                null
                            }
                        }
                        ?.filter {
                            it.active
                        }
                        ?: emptyList()


                onResult(
                    Result.success(shares)
                )
            }
    }
}