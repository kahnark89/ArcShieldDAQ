package com.arcshield.app.sensory.channels

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.arcshield.app.sensory.*
import com.arcshield.app.sensory.fft.FFTProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Vibration channel — captures from the phone's accelerometer, computes FFT
 * over a short window. Hardware ceiling on Pixel 9 Pro is ~100-200 Hz typical.
 *
 * Gen 1: phone-mounted on extruder housing (handheld validation acceptable for POC).
 * Gen 2: wearable IMU on hearing protection or wrist.
 *
 * Window-based capture identical in shape to AcousticChannel.
 */
class VibrationChannel(
    private val context: Context,
    private val windowMs: Int = 1000  // longer window than acoustic — lower sample rate
) : SensoryChannel<VibrationSnapshot> {

    override val channelId: SensoryChannelId = SensoryChannelId.VIBRATION

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var _availability: ChannelAvailability = ChannelAvailability.NOT_INITIALIZED
    override val availability: ChannelAvailability get() = _availability

    override suspend fun initialize(): ChannelAvailability {
        _availability = if (accelerometer != null)
            ChannelAvailability.AVAILABLE
        else ChannelAvailability.HARDWARE_UNAVAILABLE
        return _availability
    }

    override suspend fun captureBaseline(): VibrationSnapshot? = captureWindow()
    override suspend fun captureEvent(): VibrationSnapshot? = captureWindow()

    override fun significantDeltaStream(): Flow<SensoryDelta> = emptyFlow()

    override suspend fun release() {
        _availability = ChannelAvailability.NOT_INITIALIZED
    }

    private suspend fun captureWindow(): VibrationSnapshot? = withContext(Dispatchers.IO) {
        val sensor = accelerometer ?: return@withContext null

        val samples = mutableListOf<Float>()  // vector magnitude per sample
        val timestamps = mutableListOf<Long>()
        val startNs = System.nanoTime()
        val durationNs = windowMs * 1_000_000L

        suspendCancellableCoroutine<Unit> { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.values.size < 3) return
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    samples.add(sqrt(x * x + y * y + z * z))
                    timestamps.add(event.timestamp)
                    if (event.timestamp - startNs >= durationNs) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
        }

        if (samples.size < 4) return@withContext null

        // Remove gravity offset: detrend by subtracting mean
        val mean = samples.average().toFloat()
        val detrended = FloatArray(samples.size) { samples[it] - mean }

        // RMS and peak-to-peak
        var sumSq = 0.0
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (s in detrended) {
            sumSq += s * s
            if (s < minV) minV = s
            if (s > maxV) maxV = s
        }
        val rms = sqrt(sumSq / detrended.size).toFloat()
        val p2p = abs(maxV - minV)

        // Achieved sample rate
        val durSec = ((timestamps.last() - timestamps.first()) / 1_000_000_000.0).coerceAtLeast(0.001)
        val achievedRate = (samples.size / durSec).toInt()

        val mags = FFTProcessor.computeMagnitudes(detrended)
        val dominantHz = FFTProcessor.dominantFrequency(mags, achievedRate)
        val centroidHz = FFTProcessor.spectralCentroid(mags, achievedRate)
        val histogram = FFTProcessor.spectrumHistogram(mags, binCount = 32)

        VibrationSnapshot(
            timestampMs = System.currentTimeMillis(),
            dominantFrequencyHz = dominantHz,
            spectralCentroidHz = centroidHz,
            rmsMagnitude = rms,
            peakToPeak = p2p,
            spectrumHistogram = histogram,
            sampleRateHz = achievedRate
        )
    }
}
