package com.example.androidapp.pages.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.FirebaseStorageRepository
import com.example.androidapp.data.UserRepository
import com.example.androidapp.repository.SettingsRepository
import com.example.androidapp.repository.UserDocument
import com.example.androidapp.utils.NoteTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DocumentsViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
    private val storageRepository: FirebaseStorageRepository = FirebaseStorageRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    var documents by mutableStateOf<List<UserDocument>>(emptyList())
        private set

    var isUploading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    fun loadDocuments(context: Context) {
        val repo = SettingsRepository(context)
        val localDocs = repo.getUploadedDocuments().toMutableList()
        documents = localDocs

        val uid = authRepository.currentUser?.uid ?: return

        userRepository.getProfile(uid) { result ->
            result.onSuccess { profile ->
                if (profile != null && profile.uploadedDocuments.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val currentList = repo.getUploadedDocuments().toMutableList()
                        var hasChanges = false

                        for (docString in profile.uploadedDocuments) {
                            val parts = docString.split("|")
                            val category: String
                            val name: String
                            val onlineUrl: String?

                            if (parts.size >= 3) {
                                category = parts[0].trim()
                                name = parts[1].trim()
                                onlineUrl = parts[2].trim()
                            } else {
                                val colonParts = docString.split(":", limit = 2)
                                category = colonParts.getOrNull(0)?.trim() ?: "Document"
                                name = if (colonParts.size > 1) colonParts[1].trim() else docString
                                onlineUrl = null
                            }

                            val existing = currentList.find { it.name == name || (!onlineUrl.isNullOrBlank() && it.onlineUrl == onlineUrl) }
                            val hasLocalFile = existing != null && existing.storagePath.isNotBlank() && File(existing.storagePath).exists()

                            if (!hasLocalFile && !onlineUrl.isNullOrBlank()) {
                                try {
                                    val bytes = java.net.URL(onlineUrl).readBytes()
                                    val destFile = File(context.filesDir, "doc_downloaded_${System.currentTimeMillis()}_$name")
                                    destFile.writeBytes(bytes)

                                    val updatedDoc = existing?.copy(
                                        storagePath = destFile.absolutePath,
                                        onlineUrl = onlineUrl
                                    ) ?: UserDocument(
                                        id = UUID.nameUUIDFromBytes(docString.toByteArray()).toString(),
                                        name = name,
                                        type = category,
                                        storagePath = destFile.absolutePath,
                                        onlineUrl = onlineUrl,
                                        uploadedAt = System.currentTimeMillis(),
                                        sharedWithSupport = false
                                    )

                                    if (existing != null) {
                                        val idx = currentList.indexOf(existing)
                                        if (idx >= 0) currentList[idx] = updatedDoc
                                    } else {
                                        currentList.add(updatedDoc)
                                    }
                                    hasChanges = true
                                } catch (_: Exception) {
                                    if (existing == null) {
                                        val newDoc = UserDocument(
                                            id = UUID.nameUUIDFromBytes(docString.toByteArray()).toString(),
                                            name = name,
                                            type = category,
                                            storagePath = "",
                                            onlineUrl = onlineUrl,
                                            uploadedAt = System.currentTimeMillis(),
                                            sharedWithSupport = false
                                        )
                                        currentList.add(newDoc)
                                        hasChanges = true
                                    }
                                }
                            } else if (existing == null) {
                                val newDoc = UserDocument(
                                    id = UUID.nameUUIDFromBytes(docString.toByteArray()).toString(),
                                    name = name,
                                    type = category,
                                    storagePath = "",
                                    onlineUrl = onlineUrl,
                                    uploadedAt = System.currentTimeMillis(),
                                    sharedWithSupport = false
                                )
                                currentList.add(newDoc)
                                hasChanges = true
                            }
                        }

                        if (hasChanges) {
                            repo.setUploadedDocuments(currentList)
                            withContext(Dispatchers.Main) {
                                documents = currentList
                            }
                        }
                    }
                }
            }
        }
    }

    fun uploadDocument(
        context: Context,
        uri: Uri,
        documentType: String
    ) {
        if (isUploading) return
        isUploading = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            try {
                val uid = authRepository.currentUser?.uid ?: "anonymous"

                // 1. Get file display name
                val name = getFileName(context, uri) ?: "Document_${System.currentTimeMillis()}"

                // 2. Copy to internal filesDir for permanent local access
                val destFile = File(context.filesDir, "doc_${System.currentTimeMillis()}_$name")
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists()) destFile.readBytes() else null
                }

                if (bytes == null) {
                    isUploading = false
                    errorMessage = "Could not read document bytes."
                    return@launch
                }

                // 3. Upload to Firebase Storage
                val uploadResult = storageRepository.uploadDocument(uid, name, bytes)
                val onlineUrl = uploadResult.getOrNull()

                // 4. Save metadata to SettingsRepository
                val userDoc = UserDocument(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = documentType,
                    storagePath = destFile.absolutePath,
                    onlineUrl = onlineUrl,
                    uploadedAt = NoteTimestamp.now(),
                    sharedWithSupport = false
                )

                val repo = SettingsRepository(context)
                repo.addUploadedDocument(userDoc)
                documents = repo.getUploadedDocuments()

                // 5. Sync profile in Firestore
                if (uid != "anonymous" && !onlineUrl.isNullOrBlank()) {
                    userRepository.getProfile(uid) { profileResult ->
                        val currentProfile = profileResult.getOrNull()
                        if (currentProfile != null) {
                            val newEntry = "$documentType|$name|$onlineUrl"
                            val updatedList = currentProfile.uploadedDocuments + newEntry
                            userRepository.updateFields(uid, mapOf("uploadedDocuments" to updatedList)) {}
                        }
                    }
                }

                isUploading = false
                successMessage = "Document \"$name\" uploaded successfully!"
            } catch (e: Exception) {
                isUploading = false
                errorMessage = "Upload failed: ${e.localizedMessage}"
            }
        }
    }

    fun toggleShareWithSupport(context: Context, docId: String) {
        val repo = SettingsRepository(context)
        val updatedList = documents.map { doc ->
            if (doc.id == docId) {
                doc.copy(sharedWithSupport = !doc.sharedWithSupport)
            } else {
                doc
            }
        }
        repo.setUploadedDocuments(updatedList)
        documents = updatedList
    }

    fun deleteDocument(context: Context, docId: String) {
        val repo = SettingsRepository(context)
        val docToDelete = documents.find { it.id == docId }
        docToDelete?.storagePath?.let { path ->
            try {
                File(path).delete()
            } catch (_: Exception) {}
        }
        val updatedList = documents.filterNot { it.id == docId }
        repo.setUploadedDocuments(updatedList)
        documents = updatedList
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
