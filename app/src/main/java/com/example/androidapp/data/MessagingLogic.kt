package com.example.androidapp.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val MAX_MESSAGE_LENGTH = 2000
const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024   // 10 MB, also enforced by Storage rules

private val ALLOWED_DOCUMENT_TYPES = setOf(
    "application/pdf",
    "text/plain",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
)

// A file attached to a message, as stored on the message document.
data class ChatAttachment(
    val name: String,
    val path: String,          // Firebase Storage path: message-files/{conversationId}/{file}
    val sizeBytes: Long,
    val contentType: String
)

// A file the user has picked but not yet uploaded.
data class PendingAttachment(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
)

// What the inbox shows for one approved connection.
data class ChatConversation(
    val id: String,               // same as the supportLinks document id
    val otherUserId: String,
    val otherName: String,
    val lastMessage: String,
    val lastMessageAtMillis: Long?,
    val unreadCount: Int
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAtMillis: Long,
    val isMine: Boolean,
    val isPending: Boolean,       // true until the server has confirmed the write
    val attachment: ChatAttachment? = null
)

// Raw conversations/{id} document, without Firebase types so it can be unit tested.
data class ConversationDoc(
    val id: String,
    val userId: String,
    val supportId: String,
    val participantNames: Map<String, String>,
    val lastMessage: String,
    val lastMessageAtMillis: Long?,
    val unreadCounts: Map<String, Int>
)

data class ListSnapshot<T>(
    val items: List<T>,
    val fromCache: Boolean
)

/**
 * One inbox row per ACCEPTED support link, merged with its conversation document
 * (if any). The link is the source of truth for who may talk to whom.
 */
fun buildInbox(
    myUid: String,
    links: List<SupportLink>,
    docs: List<ConversationDoc>
): List<ChatConversation> {
    val docById = docs.associateBy { it.id }
    return links
        .filter { it.status == "accepted" && (it.userId == myUid || it.supportId == myUid) }
        .distinctBy { it.id }
        .map { link ->
            val iAmUser = link.userId == myUid
            val otherId = if (iAmUser) link.supportId else link.userId
            val doc = docById[link.id]
            ChatConversation(
                id = link.id,
                otherUserId = otherId,
                otherName = if (iAmUser) {
                    doc?.participantNames?.get(link.supportId)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Support contact"
                } else {
                    "User"
                },
                lastMessage = doc?.lastMessage.orEmpty(),
                lastMessageAtMillis = doc?.lastMessageAtMillis,
                unreadCount = doc?.unreadCounts?.get(myUid) ?: 0
            )
        }
        .sortedWith(
            compareByDescending<ChatConversation> { it.lastMessageAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.otherName.lowercase() }
        )
}

fun previewOf(text: String): String =
    text.trim().replace(Regex("\\s+"), " ").take(100)

// Summary text for the inbox: the message, or the file name when there is no text.
fun messagePreview(text: String, attachment: ChatAttachment?): String =
    if (text.isBlank() && attachment != null) "📎 " + attachment.name.take(80)
    else previewOf(text)

fun isValidMessage(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isNotEmpty() && trimmed.length <= MAX_MESSAGE_LENGTH
}

// A message needs text, or an attachment (with optional text).
fun canSend(text: String, hasAttachment: Boolean): Boolean =
    if (hasAttachment) text.trim().length <= MAX_MESSAGE_LENGTH else isValidMessage(text)

fun isAllowedAttachmentType(mimeType: String?): Boolean =
    mimeType != null && (mimeType.startsWith("image/") || mimeType in ALLOWED_DOCUMENT_TYPES)

/** Returns an error message, or null when the file may be sent. */
fun attachmentError(attachment: PendingAttachment): String? = when {
    attachment.sizeBytes > MAX_ATTACHMENT_BYTES -> "Files must be 10 MB or smaller."
    !isAllowedAttachmentType(attachment.mimeType) ->
        "That file type can't be sent. Use a PDF, image, Word or text file."
    else -> null
}

// Safe for a Storage object name: no slashes or unusual characters.
fun sanitizeFileName(name: String): String {
    val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).trim('_', '.')
    return cleaned.ifBlank { "file" }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun formatUnread(count: Int): String = if (count > 99) "99+" else count.toString()

fun formatMessageTime(
    millis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val moment = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val pattern = if (moment.toLocalDate() == today) "h:mm a" else "MMM d, h:mm a"
    return moment.format(DateTimeFormatter.ofPattern(pattern, locale))
}