package com.example.androidapp.repository

import android.content.Context

data class AccountInformation(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val accountType: String = "Normal User"
)

data class UserDocument(
    val id: String = "",
    val name: String = "",
    val type: String = "Identification",

    // Local file path used for viewing on this device.
    val storagePath: String = "",

    // Retained for compatibility with previously uploaded documents.
    val onlineUrl: String? = null,

    val uploadedAt: Long = System.currentTimeMillis(),
    val sharedWithSupport: Boolean = false,

    // Account that owns this document.
    val ownerUid: String = "",

    // Firebase Storage object path, separate from the local path.
    val cloudStoragePath: String = ""
)

data class UserSettings(
    val accountInformation: AccountInformation = AccountInformation(),
    val locationSharingEnabled: Boolean = false,
    val disguiseEnabled: Boolean = true,
    val emailNotificationsEnabled: Boolean = true,
    val disguisePasscodeConfigured: Boolean = false,
    val uploadedDocuments: List<UserDocument> = emptyList(),
    val extremePrivacyEnabled: Boolean = false
)

class SettingsRepository(context: Context? = null) {

    private val prefs = context?.applicationContext?.getSharedPreferences(
        "app_settings", Context.MODE_PRIVATE
    )

    private var userSettings = loadSettingsFromPrefs()

    private fun loadSettingsFromPrefs(): UserSettings {
        if (prefs == null) return UserSettings()
        return UserSettings(
            accountInformation = AccountInformation(
                firstName = prefs.getString("first_name", "") ?: "",
                lastName = prefs.getString("last_name", "") ?: "",
                email = prefs.getString("email", "") ?: "",
                phoneNumber = prefs.getString("phone_number", "") ?: "",
                address = prefs.getString("address", "") ?: "",
                accountType = prefs.getString("account_type", "Normal User") ?: "Normal User"
            ),
            locationSharingEnabled = prefs.getBoolean("location_sharing", false),
            disguiseEnabled = prefs.getBoolean("disguise_enabled", true),
            emailNotificationsEnabled = prefs.getBoolean("email_notifications", true),
            disguisePasscodeConfigured = prefs.getBoolean("disguise_passcode_configured", false),
            extremePrivacyEnabled = prefs.getBoolean("extreme_privacy_enabled", false)
        )
    }

    private fun saveSettingsToPrefs(settings: UserSettings) {
        prefs?.edit()?.apply {
            putString("first_name", settings.accountInformation.firstName)
            putString("last_name", settings.accountInformation.lastName)
            putString("email", settings.accountInformation.email)
            putString("phone_number", settings.accountInformation.phoneNumber)
            putString("address", settings.accountInformation.address)
            putString("account_type", settings.accountInformation.accountType)
            putBoolean("location_sharing", settings.locationSharingEnabled)
            putBoolean("disguise_enabled", settings.disguiseEnabled)
            putBoolean("email_notifications", settings.emailNotificationsEnabled)
            putBoolean("disguise_passcode_configured", settings.disguisePasscodeConfigured)
            // Extreme Privacy is saved only by its dedicated updater.
            apply()
        }
    }

    fun getSettings(): UserSettings {
        userSettings = userSettings.copy(
            extremePrivacyEnabled = isExtremePrivacyEnabled()
        )
        return userSettings
    }

    fun updateAccountInformation(accountInformation: AccountInformation) {
        userSettings = userSettings.copy(accountInformation = accountInformation)
        saveSettingsToPrefs(userSettings)
    }

    fun updateLocationSharing(enabled: Boolean) {
        userSettings = userSettings.copy(locationSharingEnabled = enabled)
        saveSettingsToPrefs(userSettings)
    }

    fun updateDisguiseMode(enabled: Boolean) {
        userSettings = userSettings.copy(disguiseEnabled = enabled)
        saveSettingsToPrefs(userSettings)
    }

    fun isExtremePrivacyEnabled(): Boolean {
        return prefs?.getBoolean("extreme_privacy_enabled", false)
            ?: userSettings.extremePrivacyEnabled
    }

    fun updateExtremePrivacy(enabled: Boolean) {
        userSettings = userSettings.copy(extremePrivacyEnabled = enabled)
        prefs?.edit()
            ?.putBoolean("extreme_privacy_enabled", enabled)
            ?.apply()
    }

    fun updateEmailNotifications(enabled: Boolean) {
        userSettings = userSettings.copy(emailNotificationsEnabled = enabled)
        saveSettingsToPrefs(userSettings)
    }

    fun updateDisguisePasscode(newPasscode: String) {
        if (newPasscode.isBlank()) return
        userSettings = userSettings.copy(disguisePasscodeConfigured = true)
        saveSettingsToPrefs(userSettings)
    }

    fun getUploadedDocuments(): List<UserDocument> {
        return userSettings.uploadedDocuments
    }

    fun setUploadedDocuments(documents: List<UserDocument>) {
        userSettings = userSettings.copy(uploadedDocuments = documents)
    }

    fun addUploadedDocument(document: UserDocument) {
        userSettings = userSettings.copy(
            uploadedDocuments = userSettings.uploadedDocuments + document
        )
    }
}
