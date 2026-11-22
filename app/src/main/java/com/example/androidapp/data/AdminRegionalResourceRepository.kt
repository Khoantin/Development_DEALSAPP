package com.example.androidapp.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

object AdminResourceOptions {
    val regions = linkedMapOf(
        "CA-AB" to "Alberta",
        "CA-BC" to "British Columbia"
    )

    val needs = linkedMapOf(
        "GENERAL" to "Find local services",
        "HOUSING" to "Somewhere to stay",
        "CONVERSATION" to "Someone to talk to",
        "ESSENTIALS" to "Food and essentials"
    )

    val contactMethods = linkedMapOf(
        "PHONE" to "Phone",
        "TEXT" to "Text-based contact",
        "IN_PERSON" to "In person",
        "WEBSITE" to "Website / online directory"
    )
}

data class AdminRegionalResource(
    val id: String,
    val version: String,
    val isDemo: Boolean,
    val name: String,
    val need: String,
    val summary: String,
    val exampleProcess: String,
    val contactMethods: List<String>,
    val websiteUrl: String,
    val phoneNumber: String,
    val hoursNote: String,
    val lastReviewed: String
) {
    fun editableFields(): Map<String, Any> = mapOf(
        "name" to name,
        "need" to need,
        "summary" to summary,
        "exampleProcess" to exampleProcess,
        "contactMethods" to contactMethods,
        "websiteUrl" to websiteUrl,
        "phoneNumber" to phoneNumber,
        "hoursNote" to hoursNote,
        "lastReviewed" to lastReviewed
    )
}

class AdminRegionalResourceRepository(
    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance("us-central1")
) {
    fun loadResources(
        regionId: String,
        onResult: (Result<List<AdminRegionalResource>>) -> Unit
    ) {
        functions
            .getHttpsCallable("listAdminRegionalResources")
            .call(mapOf("regionId" to regionId))
            .addOnSuccessListener { response ->
                onResult(runCatching {
                    val data = response.data as? Map<*, *>
                        ?: error("Invalid resources response")

                    check(data["regionId"] == regionId) {
                        "Unexpected resource region"
                    }

                    val resources = data["resources"] as? List<*>
                        ?: error("Missing resources list")

                    resources.map { item ->
                        val resource = item as? Map<*, *>
                            ?: error("Invalid resource")

                        parseResource(resource)
                    }
                })
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun saveResource(
        regionId: String,
        resource: AdminRegionalResource,
        onResult: (Result<Unit>) -> Unit
    ) {
        val request = mapOf(
            "regionId" to regionId,
            "resourceId" to resource.id,
            "expectedVersion" to resource.version,
            "fields" to resource.editableFields()
        )

        functions
            .getHttpsCallable("updateAdminRegionalResource")
            .call(request)
            .addOnSuccessListener { response ->
                onResult(runCatching {
                    val data = response.data as? Map<*, *>
                        ?: error("Invalid save response")

                    check(
                        data["saved"] == true &&
                                data["regionId"] == regionId &&
                                data["resourceId"] == resource.id
                    ) {
                        "Save was not confirmed"
                    }

                    Unit
                })
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    private fun parseResource(
        data: Map<*, *>
    ): AdminRegionalResource {
        fun text(key: String): String =
            data[key] as? String
                ?: error("Missing resource field: $key")

        val methods = data["contactMethods"] as? List<*>
            ?: error("Missing contact methods")

        return AdminRegionalResource(
            id = text("id"),
            version = text("version"),
            isDemo = data["isDemo"] as? Boolean
                ?: error("Missing DEMO status"),
            name = text("name"),
            need = text("need"),
            summary = text("summary"),
            exampleProcess = text("exampleProcess"),
            contactMethods = methods.map { value ->
                value as? String
                    ?: error("Invalid contact method")
            },
            websiteUrl = text("websiteUrl"),
            phoneNumber = text("phoneNumber"),
            hoursNote = text("hoursNote"),
            lastReviewed = text("lastReviewed")
        )
    }
}

fun Throwable.toAdminResourceMessage(): String {
    if (this !is FirebaseFunctionsException) {
        return "Unable to complete the request. Please try again."
    }

    return when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            "Please sign in again."

        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            "An active administrator account is required."

        FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
            message ?: "Check the resource fields and try again."

        FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
            message ?: "This resource changed. Reload before saving."

        FirebaseFunctionsException.Code.NOT_FOUND ->
            "The resource or editor service could not be found. Reload and try again."

        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "The request could not be confirmed. Check your connection and reload before retrying."

        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
            "The service limit was reached. Please try again later."

        else ->
            "Unable to complete the request. Please try again."
    }
}