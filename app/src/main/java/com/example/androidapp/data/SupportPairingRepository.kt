package com.example.androidapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

data class SupportPairingCode(
    val code: String,
    val expiresAt: Long
)

data class SupportPairingResult(
    val linkId: String,
    val status: String,
    val duplicate: Boolean
)

class SupportPairingRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance("us-central1")
) {
    fun generateCode(
        onResult: (Result<SupportPairingCode>) -> Unit
    ) {
        call(
            name = "generateSupportPairingCode",
            payload = emptyMap(),
            parse = { data ->
                val code = data["code"] as? String
                    ?: error("Missing pairing code.")

                check(Regex("^[0-9]{6}$").matches(code)) {
                    "Invalid pairing code."
                }

                val expiry = (data["expiresAt"] as? Number)?.toDouble()
                    ?: error("Missing expiry.")

                check(
                    expiry.isFinite() &&
                            expiry > 0 &&
                            expiry < Long.MAX_VALUE.toDouble() &&
                            expiry == expiry.toLong().toDouble()
                ) {
                    "Invalid expiry."
                }

                SupportPairingCode(
                    code = code,
                    expiresAt = expiry.toLong()
                )
            },
            onResult = onResult
        )
    }

    fun redeemCode(
        code: String,
        requestId: String,
        onResult: (Result<SupportPairingResult>) -> Unit
    ) {
        if (!Regex("^[0-9]{6}$").matches(code)) {
            onResult(
                Result.failure(
                    IllegalArgumentException("Enter a six-digit code.")
                )
            )
            return
        }

        call(
            name = "redeemSupportPairingCode",
            payload = mapOf(
                "code" to code,
                "requestId" to requestId
            ),
            parse = { data ->
                check(data["success"] == true) {
                    "Request was not confirmed."
                }

                val linkId = data["linkId"] as? String
                    ?: error("Missing connection ID.")

                check(linkId.isNotBlank() && !linkId.contains("/")) {
                    "Invalid connection ID."
                }

                val status = data["status"] as? String
                    ?: error("Missing connection status.")

                check(
                    status in setOf(
                        "pending",
                        "accepted",
                        "rejected",
                        "removed"
                    )
                ) {
                    "Invalid connection status."
                }

                val duplicate = data["duplicate"] as? Boolean
                    ?: error("Missing retry status.")

                SupportPairingResult(
                    linkId = linkId,
                    status = status,
                    duplicate = duplicate
                )
            },
            onResult = onResult
        )
    }

    private fun <T> call(
        name: String,
        payload: Map<String, Any>,
        parse: (Map<*, *>) -> T,
        onResult: (Result<T>) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onResult(
                Result.failure(
                    IllegalStateException("Please sign in again.")
                )
            )
            return
        }

        functions.getHttpsCallable(name)
            .call(payload)
            .addOnSuccessListener { response ->
                if (auth.currentUser?.uid != uid) {
                    onResult(
                        Result.failure(
                            IllegalStateException("Your account changed.")
                        )
                    )
                    return@addOnSuccessListener
                }

                onResult(
                    runCatching {
                        val data = response.data as? Map<*, *>
                            ?: error("Invalid server response.")

                        parse(data)
                    }
                )
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }
}

fun Throwable.toSupportPairingMessage(): String {
    if (this !is FirebaseFunctionsException) {
        return "Could not confirm the operation. Check your connection and try again."
    }

    return when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            "Please sign in again."

        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            "Your account is not eligible for this pairing action."

        FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
            "Check the six-digit code and try again."

        FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
            "This code or request is unavailable. Ask the User for a new code."

        FirebaseFunctionsException.Code.ALREADY_EXISTS ->
            "A connection or request already exists. Check your connections."

        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
            "Too many attempts. Wait before trying again."

        FirebaseFunctionsException.Code.NOT_FOUND ->
            "The pairing service is unavailable."

        else ->
            "Could not confirm the operation. Retry the same request."
    }
}