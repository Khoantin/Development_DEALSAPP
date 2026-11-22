package com.example.androidapp.repository

interface PasswordRepository {

    suspend fun savePassword(password: String)

    suspend fun verifyPassword(password: String): Boolean

    suspend fun hasPassword(): Boolean

    suspend fun clearPassword()

    suspend fun logFailedAttempt()

    suspend fun getFailedAttempts(): List<String>
}