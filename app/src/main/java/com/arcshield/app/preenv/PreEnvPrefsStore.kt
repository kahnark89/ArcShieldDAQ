package com.arcshield.app.preenv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preEnvDataStore: DataStore<Preferences> by preferencesDataStore(name = "preenv_prefs")

@Singleton
class PreEnvPrefsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.preEnvDataStore

    val operatorId:              Flow<String?> = ds.data.map { it[OPERATOR_ID] }
    val shiftSessionId:          Flow<String?> = ds.data.map { it[SHIFT_SESSION_ID] }
    val shiftStartedAtEpochMs:   Flow<Long?>   = ds.data.map { it[SHIFT_STARTED_AT] }
    val shiftDurationHours:      Flow<Int>     = ds.data.map { it[SHIFT_DURATION_HOURS] ?: DEFAULT_SHIFT_HOURS }
    val batchId:                 Flow<String?> = ds.data.map { it[BATCH_ID] }
    val batchStartedAtEpochMs:   Flow<Long?>   = ds.data.map { it[BATCH_STARTED_AT] }
    val weatherLatitude:         Flow<Double>  = ds.data.map { it[WEATHER_LAT] ?: DEFAULT_LAT }
    val weatherLongitude:        Flow<Double>  = ds.data.map { it[WEATHER_LON] ?: DEFAULT_LON }
    val polarDeviceId:           Flow<String?> = ds.data.map { it[POLAR_DEVICE_ID] }
    val sensoryBaselineJson:     Flow<String?> = ds.data.map { it[SENSORY_BASELINE_JSON] }

    suspend fun startShift(operatorId: String, sessionId: String, startedAtEpochMs: Long) {
        ds.edit { p ->
            p[OPERATOR_ID]      = operatorId
            p[SHIFT_SESSION_ID] = sessionId
            p[SHIFT_STARTED_AT] = startedAtEpochMs
            p.remove(BATCH_ID)
            p.remove(BATCH_STARTED_AT)
        }
    }

    suspend fun endShift() {
        ds.edit { p ->
            p.remove(SHIFT_SESSION_ID)
            p.remove(SHIFT_STARTED_AT)
            p.remove(BATCH_ID)
            p.remove(BATCH_STARTED_AT)
        }
    }

    suspend fun recordBatch(batchId: String, startedAtEpochMs: Long) {
        ds.edit { p ->
            p[BATCH_ID]         = batchId
            p[BATCH_STARTED_AT] = startedAtEpochMs
        }
    }

    suspend fun setWeatherLocation(latitude: Double, longitude: Double) {
        ds.edit { p ->
            p[WEATHER_LAT] = latitude
            p[WEATHER_LON] = longitude
        }
    }

    suspend fun setShiftDurationHours(hours: Int) {
        ds.edit { p -> p[SHIFT_DURATION_HOURS] = hours }
    }

    suspend fun savePolarDeviceId(id: String) {
        ds.edit { p -> p[POLAR_DEVICE_ID] = id }
    }

    suspend fun currentPolarDeviceId(): String? = polarDeviceId.firstOrNull()

    suspend fun saveSensoryBaseline(json: String) {
        ds.edit { p -> p[SENSORY_BASELINE_JSON] = json }
    }

    companion object {
        private val OPERATOR_ID          = stringPreferencesKey("operator_id")
        private val SHIFT_SESSION_ID     = stringPreferencesKey("shift_session_id")
        private val SHIFT_STARTED_AT     = longPreferencesKey("shift_started_at")
        private val SHIFT_DURATION_HOURS = intPreferencesKey("shift_duration_hours")
        private val BATCH_ID             = stringPreferencesKey("batch_id")
        private val BATCH_STARTED_AT     = longPreferencesKey("batch_started_at")
        private val WEATHER_LAT          = doublePreferencesKey("weather_lat")
        private val WEATHER_LON          = doublePreferencesKey("weather_lon")
        private val POLAR_DEVICE_ID      = stringPreferencesKey("polar_device_id")
        private val SENSORY_BASELINE_JSON = stringPreferencesKey("sensory_baseline_json")

        private const val DEFAULT_SHIFT_HOURS = 8
        // Hollowell Industries, Helena-West Helena, AR (72390)
        private const val DEFAULT_LAT = 34.5290
        private const val DEFAULT_LON = -90.5911
    }
}
