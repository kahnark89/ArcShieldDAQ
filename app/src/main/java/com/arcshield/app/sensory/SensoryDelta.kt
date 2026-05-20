package com.arcshield.app.sensory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Per-channel delta — what changed between shift-start baseline (Pre-ENV)
 * and the event-moment snapshot. Drives PIE (Perceptual Inference Elicitation)
 * prompt generation: "Sound shifted higher" → "Was it the sound?"
 */
sealed interface SensoryDelta {
    val channelId: SensoryChannelId
    /** Magnitude of change, normalized 0..1 within channel-specific thresholds. */
    val magnitude: Float
    /** Whether this delta crossed the channel's significance threshold. */
    val significant: Boolean
    /** Human-readable hint for the PIE prompt template. */
    fun elicitationHint(): String
}

@Serializable
data class AcousticDelta(
    @SerialName("dominant_frequency_hz_delta") val dominantFrequencyHzDelta: Float,
    @SerialName("spectral_centroid_hz_delta") val spectralCentroidHzDelta: Float,
    @SerialName("amplitude_dbfs_delta") val amplitudeDbfsDelta: Float,
    @SerialName("magnitude") override val magnitude: Float,
    @SerialName("significant") override val significant: Boolean
) : SensoryDelta {
    override val channelId: SensoryChannelId get() = SensoryChannelId.ACOUSTIC
    override fun elicitationHint(): String {
        val freqDir = when {
            dominantFrequencyHzDelta > 10 -> "higher"
            dominantFrequencyHzDelta < -10 -> "lower"
            else -> "shifted"
        }
        val ampDir = when {
            amplitudeDbfsDelta > 3 -> "louder"
            amplitudeDbfsDelta < -3 -> "quieter"
            else -> null
        }
        return buildString {
            append("Sound: tone $freqDir (${dominantFrequencyHzDelta.toInt()} Hz)")
            if (ampDir != null) append(", $ampDir (${"%.1f".format(amplitudeDbfsDelta)} dB)")
        }
    }
}

@Serializable
data class VibrationDelta(
    @SerialName("dominant_frequency_hz_delta") val dominantFrequencyHzDelta: Float,
    @SerialName("rms_magnitude_delta") val rmsMagnitudeDelta: Float,
    @SerialName("peak_to_peak_delta") val peakToPeakDelta: Float,
    @SerialName("magnitude") override val magnitude: Float,
    @SerialName("significant") override val significant: Boolean
) : SensoryDelta {
    override val channelId: SensoryChannelId get() = SensoryChannelId.VIBRATION
    override fun elicitationHint(): String {
        val intensity = when {
            rmsMagnitudeDelta > 1.0 -> "stronger"
            rmsMagnitudeDelta < -1.0 -> "smoother"
            else -> "shifted"
        }
        return "Vibration: $intensity (${"%.2f".format(rmsMagnitudeDelta)} m/s²)"
    }
}

@Serializable
data class VisualDelta(
    @SerialName("motion_score") val motionScore: Float,
    @SerialName("luminance_delta") val luminanceDelta: Float,
    @SerialName("magnitude") override val magnitude: Float,
    @SerialName("significant") override val significant: Boolean
) : SensoryDelta {
    override val channelId: SensoryChannelId get() = SensoryChannelId.VISUAL
    override fun elicitationHint(): String = when {
        motionScore > 0.3 -> "Visual: something moved or changed in view"
        abs(luminanceDelta) > 20 -> "Visual: lighting changed"
        else -> "Visual: scene shifted"
    }
}

@Serializable
data class OlfactoryDelta(
    @SerialName("baseline_annotation") val baselineAnnotation: String?,
    @SerialName("event_annotation") val eventAnnotation: String?,
    @SerialName("voc_ppb_delta") val vocPpbDelta: Float?,
    @SerialName("magnitude") override val magnitude: Float,
    @SerialName("significant") override val significant: Boolean
) : SensoryDelta {
    override val channelId: SensoryChannelId get() = SensoryChannelId.OLFACTORY
    override fun elicitationHint(): String =
        if (eventAnnotation != null && eventAnnotation != baselineAnnotation)
            "Smell: \"$eventAnnotation\" (baseline: ${baselineAnnotation ?: "none"})"
        else "Smell: changed from baseline"
}

@Serializable
data class ThermalDelta(
    @SerialName("ambient_temp_f_delta") val ambientTempFDelta: Float?,
    @SerialName("surface_temp_f_delta") val surfaceTempFDelta: Float?,
    @SerialName("event_annotation") val eventAnnotation: String?,
    @SerialName("magnitude") override val magnitude: Float,
    @SerialName("significant") override val significant: Boolean
) : SensoryDelta {
    override val channelId: SensoryChannelId get() = SensoryChannelId.THERMAL
    override fun elicitationHint(): String {
        val src = surfaceTempFDelta ?: ambientTempFDelta
        return when {
            src == null -> "Thermal: changed from baseline"
            src > 5 -> "Thermal: hotter (+${"%.1f".format(src)}°F)"
            src < -5 -> "Thermal: cooler (${"%.1f".format(src)}°F)"
            else -> "Thermal: ${"%.1f".format(src)}°F shift"
        }
    }
}

/**
 * All five channel deltas at a single capture instant.
 * Feeds PIE prompt generation. Null channels (unavailable at event time) are skipped.
 */
@Serializable
data class SensoryDeltaBundle(
    @SerialName("acoustic") val acoustic: AcousticDelta?,
    @SerialName("vibration") val vibration: VibrationDelta?,
    @SerialName("visual") val visual: VisualDelta?,
    @SerialName("olfactory") val olfactory: OlfactoryDelta?,
    @SerialName("thermal") val thermal: ThermalDelta?,
    @SerialName("timestamp_ms") val timestampMs: Long
) {
    /** All deltas (non-null), sorted by magnitude descending. */
    fun all(): List<SensoryDelta> = listOfNotNull(acoustic, vibration, visual, olfactory, thermal)
        .sortedByDescending { it.magnitude }

    /** Only deltas that crossed their significance threshold. */
    fun significant(): List<SensoryDelta> = all().filter { it.significant }

    /**
     * Human-readable elicitation hints for the PIE prompt template.
     * Returns at most [limit] entries, ordered by magnitude.
     * Example: ["Sound: tone higher (85 Hz)", "Vibration: stronger (1.2 m/s²)"]
     */
    fun toElicitationHints(limit: Int = 3): List<String> =
        significant().take(limit).map { it.elicitationHint() }

    /** True if any channel crossed its significance threshold. */
    fun hasAnySignificant(): Boolean = all().any { it.significant }
}
