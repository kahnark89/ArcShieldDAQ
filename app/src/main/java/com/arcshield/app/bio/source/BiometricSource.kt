package com.arcshield.app.bio.source

import com.arcshield.app.data.schema.BiometricSnapshot

/**
 * Abstraction over biometric sensing hardware. Gen 1 default is
 * [PixelWatchBiometrics] (Health Connect on Pixel Watch 4).
 * [EmpaticaBiometrics] is reserved for the §7.1 EDA validation sub-study.
 *
 * Implementations must return a [BiometricSnapshot] even when permissions
 * are missing or the underlying device is unavailable — the snapshot will
 * simply have null fields. The capture state machine handles missing
 * biometric data gracefully per the multi-signal corroboration principle.
 */
interface BiometricSource {
    suspend fun currentSnapshot(): BiometricSnapshot
}
