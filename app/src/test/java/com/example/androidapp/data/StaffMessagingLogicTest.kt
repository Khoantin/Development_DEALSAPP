package com.example.androidapp.data

import com.example.androidapp.security.PrivacyLevel
import com.example.androidapp.security.PrivacyRoutePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class StaffMessagingLogicTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val day1 = LocalDate.of(2026, 11, 19).atStartOfDay(zone).toInstant().toEpochMilli()
    private val day2 = day1 + 24 * 60 * 60 * 1000L
    private val hour = 60 * 60 * 1000L

    private fun thread(
        uid: String,
        name: String,
        lastSender: String = "support",
        supportUnread: Int = 0,
        adminUnread: Int = 0
    ) = StaffThread(uid, name, day1, "Hello", lastSender, supportUnread, adminUnread)

    // -------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------

    @Test
    fun `text is trimmed and must be 1 to 2000 characters`() {
        assertNull(StaffMessagingLogic.sendableText(""))
        assertNull(StaffMessagingLogic.sendableText("   \n  "))
        assertEquals("hi", StaffMessagingLogic.sendableText("  hi \n"))
        assertEquals(2000, StaffMessagingLogic.sendableText("x".repeat(2000))?.length)
        assertNull(StaffMessagingLogic.sendableText("x".repeat(2001)))
        // Surrounding spaces don't count toward the limit, as on the server.
        assertEquals(2000, StaffMessagingLogic.sendableText("  " + "x".repeat(2000) + "  ")?.length)
    }

    // -------------------------------------------------------------
    // Labels
    // -------------------------------------------------------------

    @Test
    fun `admins see Administration and the support name`() {
        assertEquals("Administration", StaffMessagingLogic.senderLabel("admin", "admin", "Sam"))
        assertEquals("Sam", StaffMessagingLogic.senderLabel("support", "admin", "Sam"))
        assertEquals("Support staff", StaffMessagingLogic.senderLabel("support", "admin", " "))
    }

    @Test
    fun `support sees the anonymous administrator label and You`() {
        assertEquals("D.E.A.L.S. Administrator", StaffMessagingLogic.senderLabel("admin", "support", "Sam"))
        assertEquals("You", StaffMessagingLogic.senderLabel("support", "support", "Sam"))
    }

    @Test
    fun `admin preview adds You prefix only when an admin sent last`() {
        assertEquals("You: Hello", StaffMessagingLogic.adminPreview(thread("a", "A", lastSender = "admin")))
        assertEquals("Hello", StaffMessagingLogic.adminPreview(thread("a", "A", lastSender = "support")))
    }

    @Test
    fun `unread totals use the viewer's own count`() {
        val threads = listOf(
            thread("a", "A", supportUnread = 4, adminUnread = 1),
            thread("b", "B", supportUnread = 0, adminUnread = 2)
        )
        assertEquals(3, StaffMessagingLogic.totalUnread(threads, "admin"))
        assertEquals(4, StaffMessagingLogic.totalUnread(threads, "support"))
        assertEquals(0, StaffMessagingLogic.totalUnread(emptyList(), "admin"))
        assertEquals("99+", StaffMessagingLogic.badgeText(150))
        assertEquals("7", StaffMessagingLogic.badgeText(7))
    }

    @Test
    fun `search matches support names ignoring case and spaces`() {
        val threads = listOf(thread("a", "Sam Support"), thread("b", "Pat Lee"))
        assertEquals(listOf("a"), StaffMessagingLogic.filterThreads(threads, "  sam ").map { it.supportUid })
        assertEquals(2, StaffMessagingLogic.filterThreads(threads, "  ").size)
        assertTrue(StaffMessagingLogic.filterThreads(threads, "zzz").isEmpty())
    }

    // -------------------------------------------------------------
    // Grouping and send states
    // -------------------------------------------------------------

    @Test
    fun `messages are grouped by day with Today and Yesterday headers`() {
        val items = StaffMessagingLogic.buildItems(
            messages = listOf(
                StaffMessage("m1", "admin", "one", day1 + hour),
                StaffMessage("m2", "support", "two", day1 + 2 * hour),
                StaffMessage("m3", "admin", "three", day2 + hour)
            ),
            outgoing = emptyList(),
            viewerRole = "support",
            supportName = "Sam",
            nowMillis = day2 + 5 * hour,
            zone = zone
        )

        assertEquals(
            listOf("Yesterday", "m1", "m2", "Today", "m3"),
            items.map { if (it is StaffChatItem.DayHeader) it.label else it.key }
        )
        val first = items[1] as StaffChatItem.Bubble
        assertEquals(false, first.isMine)
        assertEquals("D.E.A.L.S. Administrator", first.senderLabel)
        assertEquals(true, (items[2] as StaffChatItem.Bubble).isMine)
    }

    @Test
    fun `older days use a date label`() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals("Today", StaffMessagingLogic.dayLabel(today, today))
        assertEquals("Yesterday", StaffMessagingLogic.dayLabel(today.minusDays(1), today))
        assertTrue(StaffMessagingLogic.dayLabel(today.minusDays(5), today).contains("15"))
    }

    @Test
    fun `outgoing messages show sending or failed until delivered`() {
        val items = StaffMessagingLogic.buildItems(
            messages = listOf(StaffMessage("server-1", "admin", "delivered", day1 + hour)),
            outgoing = listOf(
                OutgoingStaffMessage("l1", "delivered", day1 + hour, serverId = "server-1"),
                OutgoingStaffMessage("l2", "pending", day1 + 2 * hour),
                OutgoingStaffMessage("l3", "failed", day1 + 3 * hour, errorMessage = "Too fast")
            ),
            viewerRole = "admin",
            supportName = "Sam",
            nowMillis = day1 + 4 * hour,
            zone = zone
        ).filterIsInstance<StaffChatItem.Bubble>()

        // The delivered send isn't shown twice.
        assertEquals(listOf("server-1", "local-l2", "local-l3"), items.map { it.key })
        assertEquals(StaffBubbleStatus.SENT, items[0].status)
        assertEquals(StaffBubbleStatus.SENDING, items[1].status)
        assertEquals(StaffBubbleStatus.FAILED, items[2].status)
        assertEquals("Too fast", items[2].errorMessage)
        assertTrue(items.all { it.isMine && it.senderLabel == "Administration" })
    }

    // -------------------------------------------------------------
    // Error messages
    // -------------------------------------------------------------

    @Test
    fun `errors map to rate limit, offline and inactive messages`() {
        assertTrue(StaffMessagingLogic.errorMessage("RESOURCE_EXHAUSTED", null).contains("too quickly"))
        assertTrue(StaffMessagingLogic.errorMessage("UNAVAILABLE", null).contains("offline"))
        assertTrue(StaffMessagingLogic.errorMessage("DEADLINE_EXCEEDED", null).contains("offline"))
        assertTrue(StaffMessagingLogic.errorMessage("PERMISSION_DENIED", null).contains("isn't active"))
        assertEquals(
            "That account isn't an active support account.",
            StaffMessagingLogic.errorMessage(
                "FAILED_PRECONDITION", "That account isn't an active support account."
            )
        )
        assertEquals(
            "This message can't be sent.",
            StaffMessagingLogic.errorMessage("INVALID_ARGUMENT", "INVALID_ARGUMENT")
        )
    }

    // -------------------------------------------------------------
    // Privacy policy
    // -------------------------------------------------------------

    @Test
    fun `staff messaging routes are critical`() {
        for (route in listOf(
            "adminStaffMessages",
            "adminStaffThread/{supportUid}",
            "adminStaffThread/abc123",
            "supportStaffThread"
        )) {
            assertEquals(route, PrivacyLevel.CRITICAL, PrivacyRoutePolicy.levelFor(route))
        }
    }
}
