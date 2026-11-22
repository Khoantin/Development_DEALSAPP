package com.example.androidapp.pages.documents

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.androidapp.repository.UserDocument
import com.example.androidapp.security.LocalPrivacyExternalInteraction
import com.example.androidapp.security.trackPrivacyInteraction
import java.io.File

private val sharingDocumentTypes = listOf(
    "Identification (Passport, License, Birth Cert)",
    "Legal & Protection Orders (Court/Custody)",
    "Financial & Housing (Lease, Deed, Bank)",
    "Incident Reports & Evidence",
    "Security Events"
)

private data class DocumentPreview(
    val document: UserDocument,
    val file: File
)

@Composable
fun DocumentSharingPage(
    onBackClick: () -> Unit,
    onQuickExit: () -> Unit,
    onEarlierUploadsClick: () -> Unit,
    viewModel: DocumentSharingViewModel = viewModel()
) {
    // Account changes discard the previous account's UI and preview.
    key(viewModel.currentUid) {
        DocumentSharingContent(
            viewModel = viewModel,
            onBackClick = onBackClick,
            onQuickExit = onQuickExit,
            onEarlierUploadsClick = onEarlierUploadsClick
        )
    }
}

@Composable
private fun DocumentSharingContent(
    viewModel: DocumentSharingViewModel,
    onBackClick: () -> Unit,
    onQuickExit: () -> Unit,
    onEarlierUploadsClick: () -> Unit
) {
    val context = LocalContext.current
    val externalInteraction =
        LocalPrivacyExternalInteraction.current

    var category by remember {
        mutableStateOf(sharingDocumentTypes.first())
    }
    var categoryExpanded by remember { mutableStateOf(false) }

    var preview by remember {
        mutableStateOf<DocumentPreview?>(null)
    }
    var viewerError by remember { mutableStateOf<String?>(null) }

    val active = remember { mutableStateOf(false) }
    val temporaryFiles = remember { mutableSetOf<File>() }

    // Files handed to an external viewer must remain readable
    // after this screen exits.
    val externallyOpenedFiles = remember { mutableSetOf<File>() }

    DisposableEffect(Unit) {
        active.value = true

        onDispose {
            active.value = false
            temporaryFiles
                .filterNot { it in externallyOpenedFiles }
                .forEach { it.delete() }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        externalInteraction(false)

        if (uri != null && active.value) {
            viewModel.uploadDocument(context, uri, category)
        }
    }

    fun closePreview() {
        preview?.file?.let { file ->
            if (file !in externallyOpenedFiles) {
                file.delete()
                temporaryFiles.remove(file)
            }
        }
        preview = null
        viewerError = null
    }

    val handleBack: () -> Unit = {
        when {
            preview != null -> closePreview()
            viewModel.selectedDocument != null ->
                viewModel.closeSharing()
            else -> onBackClick()
        }
    }

    BackHandler(onBack = handleBack)

    val openDocument: (UserDocument) -> Unit = { document ->
        viewerError = null

        viewModel.openDocument(context, document) { file ->
            if (active.value) {
                temporaryFiles.add(file)
                preview = DocumentPreview(document, file)
            } else {
                file.delete()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .trackPrivacyInteraction()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = handleBack,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Back")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Documents",
                    style = MaterialTheme.typography.headlineMedium
                )

                val currentPreview = preview
                val sharingDocument = viewModel.selectedDocument

                when {
                    viewModel.currentUid == null -> {
                        Text("Sign in to manage documents.")
                    }

                    currentPreview != null -> {
                        Text(
                            currentPreview.document.name,
                            style = MaterialTheme.typography.titleLarge
                        )

                        val isImage = currentPreview.file.extension
                            .lowercase() in setOf(
                            "png", "jpg", "jpeg",
                            "webp", "gif", "bmp"
                        )

                        if (isImage) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentPreview.file)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                                contentDescription =
                                    currentPreview.document.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 450.dp)
                            )
                        }

                        Text(
                            "Opening another app will return D.E.A.L.S. " +
                                    "to WeatherNow. The other app may retain " +
                                    "a copy, and D.E.A.L.S. privacy protections " +
                                    "do not cover it."
                        )

                        viewerError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        currentPreview.file
                                    )

                                    val mimeType =
                                        context.contentResolver.getType(uri)
                                            ?: "application/octet-stream"

                                    val intent = Intent(Intent.ACTION_VIEW)
                                        .apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                        }

                                    context.startActivity(
                                        Intent.createChooser(
                                            intent,
                                            "Open document"
                                        )
                                    )

                                    externallyOpenedFiles.add(
                                        currentPreview.file
                                    )
                                    onQuickExit()
                                } catch (_: ActivityNotFoundException) {
                                    viewerError =
                                        "No app is available for this file type."
                                } catch (_: SecurityException) {
                                    viewerError =
                                        "This device could not open the file."
                                } catch (_: IllegalArgumentException) {
                                    viewerError =
                                        "The document viewer's file access " +
                                                "configuration needs updating."
                                }
                            }
                        ) {
                            Text("Continue to external viewer")
                        }

                        TextButton(onClick = { closePreview() }) {
                            Text("Close preview")
                        }
                    }

                    sharingDocument != null -> {
                        Text(
                            "Share ${sharingDocument.name}",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            "Choose who can view this document. " +
                                    "Your other documents remain private."
                        )

                        Text(
                            "Removing access stops future downloads through " +
                                    "the app. It cannot remove copies a recipient " +
                                    "has already saved."
                        )

                        when {
                            viewModel.isLoadingPermissions -> {
                                CircularProgressIndicator()
                                Text("Loading sharing permissions…")
                            }

                            viewModel.permissionsError != null -> {
                                Text(
                                    viewModel.permissionsError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error
                                )

                                TextButton(
                                    enabled =
                                        viewModel.changingRecipientUid == null,
                                    onClick = {
                                        viewModel.loadSelectedDocumentPermissions()
                                    }
                                ) {
                                    Text("Retry")
                                }
                            }

                            viewModel.recipients.isEmpty() -> {
                                Text(
                                    "No recipients are available. Link a trusted contact " +
                                            "to an app account or approve a Support connection first."
                                )
                            }

                            else -> {
                                viewModel.recipients.forEach { recipient ->
                                    key(recipient.uid) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement =
                                                    Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    recipient.name,
                                                    style = MaterialTheme
                                                        .typography.titleMedium
                                                )

                                                val isShared =
                                                    recipient.uid in
                                                            viewModel.sharedRecipientIds

                                                Text(
                                                    when {
                                                        isShared &&
                                                                recipient.uid in viewModel.expiredSupportRecipientIds ->
                                                            "Access expired. Remove access, then share again " +
                                                                    "to authorize the new connection."

                                                        isShared -> "Sharing permission saved"
                                                        else -> "Not shared"
                                                    }
                                                )

                                                OutlinedButton(
                                                    enabled =
                                                        viewModel.changingRecipientUid ==
                                                                null,
                                                    onClick = {
                                                        viewModel.setSharing(
                                                            recipient,
                                                            !isShared
                                                        )
                                                    }
                                                ) {
                                                    Text(
                                                        when {
                                                            viewModel.changingRecipientUid ==
                                                                    recipient.uid ->
                                                                "Saving…"

                                                            isShared ->
                                                                "Remove access"

                                                            else ->
                                                                "Share document"
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        viewModel.successMessage?.let {
                            Text(it)
                        }

                        TextButton(
                            enabled =
                                viewModel.changingRecipientUid == null,
                            onClick = { viewModel.closeSharing() }
                        ) {
                            Text("Done")
                        }
                    }

                    else -> {
                        Text(
                            "Upload a document, then choose which trusted contacts " +
                                    "or approved Support accounts can view it."
                        )

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Upload document",
                                    style = MaterialTheme.typography.titleLarge
                                )

                                Box {
                                    OutlinedButton(
                                        enabled = !viewModel.isUploading,
                                        onClick = { categoryExpanded = true }
                                    ) {
                                        Text(category)
                                    }

                                    DropdownMenu(
                                        expanded = categoryExpanded,
                                        onDismissRequest = {
                                            categoryExpanded = false
                                        }
                                    ) {
                                        sharingDocumentTypes.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type) },
                                                onClick = {
                                                    category = type
                                                    categoryExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Text("Maximum file size: 20 MB.")

                                Button(
                                    enabled = !viewModel.isUploading,
                                    onClick = {
                                        viewerError = null
                                        externalInteraction(true)

                                        try {
                                            picker.launch("*/*")
                                        } catch (_: Exception) {
                                            externalInteraction(false)
                                            viewerError =
                                                "The file picker could not open."
                                        }
                                    }
                                ) {
                                    Text(
                                        if (viewModel.isUploading) {
                                            "Uploading…"
                                        } else {
                                            "Select file"
                                        }
                                    )
                                }
                            }
                        }

                        viewerError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        viewModel.errorMessage?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        viewModel.successMessage?.let {
                            Text(it)
                        }

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                enabled = !viewModel.isLoading,
                                onClick = { viewModel.refresh() }
                            ) {
                                Text("Refresh")
                            }

                            TextButton(onClick = onEarlierUploadsClick) {
                                Text("Earlier uploads")
                            }
                        }

                        if (viewModel.isLoading) {
                            CircularProgressIndicator()
                            Text("Loading documents…")
                        } else {
                            Text(
                                "Your documents",
                                style = MaterialTheme.typography.titleLarge
                            )

                            if (viewModel.ownDocuments.isEmpty()) {
                                Text(
                                    "No documents uploaded through this " +
                                            "sharing screen yet."
                                )
                            }

                            viewModel.ownDocuments.forEach { document ->
                                key("own-${document.id}") {
                                    SharingDocumentCard(
                                        document = document,
                                        opening =
                                            viewModel.openingDocumentId ==
                                                    document.id,
                                        canOpen =
                                            viewModel.openingDocumentId == null,
                                        onOpen = { openDocument(document) },
                                        onShare = {
                                            viewModel.selectDocumentForSharing(
                                                document
                                            )
                                        }
                                    )
                                }
                            }

                            HorizontalDivider()

                            Text(
                                "Shared with me",
                                style = MaterialTheme.typography.titleLarge
                            )

                            if (viewModel.sharedDocuments.isEmpty()) {
                                Text("No documents are currently shared with you.")
                            }

                            viewModel.sharedDocuments.forEach { document ->
                                key("shared-${document.id}") {
                                    SharingDocumentCard(
                                        document = document,
                                        opening =
                                            viewModel.openingDocumentId ==
                                                    document.id,
                                        canOpen =
                                            viewModel.openingDocumentId == null,
                                        onOpen = { openDocument(document) },
                                        onShare = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharingDocumentCard(
    document: UserDocument,
    opening: Boolean,
    canOpen: Boolean,
    onOpen: () -> Unit,
    onShare: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                document.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(document.type)

            OutlinedButton(
                enabled = canOpen,
                onClick = onOpen
            ) {
                Text(if (opening) "Opening…" else "View document")
            }

            if (onShare != null) {
                Button(onClick = onShare) {
                    Text("Manage sharing")
                }
            }
        }
    }
}