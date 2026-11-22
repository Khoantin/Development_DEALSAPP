package com.example.androidapp.pages.supportlinking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.SupportLink

@Composable
fun SupportLinkingPage(
    viewModel: SupportLinkingViewModel,
    onBackClick: () -> Unit,
    onQuickExit: () -> Unit
) {
    var connectionToRemove by remember {
        mutableStateOf<SupportLink?>(null)
    }

    BackHandler(onBack = onBackClick)

    val accepted = viewModel.connections.filter {
        it.status == "accepted"
    }

    val pending = viewModel.connections.filter {
        it.status == "pending"
    }

    val rejected = viewModel.connections.filter {
        it.status == "rejected"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 96.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Back")
        }

        Text(
            text = "Linked Users",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Manage your support connections " +
                    "and send connection requests.",
            style = MaterialTheme.typography.bodyMedium
        )

        // ACCEPTED CONNECTIONS

        Text(
            text = "Connected Users (${accepted.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (viewModel.isLoadingConnections) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            viewModel.connectionsError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (accepted.isEmpty()) {
                Text("No accepted connections yet.")
            }

            accepted.forEach { connection ->
                SupportConnectionCard(
                    connection = connection,
                    statusLabel = "Connected",
                    onRemoveClick = {
                        connectionToRemove = connection
                    }
                )
            }
        }

        HorizontalDivider()

        // PENDING CONNECTION REQUESTS

        Text(
            text = "Pending Requests (${pending.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (!viewModel.isLoadingConnections) {
            if (pending.isEmpty()) {
                Text("No pending requests.")
            }

            pending.forEach { connection ->
                SupportConnectionCard(
                    connection = connection,
                    statusLabel = "Awaiting User Approval",
                    onRemoveClick = {
                        connectionToRemove = connection
                    }
                )
            }
        }

        HorizontalDivider()

        // SEND A REQUEST USING A PAIRING CODE

        Text(
            text = "Connect with a User",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Enter the 6-digit pairing code shared by " +
                    "the person you want to support. They must " +
                    "approve your request before you are connected.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = viewModel.pairingCode,
            onValueChange = viewModel::updatePairingCode,
            enabled = !viewModel.isSubmitting && !viewModel.needsRetry,
            label = {
                Text("Pairing Code")
            },
            placeholder = {
                Text("Enter 6-digit code")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${viewModel.pairingCode.length}/6 digits",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        viewModel.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        viewModel.successMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = viewModel::sendLinkRequest,
            enabled = viewModel.pairingCode.length == 6 &&
                    !viewModel.isSubmitting &&
                    viewModel.removingLinkId == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    viewModel.isSubmitting -> "Sending..."
                    viewModel.needsRetry -> "Retry Same Request"
                    else -> "Send Request"
                }
            )
        }

        if (viewModel.isSubmitting) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = if (viewModel.needsRetry) {
                "The result was not confirmed. Retry the same request " +
                        "before entering another code."
            } else {
                "Sending a request does not grant access. " +
                        "The User must approve it first."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // REJECTED REQUESTS

        if (rejected.isNotEmpty()) {
            HorizontalDivider()

            Text(
                text = "Declined Requests",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            rejected.forEach { connection ->
                SupportConnectionCard(
                    connection = connection,
                    statusLabel = "Declined"
                )

                OutlinedButton(
                    onClick = {
                        connectionToRemove = connection
                    },
                    enabled = viewModel.removingLinkId == null &&
                            !viewModel.isSubmitting &&
                            !viewModel.needsRetry
                ) {
                    Text("Remove Declined Request")
                }
            }
        }
    }

    // REMOVE CONNECTION CONFIRMATION

    connectionToRemove?.let { connection ->
        val isPending = connection.status == "pending"
        val isRejected = connection.status == "rejected"

        AlertDialog(
            onDismissRequest = {
                if (viewModel.removingLinkId == null) {
                    connectionToRemove = null
                }
            },
            title = {
                Text(
                    when {
                        isRejected -> "Remove Declined Request?"
                        isPending -> "Cancel Request?"
                        else -> "Disconnect User?"
                    }
                )
            },
            text = {
                Text(
                    when {
                        isRejected ->
                            "Remove this declined request? Any new request " +
                                    "will still require the User's approval."

                        isPending ->
                            "Cancel this pending connection request?"

                        else ->
                            "Disconnect this user? They will no longer " +
                                    "appear in your linked users list."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = viewModel.removingLinkId == null &&
                            !viewModel.isSubmitting &&
                            !viewModel.needsRetry,
                    onClick = {
                        viewModel.removeConnection(connection.id)
                    }
                ) {
                    Text(
                        when {
                            viewModel.removingLinkId == connection.id ->
                                "Removing..."

                            isRejected -> "Remove"
                            isPending -> "Cancel Request"
                            else -> "Disconnect"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = viewModel.removingLinkId == null,
                    onClick = {
                        connectionToRemove = null
                    }
                ) {
                    Text("Go Back")
                }
            }
        )
    }

    LaunchedEffect(
        connectionToRemove?.id,
        viewModel.removingLinkId,
        viewModel.successMessage
    ) {
        if (
            connectionToRemove != null &&
            viewModel.removingLinkId == null &&
            viewModel.successMessage == "Connection removed successfully."
        ) {
            connectionToRemove = null
        }
    }
}

@Composable
private fun SupportConnectionCard(
    connection: SupportLink,
    statusLabel: String,
    onRemoveClick: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Normal User",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "User ID: ${connection.userId}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Status: $statusLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (onRemoveClick != null) {
                OutlinedButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (connection.status == "pending") {
                            "Cancel Request"
                        } else {
                            "Disconnect"
                        }
                    )
                }
            }
        }
    }
}