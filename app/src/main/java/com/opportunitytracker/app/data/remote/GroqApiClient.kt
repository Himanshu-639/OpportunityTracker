package com.opportunitytracker.app.data.remote

import android.content.Context
import com.opportunitytracker.app.BuildConfig
import com.opportunitytracker.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GroqApiClient(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("groq_api_key", "")?.trim().orEmpty()
    }

    suspend fun extractOpportunity(rawText: String, sourceUrl: String? = null): Result<GroqOpportunityResponse> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Groq API key not configured. Please set it in Settings."))
        }

        val now = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val humanDateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        val currentDateTimeStr = dateFormat.format(now)
        val currentHumanDateStr = humanDateFormat.format(now)

        val systemPrompt = """
            You are an expert deadline and opportunity parser for an Android productivity app.
            Today's reference date and time is: $currentHumanDateStr ($currentDateTimeStr).

            Your job is to read the provided text or webpage content and extract key opportunity details:
            1. title: The clear, concise name of the opportunity (e.g. "Google Summer of Code 2027", "Stripe SWE Internship", "Hackathon").
            2. organization: The hosting company, university, or organizer.
            3. deadline: Exact deadline ISO-8601 formatted string (e.g. "2026-11-20T23:59:00"). If only a date is given without a specific hour, use "23:59:00" on that date. If no deadline is found, estimate 7 days from today.
            4. has_specific_time: true if a specific time (like 5:00 PM, 23:59) was specified in the text, false if only the date was specified.
            5. form_url: The direct link to apply, register, or fill the form. If not found in text, use the source URL: "${sourceUrl ?: ""}".
            6. source_url: "${sourceUrl ?: ""}".
            7. category: One of "Internship", "Job", "Hackathon", "Grant", "Event", "Fellowship", or "Form".
            8. summary: A crisp 1-2 sentence description explaining what the opportunity is and eligibility.
            9. tags: Array of 2 to 4 relevant tags with '#' prefix, e.g. ["#Remote", "#Internship", "#Paid", "#Hackathon", "#HighPriority"].

            Respond ONLY with a valid JSON object matching these fields. No markdown formatting, no backticks, no preamble.
        """.trimIndent()

        // List of models available on your Groq key
        val models = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b")

        var lastError: Exception? = null

        for (modelName in models) {
            val requestBodyObject = GroqChatRequest(
                model = modelName,
                messages = listOf(
                    GroqMessage(role = "system", content = systemPrompt),
                    GroqMessage(role = "user", content = rawText)
                ),
                responseFormat = GroqResponseFormat(type = "json_object"),
                temperature = 0.1
            )

            val requestJson = jsonParser.encodeToString(GroqChatRequest.serializer(), requestBodyObject)

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("User-Agent", "OpportunityTracker/1.0")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val bodyString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val apiError = try {
                        jsonParser.decodeFromString(GroqChatResponse.serializer(), bodyString).error?.message
                    } catch (_: Exception) { null }
                    lastError = Exception(apiError ?: "Groq API error (${response.code}): $bodyString")
                    continue // Try next model in list
                }

                val chatResponse = jsonParser.decodeFromString(GroqChatResponse.serializer(), bodyString)
                if (chatResponse.error != null) {
                    lastError = Exception(chatResponse.error.message)
                    continue
                }

                val jsonContent = chatResponse.choices.firstOrNull()?.message?.content
                if (jsonContent.isNullOrBlank()) {
                    lastError = Exception("Empty response from AI model ($modelName)")
                    continue
                }

                val extracted = jsonParser.decodeFromString(GroqOpportunityResponse.serializer(), jsonContent)
                return@withContext Result.success(extracted)
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Failed to extract opportunity details from Groq"))
    }
}
