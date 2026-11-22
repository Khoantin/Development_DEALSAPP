package com.example.androidapp.pages.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.repository.PasswordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Creates the disguise passcode: the user enters it twice, and it is only
// saved when both entries match. There is intentionally no skip option.
class PasscodeSetupViewModel(
    private val passwordRepository: PasswordRepository
) : ViewModel() {

    private val _passcode = MutableStateFlow("")
    val passcode: StateFlow<String> = _passcode.asStateFlow()

    private val _isConfirming = MutableStateFlow(false)
    val isConfirming: StateFlow<Boolean> = _isConfirming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isDone = MutableStateFlow(false)
    val isDone: StateFlow<Boolean> = _isDone.asStateFlow()

    private var firstEntry = ""
    private var isSaving = false

    fun addDigit(digit: String) {
        if (isSaving) return
        if (_passcode.value.length < PASSCODE_LENGTH) {
            _passcode.value += digit
            _error.value = null
        }
    }

    fun removeDigit() {
        if (isSaving) return
        if (_passcode.value.isNotEmpty()) {
            _passcode.value = _passcode.value.dropLast(1)
            _error.value = null
        }
    }

    fun confirm() {
        if (isSaving) return

        val entered = _passcode.value
        if (entered.length != PASSCODE_LENGTH) {
            _error.value = "Enter $PASSCODE_LENGTH digits."
            return
        }

        if (!_isConfirming.value) {
            // First entry: remember it and ask again.
            firstEntry = entered
            _passcode.value = ""
            _isConfirming.value = true
            _error.value = null
        } else if (entered == firstEntry) {
            isSaving = true
            viewModelScope.launch {
                passwordRepository.savePassword(entered)
                _isDone.value = true
            }
        } else {
            // Entries differ: start over from the first step.
            firstEntry = ""
            _passcode.value = ""
            _isConfirming.value = false
            _error.value = "Passcodes didn't match. Start again."
        }
    }
}

@Composable
fun PasscodeSetupScreen(
    viewModel: PasscodeSetupViewModel
) {
    val passcode by viewModel.passcode.collectAsState()
    val isConfirming by viewModel.isConfirming.collectAsState()
    val error by viewModel.error.collectAsState()

    PasscodeScreen(
        passcode = passcode,
        passcodeError = error != null,
        onNumberClick = viewModel::addDigit,
        onBackspaceClick = viewModel::removeDigit,
        onConfirmClick = viewModel::confirm,
        title = if (isConfirming) "Confirm your passcode" else "Create your passcode",
        subtitle = if (isConfirming) {
            "Enter the same $PASSCODE_LENGTH digits again"
        } else {
            "Choose $PASSCODE_LENGTH digits you will use to open the app"
        },
        errorText = error ?: ""
    )
}