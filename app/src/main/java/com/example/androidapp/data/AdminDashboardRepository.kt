package com.example.androidapp.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

data class AdminDashboardStats(
    val totalAccounts: Long,
    val roles: Map<String, Long>,
    val statuses: Map<String, Long>,
    val generatedAtMillis: Long
)

/** Anonymous totals only: the backend never returns ids, names or locations. */
data class AdminEmergencyStats(
    val activeNow: Long,
    val startedLast7Days: Long,
    val startedLast30Days: Long,
    val cancelledLast30Days: Long,
    val byTrigger: Map<String, Long>,
    val generatedAtMillis: Long
)

class AdminDashboardRepository(
    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance("us-central1")
) {
    fun loadStats(
        onResult: (Result<AdminDashboardStats>) -> Unit
    ) {
        functions
            .getHttpsCallable("getAdminDashboardStats")
            .call()
            .addOnSuccessListener { result ->
                onResult(runCatching {
                    val data = result.data as? Map<*, *>
                        ?: error("Invalid dashboard response")

                    AdminDashboardStats(
                        totalAccounts = readNumber(data, "totalAccounts"),
                        roles = readCounts(
                            data,
                            "roles",
                            listOf("user", "support", "admin", "other")
                        ),
                        statuses = readCounts(
                            data,
                            "statuses",
                            listOf(
                                "active",
                                "inactive",
                                "suspended",
                                "disabled",
                                "other"
                            )
                        ),
                        generatedAtMillis =
                            readNumber(data, "generatedAtMillis")
                    )
                })
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun loadEmergencyStats(
        onResult: (Result<AdminEmergencyStats>) -> Unit
    ) {
        functions
            .getHttpsCallable("getAdminEmergencyStats")
            .call()
            .addOnSuccessListener { result ->
                onResult(runCatching {
                    val data = result.data as? Map<*, *>
                        ?: error("Invalid emergency stats response")

                    AdminEmergencyStats(
                        activeNow = readNumber(data, "activeNow"),
                        startedLast7Days = readNumber(data, "startedLast7Days"),
                        startedLast30Days = readNumber(data, "startedLast30Days"),
                        cancelledLast30Days = readNumber(data, "cancelledLast30Days"),
                        byTrigger = readCounts(
                            data,
                            "byTrigger",
                            listOf("emergency_button", "voice_duress")
                        ),
                        generatedAtMillis = readNumber(data, "generatedAtMillis")
                    )
                })
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    private fun readNumber(
        data: Map<*, *>,
        key: String
    ): Long {
        val number = data[key] as? Number
            ?: error("Missing dashboard field: $key")

        return number.toLong().also {
            require(it >= 0) { "Invalid dashboard value: $key" }
        }
    }

    private fun readCounts(
        data: Map<*, *>,
        key: String,
        fields: List<String>
    ): Map<String, Long> {
        val counts = data[key] as? Map<*, *>
            ?: error("Missing dashboard counts: $key")

        return fields.associateWith { field ->
            readNumber(counts, field)
        }
    }
}

// Same specific messages as the account stats; only the fallback names the section.
fun Throwable.toAdminEmergencyStatsMessage(): String =
    toAdminDashboardMessage().takeUnless { it.startsWith("Unable to load dashboard") }
        ?: "Unable to load emergency activity. Please try again."

fun Throwable.toAdminDashboardMessage(): String {
    if (this !is FirebaseFunctionsException) {
        return "Unable to load dashboard statistics. Please try again."
    }

    return when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            "Please sign in again."

        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            "An active administrator account is required."

        FirebaseFunctionsException.Code.NOT_FOUND ->
            "The dashboard service is not available yet."

        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "The service could not be reached. Please try again."

        else ->
            "Unable to load dashboard statistics. Please try again."
    }
}