package com.arcshield.app.preenv

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG              = "AmbientTempTracker"
private const val POLL_INTERVAL_MS = 15 * 60 * 1000L

@Singleton
class AmbientTempTracker @Inject constructor(
    private val prefs: PreEnvPrefsStore,
) {
    data class Reading(val fahrenheit: Double, val source: AmbientTempSource)

    private val _current = MutableStateFlow<Reading?>(null)
    val current: StateFlow<Reading?> = _current.asStateFlow()

    private val client = OkHttpClient()
    private val json   = Json { ignoreUnknownKeys = true }
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val lat = prefs.weatherLatitude.first()
            val lon = prefs.weatherLongitude.first()
            val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m&temperature_unit=fahrenheit"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Weather API error ${response.code}")
                return@withContext
            }
            val body   = response.body?.string() ?: return@withContext
            val parsed = json.decodeFromString<OpenMeteoResponse>(body)
            _current.value = Reading(parsed.current.temperature, AmbientTempSource.WEATHER_API)
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed; retaining last-known reading", e)
        }
    }

    @Serializable
    private data class OpenMeteoResponse(val current: Current)

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature: Double,
    )
}
