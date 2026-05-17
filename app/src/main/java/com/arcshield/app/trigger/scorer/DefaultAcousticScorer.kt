package com.arcshield.app.trigger.scorer

import com.arcshield.app.sensory.AcousticSnapshot
import com.arcshield.app.sensory.channels.AcousticChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Acoustic anomaly scorer for the Layer 2 fusion engine.
 *
 * Maintains a rolling baseline of [AcousticSnapshot] samples (~3 min at the
 * 200 ms tick rate is too frequent for mic capture; the scorer de-rates by
 * capturing only when called — once per [FusionEngine] tick = 5/s max but
 * typically less when the tick interval accounts for actual processing time).
 *
 * Scoring per detection-spec §3.4:
 *   - Compute z-scores of spectralCentroidHz and amplitudeDbfs vs baseline
 *   - Map max(z_centroid, z_amplitude) → 0..1 (score = 1.0 at ≥ 2σ deviation)
 *
 * Returns null while warming up (fewer than [MIN_BASELINE_SAMPLES] snapshots).
 */
@Singleton
class DefaultAcousticScorer @Inject constructor(
    private val channel: AcousticChannel,
) : AcousticScorer {

    private val mutex = Mutex()
    private val baseline = ArrayDeque<AcousticSnapshot>(RING_CAPACITY)

    override suspend fun score(): Float? = mutex.withLock {
        val snap = channel.captureEvent() ?: return@withLock null

        if (baseline.size < MIN_BASELINE_SAMPLES) {
            enqueue(snap)
            return@withLock null
        }

        val centroidZ = zScore(snap.spectralCentroidHz, baseline.map { it.spectralCentroidHz })
        val amplitudeZ = zScore(snap.amplitudeDbfs, baseline.map { it.amplitudeDbfs })

        enqueue(snap)

        if (centroidZ == null && amplitudeZ == null) return@withLock null
        val maxZ = maxOf(centroidZ ?: 0f, amplitudeZ ?: 0f)
        min(1f, abs(maxZ) / 2f)   // 2σ deviation → score 1.0
    }

    private fun enqueue(snap: AcousticSnapshot) {
        if (baseline.size >= RING_CAPACITY) baseline.removeFirst()
        baseline.addLast(snap)
    }

    private fun zScore(value: Float, samples: List<Float>): Float? {
        if (samples.size < 2) return null
        val mean = samples.sum() / samples.size
        val variance = samples.sumOf { ((it - mean) * (it - mean)).toDouble() } / (samples.size - 1)
        val std = sqrt(variance).toFloat()
        if (std < 1e-6f) return null
        return (value - mean) / std
    }

    companion object {
        private const val RING_CAPACITY = 18          // ~3 min at one capture / 10 s
        private const val MIN_BASELINE_SAMPLES = 5
    }
}
