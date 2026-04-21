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

private const val TAG = "ClaudeLlmProvider"
private const val API_URL = "https://api.anthropic.com/v1/messages"
private const val MODEL   = "claude-sonnet-4-6"

/**
 * Gen 1 LLM provider backed by Anthropic's Claude Messages API.
 *
 * Uses [MODEL] with vision for gauge reading. The API key is supplied at
 * construction time from [ApiKeyStore] — it is never stored as a field
 * beyond the lifetime of this object.
 */
class ClaudeLlmProvider(private val apiKey: String) : LlmProvider {

    override val type: LlmProviderType = LlmProviderType.CLAUDE

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
            val response = sendVisionRequest(imageBytes, prompt)
            response.trim()
        }

    private fun sendVisionRequest(imageBytes: ByteArray, prompt: String): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val body = """
            {
              "model": "$MODEL",
              "max_tokens": 1024,
              "messages": [
                {
                  "role": "user",
                  "content": [
                    {
                      "type": "image",
                      "source": {
                        "type": "base64",
                        "media_type": "image/jpeg",
                        "data": "$b64"
                      }
                    },
                    {
                      "type": "text",
                      "text": ${JsonPrimitive(prompt)}
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw LlmProviderException("Claude API error ${response.code}: ${response.body?.string()}")
        }
        return extractTextFromClaudeResponse(response.body?.string() ?: "")
    }

    private fun extractTextFromClaudeResponse(raw: String): String {
        return try {
            val parsed = json.decodeFromString<ClaudeResponse>(raw)
            parsed.content.firstOrNull { it.type == "text" }?.text ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Claude response", e)
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
            Log.w(TAG, "Failed to parse gauge JSON from Claude: $raw", e)
            emptyList()
        }
    }

    private fun buildGaugePrompt(hint: String): String {
        val hintClause = if (hint.isNotBlank()) " Context: $hint." else ""
        return """You are an industrial gauge reader.$hintClause Examine this image and identify all visible measurement instruments. Return ONLY a JSON array with no other text. Each element: {"instrument_id": "descriptive string", "value": "numeric string", "unit": "string or null", "confidence": 0.0-1.0}"""
    }

    @Serializable
    private data class ClaudeResponse(val content: List<ContentBlock>)

    @Serializable
    private data class ContentBlock(val type: String, val text: String = "")

    @Serializable
    private data class GaugeReadingDto(
        @SerialName("instrument_id") val instrumentId: String,
        val value: String,
        val unit: String? = null,
        val confidence: Float = 0.5f,
    )
}
