package com.example.androidapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.androidapp.repository.UserDocument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class DocumentRecipient(
    val contactId: String,
    val name: String,
    val uid: String,
    val recipientType: String = "trustedContact",
    val supportLinkId: String? = null,
    val supportLinkCreatedAt: com.google.firebase.Timestamp? = null
)

class DocumentSharingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val documentFiles = db.collection("documentFiles")
    private val documentShares = db.collection("documentShares")

    private suspend fun <T> asCurrentUser(
        action: suspend (String) -> T
    ): Result<T> {
        return try {
            val uid = auth.currentUser?.uid
                ?: error("Please sign in again.")

            val value = action(uid)
            checkAccount(uid)

            Result.success(value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun checkAccount(uid: String) {
        check(auth.currentUser?.uid == uid) {
            "The signed-in account changed. Please reopen Documents."
        }
    }

    suspend fun loadOwnDocuments(): Result<List<UserDocument>> =
        asCurrentUser { uid ->
            documentFiles
                .whereEqualTo("ownerUid", uid)
                .get(Source.SERVER)
                .await()
                .documents
                .map { snapshot ->
                    UserDocument(
                        id = snapshot.id,
                        name = snapshot.getString("name").orEmpty(),
                        type = snapshot.getString("type").orEmpty(),
                        ownerUid = snapshot.getString("ownerUid").orEmpty(),
                        cloudStoragePath =
                            snapshot.getString("cloudStoragePath").orEmpty(),
                        uploadedAt = snapshot.getTimestamp("createdAt")
                            ?.toDate()?.time ?: 0L
                    )
                }
                .sortedByDescending { it.uploadedAt }
        }

    suspend fun loadSharedDocuments(): Result<List<UserDocument>> =
        asCurrentUser { uid ->
            val grants = documentShares
                .whereEqualTo("recipientUid", uid)
                .get(Source.SERVER)
                .await()

            val documents = mutableListOf<UserDocument>()

            for (grant in grants.documents) {
                checkAccount(uid)

                val documentId = grant.getString("documentId")
                    ?: continue

                try {
                    val snapshot = documentFiles.document(documentId)
                        .get(Source.SERVER)
                        .await()

                    if (snapshot.exists()) {
                        documents.add(
                            UserDocument(
                                id = snapshot.id,
                                name = snapshot.getString("name").orEmpty(),
                                type = snapshot.getString("type").orEmpty(),
                                ownerUid =
                                    snapshot.getString("ownerUid").orEmpty(),
                                cloudStoragePath = snapshot
                                    .getString("cloudStoragePath").orEmpty(),
                                uploadedAt = snapshot
                                    .getTimestamp("createdAt")
                                    ?.toDate()?.time ?: 0L
                            )
                        )
                    }
                } catch (error: FirebaseFirestoreException) {
                    // A removed contact or revoked grant must not
                    // leave an accessible document in the list.
                    if (
                        error.code !=
                        FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                        error.code !=
                        FirebaseFirestoreException.Code.NOT_FOUND
                    ) {
                        throw error
                    }
                }
            }

            documents.sortedByDescending { it.uploadedAt }
        }

    suspend fun loadRecipients(): Result<List<DocumentRecipient>> =
        asCurrentUser { uid ->
            val trustedContacts = db.collection("users")
                .document(uid)
                .collection("trustedContacts")
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { contact ->
                    val linkedUid = contact.getString("linkedUserId")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && it != uid }
                        ?: return@mapNotNull null

                    DocumentRecipient(
                        contactId = contact.id,
                        name = contact.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: "Trusted contact",
                        uid = linkedUid
                    )
                }
                .distinctBy { it.uid }

            checkAccount(uid)

            val supportRecipients = db.collection("supportLinks")
                .whereEqualTo("userId", uid)
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { link ->
                    if (link.getString("status") != "accepted") {
                        return@mapNotNull null
                    }

                    val supportUid = link.getString("supportId")
                        ?.takeIf { it.isNotBlank() && it != uid }
                        ?: return@mapNotNull null

                    if (link.id != "${uid}_$supportUid") {
                        return@mapNotNull null
                    }

                    val createdAt = link.getTimestamp("createdAt")
                        ?: return@mapNotNull null

                    DocumentRecipient(
                        contactId = "",
                        name = "Support account ($supportUid)",
                        uid = supportUid,
                        recipientType = "support",
                        supportLinkId = link.id,
                        supportLinkCreatedAt = createdAt
                    )
                }

            // One entry per recipient. Keep an existing trusted-contact
            // entry when the same account is also an approved Support.
            (trustedContacts + supportRecipients)
                .distinctBy { it.uid }
                .sortedBy { it.name.lowercase() }
        }

    suspend fun loadSharedRecipientIds(
        documentId: String
    ): Result<Set<String>> = asCurrentUser { uid ->
        documentShares
            .whereEqualTo("ownerUid", uid)
            .whereEqualTo("documentId", documentId)
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { it.getString("recipientUid") }
            .toSet()
    }
    suspend fun loadExpiredSupportRecipientIds(
        documentId: String
    ): Result<Set<String>> = asCurrentUser { uid ->
        val links = db.collection("supportLinks")
            .whereEqualTo("userId", uid)
            .get(Source.SERVER)
            .await()
            .documents
            .associateBy { it.id }

        val grants = documentShares
            .whereEqualTo("ownerUid", uid)
            .whereEqualTo("documentId", documentId)
            .get(Source.SERVER)
            .await()

        grants.documents.mapNotNull { grant ->
            if (grant.getString("recipientType") != "support") {
                return@mapNotNull null
            }

            val recipientUid = grant.getString("recipientUid")
                ?: return@mapNotNull null

            val expectedLinkId = "${uid}_$recipientUid"
            val link = links[expectedLinkId]
            val savedTimestamp = grant.getTimestamp("supportLinkCreatedAt")

            val valid = link != null &&
                    grant.getString("supportLinkId") == expectedLinkId &&
                    link.getString("userId") == uid &&
                    link.getString("supportId") == recipientUid &&
                    link.getString("status") == "accepted" &&
                    savedTimestamp != null &&
                    link.getTimestamp("createdAt") == savedTimestamp

            if (valid) null else recipientUid
        }.toSet()
    }

    suspend fun shareDocument(
        documentId: String,
        recipient: DocumentRecipient
    ): Result<Unit> = asCurrentUser { uid ->
        require(recipient.uid.isNotBlank() && recipient.uid != uid) {
            "Please select a valid recipient."
        }

        val data = mutableMapOf<String, Any>(
            "documentId" to documentId,
            "ownerUid" to uid,
            "recipientUid" to recipient.uid,
            "createdAt" to FieldValue.serverTimestamp()
        )

        when (recipient.recipientType) {
            "support" -> {
                val linkId = "${uid}_${recipient.uid}"

                require(recipient.supportLinkId == linkId) {
                    "The support connection is invalid. Refresh and try again."
                }

                val expectedCreatedAt = recipient.supportLinkCreatedAt
                    ?: error("Refresh your support connections and try again.")

                val link = db.collection("supportLinks")
                    .document(linkId)
                    .get(Source.SERVER)
                    .await()

                check(
                    link.exists() &&
                            link.getString("userId") == uid &&
                            link.getString("supportId") == recipient.uid &&
                            link.getString("status") == "accepted" &&
                            link.getTimestamp("createdAt") == expectedCreatedAt
                ) {
                    "This support connection has changed. Refresh and try again."
                }

                data["recipientType"] = "support"
                data["supportLinkId"] = linkId
                data["supportLinkCreatedAt"] = expectedCreatedAt
            }

            "trustedContact" -> {
                require(recipient.contactId.isNotBlank()) {
                    "Please select a valid trusted contact."
                }

                // Preserve the existing trusted-contact grant format.
                data["contactId"] = recipient.contactId
            }

            else -> error("Unsupported recipient type.")
        }

        checkAccount(uid)

        documentShares
            .document("${documentId}_${recipient.uid}")
            .set(data)
            .await()

        Unit
    }
    suspend fun revokeShare(
        documentId: String,
        recipientUid: String
    ): Result<Unit> = asCurrentUser {
        documentShares.document("${documentId}_$recipientUid")
            .delete()
            .await()

        Unit
    }

    suspend fun uploadDocument(
        context: Context,
        uri: Uri,
        category: String
    ): Result<Unit> = asCurrentUser { uid ->
        require(category.isNotBlank() && category.length <= 150)

        val resolver = context.contentResolver

        val name = withContext(Dispatchers.IO) {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
        }?.takeIf { it.isNotBlank() } ?: "Document"

        require(name.length <= 255) {
            "The file name must be 255 characters or fewer."
        }

        val contentType = resolver.getType(uri)
            ?: "application/octet-stream"

        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0

                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break

                    total += count
                    require(total <= MAX_FILE_BYTES) {
                        "Choose a document smaller than 20 MB."
                    }
                    output.write(buffer, 0, count)
                }

                output.toByteArray()
            } ?: error("The selected file could not be read.")
        }

        require(bytes.isNotEmpty()) {
            "The selected file is empty."
        }

        checkAccount(uid)

        val documentId = UUID.randomUUID().toString()
        val cloudPath = "protectedDocuments/$uid/$documentId/content"
        val fileReference = storage.reference.child(cloudPath)

        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .build()

        fileReference.putBytes(bytes, metadata).await()
        checkAccount(uid)

        // Do not generate or distribute a download URL.
        documentFiles.document(documentId).set(
            mapOf(
                "ownerUid" to uid,
                "name" to name,
                "type" to category,
                "cloudStoragePath" to cloudPath,
                "contentType" to contentType,
                "sizeBytes" to bytes.size.toLong(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()

        Unit
    }

    suspend fun downloadForViewing(
        context: Context,
        document: UserDocument
    ): Result<File> = asCurrentUser { uid ->
        // Always recheck current server permission before viewing.
        val snapshot = documentFiles.document(document.id)
            .get(Source.SERVER)
            .await()

        check(snapshot.exists()) {
            "This document is no longer available."
        }

        val ownerUid = snapshot.getString("ownerUid")
            ?: error("The document owner is missing.")

        val path = snapshot.getString("cloudStoragePath")
            ?: error("The document file is missing.")

        check(
            path == "protectedDocuments/$ownerUid/${document.id}/content"
        ) {
            "The document path is invalid."
        }

        checkAccount(uid)

        val folder = File(
            context.filesDir,
            "document_view_cache/$uid"
        )

        val suffix = snapshot.getString("name")
            .orEmpty()
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
            ?.let { ".$it" }
            ?: ".bin"

        val localFile = withContext(Dispatchers.IO) {
            check(folder.exists() || folder.mkdirs()) {
                "Could not prepare the document viewer."
            }

            File.createTempFile("view_", suffix, folder)
        }

        try {
            storage.reference.child(path)
                .getFile(localFile)
                .await()

            checkAccount(uid)
            localFile
        } catch (error: Exception) {
            withContext(Dispatchers.IO) {
                localFile.delete()
            }
            throw error
        }
    }

    companion object {
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}