package com.example.androidapp.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EmergencyLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAtMillis: Long
) {
    fun isValid(): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracyMeters.isFinite() && accuracyMeters in 0.0..100000.0 && capturedAtMillis > 0L

    fun toMap(): Map<String, Any> = mapOf(
        "latitude" to latitude, "longitude" to longitude,
        "accuracyMeters" to accuracyMeters, "capturedAtMillis" to capturedAtMillis
    )

    companion object {
        fun fromMap(data: Map<*, *>): EmergencyLocationSnapshot? {
            val time = (data["capturedAtMillis"] as? Number)?.toDouble() ?: return null
            if (!time.isFinite() || time <= 0 || time > 9007199254740991.0 || time != time.toLong().toDouble()) return null
            return EmergencyLocationSnapshot(
                (data["latitude"] as? Number)?.toDouble() ?: return null,
                (data["longitude"] as? Number)?.toDouble() ?: return null,
                (data["accuracyMeters"] as? Number)?.toDouble() ?: return null,
                time.toLong()
            ).takeIf { it.isValid() }
        }
    }
}

// Pending coordinates are encrypted with a non-exportable Android Keystore key.
// Bind ciphertext to its sender and request so it cannot be swapped between alerts.
object EmergencyPendingLocationCipher {
    private const val ALIAS = "deals_emergency_pending_location_v1"
    @Synchronized
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Pending location key is unavailable." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun encrypt(uid: String, requestId: String, location: EmergencyLocationSnapshot): String {
        require(location.isValid())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD("$uid:$requestId".toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(JSONObject(location.toMap()).toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    fun decrypt(uid: String, requestId: String, encoded: String): EmergencyLocationSnapshot {
        val parts = encoded.split('.')
        check(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD("$uid:$requestId".toByteArray(Charsets.UTF_8))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        return checkNotNull(EmergencyLocationSnapshot.fromMap(mapOf(
            "latitude" to json.getDouble("latitude"), "longitude" to json.getDouble("longitude"),
            "accuracyMeters" to json.getDouble("accuracyMeters"), "capturedAtMillis" to json.getLong("capturedAtMillis")
        )))
    }
}

data class EmergencyAlertResult(
    val eventId: String, val state: String, val inAppRecordCount: Int, val duplicate: Boolean
)

class EmergencyAlertRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1")
) {
    fun cancelInAppAlert(
        uid: String,
        eventId: String,
        onResult: (Result<EmergencyCancellationResult>) -> Unit
    ) {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            onResult(Result.failure(IllegalStateException("Please sign in again.")))
            return
        }
        if (eventId.isBlank() || eventId.contains("/") || eventId == "." ||
            eventId == ".." || eventId.toByteArray(Charsets.UTF_8).size > 1500) {
            onResult(Result.failure(IllegalArgumentException("Invalid event ID.")))
            return
        }
        functions.getHttpsCallable("cancelEmergencyInAppAlert")
            .call(mapOf("eventId" to eventId))
            .addOnSuccessListener { response ->
                if (auth.currentUser?.uid != uid) {
                    onResult(Result.failure(IllegalStateException("Your account changed. Check cancellation after signing back in.")))
                    return@addOnSuccessListener
                }
                onResult(runCatching {
                    val data = response.data as? Map<*, *> ?: error("Invalid cancellation response.")
                    val returnedId = data["eventId"] as? String ?: error("Missing event ID.")
                    val state = data["state"] as? String ?: error("Missing state.")
                    val count = (data["inAppRecordCount"] as? Number)?.toDouble() ?: error("Missing count.")
                    val repeated = data["alreadyCancelled"] as? Boolean ?: error("Missing retry status.")
                    check(returnedId == eventId && state == "cancelled")
                    check(count.isFinite() && count in 1.0..10.0 && count == count.toInt().toDouble())
                    EmergencyCancellationResult(returnedId, state, count.toInt(), repeated)
                })
            }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendInAppAlert(
        uid: String,
        requestId: String,
        contactIds: List<String>,
        location: EmergencyLocationSnapshot? = null,
        onResult: (Result<EmergencyAlertResult>) -> Unit
    ) {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            onResult(Result.failure(IllegalStateException("Please sign in again.")))
            return
        }
        val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        val ids = contactIds.distinct().sorted()
        if (!uuidPattern.matches(requestId) || ids.size !in 1..10 || ids.any {
                it.isBlank() || it.contains("/") || it == "." || it == ".." || it.toByteArray(Charsets.UTF_8).size > 1500
            } || (location != null && !location.isValid())) {
            onResult(Result.failure(IllegalArgumentException("Invalid alert request.")))
            return
        }
        val normalizedId = requestId.lowercase()
        val payload = mutableMapOf<String, Any>("requestId" to normalizedId, "contactIds" to ids)
        location?.let { payload["location"] = it.toMap() }
        functions.getHttpsCallable("sendEmergencyInAppAlert").call(payload)
            .addOnSuccessListener { response ->
                if (auth.currentUser?.uid != uid) {
                    onResult(Result.failure(IllegalStateException("Your account changed. Check the alert after signing back in.")))
                    return@addOnSuccessListener
                }
                onResult(runCatching {
                    val data = response.data as? Map<*, *> ?: error("Invalid response.")
                    val eventId = data["eventId"] as? String ?: error("Missing event ID.")
                    val state = data["state"] as? String ?: error("Missing state.")
                    val count = (data["inAppRecordCount"] as? Number)?.toDouble() ?: error("Missing count.")
                    val duplicate = data["duplicate"] as? Boolean ?: error("Missing retry status.")
                    check(eventId == "${uid}_$normalizedId")
                    check(state == "active" || state == "cancelled")
                    check(count.isFinite() && count >= 1 && count <= ids.size && count == count.toInt().toDouble())
                    EmergencyAlertResult(eventId, state, count.toInt(), duplicate)
                })
            }.addOnFailureListener { onResult(Result.failure(it)) }
    }
}

fun Throwable.toEmergencyAlertMessage(): String = when (this) {
    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again."
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Your account cannot send this alert."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Check the saved message, contact preferences, and whether this request was already used. Retry keeps the original alert."
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "An alert was recently created. Wait briefly before retrying."
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "The request or location is invalid. Check the device clock. Keep this request ID until its status is resolved."
        FirebaseFunctionsException.Code.NOT_FOUND -> "The alert service could not be found."
        else -> "Could not confirm the alert. Retry the same alert to avoid duplicates."
    }
    else -> "Could not confirm the alert. Retry the same alert to avoid duplicates."
}

data class EmergencyCancellationResult(
    val eventId: String,
    val state: String,
    val inAppRecordCount: Int,
    val alreadyCancelled: Boolean
)

fun Throwable.toEmergencyCancellationMessage(): String = when (this) {
    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again to retry cancellation."
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Only the sender can cancel this alert."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Cancellation could not be completed. The alert or notification records need review."
        FirebaseFunctionsException.Code.NOT_FOUND -> "The cancellation service is unavailable. Check its deployment, then retry."
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "The cancellation request is invalid. Keep the saved alert until its status is checked."
        else -> "Could not confirm cancellation. Retry cancelling this same alert."
    }
    else -> "Could not confirm cancellation. Retry cancelling this same alert."
}
