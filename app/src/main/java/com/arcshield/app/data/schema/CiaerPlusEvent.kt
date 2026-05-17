package com.arcshield.app.data.schema

import com.arcshield.app.preenv.PreEnvSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CIAER+ event, matching schema/ciaer_plus_v1.json exactly.
 *
 * All fields follow snake_case JSON via @SerialName. The schema is the single
 * source of truth: any change to the JSON schema must be reflected here and
 * vice versa — Python side generates from the same file.
 */
@Serializable
data class CiaerPlusEvent(
    @SerialName("event_id")          val eventId: String,
    @SerialName("schema_version")    val schemaVersion: String = SCHEMA_VERSION,
    @SerialName("pre_env")           val preEnv: PreEnvSnapshot,
    @SerialName("cause")             val cause: Cause,
    @SerialName("intuition")         val intuition: Intuition,
    @SerialName("action")            val action: Action,
    @SerialName("shadow_actions")    val shadowActions: List<ShadowAction> = emptyList(),
    @SerialName("effect")            val effect: Effect,
    @SerialName("result")            val result: Result,
    @SerialName("parent_event_id")   val parentEventId: String? = null,
    @SerialName("capture_device")    val captureDevice: CaptureDevice? = null,
) {
    companion object {
        const val SCHEMA_VERSION = "ciaer_plus_v1"
    }
}

@Serializable
data class Cause(
    @SerialName("capture_timestamp")         val captureTimestamp: String,
    @SerialName("trigger_source")            val triggerSource: TriggerSource,
    @SerialName("trigger_channels")          val triggerChannels: List<TriggerChannel> = emptyList(),
    @SerialName("sensor_readings")           val sensorReadings: List<SensorReading> = emptyList(),
    @SerialName("biometric_snapshot")        val biometricSnapshot: BiometricSnapshot? = null,
    @SerialName("gaze_dwell_duration_sec")   val gazeDwellDurationSec: Double? = null,
    @SerialName("visual_anchor_description") val visualAnchorDescription: String? = null,
    @SerialName("visual_anchor_frame_ref")   val visualAnchorFrameRef: String? = null,
    @SerialName("acoustic_profile")          val acousticProfile: String? = null,
    @SerialName("trigger_context")           val triggerContext: TriggerContext? = null,
)

@Serializable
data class TriggerContext(
    @SerialName("trigger_type")       val triggerType: TriggerType? = null,
    @SerialName("confidence_score")   val confidenceScore: Double? = null,
    @SerialName("signal_scores")      val signalScores: SignalScores? = null,
    @SerialName("temporal_modifier")  val temporalModifier: Double? = null,
    @SerialName("operational_mode")   val operationalMode: OperationalMode? = null,
    @SerialName("threshold_at_fire")  val thresholdAtFire: Double? = null,
    @SerialName("sensor_context")     val sensorContext: TriggerSensorContext? = null,
    @SerialName("feedback")           val feedback: TriggerFeedback? = null,
)

@Serializable
enum class TriggerType {
    @SerialName("automatic") AUTOMATIC,
    @SerialName("manual")    MANUAL,
}

@Serializable
data class SignalScores(
    @SerialName("gaze")     val gaze: Double? = null,
    @SerialName("hand")     val hand: Double? = null,
    @SerialName("hrv")      val hrv: Double? = null,
    @SerialName("acoustic") val acoustic: Double? = null,
)

@Serializable
enum class OperationalMode {
    @SerialName("setup")           SETUP,
    @SerialName("steady")          STEADY,
    @SerialName("troubleshooting") TROUBLESHOOTING,
}

@Serializable
data class TriggerSensorContext(
    @SerialName("video_clip_url")     val videoClipUrl: String? = null,
    @SerialName("audio_clip_url")     val audioClipUrl: String? = null,
    @SerialName("biometric_snapshot") val biometricSnapshot: BiometricSnapshot? = null,
)

@Serializable
data class TriggerFeedback(
    @SerialName("operator_validated") val operatorValidated: Boolean? = null,
    @SerialName("notes")              val notes: String? = null,
)

