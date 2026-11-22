package com.example.androidapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccountsLogicTest {

    private fun account(
        uid: String,
        name: String = "Name $uid",
        email: String = "$uid@example.com",
        status: String = "active"
    ) = AdminAccount(
        uid = uid,
        displayName = name,
        email = email,
        role = "user",
        accountStatus = status,
        createdAtMillis = null,
        statusUpdatedAtMillis = null
    )

    @Test
    fun `search matches name or email, ignoring case and spaces`() {
        val accounts = listOf(
            account("a", name = "Alice Smith", email = "alice@example.com"),
            account("b", name = "Bob", email = "robert@Sample.org"),
            account("c", name = "Carol", email = "carol@example.com")
        )

        assertEquals(listOf("a"), AdminAccountsLogic.filterAccounts(accounts, "  SMITH ").map { it.uid })
        assertEquals(listOf("b"), AdminAccountsLogic.filterAccounts(accounts, "sample.ORG").map { it.uid })
        assertEquals(3, AdminAccountsLogic.filterAccounts(accounts, "   ").size)
        assertTrue(AdminAccountsLogic.filterAccounts(accounts, "zzz").isEmpty())
    }

    @Test
    fun `actions offered depend on current status`() {
        assertEquals(listOf("suspended", "disabled"), AdminAccountsLogic.availableTargets("active"))
        assertEquals(listOf("active", "disabled"), AdminAccountsLogic.availableTargets("suspended"))
        assertEquals(listOf("active", "suspended"), AdminAccountsLogic.availableTargets("disabled"))
        assertEquals(listOf("active", "suspended", "disabled"), AdminAccountsLogic.availableTargets("inactive"))
    }

    @Test
    fun `reason must be 5 to 500 characters after trimming`() {
        assertFalse(AdminAccountsLogic.isValidReason(""))
        assertFalse(AdminAccountsLogic.isValidReason("abcd"))
        assertFalse(AdminAccountsLogic.isValidReason("   ab    "))
        assertTrue(AdminAccountsLogic.isValidReason("  spam!  "))
        assertTrue(AdminAccountsLogic.isValidReason("x".repeat(500)))
        assertFalse(AdminAccountsLogic.isValidReason("x".repeat(501)))
    }

    @Test
    fun `status change updates the row and its timestamp`() {
        val result = AdminAccountsLogic.applyStatusChange(
            listOf(account("a"), account("b")),
            targetUid = "a",
            toStatus = "suspended",
            statusFilter = null,
            nowMillis = 42L
        )

        assertEquals("suspended", result[0].accountStatus)
        assertEquals(42L, result[0].statusUpdatedAtMillis)
        assertEquals("active", result[1].accountStatus)
    }

    @Test
    fun `status change removes the row when it no longer matches the filter`() {
        val result = AdminAccountsLogic.applyStatusChange(
            listOf(account("a"), account("b")),
            targetUid = "a",
            toStatus = "suspended",
            statusFilter = "active",
            nowMillis = 42L
        )

        assertEquals(listOf("b"), result.map { it.uid })
    }

    @Test
    fun `repeating the current status leaves the row unchanged`() {
        val original = account("a", status = "suspended")
        val result = AdminAccountsLogic.applyStatusChange(
            listOf(original),
            targetUid = "a",
            toStatus = "suspended",
            statusFilter = "suspended",
            nowMillis = 42L
        )

        assertEquals(listOf(original), result)
    }

    @Test
    fun `role actions depend on current role and never apply to yourself`() {
        assertEquals(listOf("admin"), AdminAccountsLogic.roleTargets("user", isSelf = false))
        assertEquals(listOf("admin"), AdminAccountsLogic.roleTargets("support", isSelf = false))
        assertEquals(listOf("user", "support"), AdminAccountsLogic.roleTargets("admin", isSelf = false))
        assertTrue(AdminAccountsLogic.roleTargets("admin", isSelf = true).isEmpty())
        assertTrue(AdminAccountsLogic.roleTargets("user", isSelf = true).isEmpty())
    }

    @Test
    fun `role change updates the row and its timestamp`() {
        val result = AdminAccountsLogic.applyRoleChange(
            listOf(account("a"), account("b")),
            targetUid = "a",
            toRole = "admin",
            roleFilter = null,
            nowMillis = 7L
        )

        assertEquals("admin", result[0].role)
        assertEquals(7L, result[0].roleUpdatedAtMillis)
        assertEquals("user", result[1].role)
    }

    @Test
    fun `role change removes the row when it no longer matches the role filter`() {
        val result = AdminAccountsLogic.applyRoleChange(
            listOf(account("a"), account("b")),
            targetUid = "a",
            toRole = "admin",
            roleFilter = "user",
            nowMillis = 7L
        )

        assertEquals(listOf("b"), result.map { it.uid })
    }

    @Test
    fun `appending a page skips rows already loaded`() {
        val result = AdminAccountsLogic.appendPage(
            listOf(account("a"), account("b")),
            listOf(account("b"), account("c"))
        )

        assertEquals(listOf("a", "b", "c"), result.map { it.uid })
    }
}
