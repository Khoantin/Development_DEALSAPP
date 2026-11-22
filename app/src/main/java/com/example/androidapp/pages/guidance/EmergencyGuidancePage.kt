package com.example.androidapp.pages.guidance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun EmergencyGuidancePage(
    viewModel: EmergencyGuidanceViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(viewModel) {
        viewModel.start(asAdmin = false)
    }

    key(viewModel.state.sessionVersion) {
        PublishedGuidanceContent(viewModel, onBack)
    }
}

@Composable
private fun PublishedGuidanceContent(
    viewModel: EmergencyGuidanceViewModel,
    onBack: () -> Unit
) {
    val state = viewModel.state
    var selectedId by remember { mutableStateOf<String?>(null) }

    // Resolve from the latest response rather than keeping an old copy.
    val selected = state.guides.find { it.id == selectedId }

    val handleBack: () -> Unit = {
        if (selectedId != null) {
            selectedId = null
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 104.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = handleBack,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(if (selectedId == null) "Back" else "Back to guides")
        }

        Text(
            "Emergency response guidance",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "Read published guidance and fictional practice examples. " +
                    "Opening this page does not send an emergency alert " +
                    "or contact anyone."
        )

        OutlinedButton(
            enabled = !state.isLoading,
            onClick = { viewModel.refresh() }
        ) {
            Text("Refresh")
        }

        when {
            state.isLoading -> {
                CircularProgressIndicator()
            }

            state.error != null -> {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error
                )
            }

            selectedId != null -> {
                if (selected == null) {
                    Text(
                        "This guide is no longer available to your account. " +
                                "Return to the guide list."
                    )
                } else {
                    selected.lastReviewedAtMillis?.let {
                        Text(
                            "Last reviewed: " +
                                    DateFormat.getDateTimeInstance(
                                        DateFormat.MEDIUM,
                                        DateFormat.SHORT
                                    ).format(Date(it)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    GuidancePreview(selected.content)
                }
            }

            state.guides.isEmpty() -> {
                Text("No published guides are available for your account yet.")
            }

            else -> {
                state.guides.forEach { guide ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                guide.content.title,
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text(guide.content.summary)

                            Button(onClick = { selectedId = guide.id }) {
                                Text("Read guide")
                            }
                        }
                    }
                }
            }
        }
    }
}