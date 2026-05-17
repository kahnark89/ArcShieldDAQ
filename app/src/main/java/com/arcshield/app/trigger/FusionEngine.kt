package com.arcshield.app.trigger

import com.arcshield.app.trigger.scorer.AcousticScorer
import com.arcshield.app.trigger.scorer.GazeHandScorer
import com.arcshield.app.trigger.scorer.HrvScorer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer 2 — on-device weighted-sum fusion trigger.
 *
 * Implements detection-spec §4 (fusion logic) and §4.4 (lockout). Every
 * [evalIntervalMs] ms the engine reads each operator-state signal, computes
 *
 *   adjusted = (0.35·gaze + 0.25·hand + 0.20·hrv + 0.20·acoustic) × temporal_modifier
 *
 * and applies two firing rules:
 *
 *   - **immediate** if `adjusted >= immediateThreshold` (default 0.75)
 *   - **counter**   if `adjusted >= counterThreshold`   (default 0.60)
 *                   and at least 3 such hits have landed within
 *                   [counterWindowMs] ms (default 10 000)
 *
 * After any fire a 10 s lockout suppresses further fires per §4.4.
 *
 * Internal state (lockout timestamp + near-hit ring buffer) is held in the
 * singleton so it survives `MainActivity` recreations. The 200 ms evaluation
 * loop lives in the [triggers] cold flow — it runs only while collected and
 * the collector owns its lifecycle.
 *
 * Mode-based threshold adjustment from §7.7 is recorded in the emitted event
 * (`thresholdAtFire`) but does not yet replace the fixed immediate/counter
 * constants — that's deferred until [OperationalModeDetector] has real
 * inputs.
 *
 * Temporal modifier (§3.5, §5.1) is held at 1.0 until the warm-up /
 * calibration logic lands.
 */
@Singleton
class FusionEngine @Inject constructor(
    private val gazeHandScorer: GazeHandScorer,
    private val hrvScorer: HrvScorer,
    private val acousticScorer: AcousticScorer,
    private val modeDetector: OperationalModeDetector,
    private val clock: TriggerClock,
) {
    private val mutex = Mutex()
    private var lastFireAtMs: Long = 0
    private val nearHits = ArrayDeque<Long>()

    val triggers: Flow<TriggerEvent> = flow {
        while (true) {
            delay(evalIntervalMs)
            evaluate()?.let { emit(it) }
        }
    }

    /**
     * One evaluation pass. Exposed for tests; the runtime path drives it via
     * the [triggers] flow. Returns the [TriggerEvent] if a fire condition was
     * met this tick, otherwise null.
     */
    suspend fun evaluate(): TriggerEvent? = mutex.withLock {
        val now = clock.nowMs()
        if (now - lastFireAtMs < lockoutMs) return@withLock null

        val gaze     = gazeHandScorer.gaze()
        val hand     = gazeHandScorer.hand()
        val hrv      = hrvScorer.score()
        val acoustic = acousticScorer.score()

        val confidence =
            WEIGHT_GAZE     * (gaze ?: 0f) +
            WEIGHT_HAND     * (hand ?: 0f) +
            WEIGHT_HRV      * (hrv ?: 0f) +
            WEIGHT_ACOUSTIC * (acoustic ?: 0f)

        val mode = modeDetector.currentMode()
        val temporalModifier = 1.0f
        val adjusted = confidence * temporalModifier

        // Maintain the near-hit ring buffer for counter mode.
        if (adjusted >= counterThreshold) nearHits.addLast(now)
        while (nearHits.isNotEmpty() && now - nearHits.first() > counterWindowMs) {
            nearHits.removeFirst()
        }

        val fireMode = when {
            adjusted >= immediateThreshold       -> FireMode.IMMEDIATE
            adjusted >= counterThreshold && nearHits.size >= counterMinHits -> FireMode.COUNTER
            else                                 -> return@withLock null
        }

        lastFireAtMs = now
        // After firing we clear the near-hit buffer so the next counter-mode
        // fire requires a fresh burst of hits rather than reusing the old
        // ones.
        nearHits.clear()

        TriggerEvent(
            firedAt          = Instant.ofEpochMilli(now),
            confidenceScore  = adjusted,
            signalScores     = TriggerSignalScores(gaze, hand, hrv, acoustic),
            temporalModifier = temporalModifier,
            operationalMode  = mode,
            thresholdAtFire  = if (fireMode == FireMode.IMMEDIATE) immediateThreshold else counterThreshold,
            mode             = fireMode,
        )
    }

    companion object {
        const val evalIntervalMs: Long = 200L
        const val lockoutMs: Long = 10_000L
        const val counterWindowMs: Long = 10_000L
        const val counterMinHits: Int = 3

        const val WEIGHT_GAZE: Float     = 0.35f
        const val WEIGHT_HAND: Float     = 0.25f
        const val WEIGHT_HRV: Float      = 0.20f
        const val WEIGHT_ACOUSTIC: Float = 0.20f

        const val immediateThreshold: Float = 0.75f
        const val counterThreshold: Float   = 0.60f
    }
}

/**
 * Indirection over the system clock so unit tests can advance time without
 * sleeping. Production wires [WallTriggerClock] (provided by
 * [com.arcshield.app.trigger.di.FusionEngineModule]); tests pass in a fake.
 */
interface TriggerClock { fun nowMs(): Long }
object WallTriggerClock : TriggerClock { override fun nowMs(): Long = System.currentTimeMillis() }