@Serializable
enum class TriggerSource {
    @SerialName("biometric")          BIOMETRIC,
    @SerialName("gaze_dwell")         GAZE_DWELL,
    @SerialName("operator_initiated") OPERATOR_INITIATED,
    @SerialName("acoustic")           ACOUSTIC,
    @SerialName("multimodal")         MULTIMODAL,
}

@Serializable
enum class TriggerChannel {
    @SerialName("hr_deviation")        HR_DEVIATION,
    @SerialName("hrv_rmssd_deviation") HRV_RMSSD_DEVIATION,
    @SerialName("gaze_dwell")          GAZE_DWELL,
    @SerialName("operator_button")     OPERATOR_BUTTON,
    @SerialName("acoustic_anomaly")    ACOUSTIC_ANOMALY,
    @SerialName("hand_pose")           HAND_POSE,
    @SerialName("fusion_threshold")    FUSION_THRESHOLD,
}

@Serializable
data class Intuition(
    @SerialName("srk_level")           val srkLevel: SrkLevel,
    @SerialName("srk_confidence")      val srkConfidence: Double? = null,
    @SerialName("causal_hypothesis")   val causalHypothesis: String,
    @SerialName("failure_mode_tag")    val failureModeTag: String? = null,
    @SerialName("projection")          val projection: String? = null,
    @SerialName("confidence_level")    val confidenceLevel: Double,
    @SerialName("voice_transcript")    val voiceTranscript: String? = null,
    @SerialName("biometric_signature") val biometricSignature: BiometricSnapshot? = null,
    @SerialName("precedent_event_ids") val precedentEventIds: List<String> = emptyList(),
    @SerialName("knowledge_source")    val knowledgeSource: KnowledgeSource? = null,
)

@Serializable
enum class SrkLevel {
    @SerialName("SKILL")     SKILL,
    @SerialName("RULE")      RULE,
    @SerialName("KNOWLEDGE") KNOWLEDGE,
}

@Serializable
enum class KnowledgeSource {
    @SerialName("PERSONAL_EXPERIENCE")     PERSONAL_EXPERIENCE,
    @SerialName("LEARNED_FROM_COLLEAGUE")  LEARNED_FROM_COLLEAGUE,
    @SerialName("FORMAL_TRAINING")         FORMAL_TRAINING,
    @SerialName("INFERRED_NOW")            INFERRED_NOW,
}

@Serializable
data class Action(
    @SerialName("action_type")              val actionType: String,
    @SerialName("action_timestamp")         val actionTimestamp: String,
    @SerialName("action_rationale")         val actionRationale: String? = null,
    @SerialName("action_sequence")          val actionSequence: List<ActionStep> = emptyList(),
    @SerialName("was_deliberate_non_action") val wasDeliberateNonAction: Boolean = false,
)

@Serializable
data class ActionStep(
    @SerialName("step_id")            val stepId: Int,
    @SerialName("description")        val description: String,
    @SerialName("parameter_changed")  val parameterChanged: String? = null,
    @SerialName("from_value")         val fromValue: String? = null,
    @SerialName("to_value")           val toValue: String? = null,
    @SerialName("rationale")          val rationale: String? = null,
)

@Serializable
data class ShadowAction(
    @SerialName("action_type")             val actionType: String,
    @SerialName("rejection_rationale")     val rejectionRationale: String,
    @SerialName("confidence_in_rejection") val confidenceInRejection: Double? = null,
)

@Serializable
data class Effect(
    @SerialName("capture_timestamp")       val captureTimestamp: String,
    @SerialName("sensor_readings")         val sensorReadings: List<SensorReading> = emptyList(),
    @SerialName("deltas")                  val deltas: List<SensorDelta> = emptyList(),
    @SerialName("prediction_match")        val predictionMatch: PredictionMatch,
    @SerialName("visual_anchor_frame_ref") val visualAnchorFrameRef: String? = null,
)

@Serializable
enum class PredictionMatch {
    @SerialName("CONFIRMED")     CONFIRMED,
    @SerialName("PARTIAL")       PARTIAL,
    @SerialName("DISCONFIRMED")  DISCONFIRMED,
    @SerialName("INDETERMINATE") INDETERMINATE,
}

