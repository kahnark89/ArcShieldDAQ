package com.arcshield.app.trigger

import com.arcshield.app.data.schema.OperationalMode
import com.arcshield.app.data.schema.SignalScores
import com.arcshield.app.data.schema.TriggerContext
import com.arcshield.app.data.schema.TriggerType
import java.time.Instant

/**
 * One Layer-2 firing decision. Emitted by [FusionEngine] when the weighted
 * signal sum crosses the configured threshold. Consumed by the capture state
 * machine, which uses it to enter the CAUSE phase and stash the context for
 * eventual attachment to [com.arcshield.app.data.schema.Cause.triggerContext].
 */
data class TriggerEvent(
    val firedAt: Instant,
    val confidenceScore: Float,
    val signalScores: TriggerSignalScores,
    val temporalModifier: Float,
    val operationalMode: OperationalMode,
    val thresholdAtFire: Float,
    val mode: FireMode,
) {
    fun toSchemaContext(): TriggerContext = TriggerContext(
        triggerType      = TriggerType.AUTOMATIC,
        confidenceScore  = confidenceScore.toDouble(),
        signalScores     = SignalScores(
            gaze     = signalScores.gaze?.toDouble(),
            hand     = signalScores.hand?.toDouble(),
            hrv      = signalScores.hrv?.toDouble(),
            acoustic = signalScores.acoustic?.toDouble(),
        ),
        temporalModifier = temporalModifier.toDouble(),
        operationalMode  = operationalMode,
        thresholdAtFire  = thresholdAtFire.toDouble(),
    )
}

/**
 * Per-signal contribution at firing time. Null means the signal was not
 * available (provider stubbed or sensor disconnected); the fusion math
 * treats null as 0 contribution.
 */
data class TriggerSignalScores(
    val gaze: Float?,
    val hand: Float?,
    val hrv: Float?,
    val acoustic: Float?,
)

/**
 * Which of the two fire rules from detection-spec §4.3 produced this event.
 * Useful for tuning and dashboard analytics.
 */
enum class FireMode {
    /** adjusted_score >= 0.75 within a single 2 s window. */
    IMMEDIATE,
    /** adjusted_score >= 0.60 with 3+ hits accumulated within 10 s. */
    COUNTER,
}
