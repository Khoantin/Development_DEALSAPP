package com.example.androidapp.pages.accountsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.UserRepository
import com.example.androidapp.data.toFriendlyFirestoreMessage
import com.example.androidapp.repository.AccountInformation
import com.example.androidapp.repository.PasswordRepository
import com.example.androidapp.repository.SettingsRepository
import com.example.androidapp.repository.UserDocument
import kotlinx.coroutines.launch
import com.example.androidapp.data.SupportPairingRepository
import com.example.androidapp.data.toSupportPairingMessage
import com.google.firebase.auth.FirebaseAuth

data class AccountSettingsState(
    val accountInformation: AccountInformation = AccountInformation(),
    val locationSharingEnabled: Boolean = false,
    val disguiseEnabled: Boolean = true,
    val emailNotificationsEnabled: Boolean = true,
    val uploadedDocuments: List<UserDocument> = emptyList(),
    val failedPasscodeAttempts: List<String> = emptyList(),
    val extremePrivacyEnabled: Boolean = false
)

class AccountSettingsViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
    private val passwordRepository: PasswordRepository? = null,
    private val authRepository: AuthRepository? = null,
    private val userRepository: UserRepository? = null
) : ViewModel() {

    var settings by mutableStateOf(AccountSettingsState())
        private set

    var isProfileLoading by mutableStateOf(false)
        private set

    var profileError by mutableStateOf<String?>(null)
        private set

    // Temporary support pairing code state.
    // Code generation and validation will be connected to a trusted Firebase backend.
    var pairingCode by mutableStateOf<String?>(null)
        private set

    var pairingCodeExpiresAt by mutableStateOf<Long?>(null)
        private set

    var isGeneratingPairingCode by mutableStateOf(false)
        private set

    var pairingCodeError by mutableStateOf<String?>(null)
        private set

    private val pairingRepository = SupportPairingRepository()
    private val pairingAuth = FirebaseAuth.getInstance()
    private var pairingOwnerUid = pairingAuth.currentUser?.uid
    private var pairingRequestVersion = 0

    private val pairingAuthListener = FirebaseAuth.AuthStateListener { auth ->
        val uid = auth.currentUser?.uid

        if (uid != pairingOwnerUid) {
            pairingOwnerUid = uid
            clearPairingCode()
        }
    }

    init {
        loadSettings()
        pairingAuth.addAuthStateListener(pairingAuthListener)
    }

    fun loadProfile() {
        val auth = authRepository ?: return
        val users = userRepository ?: return
        val user = auth.currentUser ?: return

        isProfileLoading = true
        profileError = null

        users.getProfile(user.uid) { result ->
            isProfileLoading = false
            result
                .onSuccess { profile ->
                    if (profile != null) {
                        val nameParts = profile.legalName.trim().split(" ", limit = 2)
                        val info = settings.accountInformation.copy(
                            firstName = nameParts.getOrElse(0) { "" },
                            lastName = nameParts.getOrElse(1) { "" },
                            email = profile.email.ifBlank { user.email ?: "" },
                            phoneNumber = profile.phoneNumber,
                            address = profile.address
                        )
                        settingsRepository.updateAccountInformation(info)
                        settings = settings.copy(accountInformation = info)
                    }
                }
                .onFailure { profileError = it.toFriendlyFirestoreMessage() }
        }
    }

    private fun loadSettings() {
        val repositorySettings = settingsRepository.getSettings()
        settings = AccountSettingsState(
            accountInformation = repositorySettings.accountInformation,
            locationSharingEnabled = repositorySettings.locationSharingEnabled,
            disguiseEnabled = repositorySettings.disguiseEnabled,
            emailNotificationsEnabled = repositorySettings.emailNotificationsEnabled,
            uploadedDocuments = repositorySettings.uploadedDocuments,
            extremePrivacyEnabled = repositorySettings.extremePrivacyEnabled
        )
    }

    fun updateAccountInformation(
        firstName: String,
        lastName: String,
        email: String, // Email remains read-only.
        phoneNumber: String,
        address: String
    ) {
        val updated = settings.accountInformation.copy(
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phoneNumber,
            address = address
        )
        settingsRepository.updateAccountInformation(updated)
        settings = settings.copy(accountInformation = updated)

        val uid = authRepository?.currentUser?.uid
        if (uid != null && userRepository != null) {
            userRepository.updateFields(
                uid,
                mapOf(
                    "legalName" to "$firstName $lastName".trim(),
                    "phoneNumber" to phoneNumber,
                    "address" to address
                )
            ) { result ->
                result.onFailure { profileError = it.toFriendlyFirestoreMessage() }
            }
        }
    }

    fun updateLocationSharing(enabled: Boolean) {
        settingsRepository.updateLocationSharing(enabled)
        settings = settings.copy(locationSharingEnabled = enabled)
    }

    fun updateDisguiseMode(enabled: Boolean) {
        settingsRepository.updateDisguiseMode(enabled)
        settings = settings.copy(disguiseEnabled = enabled)
    }

    fun updateExtremePrivacy(enabled: Boolean) {
        settingsRepository.updateExtremePrivacy(enabled)
        settings = settings.copy(extremePrivacyEnabled = enabled)
    }

    fun updateEmailNotifications(enabled: Boolean) {
        settingsRepository.updateEmailNotifications(enabled)
        settings = settings.copy(emailNotificationsEnabled = enabled)
    }

    fun changeDisguisePasscode(newPasscode: String) {
        settingsRepository.updateDisguisePasscode(newPasscode)
        viewModelScope.launch {
            passwordRepository?.savePassword(newPasscode)
        }
    }

    fun refreshUploadedDocuments() {
        settings = settings.copy(
            uploadedDocuments = settingsRepository.getUploadedDocuments()
        )
    }

    fun refreshFailedAttempts() {
        viewModelScope.launch {
            val attempts = passwordRepository?.getFailedAttempts() ?: emptyList()
            settings = settings.copy(failedPasscodeAttempts = attempts)
        }
    }

    fun addUploadedDocument(document: UserDocument) {
        settingsRepository.addUploadedDocument(document)
        refreshUploadedDocuments()
    }

    fun setUploadedDocuments(documents: List<UserDocument>) {
        settingsRepository.setUploadedDocuments(documents)
        refreshUploadedDocuments()
    }


    fun generatePairingCode() {
        if (isGeneratingPairingCode) return

        val uid = pairingAuth.currentUser?.uid

        if (uid == null) {
            clearPairingCode()
            pairingCodeError = "Please sign in again."
            return
        }

        // Hide the previous code while requesting its replacement.
        clearPairingCode()
        pairingOwnerUid = uid

        val version = pairingRequestVersion
        isGeneratingPairingCode = true

        pairingRepository.generateCode { result ->
            if (
                version != pairingRequestVersion ||
                pairingAuth.currentUser?.uid != uid
            ) {
                return@generateCode
            }

            isGeneratingPairingCode = false

            result.onSuccess { generated ->
                pairingCode = generated.code
                pairingCodeExpiresAt = generated.expiresAt
                pairingCodeError = null
            }.onFailure { error ->
                pairingCodeError = error.toSupportPairingMessage()
            }
        }
    }

    // Clears the display only; it does not revoke a server-side code.
    fun clearPairingCode() {
        pairingRequestVersion++
        pairingCode = null
        pairingCodeExpiresAt = null
        pairingCodeError = null
        isGeneratingPairingCode = false
    }

    fun getValidPairingCode(): String? {
        val code = pairingCode ?: return null
        val expiresAt = pairingCodeExpiresAt ?: return null

        if (
            pairingAuth.currentUser?.uid != pairingOwnerUid ||
            System.currentTimeMillis() >= expiresAt
        ) {
            clearPairingCode()
            return null
        }

        return code
    }

    override fun onCleared() {
        pairingRequestVersion++
        pairingAuth.removeAuthStateListener(pairingAuthListener)
        super.onCleared()
    }
}
