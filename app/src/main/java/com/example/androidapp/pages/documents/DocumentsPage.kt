package com.example.androidapp.pages.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.androidapp.repository.UserDocument
import com.example.androidapp.security.LocalPrivacyExternalInteraction
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.utils.NoteTimestamp
import java.io.File

private val DOCUMENT_TYPES = listOf(
    "Identification (Passport, License, Birth Cert)",
    "Legal & Protection Orders (Court/Custody)",
    "Financial & Housing (Lease, Deed, Bank)",
    "Incident Reports & Evidence",
    "Security Events"
)

@Composable
fun DocumentsPage(
    viewModel: DocumentsViewModel,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val onExternalInteractionChanged = LocalPrivacyExternalInteraction.current

    LaunchedEffect(Unit) {
        viewModel.loadDocuments(context)
    }

    var selectedType by remember { mutableStateOf(DOCUMENT_TYPES[0]) }
    var expandedTypeDropdown by remember { mutableStateOf(false) }
    var imagePreviewDoc by remember { mutableStateOf<UserDocument?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        onExternalInteractionChanged(false)
        if (uri != null) {
            viewModel.uploadDocument(context, uri, selectedType)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBackClick,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "←",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Personal Documents",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Upload and store critical identification, legal, and incident evidence documents securely.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Upload Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Upload New Document",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Document Category:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedTypeDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedType,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Text("▼")
                    }

                    DropdownMenu(
                        expanded = expandedTypeDropdown,
                        onDismissRequest = { expandedTypeDropdown = false }
                    ) {
                        DOCUMENT_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    selectedType = type
                                    expandedTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onExternalInteractionChanged(true)
                        try {
                            filePicker.launch("*/*")
                        } catch (_: Exception) {
                            onExternalInteractionChanged(false)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !viewModel.isUploading
                ) {
                    if (viewModel.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uploading to Cloud Storage...")
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "Upload"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ Select & Upload Document")
                    }
                }
            }
        }

        viewModel.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        viewModel.successMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your Documents (${viewModel.documents.size})",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (viewModel.documents.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No documents uploaded yet.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Select a document category above and tap upload to store your critical papers.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            viewModel.documents.forEach { doc ->
                DocumentCard(
                    document = doc,
                    onDelete = {
                        viewModel.deleteDocument(context, doc.id)
                    },
                    onOpen = {
                        if (isImageDocument(doc)) {
                            imagePreviewDoc = doc
                        } else {
                            openDocumentFile(context, doc, onExternalInteractionChanged)
                        }
                    }
                )
            }
        }
    }

    if (imagePreviewDoc != null) {
        ImagePreviewDialog(
            document = imagePreviewDoc!!,
            onDismiss = { imagePreviewDoc = null },
            onOpenExternal = {
                val docToOpen = imagePreviewDoc!!
                imagePreviewDoc = null
                openDocumentFile(context, docToOpen, onExternalInteractionChanged)
            }
        )
    }
}

@Composable
private fun DocumentCard(
    document: UserDocument,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Category: ${document.type}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Uploaded: ${NoteTimestamp.format(document.uploadedAt)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Document",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Sync Status
            if (!document.onlineUrl.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDone,
                        contentDescription = "Cloud Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Synced to Cloud",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isImageDocument(document)) "View Image Preview" else "View / Open Document")
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    document: UserDocument,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val imageModel: Any = when {
        document.storagePath.isNotBlank() && File(document.storagePath).exists() -> File(document.storagePath)
        !document.onlineUrl.isNullOrBlank() -> document.onlineUrl
        else -> document.storagePath
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = document.name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = document.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "Category: ${document.type}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenExternal) {
                Text("External App")
            }
        }
    )
}

private fun isImageDocument(document: UserDocument): Boolean {
    val name = document.name.lowercase()
    val path = document.storagePath.lowercase()
    val imageExtensions = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp")
    return imageExtensions.any { name.endsWith(it) || path.endsWith(it) }
}

private fun openDocumentFile(
    context: Context,
    document: UserDocument,
    onExternalInteractionChanged: (Boolean) -> Unit = {}
) {
    onExternalInteractionChanged(true)
    val path = document.storagePath
    val onlineUrl = document.onlineUrl

    try {
        when {
            path.startsWith("content://") || path.startsWith("file://") -> {
                val uri = Uri.parse(path)
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open Document"))
            }
            path.isNotBlank() && File(path).exists() -> {
                val file = File(path)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open Document"))
            }
            !onlineUrl.isNullOrBlank() -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(onlineUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open Link"))
            }
            else -> {
                onExternalInteractionChanged(false)
                Toast.makeText(
                    context,
                    "Document file not stored locally or online. Upload the document file above to enable viewing.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    } catch (_: Exception) {
        if (!onlineUrl.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(onlineUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open Link"))
                return
            } catch (_: Exception) {}
        }
        onExternalInteractionChanged(false)
        Toast.makeText(
            context,
            "No app available on this device to open this document type.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
