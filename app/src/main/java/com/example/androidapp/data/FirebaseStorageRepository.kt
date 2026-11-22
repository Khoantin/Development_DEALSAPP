package com.example.androidapp.data

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseStorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    suspend fun uploadFile(
        uid: String,
        bucketName: String,
        fileName: String,
        bytes: ByteArray
    ): Result<String> {
        return try {
            val ref = storage.reference.child("users/$uid/$bucketName/${UUID.randomUUID()}_$fileName")
            ref.putBytes(bytes).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadDocument(uid: String, fileName: String, bytes: ByteArray): Result<String> {
        return uploadFile(uid, "documents", fileName, bytes)
    }

    suspend fun uploadImage(uid: String, fileName: String, bytes: ByteArray): Result<String> {
        return uploadFile(uid, "documents", fileName, bytes)
    }

    suspend fun uploadNoteContent(uid: String, noteId: Long, jsonString: String): Result<String> {
        return uploadFile(uid, "notes", "note_$noteId.json", jsonString.encodeToByteArray())
    }
}
