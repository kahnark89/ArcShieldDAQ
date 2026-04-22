package com.arcshield.app.twin

import android.util.Log
import com.arcshield.app.data.schema.CiaerPlusEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for the Personal AI Twin. All model logic lives on the
 * Python corpus server (repo `arcshield-corpus`, not yet built).
 *
 * The Twin exposes two operations:
 *   - [retrievePrecedents] — RAG lookup of similar past events to ground
 *     the base model on Kahn's own prior decisions (active from event 1).
 *   - [requestGuidance]    — multi-turn reasoning against the LoRA-tuned
 *     model once the corpus reaches ~200 events.
 *
 * If [baseUrl] is blank (Gen 0, no server deployed), both calls no-op with
 * an empty result so capture flow is unaffected.
 */
@Singleton
class TwinClient @Inject constructor(
    private val json: Json,
) {
    private val client = OkHttpClient()

    // Will be surfaced in ConfigScreen in a later phase.
    private val baseUrl: String = ""

    suspend fun retrievePrecedents(draft: CiaerPlusEvent): List<Precedent> =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) return@withContext emptyList()
            try {
                val body = json.encodeToString(CiaerPlusEvent.serializer(), draft)
                    .toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url("$baseUrl/twin/precedents")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val text = response.body?.string().orEmpty()
                    json.decodeFromString(PrecedentsResponse.serializer(), text).precedents
                }
            } catch (t: Throwable) {
                Log.w(TAG, "precedent retrieval failed", t); emptyList()
            }
        }

    suspend fun requestGuidance(draft: CiaerPlusEvent): Guidance? =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) return@withContext null
            try {
                val body = json.encodeToString(CiaerPlusEvent.serializer(), draft)
                    .toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url("$baseUrl/twin/guidance")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string().orEmpty()
                    json.decodeFromString(Guidance.serializer(), text)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "guidance request failed", t); null
            }
        }

    @Serializable
    data class Precedent(
        @SerialName("event_id")     val eventId: String,
        @SerialName("summary")      val summary: String,
        @SerialName("similarity")   val similarity: Double,
        @SerialName("outcome_tag")  val outcomeTag: String,
    )

    @Serializable
    data class Guidance(
        @SerialName("narrative")        val narrative: String,
        @SerialName("suggested_action") val suggestedAction: String? = null,
        @SerialName("confidence")       val confidence: Double? = null,
        @SerialName("withheld")         val withheld: Boolean = false,
    )

    @Serializable
    private data class PrecedentsResponse(val precedents: List<Precedent>)

    private companion object {
        const val TAG = "TwinClient"
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
