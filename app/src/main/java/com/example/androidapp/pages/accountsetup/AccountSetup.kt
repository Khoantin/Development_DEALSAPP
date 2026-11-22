package com.example.androidapp.pages.accountsetup
import com.example.androidapp.security.trackPrivacyInteraction
import android.widget.Toast
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.androidapp.security.PrivacyDialogFocus
import com.example.androidapp.data.TrustedContact
import com.example.androidapp.repository.SettingsRepository
import com.example.androidapp.repository.UserDocument
import java.io.File
import java.util.UUID

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.utils.getFileNameFromUri
import android.util.Patterns

// ---------------------------------------------------------
// DATA CLASSES
// ---------------------------------------------------------



data class AccountSetupState(
    val accountType: String? = null,

    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",

    val uploadedDocuments: List<String> = emptyList(),

    val trustedContacts: List<TrustedContact> = emptyList()
)


// ---------------------------------------------------------
// REUSABLE BACK BUTTON
// ---------------------------------------------------------

@Composable
fun AccountSetupBackButton(
    onClick: () -> Unit
) {

    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(
            horizontal = 0.dp,
            vertical = 8.dp
        )
    ) {

        Text(
            text = "←",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


// ---------------------------------------------------------
// LANDING SCREEN
// ---------------------------------------------------------

@Composable
fun AccountLandingStep(
    onLoginClick: () -> Unit = {},
    onCreateAccountClick: () -> Unit = {}
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "D.E.A.L.S.",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Direct Emergency Access to Local Support",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {

            Text(
                text = "Log In",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCreateAccountClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {

            Text(
                text = "Create Account",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}


// ---------------------------------------------------------
// ACCOUNT TYPE SCREEN
// ---------------------------------------------------------

@Composable
fun AccountTypeStep(
    selectedAccountType: String? = null,
    onAccountTypeSelected: (String) -> Unit = {},
    onBackClick: () -> Unit = {},
    onContinueClick: () -> Unit = {}
) {

    var showError by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        AccountSetupBackButton(
            onClick = onBackClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Create Account",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = "What type of account?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        AccountTypeOption(
            title = "Normal User",
            selected = selectedAccountType == "Normal User",
            onClick = {
                onAccountTypeSelected("Normal User")
                showError = false
            }
        )

        AccountTypeOption(
            title = "Support User",
            selected = selectedAccountType == "Support User",
            onClick = {
                onAccountTypeSelected("Support User")
                showError = false
            }
        )

        AccountTypeOption(
            title = "Administrator",
            selected = selectedAccountType == "Administrator",
            onClick = {
                onAccountTypeSelected("Administrator")
                showError = false
            }
        )

        if (showError && selectedAccountType == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please select an account type",
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (selectedAccountType == null) {
                    showError = true
                } else {
                    onContinueClick()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}


// ---------------------------------------------------------
// ACCOUNT TYPE OPTION
// ---------------------------------------------------------

@Composable
fun AccountTypeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Text(
            text = title,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}


// ---------------------------------------------------------
// PERSONAL INFORMATION SCREEN
// ---------------------------------------------------------

@Composable
fun PersonalInformationStep(
    firstName: String = "",
    onFirstNameChange: (String) -> Unit = {},

    lastName: String = "",
    onLastNameChange: (String) -> Unit = {},

    email: String = "",
    onEmailChange: (String) -> Unit = {},

    phoneNumber: String = "",
    onPhoneNumberChange: (String) -> Unit = {},

    address: String = "",
    onAddressChange: (String) -> Unit = {},

    username: String = "",
    onUsernameChange: (String) -> Unit = {},

    password: String = "",
    onPasswordChange: (String) -> Unit = {},

    confirmPassword: String = "",
    onConfirmPasswordChange: (String) -> Unit = {},

    onBackClick: () -> Unit = {},
    onNextClick: () -> Unit = {}
) {

    var showErrors by remember {
        mutableStateOf(false)
    }

    val isFirstNameError = showErrors && firstName.isBlank()
    val isLastNameError = showErrors && lastName.isBlank()
    val isPhoneError = showErrors && phoneNumber.isBlank()
    val isAddressError = showErrors && address.isBlank()
    val isUsernameError = showErrors && username.isBlank()
    val isEmailError = showErrors && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val isPasswordError = showErrors && password.length < 8
    val isConfirmPasswordError = showErrors && (confirmPassword.isBlank() || confirmPassword != password)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        AccountSetupBackButton(
            onClick = onBackClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Create Account",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = {
                0.33f
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Personal Information",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        AccountSetupTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            label = "First Name *",
            isError = isFirstNameError,
            errorMessage = if (isFirstNameError) "First name cannot be empty" else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccountSetupTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = "Last Name *",
            isError = isLastNameError,
            errorMessage = if (isLastNameError) "Last name cannot be empty" else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccountSetupTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email *",
            isError = isEmailError,
            errorMessage = if (isEmailError) "Enter a valid email address" else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccountSetupTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Phone Number *",
            isError = isPhoneError,
            errorMessage = if (isPhoneError) "Phone number cannot be empty" else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccountSetupTextField(
            value = address,
            onValueChange = onAddressChange,
            label = "Address *",
            isError = isAddressError,
            errorMessage = if (isAddressError) "Address cannot be empty" else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccountSetupTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "Username *",
            isError = isUsernameError,
            errorMessage = if (isUsernameError) "Username cannot be empty" else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccountSetupTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password *",
            isPassword = true,
            isError = isPasswordError,
            errorMessage = if (isPasswordError) "Password must be at least 8 characters" else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccountSetupTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Confirm Password *",
            isPassword = true,
            isError = isConfirmPasswordError,
            errorMessage = if (isConfirmPasswordError) {
                if (confirmPassword.isBlank()) "Confirm password cannot be empty" else "Passwords do not match"
            } else null
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (firstName.isBlank() || lastName.isBlank() ||
                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ||
                    phoneNumber.isBlank() || address.isBlank() || username.isBlank() ||
                    password.length < 8 || confirmPassword != password
                ) {
                    showErrors = true
                } else {
                    onNextClick()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {

            Text(
                text = "Next",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}


// ---------------------------------------------------------
// DOCUMENTS SCREEN
// ---------------------------------------------------------

@Composable
fun DocumentsStep(
    uploadedDocuments: List<String> = emptyList(),
    onAddDocument: (String) -> Unit = {},
    onRemoveDocument: (String) -> Unit = {},
    onBackClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onDocumentPickerActiveChange: (Boolean) -> Unit = {}
) {

    val context = LocalContext.current

    var selectedDocumentType by remember {
        mutableStateOf<String?>(null)
    }

    var pendingDocumentType by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onDocumentPickerActiveChange(false)
        try {
            if (uri != null) {
                val fileName = getFileNameFromUri(context, uri) ?: "Selected File"
                val type = pendingDocumentType ?: "Document"

                try {
                    val destFile = File(context.filesDir, "doc_${System.currentTimeMillis()}_$fileName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists()) {
                        val userDoc = UserDocument(
                            id = UUID.randomUUID().toString(),
                            name = fileName,
                            type = type,
                            storagePath = destFile.absolutePath,
                            onlineUrl = null,
                            uploadedAt = System.currentTimeMillis(),
                            sharedWithSupport = false
                        )
                        SettingsRepository(context).addUploadedDocument(userDoc)
                        onAddDocument("$type|$fileName|${destFile.absolutePath}")
                    } else {
                        onAddDocument("$type: $fileName")
                    }
                } catch (_: Exception) {
                    onAddDocument("$type: $fileName")
                }
            }
        } finally {
            pendingDocumentType = null
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        AccountSetupBackButton(
            onClick = onBackClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Create Account",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = {
                0.66f
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Documents",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Optional",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(24.dp))

        DocumentUploadOption(
            title = "Upload ID",
            onClick = {
                selectedDocumentType = "ID"
            }
        )

        DocumentUploadOption(
            title = "Upload Passport",
            onClick = {
                selectedDocumentType = "Passport"
            }
        )

        DocumentUploadOption(
            title = "Additional documents",
            onClick = {
                selectedDocumentType = "Additional Document"
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Uploaded documents",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uploadedDocuments.isEmpty()) {

            Text(
                text = "No documents uploaded yet.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.tertiary
            )

        } else {

            uploadedDocuments.forEach { document ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓ $document",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveDocument(document) }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove Document",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNextClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {

            Text(
                text = "Next",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }


    if (selectedDocumentType != null) {

        DocumentUploadDialog(
            documentType = selectedDocumentType!!,

            onDismiss = {
                selectedDocumentType = null
            },

            onAddFile = {
                pendingDocumentType = selectedDocumentType
                selectedDocumentType = null
                onDocumentPickerActiveChange(true)
                try {
                    filePickerLauncher.launch("*/*")
                } catch (exception: RuntimeException) {
                    onDocumentPickerActiveChange(false)
                    pendingDocumentType = null
                    Toast.makeText(
                        context,
                        "Unable to open the file picker. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}


// ---------------------------------------------------------
// DOCUMENT UPLOAD OPTION
// ---------------------------------------------------------

@Composable
fun DocumentUploadOption(
    title: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(vertical = 14.dp)
    ) {

        Text(
            text = "+ $title",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


// ---------------------------------------------------------
// DOCUMENT UPLOAD POPUP
// ---------------------------------------------------------

@Composable
fun DocumentUploadDialog(
    documentType: String,
    onDismiss: () -> Unit,
    onAddFile: () -> Unit
) {

    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),
        onDismissRequest = onDismiss,

        title = {

            Text(
                text = "Upload $documentType",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },

        text = {
            PrivacyDialogFocus()

            Text(
                text = "Choose a file to add to your account.",
                color = MaterialTheme.colorScheme.tertiary
            )
        },

        confirmButton = {

            TextButton(
                onClick = onAddFile
            ) {

                Text(
                    text = "Add File",
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
// TRUSTED CONTACTS SCREEN
// ---------------------------------------------------------

@Composable
fun TrustedContactsStep(
    trustedContacts: List<TrustedContact> = emptyList(),
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onAddContact: (TrustedContact) -> Unit = {},
    onBackClick: () -> Unit = {},
    onFinishClick: () -> Unit = {}
) {

    var showAddContactDialog by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        AccountSetupBackButton(
            onClick = onBackClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Create Account",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = {
                1f
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Add Trusted Contacts",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Optional",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "+ Add contacts",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showAddContactDialog = true
                }
                .padding(vertical = 14.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (trustedContacts.isEmpty()) {

            Text(
                text = "No trusted contacts added yet.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.tertiary
            )

        } else {

            trustedContacts.forEach { contact ->

                TrustedContactCard(
                    contact = contact
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = onFinishClick,
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
                text = if (isSubmitting) "Creating account..." else "Finish",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }


    if (showAddContactDialog) {

        AddTrustedContactDialog(
            onDismiss = {
                showAddContactDialog = false
            },

            onAddContact = { newContact ->

                onAddContact(newContact)

                showAddContactDialog = false
            }
        )
    }
}


// ---------------------------------------------------------
// TRUSTED CONTACT CARD
// ---------------------------------------------------------

@Composable
fun TrustedContactCard(
    contact: TrustedContact
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            )
            .padding(16.dp)
    ) {

        Text(
            text = contact.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Phone Number: ${contact.phoneNumber}",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Relationship: ${contact.relationship}",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text =
                if (contact.accessGiven) {
                    "Access given: Yes"
                } else {
                    "Access given: No"
                },
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}


// ---------------------------------------------------------
// ADD TRUSTED CONTACT POPUP
// ---------------------------------------------------------

@Composable
fun AddTrustedContactDialog(
    onDismiss: () -> Unit,
    onAddContact: (TrustedContact) -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    var phoneNumber by remember {
        mutableStateOf("")
    }

    var relationship by remember {
        mutableStateOf("")
    }

    var accessGiven by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),
        onDismissRequest = onDismiss,

        title = {

            Text(
                text = "Add Trusted Contact",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },

        text = {
            PrivacyDialogFocus()

            Column {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text("Name")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                    },
                    label = {
                        Text("Phone Number")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = relationship,
                    onValueChange = {
                        relationship = it
                    },
                    label = {
                        Text("Relationship")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "Give access",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Switch(
                        checked = accessGiven,
                        onCheckedChange = {
                            accessGiven = it
                        }
                    )
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick = {

                    if (
                        name.isNotBlank() &&
                        phoneNumber.isNotBlank() &&
                        relationship.isNotBlank()
                    ) {

                        onAddContact(
                            TrustedContact(
                                name = name,
                                phoneNumber = phoneNumber,
                                relationship = relationship,
                                accessGiven = accessGiven
                            )
                        )
                    }
                }
            ) {

                Text(
                    text = "Add Contact",
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
// REUSABLE TEXT FIELD
// ---------------------------------------------------------

@Composable
fun AccountSetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null
) {

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,

        label = {

            Text(
                text = label,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            )
        },

        isError = isError,

        supportingText = if (isError && errorMessage != null) {
            {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else null,

        modifier = Modifier.fillMaxWidth(),

        singleLine = true,

        visualTransformation =
            if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            }
    )
}


// ---------------------------------------------------------
// ACCOUNT CREATED SCREEN
// ---------------------------------------------------------

@Composable
fun AccountCreatedStep(
    onGoToLoginClick: () -> Unit = {}
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "✓",
            fontSize = 90.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Account Created!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You can now log in to your account",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGoToLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {

            Text(
                text = "Go To Login",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}





// ---------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun AccountLandingStepPreview() {

    AndroidAppTheme {
        AccountLandingStep()
    }
}


@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun AccountTypeStepPreview() {

    AndroidAppTheme {
        AccountTypeStep()
    }
}


@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun PersonalInformationStepPreview() {

    AndroidAppTheme {
        PersonalInformationStep()
    }
}


@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun DocumentsStepPreview() {

    AndroidAppTheme {
        DocumentsStep()
    }
}


@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun TrustedContactsStepPreview() {

    AndroidAppTheme {
        TrustedContactsStep()
    }
}


@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun AccountCreatedStepPreview() {

    AndroidAppTheme {
        AccountCreatedStep()
    }
}
