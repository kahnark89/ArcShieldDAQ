package com.arcshield.app.preenv

import com.arcshield.app.sensory.SensoryBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreEnvTracker @Inject constructor(
    private val prefs:         PreEnvPrefsStore,
    private val batchTracker:  MaterialBatchTracker,
    private val tempTracker:   AmbientTempTracker,
    private val rollup:        RecentEventsRollup,
    private val json:          Json,
) {
    private data class ShiftState(
        val operatorId:     String?,
        val sessionId:      String?,
        val shiftStartedAt: Long?,
        val shiftHours:     Int,
    )

    private val shiftStateFlow: Flow<ShiftState> = combine(
        prefs.operatorId,
        prefs.shiftSessionId,
        prefs.shiftStartedAtEpochMs,
        prefs.shiftDurationHours,
    ) { op, session, started, hours -> ShiftState(op, session, started, hours) }

    val snapshotFlow: Flow<PreEnvSnapshot> = combine(
        shiftStateFlow,
        batchTracker.current,
        tempTracker.current,
        prefs.sensoryBaselineJson,
    ) { shift, batch, temp, baselineJson -> build(shift, batch, temp, baselineJson) }

    suspend fun now(): PreEnvSnapshot {
        val shift = ShiftState(
            operatorId     = prefs.operatorId.first(),
            sessionId      = prefs.shiftSessionId.first(),
            shiftStartedAt = prefs.shiftStartedAtEpochMs.first(),
            shiftHours     = prefs.shiftDurationHours.first(),
        )
        val baselineJson = prefs.sensoryBaselineJson.first()
        return build(shift, batchTracker.current.first(), tempTracker.current.value, baselineJson)
    }

    private suspend fun build(
        shift:        ShiftState,
        batch:        MaterialBatchTracker.Batch?,
        temp:         AmbientTempTracker.Reading?,
        baselineJson: String?,
    ): PreEnvSnapshot {
        val now             = Instant.now()
        val shiftInstant    = shift.shiftStartedAt?.let { Instant.ofEpochMilli(it) }
        val sensoryBaseline = baselineJson?.let { str ->
            runCatching { json.decodeFromString(SensoryBundle.serializer(), str) }.getOrNull()
        }
        return PreEnvSnapshot(
            snapshotTimestamp      = ISO.format(now),
            operatorId             = shift.operatorId.orEmpty(),
            shiftPhase             = ShiftPhaseDetector.detect(shiftInstant, shift.shiftHours, now),
            shiftSessionId         = shift.sessionId,
            materialBatchId        = batch?.id,
            materialBatchStartedAt = batch?.let { ISO.format(Instant.ofEpochMilli(it.startedAtEpochMs)) },
            ambientTempF           = temp?.fahrenheit,
            ambientTempSource      = temp?.source ?: AmbientTempSource.UNAVAILABLE,
            recentEventsSummary    = rollup.summary(),
            sensoryBaseline        = sensoryBaseline,
        )
    }

    companion object {
        private val ISO = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    }
}
