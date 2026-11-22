package com.example.androidapp.pages.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.androidapp.ui.theme.AndroidAppTheme

@Composable
fun PasscodeScreenWithViewModel(
    viewModel: WeatherAppViewModel
) {
    val passcode by viewModel.passcode.collectAsState()
    val passcodeError by viewModel.passcodeError.collectAsState()

    PasscodeScreen(
        passcode = passcode,
        passcodeError = passcodeError,

        onNumberClick = { digit ->
            viewModel.addPasscodeDigit(digit)
        },

        onBackspaceClick = {
            viewModel.removePasscodeDigit()
        },

        onConfirmClick = {
            viewModel.checkPasscode()
        }
    )
}

@Composable
fun PasscodeScreen(
    passcode: String,
    passcodeError: Boolean,
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirmClick: () -> Unit,
    title: String = "Enter your passcode",
    subtitle: String = "Enter your passcode to continue",
    errorText: String = "Incorrect passcode"
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = 32.dp,
                vertical = 48.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        PasscodeDots(
            enteredDigits = passcode.length,
            modifier = Modifier.padding(top = 24.dp)
        )

        if (passcodeError) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(
            modifier = Modifier.weight(1f)
        )

        NumberPad(
            onNumberClick = onNumberClick,
            onBackspaceClick = onBackspaceClick,
            onConfirmClick = onConfirmClick
        )
    }
}

@Composable
fun PasscodeDots(
    enteredDigits: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(PASSCODE_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < enteredDigits) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}

@Composable
fun NumberPad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        NumberPadRow(
            numbers = listOf("1", "2", "3"),
            onNumberClick = onNumberClick
        )

        NumberPadRow(
            numbers = listOf("4", "5", "6"),
            onNumberClick = onNumberClick
        )

        NumberPadRow(
            numbers = listOf("7", "8", "9"),
            onNumberClick = onNumberClick
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            KeypadIconButton(
                onClick = onBackspaceClick
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete last digit"
                )
            }

            NumberButton(
                number = "0",
                onClick = {
                    onNumberClick("0")
                }
            )

            KeypadIconButton(
                onClick = onConfirmClick
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Confirm passcode"
                )
            }
        }
    }
}

@Composable
fun NumberPadRow(
    numbers: List<String>,
    onNumberClick: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        numbers.forEach { number ->
            NumberButton(
                number = number,
                onClick = {
                    onNumberClick(number)
                }
            )
        }
    }
}

@Composable
fun NumberButton(
    number: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun KeypadIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
    ) {
        content()
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun PasscodeScreenPreview() {
    AndroidAppTheme {
        PasscodeScreen(
            passcode = "12",
            passcodeError = false,
            onNumberClick = {},
            onBackspaceClick = {},
            onConfirmClick = {}
        )
    }
}