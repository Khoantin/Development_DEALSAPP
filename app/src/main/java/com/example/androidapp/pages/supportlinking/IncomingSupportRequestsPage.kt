
package com.example.androidapp.pages.supportlinking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import com.example.androidapp.data.SupportLink

@Composable
fun IncomingSupportRequestsPage(
    viewModel: IncomingSupportRequestsViewModel,
    onBackClick: () -> Unit,
    onQuickExit: () -> Unit
) {
    var connectionToRemove by remember {
        mutableStateOf<SupportLink?>(null)
    }
    BackHandler(onBack = onBackClick)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBackClick) {
                Text("Back")
            }

            TextButton(onClick = onQuickExit) {
                Text("Quick Exit")
            }
        }

        Text(
            text = "Support Requests",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Review trusted support connections. " +
                    "Only approve someone you recognize and trust.",
            style = MaterialTheme.typography.bodyMedium
        )

        viewModel.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        viewModel.successMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        if (viewModel.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        } else {

            val pending = viewModel.requests.filter {
                it.status == "pending"
            }

            val accepted = viewModel.requests.filter {
                it.status == "accepted"
            }

            Text(
                text = "Pending Requests",
                style = MaterialTheme.typography.titleLarge
            )

            if (pending.isEmpty()) {
                Text("No pending requests.")
            }

            pending.forEach { link ->

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            "Support Account",
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "ID: ${link.supportId}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "This account wants to connect with you."
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.respond(link.id, true)
                                },
                                enabled = !viewModel.isSubmitting
                            ) {
                                Text("Accept")
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.respond(link.id, false)
                                },
                                enabled = !viewModel.isSubmitting
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Approved Connections",
                style = MaterialTheme.typography.titleLarge
            )

            if (accepted.isEmpty()) {
                Text("No approved connections yet.")
            }

            accepted.forEach { link ->

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Connected Support Account",
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "ID: ${link.supportId}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "Status: Approved",
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = {
                                connectionToRemove = link
                            },
                            enabled = viewModel.removingLinkId == null
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }
    }

    connectionToRemove?.let { connection ->
        AlertDialog(
            onDismissRequest = {
                if (viewModel.removingLinkId == null) {
                    connectionToRemove = null
                }
            },
            title = {
                Text("Disconnect Support Account?")
            },
            text = {
                Text(
                    "This support account will no longer " +
                            "be linked to you."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = viewModel.removingLinkId == null,
                    onClick = {
                        viewModel.removeConnection(connection.id)
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = viewModel.removingLinkId == null,
                    onClick = {
                        connectionToRemove = null
                    }
                ) {
                    Text("Keep Connection")
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
            viewModel.successMessage ==
            "Support connection removed."
        ) {
            connectionToRemove = null
        }
    }

}

