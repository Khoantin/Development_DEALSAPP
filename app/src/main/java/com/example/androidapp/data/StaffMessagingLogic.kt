package com.example.androidapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** staffConversations/{supportUid}. Only the support account is named. */
data class StaffThread(
    val supportUid: String,
    val supportDisplayName: String,
    val lastMessageAtMillis: Long?,
    val lastMessagePreview: String,
    val lastSenderRole: String,
    val supportUnread: Int,
    val adminUnread: Int
)

/** staffConversations/{supportUid}/messages/{id}. No admin identity is stored. */
data class StaffMessage(
    val id: String,
    val senderRole: String,
    val text: String,
    val createdAtMillis: Long
)

/** A message this device sent that the listener hasn't delivered yet. */
data class OutgoingStaffMessage(
    val localId: String,
    val text: String,
    val createdAtMillis: Long,
    val errorMessage: String? = null,
    val serverId: String? = null
)

enum class StaffBubbleStatus { SENT, SENDING, FAILED }

sealed interface StaffChatItem {
    val key: String

    data class DayHeader(val label: String, override val key: String) : StaffChatItem

    data class Bubble(
        override val key: String,
        val text: String,
        val timeMillis: Long,
        val isMine: Boolean,
        val senderLabel: String,
        val status: StaffBubbleStatus = StaffBubbleStatus.SENT,
        val errorMessage: String? = null,
        val localId: String? = null
    ) : StaffChatItem
}

object StaffMessagingLogic {
    const val ADMIN = "admin"
    const val SUPPORT = "support"
    const val TEXT_MAX = 2000

    /** How support staff see every admin. Admins never learn which admin wrote. */
    const val ADMIN_LABEL_FOR_SUPPORT = "D.E.A.L.S. Administrator"
    const val ADMIN_LABEL_FOR_ADMIN = "Administration"

    /** Trimmed text when it can be sent (same rule as sendStaffMessage), else null. */
    fun sendableText(draft: String): String? =
        draft.trim().takeIf { it.length in 1..TEXT_MAX }

    fun senderLabel(senderRole: String, viewerRole: String, supportName: String): String =
        when {
            senderRole == ADMIN && viewerRole == ADMIN -> ADMIN_LABEL_FOR_ADMIN
            senderRole == ADMIN -> ADMIN_LABEL_FOR_SUPPORT
            viewerRole == SUPPORT -> "You"
            else -> supportName.ifBlank { "Support staff" }
        }

    /** Inbox preview for admins: "You:" when an admin sent the last message. */
    fun adminPreview(thread: StaffThread): String =
        if (thread.lastSenderRole == ADMIN) "You: ${thread.lastMessagePreview}"
        else thread.lastMessagePreview

    fun unreadFor(thread: StaffThread, viewerRole: String): Int =
        if (viewerRole == ADMIN) thread.adminUnread else thread.supportUnread

    fun totalUnread(threads: List<StaffThread>, viewerRole: String): Int =
        threads.sumOf { unreadFor(it, viewerRole).coerceAtLeast(0) }

    fun filterThreads(threads: List<StaffThread>, query: String): List<StaffThread> {
        val q = query.trim()
        return if (q.isEmpty()) threads
        else threads.filter { it.supportDisplayName.contains(q, ignoreCase = true) }
    }

    fun badgeText(count: Int): String = if (count > 99) "99+" else count.toString()

    /**
     * Server messages in order, then this device's unconfirmed sends, with a
     * day header whenever the local date changes. An outgoing message is
     * dropped once the listener has delivered the message it created.
     */
    fun buildItems(
        messages: List<StaffMessage>,
        outgoing: List<OutgoingStaffMessage>,
        viewerRole: String,
        supportName: String,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<StaffChatItem> {
        val delivered = messages.mapTo(HashSet()) { it.id }

        val bubbles = messages.map { m ->
            StaffChatItem.Bubble(
                key = m.id,
                text = m.text,
                timeMillis = m.createdAtMillis,
                isMine = m.senderRole == viewerRole,
                senderLabel = senderLabel(m.senderRole, viewerRole, supportName)
            )
        } + outgoing.filter { it.serverId !in delivered }.map { o ->
            StaffChatItem.Bubble(
                key = "local-${o.localId}",
                text = o.text,
                timeMillis = o.createdAtMillis,
                isMine = true,
                senderLabel = senderLabel(viewerRole, viewerRole, supportName),
                status = if (o.errorMessage == null) StaffBubbleStatus.SENDING
                else StaffBubbleStatus.FAILED,
                errorMessage = o.errorMessage,
                localId = o.localId
            )
        }

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val items = mutableListOf<StaffChatItem>()
        var lastDay: LocalDate? = null

        for (bubble in bubbles) {
            val day = Instant.ofEpochMilli(bubble.timeMillis).atZone(zone).toLocalDate()
            if (day != lastDay) {
                items += StaffChatItem.DayHeader(dayLabel(day, today), "day-$day")
                lastDay = day
            }
            items += bubble
        }
        return items
    }

    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

    /**
     * Maps a Cloud Functions or Firestore error code name (both use the same
     * gRPC names) to a message the screens can show.
     */
    fun errorMessage(code: String?, serverMessage: String?): String = when (code) {
        "UNAUTHENTICATED" -> "Please sign in again."
        "PERMISSION_DENIED" ->
            "Your account isn't active, so staff messaging isn't available."
        "RESOURCE_EXHAUSTED" ->
            "You're sending messages too quickly. Wait a minute, then try again."
        "UNAVAILABLE", "DEADLINE_EXCEEDED" ->
            "You appear to be offline. Check your connection and try again."
        "INVALID_ARGUMENT", "FAILED_PRECONDITION" ->
            serverMessage?.takeUnless { it.isBlank() || it == code }
                ?: "This message can't be sent."
        "NOT_FOUND" -> "Staff messaging isn't available yet."
        else -> "Something went wrong. Please try again."
    }
}
