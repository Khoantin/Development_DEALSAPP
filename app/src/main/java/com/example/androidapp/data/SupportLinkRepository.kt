
package com.example.androidapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class SupportLinkRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val supportLinks = db.collection("supportLinks")

    // =========================================================
    // 1. SEND A SUPPORT LINK REQUEST
    // =========================================================

    fun requestLink(
        userId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val supportId = auth.currentUser?.uid

        if (supportId == null) {
            onResult(
                Result.failure(
                    Exception("You must be logged in.")
                )
            )
            return
        }

        val requestedUserId = userId.trim()

        if (requestedUserId.isBlank() ||
            requestedUserId == supportId
        ) {
            onResult(
                Result.failure(
                    Exception("Please enter a valid User ID.")
                )
            )
            return
        }

        db.collection("users")
            .document(supportId)
            .get()
            .addOnSuccessListener { supportDoc ->

                if (supportDoc.getString("role") != "support" ||
                    supportDoc.getString("accountStatus") != "active"
                ) {
                    onResult(
                        Result.failure(
                            Exception(
                                "Only active support accounts can send requests."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                val linkId =
                    "${requestedUserId}_${supportId}"

                val data = hashMapOf<String, Any>(
                    "userId" to requestedUserId,
                    "supportId" to supportId,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // Firestore rules must prevent overwriting
                // any existing connection document.
                supportLinks.document(linkId)
                    .set(data)
                    .addOnSuccessListener {
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    // =========================================================
    // 2. LIST INCOMING REQUESTS FOR NORMAL USER
    // =========================================================

    fun listenToIncomingRequests(
        onResult: (Result<List<SupportLink>>) -> Unit
    ): ListenerRegistration? {

        val uid = auth.currentUser?.uid

        if (uid == null) {
            onResult(
                Result.failure(
                    Exception("You must be logged in.")
                )
            )
            return null
        }

        return supportLinks
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }

                val links = snapshot?.documents
                    ?.mapNotNull { document ->

                        val userId =
                            document.getString("userId")

                        val supportId =
                            document.getString("supportId")

                        val status =
                            document.getString("status")

                        if (userId == null ||
                            supportId == null ||
                            status == null
                        ) {
                            null
                        } else {
                            SupportLink(
                                id = document.id,
                                userId = userId,
                                supportId = supportId,
                                status = status
                            )
                        }
                    } ?: emptyList()

                onResult(Result.success(links))
            }
    }

    // =========================================================
    // 3. ACCEPT OR REJECT A SUPPORT REQUEST
    // =========================================================

    fun respondToRequest(
        linkId: String,
        accept: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onResult(
                Result.failure(
                    Exception("You must be logged in.")
                )
            )
            return
        }

        if (linkId.isBlank()) {
            onResult(
                Result.failure(
                    Exception("Invalid connection request.")
                )
            )
            return
        }

        val newStatus =
            if (accept) "accepted" else "rejected"

        val document = supportLinks.document(linkId)

        // Transaction ensures a pending request can only
        // be accepted or rejected once.
        db.runTransaction { transaction ->

            val snapshot = transaction.get(document)

            if (!snapshot.exists()) {
                throw IllegalStateException(
                    "Connection request not found."
                )
            }

            if (snapshot.getString("userId") != uid) {
                throw IllegalStateException(
                    "You cannot respond to this request."
                )
            }

            if (snapshot.getString("status") != "pending") {
                throw IllegalStateException(
                    "This request has already been handled."
                )
            }

            transaction.update(
                document,
                "status",
                newStatus
            )

            Unit

        }.addOnSuccessListener {

            onResult(Result.success(Unit))

        }.addOnFailureListener { error ->

            onResult(Result.failure(error))
        }
    }

    // =========================================================
    // 4. LIST CONNECTIONS FOR SUPPORT USER
    // =========================================================

    fun listenToSupportConnections(
        onResult: (Result<List<SupportLink>>) -> Unit
    ): ListenerRegistration? {

        val supportId = auth.currentUser?.uid

        if (supportId == null) {
            onResult(
                Result.failure(
                    Exception("You must be logged in.")
                )
            )
            return null
        }

        return supportLinks
            .whereEqualTo("supportId", supportId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }

                val connections = snapshot?.documents
                    ?.mapNotNull { document ->

                        val userId =
                            document.getString("userId")

                        val storedSupportId =
                            document.getString("supportId")

                        val status =
                            document.getString("status")

                        if (userId == null ||
                            storedSupportId != supportId ||
                            status == null
                        ) {
                            null
                        } else {
                            SupportLink(
                                id = document.id,
                                userId = userId,
                                supportId = storedSupportId,
                                status = status
                            )
                        }
                    } ?: emptyList()

                onResult(Result.success(connections))
            }
    }

    // =========================================================
    // 5. DISCONNECT OR CANCEL A SUPPORT LINK
    // =========================================================

    fun removeConnection(
        linkId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onResult(
                Result.failure(
                    Exception("You must be logged in.")
                )
            )
            return
        }

        if (linkId.isBlank()) {
            onResult(
                Result.failure(
                    Exception("Invalid connection.")
                )
            )
            return
        }

        val document = supportLinks.document(linkId)

        document.get()
            .addOnSuccessListener { snapshot ->

                if (!snapshot.exists()) {
                    onResult(
                        Result.failure(
                            Exception("Connection not found.")
                        )
                    )
                    return@addOnSuccessListener
                }

                val userId =
                    snapshot.getString("userId")

                val supportId =
                    snapshot.getString("supportId")

                if (uid != userId && uid != supportId) {
                    onResult(
                        Result.failure(
                            Exception(
                                "You are not authorized to remove this connection."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                document.delete()
                    .addOnSuccessListener {
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }
}
