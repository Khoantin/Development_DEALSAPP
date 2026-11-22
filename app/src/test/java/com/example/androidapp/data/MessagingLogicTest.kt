package com.example.androidapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class MessagingLogicTest {

    private val me = "user1"

    private fun link(id: String, userId: String, supportId: String, status: String) =
        SupportLink(id, userId, supportId, status)

    private fun doc(
        id: String,
        userId: String,
        supportId: String,
        names: Map<String, String> = emptyMap(),
        last: String = "",
        at: Long? = null,
        unread: Map<String, Int> = emptyMap()
    ) = ConversationDoc(id, userId, supportId, names, last, at, unread)

    @Test
    fun inboxOnlyContainsAcceptedLinksInvolvingMe() {
        val links = listOf(
            link("user1_s1", "user1", "s1", "accepted"),
            link("user1_s2", "user1", "s2", "pending"),
            link("user1_s3", "user1", "s3", "rejected"),
            link("other_s4", "other", "s4", "accepted")
        )
        val inbox = buildInbox(me, links, emptyList())
        assertEquals(listOf("user1_s1"), inbox.map { it.id })
    }

    @Test
    fun inboxUsesConversationDataAndUnreadForMe() {
        val links = listOf(link("user1_s1", "user1", "s1", "accepted"))
        val docs = listOf(
            doc(
                "user1_s1", "user1", "s1",
                names = mapOf("s1" to "Sam"),
                last = "Hello",
                at = 1000L,
                unread = mapOf("user1" to 3, "s1" to 9)
            )
        )
        val row = buildInbox(me, links, docs).single()
        assertEquals("Sam", row.otherName)
        assertEquals("s1", row.otherUserId)
        assertEquals("Hello", row.lastMessage)
        assertEquals(3, row.unreadCount)
    }

    @Test
    fun inboxFallsBackToRoleLabelWhenNameUnknown() {
        val asUser = buildInbox("user1", listOf(link("user1_s1", "user1", "s1", "accepted")), emptyList())
        assertEquals("Support contact", asUser.single().otherName)

        val asSupport = buildInbox("s1", listOf(link("user1_s1", "user1", "s1", "accepted")), emptyList())
        assertEquals("User", asSupport.single().otherName)
        assertEquals("user1", asSupport.single().otherUserId)
    }

    @Test
    fun inboxSortsNewestFirstAndEmptyConversationsLast() {
        val links = listOf(
            link("user1_a", "user1", "a", "accepted"),
            link("user1_b", "user1", "b", "accepted"),
            link("user1_c", "user1", "c", "accepted")
        )
        val docs = listOf(
            doc("user1_a", "user1", "a", at = 100L, last = "old"),
            doc("user1_b", "user1", "b", at = 200L, last = "new")
        )
        assertEquals(listOf("user1_b", "user1_a", "user1_c"), buildInbox(me, links, docs).map { it.id })
    }

    @Test
    fun duplicateLinksProduceOneRow() {
        val l = link("user1_s1", "user1", "s1", "accepted")
        assertEquals(1, buildInbox(me, listOf(l, l), emptyList()).size)
    }

    @Test
    fun messageValidation() {
        assertFalse(isValidMessage(""))
        assertFalse(isValidMessage("   \n "))
        assertTrue(isValidMessage(" hi "))
        assertTrue(isValidMessage("a".repeat(MAX_MESSAGE_LENGTH)))
        assertFalse(isValidMessage("a".repeat(MAX_MESSAGE_LENGTH + 1)))
    }

    @Test
    fun previewCollapsesWhitespaceAndTruncates() {
        assertEquals("a b c", previewOf("  a \n b   c "))
        assertEquals(100, previewOf("x".repeat(500)).length)
    }

    @Test
    fun unreadBadgeCapsAt99() {
        assertEquals("5", formatUnread(5))
        assertEquals("99", formatUnread(99))
        assertEquals("99+", formatUnread(100))
    }

    @Test
    fun timeFormatShowsDateOnlyForOtherDays() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 11, 3, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val sameDay = ZonedDateTime.of(2026, 11, 3, 9, 5, 0, 0, zone).toInstant().toEpochMilli()
        val earlier = ZonedDateTime.of(2026, 11, 1, 9, 5, 0, 0, zone).toInstant().toEpochMilli()

        val today = formatMessageTime(sameDay, now, zone, Locale.US)
        val other = formatMessageTime(earlier, now, zone, Locale.US)

        assertTrue(today.contains("9:05"))
        assertFalse(today.contains("Nov"))
        assertTrue(other.contains("Nov 1"))
    }
}