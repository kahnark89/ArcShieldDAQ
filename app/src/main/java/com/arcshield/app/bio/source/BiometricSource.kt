package com.arcshield.app.bio.source

import com.arcshield.app.data.schema.BiometricSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over biometric sensing hardware. The Gen 1 (and only) operational
 * implementation is [PolarBiometrics], which talks to a Polar H10 chest strap
 * or Polar Verity Sense armband over BLE.
 *
 * Pixel Watch was evaluated and rejected because the device exposes HRV only
 * during sleep with no path to raw inter-beat intervals, which is fatal for
 * the on-shift fusion trigger. Empatica was a placeholder for a cEDA validation
 * sub-study that may return later as its own implementation if hardware
 * arrives — the abstraction is preserved for that case.
 *
 * Implementations must return a [BiometricSnapshot] even when the device is
 * not connected — the snapshot will simply have null fields. The capture state
 * machine and fusion engine handle missing biometric data gracefully.
 */
interface BiometricSource {
    /**
     * Latest cached snapshot. Streamed so the fusion engine can observe HRV
     * changes at its 200 ms cadence without re-querying the device.
     */
    val snapshots: StateFlow<BiometricSnapshot>

    /**
     * Convenience accessor: read the current value of [snapshots]. Identical
     * to `snapshots.value`; kept as a suspend function so callers can swap in
     * a remote source later without re-shaping the call site.
     */
    suspend fun currentSnapshot(): BiometricSnapshot

    /**
     * Connection lifecycle. The DI graph holds a singleton; the foreground UI
     * is responsible for calling [start] when the operator opens the app and
     * [stop] when it goes to background or the operator signs out.
     *
     * [deviceId] is an opaque identifier for the underlying transport. For
     * [PolarBiometrics] this is the Polar device ID printed on the strap and
     * persisted in `ConfigStore` after onboarding pair.
     */
    suspend fun start(deviceId: String)
    suspend fun stop()

    /**
     * Scans for nearby BLE devices compatible with this biometric source.
     * Emits discovered devices as they are found. Callers should cancel the
     * collection after a suitable timeout (e.g. 10 seconds).
     */
    fun scanForDevices(): Flow<ScannedDevice>

    /** Transport-agnostic descriptor for a discovered biometric device. */
    data class ScannedDevice(val deviceId: String, val name: String, val rssi: Int)
}
