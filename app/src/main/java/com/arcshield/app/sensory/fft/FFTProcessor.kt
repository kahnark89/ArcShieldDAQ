package com.arcshield.app.sensory.fft

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Cooley-Tukey FFT processor.
 * Shared utility for AcousticChannel and VibrationChannel.
 * No external dependencies — runs on any Android API level.
 */
object FFTProcessor {

    /** Magnitude spectrum from time-domain samples. Input zero-padded to next power of 2. */
    fun computeMagnitudes(samples: FloatArray): FloatArray {
        val n = nextPowerOf2(samples.size)
        val real = FloatArray(n) { if (it < samples.size) samples[it] else 0f }
        val imag = FloatArray(n)
        fft(real, imag)
        val half = n / 2
        return FloatArray(half) { i ->
            sqrt((real[i] * real[i] + imag[i] * imag[i]).toDouble()).toFloat()
        }
    }

    /** Frequency bin with highest magnitude (skips DC). */
    fun dominantFrequency(magnitudes: FloatArray, sampleRate: Int): Float {
        var maxIdx = 1
        var maxMag = 0f
        for (i in 1 until magnitudes.size) {
            if (magnitudes[i] > maxMag) {
                maxMag = magnitudes[i]
                maxIdx = i
            }
        }
        val binHz = sampleRate.toFloat() / (magnitudes.size * 2)
        return maxIdx * binHz
    }

    /** Energy-weighted average frequency — perceived "brightness." */
    fun spectralCentroid(magnitudes: FloatArray, sampleRate: Int): Float {
        val binHz = sampleRate.toFloat() / (magnitudes.size * 2)
        var weightedSum = 0.0
        var totalMag = 0.0
        for (i in magnitudes.indices) {
            weightedSum += i * binHz * magnitudes[i]
            totalMag += magnitudes[i]
        }
        return if (totalMag > 0) (weightedSum / totalMag).toFloat() else 0f
    }

    /** Reduce spectrum to bin-count buckets (log-spaced) — for compact storage in CIAER record. */
    fun spectrumHistogram(magnitudes: FloatArray, binCount: Int = 32): FloatArray {
        val out = FloatArray(binCount)
        if (magnitudes.isEmpty()) return out
        val step = magnitudes.size.toFloat() / binCount
        for (i in 0 until binCount) {
            val start = (i * step).toInt()
            val end = ((i + 1) * step).toInt().coerceAtMost(magnitudes.size)
            var sum = 0f
            for (j in start until end) sum += magnitudes[j]
            out[i] = if (end > start) sum / (end - start) else 0f
        }
        return out
    }

    // ──────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────

    private fun nextPowerOf2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /** In-place iterative Cooley-Tukey radix-2 FFT. n must be power of 2. */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        // Butterfly
        var size = 2
        while (size <= n) {
            val half = size / 2
            val tableStep = n / size
            var k = 0
            while (k < n) {
                var idx = 0
                for (i in k until k + half) {
                    val angle = -2.0 * PI * idx / n
                    val cosA = cos(angle).toFloat()
                    val sinA = sin(angle).toFloat()
                    val tReal = real[i + half] * cosA - imag[i + half] * sinA
                    val tImag = real[i + half] * sinA + imag[i + half] * cosA
                    real[i + half] = real[i] - tReal
                    imag[i + half] = imag[i] - tImag
                    real[i] += tReal
                    imag[i] += tImag
                    idx += tableStep
                }
                k += size
            }
            size = size shl 1
        }
    }
}
