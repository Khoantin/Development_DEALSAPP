package com.example.androidapp.data

import com.google.firebase.functions.FirebaseFunctions

data class EmergencyGuideContent(
    val title: String = "",
    val summary: String = "",
    val audience: String = "both",
    val steps: List<String> = emptyList(),
    val practiceScenario: String = ""
)

data class EmergencyGuide(
    val id: String,
    val revision: Long = 0,
    val content: EmergencyGuideContent,
    val published: EmergencyGuideContent? = null,
    val isPublished: Boolean = false,
    val hasUnpublishedChanges: Boolean = false,
    val lastReviewedAtMillis: Long? = null
)

class EmergencyGuidanceRepository(
    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance("us-central1")
) {
    fun load(
        adminMode: Boolean,
        onResult: (Result<List<EmergencyGuide>>) -> Unit
    ) {
        functions.getHttpsCallable("listEmergencyGuides")
            .call(
                mapOf(
                    "mode" to if (adminMode) "admin" else "published"
                )
            )
            .addOnSuccessListener { response ->
                onResult(
                    runCatching {
                        val root = response.data as? Map<*, *>
                            ?: error("Invalid guidance response.")

                        val records = root["guides"] as? List<*>
                            ?: error("Missing guide list.")

                        records.map { value ->
                            val record = value as? Map<*, *>
                                ?: error("Invalid guide.")

                            val id = record["id"] as? String
                                ?: error("Missing guide ID.")

                            if (adminMode) {
                                val draft = record["draft"] as? Map<*, *>
                                    ?: error("Missing draft.")

                                EmergencyGuide(
                                    id = id,
                                    revision = (
                                            record["revision"] as? Number
                                            )?.toLong()
                                        ?: error("Missing guide revision."),
                                    content = parseContent(draft),
                                    published = (
                                            record["published"] as? Map<*, *>
                                            )?.let(::parseContent),
                                    isPublished =
                                        record["isPublished"] == true,
                                    hasUnpublishedChanges =
                                        record["hasUnpublishedChanges"] == true,
                                    lastReviewedAtMillis = (
                                            record["lastReviewedAtMillis"] as? Number
                                            )?.toLong()
                                )
                            } else {
                                EmergencyGuide(
                                    id = id,
                                    content = parseContent(record),
                                    isPublished = true,
                                    lastReviewedAtMillis = (
                                            record["lastReviewedAtMillis"] as? Number
                                            )?.toLong()
                                )
                            }
                        }
                    }
                )
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun save(
        id: String,
        revision: Long,
        content: EmergencyGuideContent,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutate(
            mapOf(
                "action" to "save",
                "guideId" to id,
                "expectedRevision" to revision,
                "fields" to mapOf(
                    "title" to content.title,
                    "summary" to content.summary,
                    "audience" to content.audience,
                    "steps" to content.steps,
                    "practiceScenario" to content.practiceScenario
                )
            ),
            onResult
        )
    }

    fun publish(
        id: String,
        revision: Long,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutate(
            mapOf(
                "action" to "publish",
                "guideId" to id,
                "expectedRevision" to revision,
                "reviewConfirmed" to true
            ),
            onResult
        )
    }

    fun withdraw(
        id: String,
        revision: Long,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutate(
            mapOf(
                "action" to "withdraw",
                "guideId" to id,
                "expectedRevision" to revision
            ),
            onResult
        )
    }

    private fun mutate(
        data: Map<String, Any>,
        onResult: (Result<Unit>) -> Unit
    ) {
        functions.getHttpsCallable("manageEmergencyGuide")
            .call(data)
            .addOnSuccessListener { response ->
                onResult(
                    runCatching {
                        val result = response.data as? Map<*, *>
                            ?: error("Invalid save response.")

                        check(result["saved"] == true) {
                            "The change could not be confirmed."
                        }
                        Unit
                    }
                )
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    private fun parseContent(
        data: Map<*, *>
    ): EmergencyGuideContent {
        fun text(key: String): String =
            data[key] as? String
                ?: error("Missing guide field: $key.")

        val steps = data["steps"] as? List<*>
            ?: error("Missing guide steps.")

        val audience = text("audience")
        check(audience in listOf("user", "support", "both")) {
            "Invalid guide audience."
        }

        return EmergencyGuideContent(
            title = text("title"),
            summary = text("summary"),
            audience = audience,
            steps = steps.map {
                it as? String ?: error("Invalid guide step.")
            },
            practiceScenario = text("practiceScenario")
        )
    }
}