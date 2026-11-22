package com.example.androidapp.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException

/**
 * Admin <-> Support staff messaging (Step 4a backend).
 *   staffConversations/{supportUid}           read by that support account and active admins
 *   staffConversations/{supportUid}/messages  senderRole, text, createdAt
 * Clients never write these: sendStaffMessage and markStaffConversationRead do.
 */
class StaffMessagingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1")
) {
    private val threads = db.collection("staffConversations")

    /** Admin inbox: every thread, newest first. */
    fun listenToThreads(
        onResult: (Result<ListSnapshot<StaffThread>>) -> Unit
    ): ListenerRegistration =
        threads.orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toStaffThread() }.orEmpty()
                onResult(Result.success(ListSnapshot(items, snapshot?.metadata?.isFromCache ?: false)))
            }

    /** One thread. The list is empty until the first message creates it. */
    fun listenToThread(
        supportUid: String,
        onResult: (Result<ListSnapshot<StaffThread>>) -> Unit
    ): ListenerRegistration =
        threads.document(supportUid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }
                onResult(
                    Result.success(
                        ListSnapshot(
                            listOfNotNull(snapshot?.toStaffThread()),
                            snapshot?.metadata?.isFromCache ?: false
                        )
                    )
                )
            }

    fun listenToMessages(
        supportUid: String,
        onResult: (Result<ListSnapshot<StaffMessage>>) -> Unit
    ): ListenerRegistration =
        threads.document(supportUid).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            // shortcut: shows the latest 300 only; add paging if threads get longer.
            .limitToLast(300)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { d ->
                    StaffMessage(
                        id = d.id,
                        senderRole = d.getString("senderRole") ?: return@mapNotNull null,
                        text = d.getString("text") ?: return@mapNotNull null,
                        createdAtMillis = d.millis("createdAt") ?: System.currentTimeMillis()
                    )
                }.orEmpty()
                onResult(Result.success(ListSnapshot(messages, snapshot?.metadata?.isFromCache ?: false)))
            }

    /** Support passes null (always their own thread). Returns the new message id. */
    fun send(supportUid: String?, text: String, onResult: (Result<String>) -> Unit) {
        val request = buildMap {
            supportUid?.let { put("supportUid", it) }
            put("text", text)
        }
        functions.getHttpsCallable("sendStaffMessage").call(request)
            .addOnSuccessListener { response ->
                onResult(runCatching {
                    (response.data as? Map<*, *>)?.get("messageId") as? String
                        ?: error("Invalid send response")
                })
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun markRead(supportUid: String?, onResult: (Result<Unit>) -> Unit) {
        val request = buildMap { supportUid?.let { put("supportUid", it) } }
        functions.getHttpsCallable("markStaffConversationRead").call(request)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun DocumentSnapshot.millis(field: String): Long? =
        // ESTIMATE gives a pending server timestamp a sensible local time.
        getTimestamp(field, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time

    private fun DocumentSnapshot.toStaffThread(): StaffThread? {
        if (!exists()) return null
        return StaffThread(
            supportUid = id,
            supportDisplayName = getString("supportDisplayName").orEmpty(),
            lastMessageAtMillis = millis("lastMessageAt"),
            lastMessagePreview = getString("lastMessagePreview").orEmpty(),
            lastSenderRole = getString("lastSenderRole").orEmpty(),
            supportUnread = getLong("supportUnread")?.toInt() ?: 0,
            adminUnread = getLong("adminUnread")?.toInt() ?: 0
        )
    }
}

fun Throwable.toStaffMessagingMessage(): String {
    val code = when {
        this is FirebaseFunctionsException -> code.name
        this is FirebaseFirestoreException -> code.name
        else -> null
    }
    // A callable that can't reach the network may report INTERNAL with an IOException cause.
    val offline = generateSequence(this) { it.cause }.any { it is IOException }
    return StaffMessagingLogic.errorMessage(
        if (offline && code in setOf(null, "INTERNAL", "UNKNOWN")) "UNAVAILABLE" else code,
        message
    )
}
