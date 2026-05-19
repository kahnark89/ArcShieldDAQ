package com.arcshield.app.sensory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.min

/**
 * All five channel snapshots captured at a single instant.
 * The baseline bundle is captured at shift start (Pre-ENV); the event bundle
 * is captured at trigger time. Their difference is the SensoryDeltaBundle.
 *
 * Channels that fail at capture time contribute null — the pipeline survives
 * partial sensor coverage, which is essential on Gen 1 phone-mounted hardware.
 */
@Serializable
data class SensoryBundle(
    @SerialName("acoustic") val acoustic: AcousticSnapshot?,
    @SerialName("vibration") val vibration: VibrationSnapshot?,
    @SerialName("visual") val visual: VisualSnapshot?,
    @SerialName("olfactory") val olfactory: OlfactorySnapshot?,
    @SerialName("thermal") val thermal: ThermalSnapshot?,
    @SerialName("timestamp_ms") val timestampMs: Long
) {

    /**
     * Compute the per-channel delta from a baseline bundle.
     * Significance thresholds are channel-specific and tunable for PPVC Line 1
     * (see CLAUDE_CODE_HANDOFF_SENSORY.md → Tuning Constants).
     */
    fun deltaFrom(baseline: SensoryBundle): SensoryDeltaBundle = SensoryDeltaBundle(
        acoustic = computeAcousticDelta(baseline.acoustic, this.acoustic),
        vibration = computeVibrationDelta(baseline.vibration, this.vibration),
        visual = computeVisualDelta(baseline.visual, this.visual),
        olfactory = computeOlfactoryDelta(baseline.olfactory, this.olfactory),
        thermal = computeThermalDelta(baseline.thermal, this.thermal),
        timestampMs = timestampMs
    )

    private fun computeAcousticDelta(b: AcousticSnapshot?, e: AcousticSnapshot?): AcousticDelta? {
        if (b == null || e == null) return null
        val dFreq = e.dominantFrequencyHz - b.dominantFrequencyHz
        val dCent = e.spectralCentroidHz - b.spectralCentroidHz
        val dAmp = e.amplitudeDbfs - b.amplitudeDbfs
        // Normalize: 50 Hz freq shift OR 5 dB amplitude = magnitude 1.0
        val mag = min(1f, (abs(dFreq) / 50f + abs(dAmp) / 5f) / 2f)
        return AcousticDelta(dFreq, dCent, dAmp, mag, significant = mag >= ACOUSTIC_THRESHOLD)
    }

    private fun computeVibrationDelta(b: VibrationSnapshot?, e: VibrationSnapshot?): VibrationDelta? {
        if (b == null || e == null) return null
        val dFreq = e.dominantFrequencyHz - b.dominantFrequencyHz
        val dRms = e.rmsMagnitude - b.rmsMagnitude
        val dP2P = e.peakToPeak - b.peakToPeak
        val mag = min(1f, abs(dRms) / 2f)  // 2 m/s² RMS shift = magnitude 1.0
        return VibrationDelta(dFreq, dRms, dP2P, mag, significant = mag >= VIBRATION_THRESHOLD)
    }

    private fun computeVisualDelta(b: VisualSnapshot?, e: VisualSnapshot?): VisualDelta? {
        if (b == null || e == null) return null
        val dLum = e.meanLuminance - b.meanLuminance
        // motion_score is already 0..1 from the frame-diff layer
        val mag = min(1f, e.motionScore + abs(dLum) / 50f)
        return VisualDelta(e.motionScore, dLum, mag, significant = mag >= VISUAL_THRESHOLD)
    }

    private fun computeOlfactoryDelta(b: OlfactorySnapshot?, e: OlfactorySnapshot?): OlfactoryDelta? {
        if (b == null && e == null) return null
        val ba = b?.annotation
        val ea = e?.annotation
        val annotationChanged = ea != null && ea != ba && ea.isNotBlank()
        val vocDelta = if (b?.vocPpb != null && e?.vocPpb != null) e.vocPpb - b.vocPpb else null
        val mag = when {
            annotationChanged -> 1f
            vocDelta != null -> min(1f, abs(vocDelta) / 1000f)
            else -> 0f
        }
        return OlfactoryDelta(ba, ea, vocDelta, mag, significant = mag >= OLFACTORY_THRESHOLD)
    }

    private fun computeThermalDelta(b: ThermalSnapshot?, e: ThermalSnapshot?): ThermalDelta? {
        if (b == null && e == null) return null
        val dAmb = if (b?.ambientTempF != null && e?.ambientTempF != null)
            e.ambientTempF - b.ambientTempF else null
        val dSurf = if (b?.surfaceTempF != null && e?.surfaceTempF != null)
            e.surfaceTempF - b.surfaceTempF else null
        val src = dSurf ?: dAmb
        val mag = if (src != null) min(1f, abs(src) / 20f) else 0f  // 20°F shift = 1.0
        return ThermalDelta(dAmb, dSurf, e?.annotation, mag, significant = mag >= THERMAL_THRESHOLD)
    }

    companion object {
        // Channel-specific significance thresholds (0..1 normalized magnitude).
        // TUNE THESE on PPVC Line 1 after baseline corpus collection.
        const val ACOUSTIC_THRESHOLD = 0.30f
        const val VIBRATION_THRESHOLD = 0.30f
        const val VISUAL_THRESHOLD = 0.25f
        const val OLFACTORY_THRESHOLD = 0.50f  // annotation-driven; high to avoid noise
        const val THERMAL_THRESHOLD = 0.25f
    }
}
