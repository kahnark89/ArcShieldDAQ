package com.arcshield.app.sync

import android.util.Log
import com.arcshield.app.data.schema.CiaerPlusEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes events to the ArcShield on-premise Python corpus server. The server
 * repo (arcshield-corpus) does not exist yet — this client is intentionally
 * usable as a stub: if [baseUrl] is blank, [persist] no-ops and returns
 * success so the capture flow is not blocked.
 */
@Singleton
class LocalServerSink @Inject constructor(
    private val json: Json,
) : CorpusSink {

    private val client = OkHttpClient()

    // Will be wired to a user-configurable setting in a later phase.
    private val baseUrl: String = ""

    override suspend fun persist(event: CiaerPlusEvent): CorpusSink.Result = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext CorpusSink.Result.Success
        }
        try {
            val body = json.encodeToString(CiaerPlusEvent.serializer(), event)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/events")
                .header("content-type", "application/json")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                CorpusSink.Result.Success
            } else {
                CorpusSink.Result.Failure(IllegalStateException("Server ${response.code}"))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Server push failed", t)
            CorpusSink.Result.Failure(t)
        }
    }

    companion object {
        private const val TAG = "LocalServerSink"
    }
}
