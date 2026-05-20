package com.arcshield.app.trigger

import com.arcshield.app.data.schema.OperationalMode
import com.arcshield.app.trigger.scorer.AcousticScorer
import com.arcshield.app.trigger.scorer.GazeHandScorer
import com.arcshield.app.trigger.scorer.HrvScorer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behaviour-level coverage of [FusionEngine.evaluate]. Drives a fake clock
 * so lockout and counter-window timing are deterministic without sleeping.
 *
 * The engine's 200 ms tick loop lives in the [FusionEngine.triggers] cold
 * flow; we don't exercise that here — we drive [FusionEngine.evaluate]
 * directly, which is the same code path the flow calls.
 */
class FusionEngineTest {

    @Test fun `low signals do not fire`() = runTest {
        val clock = FakeClock()
        val engine = engineWith(
            clock    = clock,
            gaze     = 0.1f, hand = 0.1f, hrv = 0.1f, acoustic = 0.1f,
        )
        // 0.35*.1 + 0.25*.1 + 0.20*.1 + 0.20*.1 = 0.10, well below 0.60.
        assertNull(engine.evaluate())
    }

    @Test fun `score at or above 0_75 fires immediately`() = runTest {
        val clock = FakeClock()
        val engine = engineWith(
            clock    = clock,
            gaze     = 1.0f, hand = 1.0f, hrv = 1.0f, acoustic = 1.0f,
        )
        // 0.35 + 0.25 + 0.20 + 0.20 = 1.0 → IMMEDIATE.
        val ev = engine.evaluate()
        assertNotNull(ev)
        assertEquals(FireMode.IMMEDIATE, ev!!.mode)
        assertEquals(1.0f, ev.confidenceScore, 0.001f)
        assertEquals(OperationalMode.STEADY, ev.operationalMode)
    }

    @Test fun `three sub-threshold hits within 10s fire counter mode`() = runTest {
        val clock = FakeClock()
        // gaze 0.8 + hand 0.8 → 0.35*.8 + 0.25*.8 = 0.48; add hrv 0.6 → 0.48 + 0.12 = 0.60 exactly.
        val engine = engineWith(
            clock = clock,
            gaze  = 0.8f, hand = 0.8f, hrv = 0.6f, acoustic = 0.0f,
        )

        clock.advance(0)
        assertNull(engine.evaluate())  // 1st near-hit logged, no fire (need 3)
        clock.advance(2_000)
        assertNull(engine.evaluate())  // 2nd near-hit
        clock.advance(2_000)
        val ev = engine.evaluate()     // 3rd → counter fire
        assertNotNull(ev)
        assertEquals(FireMode.COUNTER, ev!!.mode)
    }

    @Test fun `near-hits older than 10s expire from the counter window`() = runTest {
        val clock = FakeClock()
        val engine = engineWith(
            clock = clock,
            gaze  = 0.8f, hand = 0.8f, hrv = 0.6f, acoustic = 0.0f,
        )

        clock.advance(0)
        assertNull(engine.evaluate())          // hit @ t=0
        clock.advance(5_000)
        assertNull(engine.evaluate())          // hit @ t=5_000
        clock.advance(6_000)
        // t=11_000 — the t=0 hit has aged out; this is hit #2 in the window, not #3.
        assertNull(engine.evaluate())
    }

    @Test fun `lockout suppresses re-fire for 10s`() = runTest {
        val clock = FakeClock()
        val engine = engineWith(
            clock    = clock,
            gaze     = 1.0f, hand = 1.0f, hrv = 1.0f, acoustic = 1.0f,
        )

        assertNotNull(engine.evaluate())       // first fire @ t=0
        clock.advance(5_000)
        assertNull(engine.evaluate())          // still inside 10s lockout
        clock.advance(5_001)
        assertNotNull(engine.evaluate())       // lockout expired
    }

    @Test fun `null signals contribute zero`() = runTest {
        val clock = FakeClock()
        val engine = engineWith(
            clock    = clock,
            gaze     = null, hand = null, hrv = 1.0f, acoustic = null,
        )
        // Only HRV (weight 0.20) contributes → 0.20 score. No fire.
        assertNull(engine.evaluate())
    }

    private fun engineWith(
        clock:    FakeClock,
        gaze:     Float?,
        hand:     Float?,
        hrv:      Float?,
        acoustic: Float?,
    ): FusionEngine = FusionEngine(
        gazeHandScorer = object : GazeHandScorer {
            override suspend fun gaze(): Float? = gaze
            override suspend fun hand(): Float? = hand
        },
        hrvScorer = object : HrvScorer {
            override suspend fun score(): Float? = hrv
        },
        acousticScorer = object : AcousticScorer {
            override suspend fun score(): Float? = acoustic
        },
        modeDetector = OperationalModeDetector(),
        clock = clock,
    )

    private class FakeClock : TriggerClock {
        private var t: Long = 0
        override fun nowMs(): Long = t
        fun advance(deltaMs: Long) { t += deltaMs }
    }
}
