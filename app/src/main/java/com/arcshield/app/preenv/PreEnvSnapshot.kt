package com.arcshield.app.preenv

import com.arcshield.app.sensory.SensoryBundle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pre-cause environmental layer. Continuous ambient context attached to every
 * CIAER+ event by reference at Cause firing.
 *
 * PreEnv is not a phase in the capture state machine. It is sampled from
 * background trackers (ShiftPhaseDetector, MaterialBatchTracker,
 * AmbientTempTracker, RecentEventsRollup) and attached to an event when the
 * state machine reaches the Cause phase.
 *
 * This data class mirrors the PreEnv definition in schema/ciaer_plus_v1.json
 * exactly. The schema is the single source of truth. If you need to modify
 * the shape of PreEnv, edit the JSON schema first and regenerate this class.
 *
 * See: ArcShield whitepaper v10.1 §3 (CIAER+ schema), ArcShield product
 * architecture §3.2.
 */
@Serializable
data class PreEnvSnapshot(
    /**
     * ISO-8601 UTC timestamp of when this PreEnv snapshot was assembled.
     * Typically within milliseconds of cause.capture_timestamp.
     */
    @SerialName("snapshot_timestamp")
    val snapshotTimestamp: String,

    /**
     * Stable operator identifier, captured once at shift login and inherited
     * by all events this shift.
     */
    @SerialName("operator_id")
    val operatorId: String,

    /**
     * Derived from shift start time and system clock by ShiftPhaseDetector.
     * No operator input required.
     */
    @SerialName("shift_phase")
    val shiftPhase: ShiftPhase,

    /**
     * Identifier for the operator's shift session. All events from one shift
     * share one session_id. UUID string, or null if shift session has not
     * been initialized.
     */
    @SerialName("shift_session_id")
    val shiftSessionId: String? = null,

    /**
     * Current material batch identifier. Captured at batch changeover via
     * BatchChangeScreen (barcode scan or short-code entry). Null if no batch
     * has been recorded yet this shift.
     */
    @SerialName("material_batch_id")
    val materialBatchId: String? = null,

    /**
     * ISO-8601 timestamp the current batch was reported starting. Used to
     * compute time-on-batch for downstream analysis.
     */
    @SerialName("material_batch_started_at")
    val materialBatchStartedAt: String? = null,

    /**
     * Ambient temperature in Fahrenheit. Best-available proxy for shop-floor
     * thermal conditions. For Gen 1 this is outdoor temperature from a weather
     * API; the source is recorded in ambientTempSource for downstream analysis
     * of thermal confound strength.
     */
    @SerialName("ambient_temp_f")
    val ambientTempF: Double? = null,

    /**
     * How ambientTempF was obtained. Allows the training pipeline to flag
     * events with noisy ambient context.
     */
    @SerialName("ambient_temp_source")
    val ambientTempSource: AmbientTempSource = AmbientTempSource.UNAVAILABLE,

    /**
     * Short natural-language summary of recent events this shift. Generated
     * by RecentEventsRollup from the last N events in the corpus. Provides
     * the event with its operational history context.
     *
     * Empty string (not null) when no prior events exist this shift — the
     * field is required by the schema.
     */
    @SerialName("recent_events_summary")
    val recentEventsSummary: String,

    /**
     * Five-channel sensory baseline captured at shift start by
     * SensoryCaptureManager.captureBaseline(). Per-channel deltas are computed
     * against this baseline at event time and surface as elicitation hints
     * for the PIE prompt builder.
     *
     * Null when the sensory layer is disabled or has not yet captured a
     * baseline this shift. See CLAUDE_CODE_HANDOFF_INTEGRATION.md → Layer 1.
     */
    @SerialName("sensory_baseline")
    val sensoryBaseline: SensoryBundle? = null,
)

@Serializable
enum class ShiftPhase {
    @SerialName("startup") STARTUP,
    @SerialName("steady_state") STEADY_STATE,
    @SerialName("shutdown") SHUTDOWN,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
enum class AmbientTempSource {
    @SerialName("weather_api") WEATHER_API,
    @SerialName("sensor") SENSOR,
    @SerialName("manual") MANUAL,
    @SerialName("unavailable") UNAVAILABLE,
}
