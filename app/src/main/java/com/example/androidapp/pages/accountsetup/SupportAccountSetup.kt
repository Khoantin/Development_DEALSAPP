package com.example.androidapp.pages.accountsetup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.ui.theme.AndroidAppTheme

@Composable
fun SupportLinkUserStep(
    userId: String,
    onUserIdChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onFinishClick: (requestedUserId: String?) -> Unit,
    isSubmitting: Boolean = false,
    errorMessage: String? = null
) {
    BackHandler {
        if (!isSubmitting) onBackClick()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AccountSetupBackButton {
            if (!isSubmitting) onBackClick()
        }

        Text(
            text = "Create Account",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Text(
            text = "Link to a User",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Optional — enter the ID of the person you want " +
                    "to support, or skip this step.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        AccountSetupTextField(
            value = userId,
            onValueChange = {
                if (!isSubmitting) onUserIdChange(it)
            },
            label = "User ID"
        )

        Text(
            text = "The user must approve your connection request " +
                    "before you can message them or view documents " +
                    "they choose to share with you. You can also " +
                    "request a connection later.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                onFinishClick(
                    userId.trim().takeIf { it.isNotEmpty() }
                )
            },
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = if (isSubmitting) {
                    "Creating account..."
                } else {
                    "Finish"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedButton(
            onClick = {
                onFinishClick(null)
            },
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Skip Linking and Finish",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun SupportLinkUserStepPreview() {
    AndroidAppTheme {
        var userId by remember {
            mutableStateOf("")
        }

        SupportLinkUserStep(
            userId = userId,
            onUserIdChange = {
                userId = it
            },
            onBackClick = {},
            onFinishClick = {}
        )
    }
}