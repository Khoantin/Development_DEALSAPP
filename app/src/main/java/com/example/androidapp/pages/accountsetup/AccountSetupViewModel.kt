package com.example.androidapp.pages.accountsetup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.FirebaseStorageRepository
import com.example.androidapp.data.TrustedContact
import com.example.androidapp.data.TrustedContactRepository
import com.example.androidapp.data.UserProfile
import com.example.androidapp.data.UserRepository
import com.example.androidapp.data.toFriendlyAuthMessage
import com.example.androidapp.data.toFriendlyFirestoreMessage
import com.example.androidapp.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AccountSetupViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val trustedContactRepository: TrustedContactRepository =
        TrustedContactRepository(),
    private val storageRepository: FirebaseStorageRepository =
        FirebaseStorageRepository()
) : ViewModel() {

    var state by mutableStateOf(AccountSetupState())
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set
    var supportUserId by mutableStateOf("")
        private set

    fun updateSupportUserId(userId: String) {
        if (!isSubmitting) {
            supportUserId = userId
        }
    }

    // Set once the Auth account exists, so a retry only redoes the profile.
    private var registeredUid: String? = null


    fun updateAccountType(
        accountType: String
    ) {

        state =
            state.copy(
                accountType = accountType
            )
    }


    fun updatePersonalInformation(
        firstName: String,
        lastName: String,
        email: String,
        phoneNumber: String,
        address: String,
        username: String,
        password: String,
        confirmPassword: String
    ) {

        state =
            state.copy(
                firstName = firstName,
                lastName = lastName,
                email = email,
                phoneNumber = phoneNumber,
                address = address,
                username = username,
                password = password,
                confirmPassword = confirmPassword
            )
    }


    private data class PendingDocumentFile(
        val type: String,
        val fileName: String,
        val localPath: String
    )

    private val pendingDocumentFiles = mutableListOf<PendingDocumentFile>()

    fun addDocument(
        document: String
    ) {
        val parts = document.split("|")
        val displayName: String
        if (parts.size >= 3) {
            val type = parts[0].trim()
            val fileName = parts[1].trim()
            val localPath = parts[2].trim()
            displayName = "$type: $fileName"
            pendingDocumentFiles.add(PendingDocumentFile(type, fileName, localPath))
        } else {
            displayName = document
        }

        if (!state.uploadedDocuments.contains(displayName)) {
            state = state.copy(
                uploadedDocuments = state.uploadedDocuments + displayName
            )
        }
    }


    fun removeDocument(
        document: String
    ) {
        pendingDocumentFiles.removeAll {
            "${it.type}: ${it.fileName}" == document ||
                    "${it.type}|${it.fileName}|${it.localPath}" == document
        }
        state = state.copy(
            uploadedDocuments = state.uploadedDocuments - document
        )
    }


    fun addTrustedContact(
        contact: TrustedContact
    ) {

        state =
            state.copy(
                trustedContacts =
                    state.trustedContacts +
                            contact
            )
    }


    fun submitAccount(
        context: Context? = null,
        onSuccess: () -> Unit
    ) {

        if (isSubmitting) {
            return
        }

        isSubmitting = true
        errorMessage = null

        val currentState =
            state

        val accountRole = when (currentState.accountType) {
            "Normal User" -> "user"
            "Support User" -> "support"
            else -> {
                isSubmitting = false
                errorMessage = "Please select a valid account type."
                return
            }
        }

        val profile =
            UserProfile(
                displayName =
                    currentState.username.trim(),

                legalName =
                    "${currentState.firstName.trim()} ${currentState.lastName.trim()}",

                email =
                    currentState.email.trim(),

                phoneNumber =
                    currentState.phoneNumber.trim(),

                address =
                    currentState.address.trim(),

                role =
                    accountRole,

                uploadedDocuments =
                    currentState.uploadedDocuments
            )


        val existingUid =
            registeredUid

        if (existingUid != null) {

            saveProfile(
                context,
                existingUid,
                profile,
                onSuccess
            )

            return
        }


        authRepository.register(
            currentState.email.trim(),
            currentState.password
        ) { result ->

            result
                .onSuccess { user ->

                    registeredUid =
                        user.uid

                    state =
                        state.copy(
                            password = "",
                            confirmPassword = ""
                        )

                    saveProfile(
                        context,
                        user.uid,
                        profile,
                        onSuccess
                    )
                }
                .onFailure {

                    isSubmitting = false

                    errorMessage =
                        it.toFriendlyAuthMessage()
                }
        }
    }


    private fun saveProfile(
        context: Context?,
        uid: String,
        profile: UserProfile,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = if (context != null) SettingsRepository(context) else null
            val formattedDocStrings = mutableListOf<String>()

            // 1. Upload all pending document files to Firebase Storage
            for (pending in pendingDocumentFiles) {
                var onlineUrl: String? = null
                val file = File(pending.localPath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    val uploadResult = storageRepository.uploadDocument(uid, pending.fileName, bytes)
                    onlineUrl = uploadResult.getOrNull()
                }

                val docString = if (!onlineUrl.isNullOrBlank()) {
                    "${pending.type}|${pending.fileName}|$onlineUrl"
                } else {
                    "${pending.type}: ${pending.fileName}"
                }
                formattedDocStrings.add(docString)

                if (repo != null && pending.localPath.isNotBlank()) {
                    val userDoc = com.example.androidapp.repository.UserDocument(
                        id = java.util.UUID.randomUUID().toString(),
                        name = pending.fileName,
                        type = pending.type,
                        storagePath = pending.localPath,
                        onlineUrl = onlineUrl,
                        uploadedAt = System.currentTimeMillis(),
                        sharedWithSupport = false
                    )
                    repo.addUploadedDocument(userDoc)
                }
            }

            // 2. Also check any existing local documents in SettingsRepository and upload if missing onlineUrl
            if (repo != null) {
                val localDocs = repo.getUploadedDocuments()
                val updatedLocalDocs = localDocs.map { doc ->
                    if (doc.onlineUrl.isNullOrBlank() && doc.storagePath.isNotBlank()) {
                        val file = File(doc.storagePath)
                        if (file.exists()) {
                            val bytes = file.readBytes()
                            val uploadResult = storageRepository.uploadDocument(uid, doc.name, bytes)
                            val url = uploadResult.getOrNull()
                            if (!url.isNullOrBlank()) {
                                doc.copy(onlineUrl = url)
                            } else doc
                        } else doc
                    } else doc
                }
                repo.setUploadedDocuments(updatedLocalDocs)
            }

            val finalDocList = if (formattedDocStrings.isNotEmpty()) {
                formattedDocStrings
            } else {
                profile.uploadedDocuments
            }

            val finalProfile = profile.copy(uploadedDocuments = finalDocList)

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                userRepository.createProfile(uid, finalProfile) { profileResult ->
                    profileResult
                        .onSuccess {
                            saveTrustedContacts(uid, onSuccess)
                        }
                        .onFailure {
                            isSubmitting = false
                            errorMessage =
                                "Your account was created, but saving your profile failed. " +
                                        it.toFriendlyFirestoreMessage()
                        }
                }
            }
        }
    }


    private fun saveTrustedContacts(
        uid: String,
        onSuccess: () -> Unit
    ) {

        trustedContactRepository.saveContacts(
            uid = uid,
            contacts = state.trustedContacts
        ) { contactsResult ->

            isSubmitting = false

            contactsResult
                .onSuccess {

                    onSuccess()
                }
                .onFailure {

                    errorMessage =
                        "Your account was created, but saving trusted contacts failed. " +
                                it.toFriendlyFirestoreMessage()
                }
        }
    }


    fun resetState() {
        state = AccountSetupState()
        supportUserId = ""
        registeredUid = null
        isSubmitting = false
        errorMessage = null
    }
}