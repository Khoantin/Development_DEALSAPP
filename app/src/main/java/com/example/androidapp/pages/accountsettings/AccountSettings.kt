package com.example.androidapp.pages.accountsettings
import com.example.androidapp.security.trackPrivacyInteraction
import android.widget.Toast
import com.example.androidapp.security.PrivacyDialogFocus

import android.net.Uri
import android.util.Patterns
import android.widget.Space
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.pages.quickexit.ExitButtonViewModel
import com.example.androidapp.repository.AccountInformation
import com.example.androidapp.repository.UserDocument
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.utils.getFileNameFromUri
import java.util.UUID
import com.example.androidapp.pages.weather.PASSCODE_LENGTH
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf


// ---------------------------------------------------------
// ACCOUNT SETTINGS PAGE
// ---------------------------------------------------------

@Composable
fun AccountSettingsPage(
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel,
    exitButtonViewModel: ExitButtonViewModel,
    onChangePasscodeClick: () -> Unit,
    onAccountInfoClick: () -> Unit,
    onUploadedDocumentsClick: () -> Unit,
    onBackButtonClick: () -> Unit,
    onQuickExit: () -> Unit,
    onLogoutClick: () -> Unit = {},
    onDocumentPickerActiveChange: (Boolean) -> Unit = {},
    onVoiceSafetyClick: () -> Unit = {}
) {

    val settings = viewModel.settings


    var showAccountInfoDialog by remember {
        mutableStateOf(false)
    }


    var showChangePasscodeDialog by remember {
        mutableStateOf(false)
    }


    var showUploadedDocumentsDialog by remember {
        mutableStateOf(false)
    }


    var showFailedAttemptsDialog by remember {
        mutableStateOf(false)
    }

    var showPairingCodeDialog by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }
    // ---------------------------------------------------------
    // QUICK EXIT
    // ---------------------------------------------------------

    LaunchedEffect(
        exitButtonViewModel.quickExitRequested
    ) {

        if (exitButtonViewModel.quickExitRequested) {

            onQuickExit()

            exitButtonViewModel.clearQuickExitRequest()
        }
    }


    // ---------------------------------------------------------
    // MAIN SETTINGS PAGE
    // ---------------------------------------------------------



    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {

        Spacer(Modifier.height(48.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {

            Spacer(
                modifier = Modifier.height(16.dp)
            )


            // -------------------------------------------------
            // HEADER
            // -------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceAround,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                BackButton(
                    onClick = {
                        onBackButtonClick()
                    }
                )


                Spacer(
                    modifier = Modifier.weight(1f)
                )


                Text(
                    text = "Settings",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }


            Spacer(
                modifier = Modifier.height(24.dp)
            )


            // -------------------------------------------------
            // ACCOUNT
            // -------------------------------------------------

            SettingSectionHeader(
                title = "Account"
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            SettingRowItem(
                title = "Account Information",
                trailing = ">",

                onClick = {

                    showAccountInfoDialog = true

                    onAccountInfoClick()
                }
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            SettingRowItem(
                title = "Change Passcode",
                trailing = ">",

                onClick = {

                    showChangePasscodeDialog = true

                    onChangePasscodeClick()
                }
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            SettingRowItem(
                title = "Uploaded Personal Documents",
                trailing = ">",

                onClick = {

                    // Refreshes the repository-backed document list.
                    // Khoan can later connect the repository to Firebase.
                    viewModel.refreshUploadedDocuments()

                    showUploadedDocumentsDialog = true

                    onUploadedDocumentsClick()
                }
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            SettingRowItem(
                title = "Failed Passcode Attempts",
                trailing = ">",

                onClick = {
                    viewModel.refreshFailedAttempts()
                    showFailedAttemptsDialog = true
                }
            )


            Spacer(modifier = Modifier.height(12.dp))

            SettingRowItem(
                title = "Support Pairing Code",
                trailing = ">",
                onClick = { showPairingCodeDialog = true }
            )


            Spacer(
                modifier = Modifier.height(48.dp)
            )


            // -------------------------------------------------
            // PRIVACY & LOCATION
            // -------------------------------------------------

            SettingSectionHeader(
                title = "Privacy & Location"
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            SettingToggleRowItem(
                title = "Location Sharing Enabled",

                isToggled =
                    settings.locationSharingEnabled,

                switched = { newValue ->

                    viewModel.updateLocationSharing(
                        newValue
                    )
                }
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            SettingToggleRowItem(
                title = "Disguised App Toggle",

                isToggled =
                    settings.disguiseEnabled,

                switched = { newValue ->

                    viewModel.updateDisguiseMode(
                        newValue
                    )
                }
            )


            Spacer(
                modifier = Modifier.height(48.dp)
            )


            SettingToggleRowItem(
                title = "Extreme Privacy",
                isToggled = settings.extremePrivacyEnabled,
                switched = { enabled ->
                    viewModel.updateExtremePrivacy(enabled)
                }
            )
            androidx.compose.material3.TextButton(
                onClick = onVoiceSafetyClick
            ) {
                androidx.compose.material3.Text(
                    "Voice Safety — Configure and Test"
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // -------------------------------------------------
            // NOTIFICATIONS
            // -------------------------------------------------

            SettingSectionHeader(
                title = "Notifications"
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            SettingToggleRowItem(
                title = "Email Notifications",

                isToggled =
                    settings.emailNotificationsEnabled,

                switched = { newValue ->

                    viewModel.updateEmailNotifications(
                        newValue
                    )
                }
            )
            SettingRowItem(
                title = "Log Out",
                trailing = ">",
                onClick = onLogoutClick
            )

            Spacer(modifier = Modifier.height(32.dp))

            Spacer(
                modifier = Modifier.height(32.dp)
            )
        }


    }


    // ---------------------------------------------------------
    // ACCOUNT INFORMATION POPUP
    // ---------------------------------------------------------

    if (showAccountInfoDialog) {

        AccountInformationDialog(

            accountInformation =
                settings.accountInformation,

            onDismiss = {

                showAccountInfoDialog = false
            },

            onSave = {
                    firstName,
                    lastName,
                    email,
                    phoneNumber,
                    address ->

                viewModel.updateAccountInformation(

                    firstName = firstName,

                    lastName = lastName,

                    email = email,

                    phoneNumber = phoneNumber,

                    address = address
                )


                showAccountInfoDialog = false
            }
        )
    }


    // ---------------------------------------------------------
    // CHANGE PASSCODE POPUP
    // ---------------------------------------------------------

    if (showChangePasscodeDialog) {

        ChangePasscodeDialog(

            onDismiss = {

                showChangePasscodeDialog = false
            },

            onSave = { newPasscode ->

                viewModel.changeDisguisePasscode(
                    newPasscode
                )

                showChangePasscodeDialog = false
            }
        )
    }


    // ---------------------------------------------------------
    // UPLOADED DOCUMENTS POPUP
    // ---------------------------------------------------------

    if (showUploadedDocumentsDialog) {

        UploadedDocumentsDialog(
            onDocumentPickerActiveChange = onDocumentPickerActiveChange,

            documents =
                settings.uploadedDocuments,

            onAddDocument = { doc ->
                viewModel.addUploadedDocument(doc)
            },

            onDismiss = {

                showUploadedDocumentsDialog = false
            }
        )
    }


    // ---------------------------------------------------------
    // FAILED PASSCODE ATTEMPTS POPUP
    // ---------------------------------------------------------

    if (showFailedAttemptsDialog) {
        FailedPasscodeAttemptsDialog(
            attempts = settings.failedPasscodeAttempts,
            onDismiss = {
                showFailedAttemptsDialog = false
            }
        )
    }



    if (showPairingCodeDialog) {
        var currentTime by remember {
            mutableLongStateOf(System.currentTimeMillis())
        }

        LaunchedEffect(Unit) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000L)
            }
        }

        val code = viewModel.pairingCode
        val expiresAt = viewModel.pairingCodeExpiresAt
        val generating = viewModel.isGeneratingPairingCode
        val generationError = viewModel.pairingCodeError

        val remainingMillis = expiresAt?.let {
            (it - currentTime).coerceAtLeast(0L)
        } ?: 0L

        val isExpired = expiresAt != null && remainingMillis == 0L
        val remainingSeconds = (remainingMillis + 999L) / 1000L
        val minutes = remainingSeconds / 60L
        val seconds = remainingSeconds % 60L

        AlertDialog(
            modifier = Modifier.trackPrivacyInteraction(),
            onDismissRequest = {
                showPairingCodeDialog = false
            },
            title = {
                Text("Support Pairing Code")
            },
            text = {
                PrivacyDialogFocus()

                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Share this code with the Support account " +
                                "you want to connect with. You must approve " +
                                "their request before the connection is accepted."
                    )

                    when {
                        generating -> {
                            CircularProgressIndicator()
                            Text("Generating your code...")
                        }

                        code != null &&
                                expiresAt != null &&
                                !isExpired -> {
                            Text(
                                text = code,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "Expires in %02d:%02d".format(
                                    minutes,
                                    seconds
                                )
                            )

                            Text(
                                "The code can be used once. " +
                                        "This display does not update when " +
                                        "someone uses it.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        isExpired -> {
                            Text("Your pairing code has expired.")
                        }

                        generationError == null -> {
                            Text("Tap Generate Code to get started.")
                        }
                    }

                    generationError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        "Codes expire after 10 minutes. Generating a " +
                                "new code replaces your previous code. " +
                                "Wait at least 30 seconds between generations.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        "Closing this dialog does not cancel the code " +
                                "or a pending connection request.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !generating,
                    onClick = {
                        currentTime = System.currentTimeMillis()
                        viewModel.generatePairingCode()
                    }
                ) {
                    Text(
                        when {
                            generating -> "Generating..."
                            code != null -> "Generate New Code"
                            else -> "Generate Code"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPairingCodeDialog = false
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }


}


// ---------------------------------------------------------
// ACCOUNT INFORMATION DIALOG
// ---------------------------------------------------------

@Composable
fun AccountInformationDialog(
    accountInformation: AccountInformation,
    onDismiss: () -> Unit,
    onSave: (
        String,
        String,
        String,
        String,
        String
    ) -> Unit
) {

    var firstName by remember(accountInformation.firstName) {
        mutableStateOf(accountInformation.firstName)
    }

    var lastName by remember(accountInformation.lastName) {
        mutableStateOf(accountInformation.lastName)
    }

    var email by remember(accountInformation.email) {
        mutableStateOf(accountInformation.email)
    }

    var phoneNumber by remember(accountInformation.phoneNumber) {
        mutableStateOf(accountInformation.phoneNumber)
    }

    var address by remember(accountInformation.address) {
        mutableStateOf(accountInformation.address)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }


    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Account Information",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },

        text = {
            PrivacyDialogFocus()

            Column {

                OutlinedTextField(
                    value = firstName,
                    onValueChange = {
                        firstName = it
                        errorMessage = ""
                    },
                    label = {
                        Text("First Name")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                OutlinedTextField(
                    value = lastName,
                    onValueChange = {
                        lastName = it
                        errorMessage = ""
                    },
                    label = {
                        Text("Last Name")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        errorMessage = ""
                    },
                    label = {
                        Text("Phone Number")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        errorMessage = ""
                    },
                    label = {
                        Text("Address")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )


                if (errorMessage.isNotEmpty()) {

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick = {

                    when {

                        firstName.isBlank() -> {
                            errorMessage =
                                "First name cannot be empty."
                        }

                        lastName.isBlank() -> {
                            errorMessage =
                                "Last name cannot be empty."
                        }

                        email.isBlank() -> {
                            errorMessage =
                                "Email cannot be empty."
                        }

                        !Patterns.EMAIL_ADDRESS.matcher(
                            email.trim()
                        ).matches() -> {
                            errorMessage =
                                "Please enter a valid email address."
                        }

                        else -> {

                            onSave(
                                firstName.trim(),
                                lastName.trim(),
                                email.trim(),
                                phoneNumber.trim(),
                                address.trim()
                            )
                        }
                    }
                }
            ) {

                Text(
                    text = "Save",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    )
}


// ---------------------------------------------------------
// CHANGE PASSCODE DIALOG
// ---------------------------------------------------------

@Composable
fun ChangePasscodeDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {

    var newPasscode by remember {
        mutableStateOf("")
    }


    var confirmPasscode by remember {
        mutableStateOf("")
    }


    var errorMessage by remember {
        mutableStateOf("")
    }


    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),

        onDismissRequest = onDismiss,


        title = {

            Text(
                text = "Change Passcode",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },


        text = {
            PrivacyDialogFocus()

            Column {

                Text(
                    text =
                        "Enter a new passcode for the disguised application.",

                    fontSize = 14.sp,

                    color =
                        MaterialTheme.colorScheme.tertiary
                )


                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )


                OutlinedTextField(

                    value = newPasscode,

                    onValueChange = {

                        newPasscode = it

                        errorMessage = ""
                    },

                    label = {

                        Text("New Passcode")
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true,

                    visualTransformation =
                        PasswordVisualTransformation()
                )


                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                OutlinedTextField(

                    value = confirmPasscode,

                    onValueChange = {

                        confirmPasscode = it

                        errorMessage = ""
                    },

                    label = {

                        Text("Confirm Passcode")
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true,

                    visualTransformation =
                        PasswordVisualTransformation()
                )


                if (errorMessage.isNotEmpty()) {

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )


                    Text(
                        text = errorMessage,

                        fontSize = 14.sp,

                        color =
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        },


        confirmButton = {

            TextButton(

                onClick = {

                    when {

                        newPasscode.isBlank() -> {

                            errorMessage =
                                "Passcode cannot be empty."
                        }


                        newPasscode.length != PASSCODE_LENGTH || !newPasscode.all { it.isDigit() } -> {
                            errorMessage = "Passcode must be exactly $PASSCODE_LENGTH digits."
                        }


                        newPasscode != confirmPasscode -> {

                            errorMessage =
                                "Passcodes do not match."
                        }


                        else -> {

                            onSave(
                                newPasscode
                            )
                        }
                    }
                }
            ) {

                Text(
                    text = "Save",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },


        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    )
}


// ---------------------------------------------------------
// UPLOADED DOCUMENTS DIALOG
// ---------------------------------------------------------

@Composable
fun UploadedDocumentsDialog(
    documents: List<UserDocument>,
    onAddDocument: (UserDocument) -> Unit = {},
    onDismiss: () -> Unit,
    onDocumentPickerActiveChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onDocumentPickerActiveChange(false)
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri) ?: "Uploaded Document"
            val extension = fileName.substringAfterLast('.', "").uppercase()
            val typeLabel = if (extension.isNotBlank() && extension != fileName) "$extension File" else "Personal Document"
            onAddDocument(
                UserDocument(
                    id = UUID.randomUUID().toString(),
                    name = fileName,
                    type = typeLabel,
                    storagePath = uri.toString()
                )
            )
        }
    }

    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),

        onDismissRequest = onDismiss,


        title = {

            Text(
                text = "Uploaded Personal Documents",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },


        text = {
            PrivacyDialogFocus()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {

                Button(
                    onClick = {
                        onDocumentPickerActiveChange(true)
                        try {
                            filePickerLauncher.launch("*/*")
                        } catch (exception: RuntimeException) {
                            onDocumentPickerActiveChange(false)
                            Toast.makeText(
                                context,
                                "Unable to open the file picker. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "+ Upload New Document",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (documents.isEmpty()) {

                    Text(
                        text =
                            "No personal documents have been uploaded yet.",

                        fontSize = 15.sp,

                        color =
                            MaterialTheme.colorScheme.tertiary
                    )

                } else {

                    documents.forEach { document ->

                        UploadedDocumentItem(
                            document = document
                        )


                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )
                    }
                }
            }
        },


        confirmButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "Close",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}


// ---------------------------------------------------------
// UPLOADED DOCUMENT ITEM
// ---------------------------------------------------------

@Composable
fun UploadedDocumentItem(
    document: UserDocument
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            MaterialTheme.shapes.medium,

        color =
            MaterialTheme.colorScheme.surfaceVariant
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp)
        ) {

            Text(
                text =
                    if (document.name.isNotBlank()) {
                        document.name
                    } else {
                        "Uploaded Document"
                    },

                fontSize = 17.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    MaterialTheme.colorScheme.primary
            )


            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )


            Text(
                text =
                    if (document.type.isNotBlank()) {
                        "Type: ${document.type}"
                    } else {
                        "Type: Document"
                    },

                fontSize = 14.sp,

                color =
                    MaterialTheme.colorScheme.tertiary
            )
        }
    }
}


// ---------------------------------------------------------
// FAILED PASSCODE ATTEMPTS DIALOG
// ---------------------------------------------------------

@Composable
fun FailedPasscodeAttemptsDialog(
    attempts: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Failed Passcode Attempts",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            PrivacyDialogFocus()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (attempts.isEmpty()) {
                    Text(
                        text = "No failed passcode attempts recorded.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    attempts.forEach { timestamp ->
                        FailedPasscodeAttemptItem(timestamp = timestamp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}


// ---------------------------------------------------------
// FAILED PASSCODE ATTEMPT ITEM
// ---------------------------------------------------------

@Composable
fun FailedPasscodeAttemptItem(
    timestamp: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Failed Attempt",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Time: $timestamp",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}


// ---------------------------------------------------------
// PREVIEW
// ---------------------------------------------------------

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun AccountSettingsPagePreview() {

    AndroidAppTheme {

        AccountSettingsPage(

            viewModel = remember {
                AccountSettingsViewModel()
            },

            exitButtonViewModel = remember {
                ExitButtonViewModel()
            },

            onUploadedDocumentsClick = {},

            onChangePasscodeClick = {},

            onAccountInfoClick = {},

            onBackButtonClick = {},

            onQuickExit = {}
        )
    }
}


// ---------------------------------------------------------
// BACK BUTTON
// ---------------------------------------------------------

@Composable
fun BackButton(
    onClick: () -> Unit
) {
    Box(Modifier
        .width(32.dp)
        .clickable {
            onClick()
        }
    )
    {
        Text(
            text = "<",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,

            modifier = Modifier
                .align(Alignment.Center)
        )
    }

}


// ---------------------------------------------------------
// SECTION HEADER
// ---------------------------------------------------------

@Composable
fun SettingSectionHeader(
    title: String
) {

    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}


// ---------------------------------------------------------
// SETTING ROW
// ---------------------------------------------------------

@Composable
fun SettingRowItem(
    title: String,
    trailing: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(5.dp),

        horizontalArrangement =
            Arrangement.SpaceBetween,

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = title,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )


        Text(
            text = trailing,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}


// ---------------------------------------------------------
// TOGGLE ROW
// ---------------------------------------------------------

@Composable
fun SettingToggleRowItem(
    title: String,
    modifier: Modifier = Modifier,
    isToggled: Boolean = false,
    switched: (Boolean) -> Unit
) {

    Row(
        modifier = modifier
            .fillMaxWidth(),

        horizontalArrangement =
            Arrangement.SpaceBetween,

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = title,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )


        Switch(
            checked = isToggled,
            onCheckedChange = switched
        )
    }
}


// ---------------------------------------------------------
// EMERGENCY EXIT CONTAINER
// ---------------------------------------------------------

@Composable
fun EmergencyExitContainer(
    modifier: Modifier = Modifier
) {

    Surface(
        modifier =
            modifier.fillMaxWidth(),

        color =
            Color(0xFFD9D9D9)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 12.dp
                ),

            horizontalArrangement =
                Arrangement.SpaceEvenly,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            EmergencyExitLabel(
                label = "Exit"
            )
        }
    }
}


// ---------------------------------------------------------
// EMERGENCY EXIT LABEL
// ---------------------------------------------------------

@Composable
fun EmergencyExitLabel(
    label: String,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier,

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Box {}


        Spacer(
            modifier =
                Modifier.height(4.dp)
        )


        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Black
        )
    }
}
