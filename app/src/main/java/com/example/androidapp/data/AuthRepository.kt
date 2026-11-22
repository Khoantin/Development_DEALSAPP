package com.example.androidapp.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    // The signed-in user, or null if nobody is signed in.
    // Firebase saves the session on the device automatically, so this is
    // still set after the app is closed and reopened.
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun register(
        email: String,
        password: String,
        onResult: (Result<FirebaseUser>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    onResult(Result.success(user))
                } else {
                    onResult(Result.failure(IllegalStateException("No user returned")))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun login(
        email: String,
        password: String,
        onResult: (Result<FirebaseUser>) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    onResult(Result.success(user))
                } else {
                    onResult(Result.failure(IllegalStateException("No user returned")))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun logout() {
        auth.signOut()
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendVerificationEmail(onResult: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }
        user.sendEmailVerification()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}

// Turns Firebase's technical errors into messages that are safe to show a user.
fun Throwable.toFriendlyAuthMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters."
    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
    is FirebaseNetworkException -> "No internet connection. Please try again."
    else -> "Something went wrong. Please try again."
}