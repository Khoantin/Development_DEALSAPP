package com.example.androidapp.pages.login

import com.example.androidapp.pages.accountsetup.AccountSetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAndSetupTest {

    @Test
    fun testAccountSetupStateDefaults() {
        val state = AccountSetupState()
        assertEquals(null, state.accountType)
        assertTrue(state.firstName.isEmpty())
        assertTrue(state.lastName.isEmpty())
        assertTrue(state.email.isEmpty())
        assertTrue(state.trustedContacts.isEmpty())
        assertTrue(state.uploadedDocuments.isEmpty())
    }

    @Test
    fun testAccountSetupStateUploadedDocuments() {
        val state = AccountSetupState(uploadedDocuments = listOf("ID: driver_license.pdf"))
        assertEquals(1, state.uploadedDocuments.size)
        assertEquals("ID: driver_license.pdf", state.uploadedDocuments[0])
    }

    @Test
    fun testEmailFormatValidationLogic() {
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
        assertTrue(emailRegex.matches("test@example.com"))
        assertTrue(!emailRegex.matches("invalid-email"))
    }
}
