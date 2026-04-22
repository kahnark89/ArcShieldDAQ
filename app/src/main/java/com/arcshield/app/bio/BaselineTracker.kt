package com.arcshield.app.bio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Rolling personal baseline for HR and HRV-RMSSD. In-memory only for Gen 1 —
 * resets on process death. Persistence arrives with the biometric-store phase.
 */
@Singleton
class BaselineTracker @Inject constructor() {

    private val hr  = RollingStats()
    private val hrv = RollingStats()

    fun recordHr(value: Double)  { hr.add(value) }
    fun recordHrv(value: Double) { hrv.add(value) }

    fun hrZ(value: Double):  Double? = hr.zScore(value)
    fun hrvZ(value: Double): Double? = hrv.zScore(value)

    private class RollingStats(private val windowSize: Int = 60) {
        private val buffer = ArrayDeque<Double>()

        fun add(v: Double) {
            if (buffer.size == windowSize) buffer.removeFirst()
            buffer.addLast(v)
        }

        fun zScore(v: Double): Double? {
            if (buffer.size < MIN_SAMPLES) return null
            val mean     = buffer.average()
            val variance = buffer.sumOf { val d = it - mean; d * d } / buffer.size
            val sd       = sqrt(variance)
            return if (sd == 0.0) 0.0 else (v - mean) / sd
        }

        companion object { private const val MIN_SAMPLES = 5 }
    }
}
