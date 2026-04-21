package com.arcshield.app.llm

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG   = "GeminiLlmProvider"
private const val MODEL = "gemini-2.0-flash"
private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

/**
 * Gen 1 LLM provider backed by Google Gemini.
 *
 * Uses [MODEL] with inline image data for gauge reading.
 */
class GeminiLlmProvider(private val apiKey: String) : LlmProvider {

    override val type: LlmProviderType = LlmProviderType.GEMINI

    private val client = OkHttpClient()
    private val json   = Json { ignoreUnknownKeys = true }

    override suspend fun readGauges(imageBytes: ByteArray, hint: String): List<GaugeReading> =
        withContext(Dispatchers.IO) {
            val prompt = buildGaugePrompt(hint)
            val response = sendVisionRequest(imageBytes, prompt)
            parseGaugeResponse(response)
        }

    override suspend fun describeVisualAnchor(imageBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val prompt = "Describe the single most salient visual element in this image in one concise sentence, as if explaining what an industrial operator's eye was drawn to."
            sendVisionRequest(imageBytes, prompt).trim()
        }

    private fun sendVisionRequest(imageBytes: ByteArray, prompt: String): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val body = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "inline_data": {
                        "mime_type": "image/jpeg",
                        "data": "$b64"
                      }
                    },
                    {
                      "text": ${JsonPrimitive(prompt)}
                    }
                  ]
                }
              ],
              "generationConfig": {
                "maxOutputTokens": 1024
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("$BASE_URL?key=$apiKey")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw LlmProviderException("Gemini API error ${response.code}: ${response.body?.string()}")
        }
        return extractTextFromGeminiResponse(response.body?.string() ?: "")
    }

    private fun extractTextFromGeminiResponse(raw: String): String {
        return try {
            val parsed = json.decodeFromString<GeminiResponse>(raw)
            parsed.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull { it.text != null }?.text ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gemini response", e)
            ""
        }
    }

    private fun parseGaugeResponse(raw: String): List<GaugeReading> {
        val jsonText = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            json.decodeFromString<List<GaugeReadingDto>>(jsonText).map {
                GaugeReading(
                    instrumentId = it.instrumentId,
                    value        = it.value,
                    unit         = it.unit,
                    confidence   = it.confidence,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse gauge JSON from Gemini: $raw", e)
            emptyList()
        }
    }

    private fun buildGaugePrompt(hint: String): String {
        val hintClause = if (hint.isNotBlank()) " Context: $hint." else ""
        return """You are an industrial gauge reader.$hintClause Examine this image and identify all visible measurement instruments. Return ONLY a JSON array with no other text. Each element: {"instrument_id": "descriptive string", "value": "numeric string", "unit": "string or null", "confidence": 0.0-1.0}"""
    }

    @Serializable
    private data class GeminiResponse(val candidates: List<Candidate> = emptyList())

    @Serializable
    private data class Candidate(val content: Content)

    @Serializable
    private data class Content(val parts: List<Part>)

    @Serializable
    private data class Part(val text: String? = null)

    @Serializable
    private data class GaugeReadingDto(
        @SerialName("instrument_id") val instrumentId: String,
        val value: String,
        val unit: String? = null,
        val confidence: Float = 0.5f,
    )
}