@Serializable
data class Result(
    @SerialName("completed_at")           val completedAt: String,
    @SerialName("outcome_tag")            val outcomeTag: OutcomeTag,
    @SerialName("escalation_delta")       val escalationDelta: String? = null,
    @SerialName("hypothesis_confirmed")   val hypothesisConfirmed: Boolean,
    @SerialName("product_quality_impact") val productQualityImpact: String? = null,
    @SerialName("graph_weight")           val graphWeight: Double = 0.5,
    @SerialName("withhold_sample")        val withholdSample: Boolean = false,
)

@Serializable
enum class OutcomeTag {
    @SerialName("prevented")  PREVENTED,
    @SerialName("resolved")   RESOLVED,
    @SerialName("partial")    PARTIAL,
    @SerialName("no_change")  NO_CHANGE,
    @SerialName("worse")      WORSE,
    @SerialName("too_early")  TOO_EARLY,
}

@Serializable
data class SensorReading(
    @SerialName("instrument_id") val instrumentId: String,
    @SerialName("value")         val value: String,
    @SerialName("unit")          val unit: String? = null,
    @SerialName("confidence")    val confidence: Double? = null,
    @SerialName("read_source")   val readSource: ReadSource = ReadSource.VISION_LLM,
)

@Serializable
enum class ReadSource {
    @SerialName("vision_llm")        VISION_LLM,
    @SerialName("manual_entry")      MANUAL_ENTRY,
    @SerialName("network_telemetry") NETWORK_TELEMETRY,
    @SerialName("audio")             AUDIO,
}

@Serializable
data class SensorDelta(
    @SerialName("instrument_id") val instrumentId: String,
    @SerialName("delta")         val delta: Double,
    @SerialName("direction")     val direction: DeltaDirection,
)

@Serializable
enum class DeltaDirection {
    @SerialName("IMPROVED")  IMPROVED,
    @SerialName("DEGRADED")  DEGRADED,
    @SerialName("UNCHANGED") UNCHANGED,
}

@Serializable
data class BiometricSnapshot(
    @SerialName("hr_bpm")              val hrBpm: Double? = null,
    @SerialName("hr_deviation_z")      val hrDeviationZ: Double? = null,
    @SerialName("hrv_rmssd_ms")        val hrvRmssdMs: Double? = null,
    @SerialName("hrv_deviation_z")     val hrvDeviationZ: Double? = null,
    @SerialName("activity_class")      val activityClass: ActivityClass? = null,
    @SerialName("skin_temp_c")         val skinTempC: Double? = null,
    @SerialName("eda_microsiemens")    val edaMicrosiemens: Double? = null,
    @SerialName("source_device")       val sourceDevice: SourceDevice? = null,
)

@Serializable
enum class ActivityClass {
    @SerialName("stationary")         STATIONARY,
    @SerialName("low_motion")         LOW_MOTION,
    @SerialName("walking")            WALKING,
    @SerialName("physical_exertion")  PHYSICAL_EXERTION,
    @SerialName("unknown")            UNKNOWN,
}

@Serializable
enum class SourceDevice {
    @SerialName("polar_h10")           POLAR_H10,
    @SerialName("polar_verity_sense")  POLAR_VERITY_SENSE,
    @SerialName("other")               OTHER,
}

@Serializable
data class CaptureDevice(
    @SerialName("pov_source")       val povSource: PovSource,
    @SerialName("biometric_source") val biometricSource: BiometricSourceTag,
    @SerialName("app_version")      val appVersion: String,
    @SerialName("schema_version")   val schemaVersion: String = CiaerPlusEvent.SCHEMA_VERSION,
)

@Serializable
enum class PovSource {
    @SerialName("phone_mounted") PHONE_MOUNTED,
    @SerialName("meta_glasses")  META_GLASSES,
    @SerialName("other")         OTHER,
}

@Serializable
enum class BiometricSourceTag {
    @SerialName("polar_h10")           POLAR_H10,
    @SerialName("polar_verity_sense")  POLAR_VERITY_SENSE,
    @SerialName("none")                NONE,
    @SerialName("other")               OTHER,
}
