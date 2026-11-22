package com.example.androidapp.pages.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.repository.PasswordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val PASSCODE_LENGTH = 4

class WeatherAppViewModel(
    private val passwordRepository: PasswordRepository
) : ViewModel() {

    private val _passcode = MutableStateFlow("")
    val passcode: StateFlow<String> = _passcode.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _passcodeError = MutableStateFlow(false)
    val passcodeError: StateFlow<Boolean> = _passcodeError.asStateFlow()

    // null while we check, true if a passcode has been set on this device,
    // false if none has been set yet.
    private val _needsPasscode = MutableStateFlow<Boolean?>(null)
    val needsPasscode: StateFlow<Boolean?> = _needsPasscode.asStateFlow()

    init {
        viewModelScope.launch {
            _needsPasscode.value = passwordRepository.hasPassword()
        }
    }

    fun addPasscodeDigit(digit: String) {
        if (_passcode.value.length < PASSCODE_LENGTH) {
            _passcode.value += digit
            _passcodeError.value = false
        }
    }

    fun removePasscodeDigit() {
        if (_passcode.value.isNotEmpty()) {
            _passcode.value = _passcode.value.dropLast(1)
            _passcodeError.value = false
        }
    }

    fun checkPasscode() {
        viewModelScope.launch {
            val isCorrect =
                passwordRepository.verifyPassword(_passcode.value)

            _isUnlocked.value = isCorrect
            _passcodeError.value = !isCorrect

            // Clear the dots after a wrong attempt so the user can retype.
            if (!isCorrect) {
                passwordRepository.logFailedAttempt()
                _passcode.value = ""
            }
        }
    }
}