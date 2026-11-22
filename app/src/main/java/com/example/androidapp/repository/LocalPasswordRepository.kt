package com.example.androidapp.repository

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Stores the DISGUISE passcode (the weather-app lock) on this device only.
// It is never sent to Firebase. We keep a salted hash, never the passcode itself.
class LocalPasswordRepository(context: Context) : PasswordRepository {

    private val prefs = context.applicationContext
        .getSharedPreferences("disguise_passcode", Context.MODE_PRIVATE)

    override suspend fun savePassword(password: String) {
        withContext(Dispatchers.Default) {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val hash = hash(password, salt)
            prefs.edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply()
        }
    }

    override suspend fun verifyPassword(password: String): Boolean =
        withContext(Dispatchers.Default) {
            val saltText = prefs.getString(KEY_SALT, null)
            val hashText = prefs.getString(KEY_HASH, null)
            if (saltText == null || hashText == null) {
                return@withContext false
            }
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            // isEqual compares in constant time.
            MessageDigest.isEqual(expected, hash(password, salt))
        }

    override suspend fun hasPassword(): Boolean = prefs.contains(KEY_HASH)
    override suspend fun clearPassword() {
        withContext(Dispatchers.IO) {
            // Removes the salt, the hash, and the failed-attempt log.
            prefs.edit().clear().commit()
        }
    }
    override suspend fun logFailedAttempt() {
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val current = prefs.getString(KEY_FAILED_ATTEMPTS, "") ?: ""
            val updated = if (current.isBlank()) timestamp else "$current\n$timestamp"
            prefs.edit().putString(KEY_FAILED_ATTEMPTS, updated).apply()
        }
    }

    override suspend fun getFailedAttempts(): List<String> {
        return withContext(Dispatchers.IO) {
            val current = prefs.getString(KEY_FAILED_ATTEMPTS, "") ?: ""
            if (current.isBlank()) emptyList() else current.split("\n")
        }
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(spec)
            .encoded
    }

    private companion object {
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val SALT_BYTES = 16
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256
    }
}