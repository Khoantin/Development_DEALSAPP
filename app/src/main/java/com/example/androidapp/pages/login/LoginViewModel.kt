package com.example.androidapp.pages.login

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.toFriendlyAuthMessage
import com.google.firebase.FirebaseNetworkException

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var infoMessage by mutableStateOf<String?>(null)
        private set

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (isLoading) return

        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            errorMessage = "Enter your email and password."
            return
        }

        isLoading = true
        errorMessage = null
        infoMessage = null

        authRepository.login(cleanEmail, password) { result ->
            isLoading = false
            result
                .onSuccess { onSuccess() }
                .onFailure { errorMessage = it.toFriendlyAuthMessage() }
        }
    }

    fun sendPasswordReset(email: String) {
        if (isLoading) return

        val cleanEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            errorMessage = "Enter your email above first."
            infoMessage = null
            return
        }

        isLoading = true
        errorMessage = null
        infoMessage = null

        authRepository.sendPasswordReset(cleanEmail) { result ->
            isLoading = false
            result.onFailure {
                if (it is FirebaseNetworkException) {
                    errorMessage = it.toFriendlyAuthMessage()
                    return@onFailure
                }
            }
            // Same wording whether or not the account exists, so the screen
            // never reveals which emails are registered.
            if (errorMessage == null) {
                infoMessage = "If an account exists for that email, a reset link has been sent."
            }
        }
    }
}