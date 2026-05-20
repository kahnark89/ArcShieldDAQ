package com.arcshield.app.bio.source

import android.content.Context
import android.util.Log
import com.arcshield.app.bio.BaselineTracker
import com.arcshield.app.data.schema.BiometricSnapshot
import com.arcshield.app.data.schema.SourceDevice
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * BiometricSource backed by the Polar BLE SDK 7.x. Supports both:
 *   - Polar H10 chest strap — research-grade beat-to-beat HRV, no skin temp.
 *   - Polar Verity Sense armband — optical HRV (less precise than H10) plus
 *     skin temperature when the SDK exposes SKIN_TEMPERATURE for the device.
 *
 * The class owns the SDK lifecycle, connects to a single paired device per
 * shift, and exposes a [StateFlow] of [BiometricSnapshot] that the fusion
 * engine subscribes to at 200 ms cadence. HRV-RMSSD is computed locally from
 * the device's RR-interval stream — Polar gives us beat-to-beat raw RR-ms
 * lists per HR sample.
 *
 * SDK 7.0.0 migrated the public API from RxJava to native Kotlin Flow, so
 * streaming methods return [Flow] directly and Single/Completable methods are
 * now suspend functions. No RxJava bridge is needed in app code.
 */
@Singleton
class PolarBiometrics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseline: BaselineTracker,
) : BiometricSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _snapshots = MutableStateFlow(BiometricSnapshot())
    override val snapshots: StateFlow<BiometricSnapshot> = _snapshots.asStateFlow()

    private val rrWindowMs = ArrayDeque<Int>()

    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        context,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
        ),
    )

    private var deviceId: String? = null
    private var deviceModel: SourceDevice = SourceDevice.OTHER
    private var hrJob: Job? = null
    private var skinTempJob: Job? = null

    init {
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.i(TAG, "Connected: ${polarDeviceInfo.deviceId} (${polarDeviceInfo.name})")
                deviceModel = inferDeviceModel(polarDeviceInfo.name)
                _snapshots.update { it.copy(sourceDevice = deviceModel) }
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.i(TAG, "Disconnected: ${polarDeviceInfo.deviceId}")
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature,
            ) {
                if (feature == PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) {
                    subscribeHr(identifier)
                    scope.launch { subscribeSkinTempIfAvailable(identifier) }
                }
            }
        })
    }

    override suspend fun start(deviceId: String) = mutex.withLock {
        if (this.deviceId == deviceId) return@withLock
        stopInternal()
        this.deviceId = deviceId
        try {
            api.connectToDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e(TAG, "Invalid device id $deviceId", e)
            this.deviceId = null
        }
    }

    override suspend fun stop() = mutex.withLock { stopInternal() }

    override suspend fun currentSnapshot(): BiometricSnapshot = _snapshots.value

    // SDK 7.x: searchForDevice returns Flow<PolarDeviceInfo> directly; pass null
    // to discover all nearby Polar devices rather than filtering by name prefix.
    override fun scanForDevices(): Flow<BiometricSource.ScannedDevice> =
        api.searchForDevice(null).map { info ->
            BiometricSource.ScannedDevice(
                deviceId = info.deviceId,
                name     = info.name ?: "Unknown",
                rssi     = info.rssi,
            )
        }

    private fun stopInternal() {
        hrJob?.cancel(); hrJob = null
        skinTempJob?.cancel(); skinTempJob = null
        deviceId?.let { runCatching { api.disconnectFromDevice(it) } }
        rrWindowMs.clear()
        _snapshots.value = BiometricSnapshot()
        deviceId = null
    }

    // SDK 7.x: startHrStreaming returns Flow<PolarHrData> — collect in a coroutine Job
    // so it can be cancelled cleanly when the device disconnects or the app backgrounds.
    private fun subscribeHr(identifier: String) {
        hrJob?.cancel()
        hrJob = scope.launch {
            api.startHrStreaming(identifier).collect { data -> handleHr(data) }
        }
    }

    // SDK 7.x: getAvailableOnlineStreamDataTypes and requestStreamSettings are now
    // suspend functions returning values directly (was RxJava Single before).
    private suspend fun subscribeSkinTempIfAvailable(identifier: String) {
        runCatching {
            val types = api.getAvailableOnlineStreamDataTypes(identifier)
            val skinType = PolarBleApi.PolarDeviceDataType.entries
                .firstOrNull { it.name == "SKIN_TEMPERATURE" }
            if (skinType != null && skinType in types) {
                val setting = api.requestStreamSettings(identifier, skinType)
                skinTempJob?.cancel()
                skinTempJob = scope.launch {
                    api.startSkinTemperatureStreaming(identifier, setting.maxSettings())
                        .collect { sample ->
                            val tempC = sample.samples.lastOrNull()?.temperature?.toDouble()
                            _snapshots.update { it.copy(skinTempC = tempC) }
                        }
                }
            }
        }.onFailure { Log.d(TAG, "Skin temp not available on this device", it) }
    }

    private fun handleHr(data: PolarHrData) {
        val sample = data.samples.lastOrNull() ?: return
        val hrBpm = sample.hr.toDouble()
        if (sample.rrsMs.isNotEmpty()) {
            sample.rrsMs.forEach { rr ->
                rrWindowMs.addLast(rr)
                while (rrWindowMs.size > RR_WINDOW_BEATS) rrWindowMs.removeFirst()
            }
        }
        val rmssd = computeRmssd(rrWindowMs)

        baseline.recordHr(hrBpm)
        rmssd?.let { baseline.recordHrv(it) }

        _snapshots.update {
            it.copy(
                hrBpm         = hrBpm,
                hrDeviationZ  = baseline.hrZ(hrBpm),
                hrvRmssdMs    = rmssd,
                hrvDeviationZ = rmssd?.let { v -> baseline.hrvZ(v) },
                sourceDevice  = deviceModel,
            )
        }
    }

    private fun computeRmssd(rrs: Collection<Int>): Double? {
        if (rrs.size < MIN_RR_FOR_RMSSD) return null
        val list = rrs.toList()
        var sumSq = 0.0
        for (i in 1 until list.size) {
            val d = (list[i] - list[i - 1]).toDouble()
            sumSq += d * d
        }
        val meanSq = sumSq / (list.size - 1)
        return sqrt(meanSq)
    }

    private fun inferDeviceModel(name: String?): SourceDevice = when {
        name == null                                -> SourceDevice.OTHER
        name.contains("H10", ignoreCase = true)     -> SourceDevice.POLAR_H10
        name.contains("Verity", ignoreCase = true)  -> SourceDevice.POLAR_VERITY_SENSE
        else                                        -> SourceDevice.OTHER
    }

    companion object {
        private const val TAG = "PolarBiometrics"
        // RMSSD computed over last 60 beats (~1 min at rest). Tighter than the
        // clinical 5-min window but sufficient for the 200 ms fusion cadence.
        private const val RR_WINDOW_BEATS = 60
        private const val MIN_RR_FOR_RMSSD = 5
    }
}
