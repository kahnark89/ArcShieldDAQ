package com.arcshield.app.trigger.scorer

import com.arcshield.app.bio.source.BiometricSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

/**
 * Maps the operator's current HRV-RMSSD deviation to a 0..1 fusion score
 * per detection-spec §3.3. A deviation z-score of ±1.0σ from rolling
 * baseline maps to a full 1.0 signal; smaller deviations scale linearly.
 *
 * Returns null when the underlying [BiometricSource] has no usable
 * HRV reading yet (no device paired, insufficient RR samples to compute
 * RMSSD, or warm-up window not complete). Null means "signal unavailable";
 * the fusion engine treats it as 0 contribution.
 */
interface HrvScorer {
    suspend fun score(): Float?
}

@Singleton
class DefaultHrvScorer @Inject constructor(
    private val biometrics: BiometricSource,
) : HrvScorer {
    override suspend fun score(): Float? {
        val snap = biometrics.currentSnapshot()
        val z = snap.hrvDeviationZ ?: return null
        return min(1.0, abs(z)).toFloat()
    }
}
