package com.example.androidapp.data
import com.google.firebase.auth.FirebaseAuth
import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.util.UUID

/**
 * Firestore layout:
 *   conversations/{linkId}                 (linkId = supportLinks document id)
 *       participants, userId, supportId, participantNames{uid:name},
 *       lastMessage, lastMessageAt, lastSenderId, unreadCounts{uid:n}
 *   conversations/{linkId}/messages/{auto} senderId, text, createdAt,
 *       attachment{name, path, size, contentType}   (optional)
 *
 * Files live in Firebase Storage at message-files/{linkId}/{uuid}_{name}.
 * Storage rules check the same supportLinks document, so only the two people
 * on an accepted link can read or upload files for a conversation.
 */
class MessagingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val auth = FirebaseAuth.getInstance()

    private val links = db.collection("supportLinks")
    private val conversations = db.collection("conversations")

    private val storage: FirebaseStorage = FirebaseStorage.getInstance().apply {
        // Fail instead of retrying for the default 10 minutes when offline.
        maxUploadRetryTimeMillis = 30_000L
        maxDownloadRetryTimeMillis = 30_000L
    }

    // field is "userId" (normal users) or "supportId" (support accounts).
    fun listenToLinks(
        field: String,
        uid: String,
        onResult: (Result<List<SupportLink>>) -> Unit
    ): ListenerRegistration =
        links.whereEqualTo(field, uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { d ->
                val userId = d.getString("userId")
                val supportId = d.getString("supportId")
                val status = d.getString("status")
                if (userId == null || supportId == null || status == null) null
                else SupportLink(d.id, userId, supportId, status)
            }.orEmpty()
            onResult(Result.success(list))
        }

    fun listenToConversations(
        uid: String,
        onResult: (Result<ListSnapshot<ConversationDoc>>) -> Unit
    ): ListenerRegistration =
        conversations.whereArrayContains("participants", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents?.mapNotNull { it.toConversationDoc() }.orEmpty()
                onResult(Result.success(ListSnapshot(docs, snapshot?.metadata?.isFromCache ?: false)))
            }

    fun listenToMessages(
        conversationId: String,
        myUid: String,
        onResult: (Result<ListSnapshot<ChatMessage>>) -> Unit
    ): ListenerRegistration =
        conversations.document(conversationId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(300)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { d ->
                    val sender = d.getString("senderId") ?: return@mapNotNull null
                    val text = d.getString("text") ?: return@mapNotNull null
                    ChatMessage(
                        id = d.id,
                        senderId = sender,
                        text = text,
                        // ESTIMATE lets a message that is still pending show a sensible time.
                        createdAtMillis = d.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)
                            ?.toDate()?.time ?: System.currentTimeMillis(),
                        isMine = sender == myUid,
                        isPending = d.metadata.hasPendingWrites(),
                        attachment = d.toAttachment()
                    )
                }.orEmpty()
                onResult(Result.success(ListSnapshot(messages, snapshot?.metadata?.isFromCache ?: false)))
            }

    // ---------------------------------------------------------
    // FILES
    // ---------------------------------------------------------

    /** Uploads the picked file. Needs a connection: it fails after ~30 seconds offline. */
    // Returns a function the ViewModel can call to cancel this transfer.
    fun uploadAttachment(
        conversationId: String,
        attachment: PendingAttachment,
        onResult: (Result<ChatAttachment>) -> Unit
    ): () -> Unit {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(Result.failure(
                IllegalStateException("Please sign in again.")
            ))
            return {}
        }

        val problem = attachmentError(attachment)
        if (problem != null) {
            onResult(Result.failure(IllegalArgumentException(problem)))
            return {}
        }

        var cancelled = false

        val path = "message-files/$conversationId/" +
                "${UUID.randomUUID()}_${sanitizeFileName(attachment.name)}"

        val metadata = StorageMetadata.Builder()
            .setContentType(attachment.mimeType)
            .build()

        val task = storage.reference.child(path)
            .putFile(Uri.parse(attachment.uri), metadata)

        task.addOnSuccessListener { snapshot ->
            if (!cancelled && auth.currentUser?.uid == uid) {
                onResult(
                    Result.success(
                        ChatAttachment(
                            name = attachment.name,
                            path = path,
                            sizeBytes = snapshot.bytesTransferred,
                            contentType = attachment.mimeType
                        )
                    )
                )
            }
        }

        task.addOnFailureListener { error ->
            if (!cancelled && auth.currentUser?.uid == uid) {
                onResult(Result.failure(error))
            }
        }

        return {
            cancelled = true
            task.cancel()
            Unit
        }
    }

    // Each download must use its own destination file.
    fun downloadAttachment(
        path: String,
        destination: File,
        onResult: (Result<File>) -> Unit
    ): () -> Unit {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(Result.failure(
                IllegalStateException("Please sign in again.")
            ))
            return {}
        }

        var cancelled = false
        val task = storage.reference.child(path).getFile(destination)

        task.addOnSuccessListener {
            if (cancelled || auth.currentUser?.uid != uid) {
                destination.delete()
            } else {
                onResult(Result.success(destination))
            }
        }

        task.addOnFailureListener { error ->
            destination.delete()
            if (!cancelled && auth.currentUser?.uid == uid) {
                onResult(Result.failure(error))
            }
        }

        task.addOnCanceledListener {
            destination.delete()
        }

        return {
            cancelled = true
            task.cancel()
            destination.delete()
            Unit
        }
    }

    // ---------------------------------------------------------
    // SEND / READ STATUS
    // ---------------------------------------------------------

    /**
     * Writes the message and updates the conversation summary in ONE batch, so they
     * succeed or fail together. Works offline: the batch is queued and sent later.
     * onResult fires only when the server answers (success or rejection).
     */
    fun sendMessage(
        conversationId: String,
        userId: String,
        supportId: String,
        senderUid: String,
        senderName: String,
        recipientUid: String,
        text: String,
        attachment: ChatAttachment? = null,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (auth.currentUser?.uid != senderUid) {
            onResult(Result.failure(
                IllegalStateException("Your account changed. Please reopen Messages.")
            ))
            return
        }
        val cleaned = text.trim()
        val conversationRef = conversations.document(conversationId)
        val messageRef = conversationRef.collection("messages").document()

        val summary = hashMapOf<String, Any>(
            "participants" to listOf(userId, supportId),
            "userId" to userId,
            "supportId" to supportId,
            "lastMessage" to messagePreview(cleaned, attachment),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastSenderId" to senderUid,
            "unreadCounts" to mapOf(recipientUid to FieldValue.increment(1))
        )
        if (senderUid == supportId && senderName.isNotBlank()) {
            summary["participantNames"] = mapOf(senderUid to senderName)
        }

        val message = hashMapOf<String, Any>(
            "senderId" to senderUid,
            "text" to cleaned,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (attachment != null) {
            message["attachment"] = mapOf(
                "name" to attachment.name,
                "path" to attachment.path,
                "size" to attachment.sizeBytes,
                "contentType" to attachment.contentType
            )
        }

        val batch = db.batch()
        batch.set(conversationRef, summary, SetOptions.merge())
        batch.set(messageRef, message)
        batch.commit()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Resets MY unread counter only.
    fun markRead(conversationId: String, uid: String, onResult: (Result<Unit>) -> Unit) {
        if (auth.currentUser?.uid != uid) {
            onResult(Result.failure(
                IllegalStateException("Please sign in again.")
            ))
            return
        }
        conversations.document(conversationId)
            .update("unreadCounts.$uid", 0)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Stores my display name on the conversation so the other person can see it.
    fun publishMyName(
        conversationId: String,
        userId: String,
        supportId: String,
        uid: String,
        name: String
    ) {
        if (
            auth.currentUser?.uid != uid ||
            uid != supportId ||
            name.isBlank()
        ) return
        conversations.document(conversationId).set(
            hashMapOf<String, Any>(
                "participants" to listOf(userId, supportId),
                "userId" to userId,
                "supportId" to supportId,
                "participantNames" to mapOf(uid to name)
            ),
            SetOptions.merge()
        )
    }

    private fun DocumentSnapshot.toAttachment(): ChatAttachment? {
        val map = get("attachment") as? Map<*, *> ?: return null
        val name = map["name"] as? String ?: return null
        val path = map["path"] as? String ?: return null
        return ChatAttachment(
            name = name,
            path = path,
            sizeBytes = (map["size"] as? Number)?.toLong() ?: 0L,
            contentType = map["contentType"] as? String ?: ""
        )
    }

    private fun DocumentSnapshot.toConversationDoc(): ConversationDoc? {
        val userId = getString("userId") ?: return null
        val supportId = getString("supportId") ?: return null
        val names = (get("participantNames") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap().orEmpty()
        val unread = (get("unreadCounts") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> if (k is String && v is Number) k to v.toInt() else null }
            ?.toMap().orEmpty()
        return ConversationDoc(
            id = id,
            userId = userId,
            supportId = supportId,
            participantNames = names,
            lastMessage = getString("lastMessage").orEmpty(),
            lastMessageAtMillis = getTimestamp("lastMessageAt", ServerTimestampBehavior.ESTIMATE)
                ?.toDate()?.time,
            unreadCounts = unread
        )
    }
}

// Safe text for a file upload or download problem.
fun Throwable.toFriendlyStorageMessage(): String =
    if (this is StorageException) {
        when (errorCode) {
            StorageException.ERROR_NOT_AUTHORIZED ->
                "You don't have permission to share files in this conversation."
            StorageException.ERROR_OBJECT_NOT_FOUND -> "That file is no longer available."
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                "No connection. Check your internet and try again."
            else -> "The file couldn't be transferred. Please try again."
        }
    } else {
        "The file couldn't be transferred. Please try again."
    }