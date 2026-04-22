package com.arcshield.app.bio.source

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.arcshield.app.bio.BaselineTracker
import com.arcshield.app.data.schema.BiometricSnapshot
import com.arcshield.app.data.schema.SourceDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HRV-centric biometric snapshot from Pixel Watch via Health Connect.
 *
 * Raw EDA is not available through this path — see whitepaper §5.2. HR and
 * HRV-RMSSD are the primary operational channels; body_response is reserved
 * for a later corroboration pass from Fitbit Body Response notifications.
 *
 * Permissions are listed in the manifest. Runtime permission granting uses
 * the Health Connect PermissionController contract — wired in the
 * ConfigScreen (later phase). If permissions are missing this source returns
 * a snapshot with null fields rather than throwing.
 */
@Singleton
class PixelWatchBiometrics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseline: BaselineTracker,
) : BiometricSource {

    private val client: HealthConnectClient? = try {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    } catch (e: Throwable) {
        Log.w(TAG, "Health Connect not available", e); null
    }

    override suspend fun currentSnapshot(): BiometricSnapshot {
        val c   = client ?: return empty()
        val end = Instant.now()
        val start = end.minusSeconds(WINDOW_SEC)

        val hr = runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType      = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records.flatMap { it.samples }.lastOrNull()?.beatsPerMinute?.toDouble()
        }.getOrNull()

        val hrv = runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType      = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records.lastOrNull()?.heartRateVariabilityMillis
        }.getOrNull()

        hr?.let { baseline.recordHr(it) }
        hrv?.let { baseline.recordHrv(it) }

        return BiometricSnapshot(
            hrBpm          = hr,
            hrDeviationZ   = hr?.let { baseline.hrZ(it) },
            hrvRmssdMs     = hrv,
            hrvDeviationZ  = hrv?.let { baseline.hrvZ(it) },
            sourceDevice   = SourceDevice.PIXEL_WATCH_4,
        )
    }

    private fun empty() = BiometricSnapshot(sourceDevice = SourceDevice.PIXEL_WATCH_4)

    companion object {
        private const val TAG        = "PixelWatchBiometrics"
        private const val WINDOW_SEC = 300L

        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        )
    }
}
