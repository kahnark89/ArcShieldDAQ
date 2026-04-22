package com.arcshield.app.capture

import com.arcshield.app.data.schema.Action
import com.arcshield.app.data.schema.ActionStep
import com.arcshield.app.data.schema.Cause
import com.arcshield.app.data.schema.CiaerPlusEvent
import com.arcshield.app.data.schema.Effect
import com.arcshield.app.data.schema.Intuition
import com.arcshield.app.data.schema.KnowledgeSource
import com.arcshield.app.data.schema.OutcomeTag
import com.arcshield.app.data.schema.PredictionMatch
import com.arcshield.app.data.schema.Result
import com.arcshield.app.data.schema.SensorReading
import com.arcshield.app.data.schema.ShadowAction
import com.arcshield.app.data.schema.SrkLevel
import com.arcshield.app.data.schema.TriggerChannel
import com.arcshield.app.data.schema.TriggerSource
import com.arcshield.app.preenv.PreEnvSnapshot
import java.util.UUID

/**
 * Mutable builder collecting field values across the five capture phases.
 * Finalized into an immutable [CiaerPlusEvent] at Result submission via [finalize].
 */
data class CaptureDraft(
    val eventId: String = UUID.randomUUID().toString(),

    val causeCapturedAt:       String? = null,
    val triggerSource:         TriggerSource = TriggerSource.OPERATOR_INITIATED,
    val triggerChannels:       List<TriggerChannel> = emptyList(),
    val visualAnchorDescription: String? = null,
    val visualAnchorFrameRef:  String? = null,
    val causeSensorReadings:   List<SensorReading> = emptyList(),

    val srkLevel:          SrkLevel = SrkLevel.RULE,
    val srkConfidence:     Double?  = null,
    val causalHypothesis:  String   = "",
    val failureModeTag:    String?  = null,
    val projection:        String?  = null,
    val confidenceLevel:   Double   = 0.5,
    val voiceTranscript:   String?  = null,
    val knowledgeSource:   KnowledgeSource? = null,

    val actionType:             String  = "",
    val actionTimestamp:        String? = null,
    val actionRationale:        String? = null,
    val actionSteps:            List<ActionStep> = emptyList(),
    val wasDeliberateNonAction: Boolean = false,
    val shadowActions:          List<ShadowAction> = emptyList(),

    val effectCapturedAt:    String? = null,
    val effectSensorReadings: List<SensorReading> = emptyList(),
    val predictionMatch:     PredictionMatch = PredictionMatch.INDETERMINATE,

    val completedAt:         String? = null,
    val outcomeTag:          OutcomeTag = OutcomeTag.NO_CHANGE,
    val escalationDelta:     String? = null,
    val hypothesisConfirmed: Boolean = false,
    val productQualityImpact: String? = null,
) {
    fun finalize(preEnv: PreEnvSnapshot, nowIso: () -> String): CiaerPlusEvent {
        val cause = Cause(
            captureTimestamp        = causeCapturedAt ?: nowIso(),
            triggerSource           = triggerSource,
            triggerChannels         = triggerChannels,
            sensorReadings          = causeSensorReadings,
            visualAnchorDescription = visualAnchorDescription,
            visualAnchorFrameRef    = visualAnchorFrameRef,
        )
        val intuition = Intuition(
            srkLevel         = srkLevel,
            srkConfidence    = srkConfidence,
            causalHypothesis = causalHypothesis,
            failureModeTag   = failureModeTag,
            projection       = projection,
            confidenceLevel  = confidenceLevel,
            voiceTranscript  = voiceTranscript,
            knowledgeSource  = knowledgeSource,
        )
        val action = Action(
            actionType             = actionType,
            actionTimestamp        = actionTimestamp ?: nowIso(),
            actionRationale        = actionRationale,
            actionSequence         = actionSteps.ifEmpty { listOf(ActionStep(1, actionType)) },
            wasDeliberateNonAction = wasDeliberateNonAction,
        )
        val effect = Effect(
            captureTimestamp = effectCapturedAt ?: nowIso(),
            sensorReadings   = effectSensorReadings,
            predictionMatch  = predictionMatch,
        )
        val result = Result(
            completedAt          = completedAt ?: nowIso(),
            outcomeTag           = outcomeTag,
            escalationDelta      = escalationDelta,
            hypothesisConfirmed  = hypothesisConfirmed,
            productQualityImpact = productQualityImpact,
        )
        return CiaerPlusEvent(
            eventId       = eventId,
            preEnv        = preEnv,
            cause         = cause,
            intuition     = intuition,
            action        = action,
            shadowActions = shadowActions,
            effect        = effect,
            result        = result,
        )
    }
}
