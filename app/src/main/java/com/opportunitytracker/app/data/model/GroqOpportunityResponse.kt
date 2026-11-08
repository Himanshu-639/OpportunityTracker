package com.opportunitytracker.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqOpportunityResponse(
    @SerialName("title")
    val title: String,
    @SerialName("organization")
    val organization: String? = null,
    @SerialName("deadline")
    val deadline: String? = null, // Formatted as ISO-8601 string e.g. "2026-11-15T23:59:00" or "2026-11-15"
    @SerialName("has_specific_time")
    val hasSpecificTime: Boolean = false,
    @SerialName("form_url")
    val formUrl: String? = null,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    @SerialName("category")
    val category: String? = "General",
    @SerialName("summary")
    val summary: String? = null,
    @SerialName("tags")
    val tags: List<String>? = emptyList()
)

@Serializable
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    @SerialName("response_format")
    val responseFormat: GroqResponseFormat? = null,
    val temperature: Double = 0.1
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class GroqChatResponse(
    val choices: List<GroqChoice> = emptyList(),
    val error: GroqApiError? = null
)

@Serializable
data class GroqApiError(
    val message: String = "API error",
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class GroqChoice(
    val message: GroqMessage
)
