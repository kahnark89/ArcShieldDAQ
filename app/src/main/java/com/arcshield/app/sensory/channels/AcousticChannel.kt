package com.arcshield.app.sensory.channels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.arcshield.app.sensory.*
import com.arcshield.app.sensory.fft.FFTProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Acoustic channel — captures from the device microphone, computes FFT-derived
 * features (dominant frequency, spectral centroid, amplitude, 32-bin histogram).
 *
 * Gen 1: phone microphone (44.1 kHz, mono).
 * Gen 2: Meta glasses microphone via Wearables Device Access Toolkit
 *        (drop in a separate provider; this channel's API doesn't change).
 *
 * Capture is window-based — short (~500 ms) recording then released. No continuous
 * streaming on Gen 1 to preserve battery; AudioRecord opens only at capture time.
 */
class AcousticChannel(
    private val context: Context,
    private val sampleRate: Int = 44_100,
    private val windowMs: Int = 500
) : SensoryChannel<AcousticSnapshot> {

    override val channelId: SensoryChannelId = SensoryChannelId.ACOUSTIC

    private var _availability: ChannelAvailability = ChannelAvailability.NOT_INITIALIZED
    override val availability: ChannelAvailability get() = _availability

    override suspend fun initialize(): ChannelAvailability {
        _availability = if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) ChannelAvailability.AVAILABLE
        else ChannelAvailability.PERMISSION_DENIED
        return _availability
    }

    override suspend fun captureBaseline(): AcousticSnapshot? = captureWindow()
    override suspend fun captureEvent(): AcousticSnapshot? = captureWindow()

    /** Passive monitoring not implemented for Gen 1 acoustic — power cost too high. */
    override fun significantDeltaStream(): Flow<SensoryDelta> = emptyFlow()

    override suspend fun release() {
        _availability = ChannelAvailability.NOT_INITIALIZED
    }

    private suspend fun captureWindow(): AcousticSnapshot? = withContext(Dispatchers.IO) {
        if (_availability != ChannelAvailability.AVAILABLE) return@withContext null
        val numSamples = (sampleRate * windowMs / 1000)
        val bufSize = max(
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            numSamples * 2
        )
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        } catch (e: SecurityException) {
            _availability = ChannelAvailability.PERMISSION_DENIED
            return@withContext null
        } catch (e: IllegalArgumentException) {
            _availability = ChannelAvailability.HARDWARE_UNAVAILABLE
            return@withContext null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            _availability = ChannelAvailability.HARDWARE_UNAVAILABLE
            return@withContext null
        }

        val shortBuf = ShortArray(numSamples)
        recorder.startRecording()
        var read = 0
        while (read < numSamples) {
            val r = recorder.read(shortBuf, read, numSamples - read)
            if (r <= 0) break
            read += r
        }
        recorder.stop()
        recorder.release()

        if (read == 0) return@withContext null

        // Convert PCM 16-bit to float [-1.0, 1.0]
        val samples = FloatArray(read) { i -> shortBuf[i] / 32768f }

        // Amplitude (dBFS) from RMS
        var sumSq = 0.0
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / samples.size).toFloat()
        val dbfs = if (rms > 0) 20 * log10(rms.toDouble()).toFloat() else -120f

        // FFT-derived features
        val mags = FFTProcessor.computeMagnitudes(samples)
        val dominantHz = FFTProcessor.dominantFrequency(mags, sampleRate)
        val centroidHz = FFTProcessor.spectralCentroid(mags, sampleRate)
        val histogram = FFTProcessor.spectrumHistogram(mags, binCount = 32)

        AcousticSnapshot(
            timestampMs = System.currentTimeMillis(),
            dominantFrequencyHz = dominantHz,
            spectralCentroidHz = centroidHz,
            amplitudeDbfs = dbfs,
            spectrumHistogram = histogram,
            windowMs = windowMs
        )
    }
}
