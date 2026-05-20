package com.arcshield.app.sensory.providers.impl

import com.arcshield.app.sensory.ChannelAvailability
import com.arcshield.app.sensory.providers.ThermalAmbientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Gen 1 thermal provider — polls Open-Meteo for ambient outdoor temperature.
 *
 * PPVC Line 1 is an open-bay facility; outdoor temperature is a valid
 * shop-floor thermal proxy. Results are cached for 15 minutes to avoid
 * hammering the free-tier API on each sensory capture.
 */
class OpenMeteoThermalProvider(
    private val lat: Double = 34.5294,
    private val lon: Double = -90.5912,
    private val client: OkHttpClient = OkHttpClient(),
) : ThermalAmbientProvider {

    private val json = Json { ignoreUnknownKeys = true }

    private var cachedTempF: Float? = null
    private var lastFetchMs: Long = 0

    override suspend fun initialize(): ChannelAvailability = ChannelAvailability.AVAILABLE

    override suspend fun currentAmbientTempF(): Float? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedTempF != null && now - lastFetchMs < CACHE_TTL_MS) return@withContext cachedTempF

        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current_weather=true" +
                "&temperature_unit=fahrenheit"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { it.body?.string() } ?: return@runCatching null
            val response = json.decodeFromString(OpenMeteoResponse.serializer(), body)
            response.currentWeather?.temperature?.also { tempF ->
                cachedTempF = tempF
                lastFetchMs = now
            }
        }.getOrNull()
    }

    override fun sourceLabel(): String = "open_meteo"

    override suspend fun release() {
        cachedTempF = null
        lastFetchMs = 0
    }

    companion object {
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
    }

    @Serializable
    private data class OpenMeteoResponse(
        val current_weather: CurrentWeather? = null,
    ) {
        @Serializable
        data class CurrentWeather(val temperature: Float? = null)
    }
}
