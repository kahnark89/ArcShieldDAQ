package com.arcshield.app.preenv.source

import com.arcshield.app.preenv.PreEnvSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over pre-cause environmental context sources.
 *
 * The capture state machine reads the current PreEnv snapshot at Cause firing
 * via [currentSnapshot]. Downstream components never need to know whether the
 * snapshot came from operator-entered values, a networked PLC, or a hybrid.
 *
 * Current implementations:
 *   - [ManualPreEnvSource]: assembles snapshots from operator-entered fields
 *     (shift-start form, batch-change scanner) and automatic derivations
 *     (shift phase from clock, ambient temp from weather API, recent-events
 *     summary from event database). This is the Gen 1 default.
 *
 * Future implementations:
 *   - OpcUaPreEnvSource: subscribes to PLC tags over OPC-UA at facilities
 *     where the control system is network-accessible. Pulls material_batch_id
 *     and machine-state fields directly from the PLC, eliminating manual
 *     entry for those fields. Design note: a facility will commonly be
 *     partially covered — batch ID available from PLC, ambient temp still
 *     from weather API. A future HybridPreEnvSource may compose several
 *     underlying sources; interface is stable for that extension.
 *
 * Thread-safety: implementations must be safe to read from the main thread.
 * Background refresh work (weather polling, database rollup) happens off-main.
 *
 * See: ArcShield whitepaper v10.1 §3 (CIAER+ schema), §5 (hardware strategy
 * abstraction principle), ArcShield product architecture §3.2.
 */
interface PreEnvSource {

    /**
     * The current PreEnv snapshot, updated continuously by this source.
     *
     * Observers can collect this flow to track context changes across the
     * shift (e.g., the ConfigScreen may display current shift phase and
     * current batch ID as they update). The capture state machine reads
     * [StateFlow.value] at Cause firing for an immediate, point-in-time
     * snapshot.
     */
    val snapshot: StateFlow<PreEnvSnapshot>

    /**
     * Force a refresh of all underlying trackers and return the resulting
     * snapshot. Use sparingly — the common case is to read [snapshot.value]
     * because the underlying trackers keep it current.
     *
     * Intended for situations where freshness at a specific moment matters
     * more than latency, such as after a long app background period.
     */
    suspend fun currentSnapshot(): PreEnvSnapshot

    /**
     * Record that a new material batch has started. Surfaces the batch ID
     * and timestamp into subsequent [snapshot] emissions.
     *
     * For ManualPreEnvSource this is called by BatchChangeScreen after a
     * barcode scan or short-code entry. For OpcUaPreEnvSource this is a
     * no-op — the source observes batch changes directly from the PLC.
     *
     * @param batchId the new batch identifier
     * @param startedAt ISO-8601 timestamp; use null to default to now
     */
    suspend fun recordBatchChange(batchId: String, startedAt: String? = null)

    /**
     * Record the start of a new shift session. Resets per-shift state
     * (recent-events rollup, batch tracker) and stamps a fresh
     * shift_session_id.
     *
     * @param operatorId stable operator identifier, typically from login
     * @param shiftSessionId UUID for this shift session
     * @param shiftStartedAt ISO-8601 timestamp of shift start
     */
    suspend fun recordShiftStart(
        operatorId: String,
        shiftSessionId: String,
        shiftStartedAt: String,
    )

    /**
     * Record the end of the current shift session. After this call,
     * [snapshot] reflects a cleared shift context until
     * [recordShiftStart] is called again.
     */
    suspend fun recordShiftEnd()
}
