package com.arcshield.app.sensory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One instantaneous reading from a single sensory channel.
 * All five snapshot types share a common timestamp_ms field for cross-channel alignment.
 */
sealed interface SensorySnapshot {
    val timestampMs: Long
    val channelId: SensoryChannelId
}

// ──────────────────────────────────────────────────────────────
// 1. Acoustic — microphone FFT
// ──────────────────────────────────────────────────────────────

@Serializable
data class AcousticSnapshot(
    @SerialName("timestamp_ms") override val timestampMs: Long,
    /** Dominant frequency in Hz (peak FFT bin). */
    @SerialName("dominant_frequency_hz") val dominantFrequencyHz: Float,
    /** Spectral centroid in Hz — energy-weighted "brightness." */
    @SerialName("spectral_centroid_hz") val spectralCentroidHz: Float,
    /** Broadband amplitude in dBFS (0 = clip, -inf = silent). */
    @SerialName("amplitude_dbfs") val amplitudeDbfs: Float,
    /** 32-bin spectrum histogram for compact storage. */
    @SerialName("spectrum_histogram") val spectrumHistogram: FloatArray,
    /** Capture window duration in milliseconds. */
    @SerialName("window_ms") val windowMs: Int
) : SensorySnapshot {
    override val channelId: SensoryChannelId get() = SensoryChannelId.ACOUSTIC
}

// ──────────────────────────────────────────────────────────────
// 2. Vibration — accelerometer FFT (~100 Hz ceiling)
// ──────────────────────────────────────────────────────────────

@Serializable
data class VibrationSnapshot(
    @SerialName("timestamp_ms") override val timestampMs: Long,
    /** Dominant frequency in Hz. */
    @SerialName("dominant_frequency_hz") val dominantFrequencyHz: Float,
    /** Spectral centroid in Hz. */
    @SerialName("spectral_centroid_hz") val spectralCentroidHz: Float,
    /** RMS magnitude in m/s² (vector magnitude across x/y/z). */
    @SerialName("rms_magnitude") val rmsMagnitude: Float,
    /** Peak-to-peak amplitude in m/s². */
    @SerialName("peak_to_peak") val peakToPeak: Float,
    /** 32-bin spectrum histogram. */
    @SerialName("spectrum_histogram") val spectrumHistogram: FloatArray,
    /** Sample rate achieved in Hz (varies by device — typically 50-200). */
    @SerialName("sample_rate_hz") val sampleRateHz: Int
) : SensorySnapshot {
    override val channelId: SensoryChannelId get() = SensoryChannelId.VIBRATION
}

// ──────────────────────────────────────────────────────────────
// 3. Visual — frame reference + lightweight features
// ──────────────────────────────────────────────────────────────

@Serializable
data class VisualSnapshot(
    @SerialName("timestamp_ms") override val timestampMs: Long,
    /** Path to the saved full-resolution frame (JPEG). */
    @SerialName("frame_path") val framePath: String,
    /** Mean luminance 0-255. */
    @SerialName("mean_luminance") val meanLuminance: Float,
    /** Frame-differencing motion score 0..1 vs immediately preceding frame. */
    @SerialName("motion_score") val motionScore: Float,
    /** Width and height in pixels. */
    @SerialName("width_px") val widthPx: Int,
    @SerialName("height_px") val heightPx: Int,
    /** Source label — "phone_rear", "meta_glasses_pov", etc. — for provider traceability. */
    @SerialName("source_label") val sourceLabel: String
) : SensorySnapshot {
    override val channelId: SensoryChannelId get() = SensoryChannelId.VISUAL
}

// ──────────────────────────────────────────────────────────────
// 4. Olfactory — Gen 1: operator annotation; Gen 2: VOC sensor
// ──────────────────────────────────────────────────────────────

@Serializable
data class OlfactorySnapshot(
    @SerialName("timestamp_ms") override val timestampMs: Long,
    /** Free-text or quick-select label ("burnt PVC", "normal", "metallic", "none"). */
    @SerialName("annotation") val annotation: String?,
    /** Intensity 0..3 if operator provided it (0 = absent, 3 = overwhelming). */
    @SerialName("intensity") val intensity: Int?,
    /** Gen 2: VOC concentration in ppb when sensor available. Null on Gen 1. */
    @SerialName("voc_ppb") val vocPpb: Float?,
    /** Whether this snapshot originated from operator wake-word ("smell that"). */
    @SerialName("wake_word_triggered") val wakeWordTriggered: Boolean
) : SensorySnapshot {
    override val channelId: SensoryChannelId get() = SensoryChannelId.OLFACTORY
}

// ──────────────────────────────────────────────────────────────
// 5. Thermal — Gen 1: ambient proxy; Gen 2: IR thermometer
// ──────────────────────────────────────────────────────────────

@Serializable
data class ThermalSnapshot(
    @SerialName("timestamp_ms") override val timestampMs: Long,
    /** Ambient temperature in °F (Open-Meteo proxy on Gen 1, IR sensor on Gen 2). */
    @SerialName("ambient_temp_f") val ambientTempF: Float?,
    /** Surface/target temperature in °F when IR thermometer available. Null on Gen 1. */
    @SerialName("surface_temp_f") val surfaceTempF: Float?,
    /** Operator annotation (e.g., "barrel runs hot today"). */
    @SerialName("annotation") val annotation: String?,
    /** Source label — "open_meteo", "bluetooth_ir_X", etc. */
    @SerialName("source_label") val sourceLabel: String
) : SensorySnapshot {
    override val channelId: SensoryChannelId get() = SensoryChannelId.THERMAL
}
