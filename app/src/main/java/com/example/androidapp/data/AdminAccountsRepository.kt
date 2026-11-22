package com.example.androidapp.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

/** One row from listAdminAccounts. The backend returns only these fields. */
data class AdminAccount(
    val uid: String,
    val displayName: String,
    val email: String,
    val role: String,
    val accountStatus: String,
    val createdAtMillis: Long?,
    val statusUpdatedAtMillis: Long?,
    val roleUpdatedAtMillis: Long? = null
)

data class AdminAccountsPage(
    val accounts: List<AdminAccount>,
    val nextCursor: String?
)

data class AccountRoleChange(
    val targetUid: String,
    val fromRole: String?,
    val toRole: String
)

data class AccountStatusChange(
    val targetUid: String,
    val fromStatus: String?,
    val toStatus: String,
    val changed: Boolean,
    val authUserFound: Boolean
)

object AdminAccountOptions {
    val roles = linkedMapOf(
        "user" to "User",
        "support" to "Support",
        "admin" to "Admin"
    )

    val statuses = linkedMapOf(
        "active" to "Active",
        "inactive" to "Inactive",
        "suspended" to "Suspended",
        "disabled" to "Disabled"
    )

    const val REASON_MIN = 5
    const val REASON_MAX = 500

    fun roleLabel(role: String): String =
        roles[role] ?: "Other / unspecified"
}

class AdminAccountsRepository(
    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance("us-central1")
) {
    fun listAccounts(
        role: String?,
        status: String?,
        cursor: String?,
        onResult: (Result<AdminAccountsPage>) -> Unit
    ) {
        val request = buildMap {
            role?.let { put("role", it) }
            status?.let { put("status", it) }
            cursor?.let { put("cursor", it) }
        }

        functions
            .getHttpsCallable("listAdminAccounts")
            .call(request)
            .addOnSuccessListener { response ->
                onResult(runCatching {
                    val data = response.data as? Map<*, *>
                        ?: error("Invalid accounts response")

                    val accounts = data["accounts"] as? List<*>
                        ?: error("Missing accounts list")

                    AdminAccountsPage(
                        accounts = accounts.map { item ->
                            parseAccount(
                                item as? Map<*, *> ?: error("Invalid account")
                            )
                        },
                        nextCursor = data["nextCursor"] as? String
                    )
                })
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun setAccountStatus(
        targetUid: String,
        newStatus: String,
        reason: String,
        onResult: (Result<AccountStatusChange>) -> Unit
    ) {
        val request = mapOf(
            "targetUid" to targetUid,
            "newStatus" to newStatus,
            "reason" to reason
        )

        functions
            .getHttpsCallable("setAccountStatus")
            .call(request)
            .addOnSuccessListener { response ->
                onResult(runCatching {
                    val data = response.data as? Map<*, *>
                        ?: error("Invalid status response")

                    check(
                        data["saved"] == true &&
                                data["targetUid"] == targetUid &&
                                data["toStatus"] == newStatus
                    ) {
                        "Status change was not confirmed"
                    }

                    AccountStatusChange(
                        targetUid = targetUid,
                        fromStatus = data["fromStatus"] as? String,
                        toStatus = newStatus,
                        changed = data["changed"] == true,
                        authUserFound = data["authUserFound"] != false
                    )
                })
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun setAccountRole(
        targetUid: String,
        newRole: String,
        reason: String,
        onResult: (Result<AccountRoleChange>) -> Unit
    ) {
        val request = mapOf(
            "targetUid" to targetUid,
            "newRole" to newRole,
            "reason" to reason
        )

        functions
            .getHttpsCallable("setAccountRole")
            .call(request)
            .addOnSuccessListener { response ->
                onResult(runCatching {
                    val data = response.data as? Map<*, *>
                        ?: error("Invalid role response")

                    check(
                        data["saved"] == true &&
                                data["targetUid"] == targetUid &&
                                data["toRole"] == newRole
                    ) {
                        "Role change was not confirmed"
                    }

                    AccountRoleChange(
                        targetUid = targetUid,
                        fromRole = data["fromRole"] as? String,
                        toRole = newRole
                    )
                })
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    private fun parseAccount(data: Map<*, *>): AdminAccount {
        fun text(key: String): String =
            data[key] as? String ?: error("Missing account field: $key")

        return AdminAccount(
            uid = text("uid"),
            displayName = text("displayName"),
            email = text("email"),
            role = text("role"),
            accountStatus = text("accountStatus"),
            createdAtMillis = (data["createdAt"] as? Number)?.toLong(),
            statusUpdatedAtMillis = (data["statusUpdatedAt"] as? Number)?.toLong(),
            roleUpdatedAtMillis = (data["roleUpdatedAt"] as? Number)?.toLong()
        )
    }
}

/**
 * True when setAccountStatus saved the new status but couldn't finish the
 * sign-in (Firebase Auth) change. Repeating the same change finishes it.
 */
fun Throwable.isStatusSavedButSignInPending(): Boolean {
    if (this !is FirebaseFunctionsException) return false
    val details = details as? Map<*, *> ?: return false
    return details["statusSaved"] == true
}

fun Throwable.toAdminAccountsMessage(): String {
    if (this !is FirebaseFunctionsException) {
        return "Unable to complete the request. Please try again."
    }

    if (isStatusSavedButSignInPending()) {
        return "The account status was saved, but updating sign-in access " +
                "failed. Try the same change again to finish it."
    }

    return when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            "Please sign in again."

        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            "An active administrator account is required."

        FirebaseFunctionsException.Code.INVALID_ARGUMENT,
        FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
            message ?: "This change isn't allowed."

        // A missing account comes back with the backend's own message. A
        // function that isn't deployed yet only has the bare code as message.
        FirebaseFunctionsException.Code.NOT_FOUND ->
            message?.takeUnless { it.isBlank() || it == "NOT_FOUND" }
                ?: "This account or the accounts service could not be found."

        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "The service could not be reached. Check your connection and try again."

        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
            "The service limit was reached. Please try again later."

        else ->
            "Unable to complete the request. Please try again."
    }
}
