package com.arcshield.app.data.repository

import com.arcshield.app.capture.CaptureDraft
import com.arcshield.app.data.EventDao
import com.arcshield.app.data.EventEntity
import com.arcshield.app.data.schema.CiaerPlusEvent
import com.arcshield.app.data.schema.OutcomeTag
import com.arcshield.app.data.schema.SrkLevel
import com.arcshield.app.preenv.source.PreEnvSource
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles a [CiaerPlusEvent] from a completed [CaptureDraft], computes
 * [graph_weight] per whitepaper §10.12, persists to Room, and returns the
 * saved event.
 *
 * This is the single submission path for all capture cycles. CaptureViewModel
 * delegates here instead of building the event inline, ensuring graph_weight
 * and preEnv are consistently applied.
 */
@Singleton
class SensoryEventRepository @Inject constructor(
    private val dao:        EventDao,
    private val preEnv:     PreEnvSource,
    private val json:       Json,
) {
    sealed interface SaveResult {
        data class Success(val event: CiaerPlusEvent) : SaveResult
        data class Failure(val cause: Throwable) : SaveResult
    }

    suspend fun assembleAndSave(draft: CaptureDraft): SaveResult = runCatching {
        val snap    = preEnv.currentSnapshot()
        val weight  = computeGraphWeight(draft)
        val nowIso  = { Instant.now().toString() }
        val event   = draft.copy(graphWeight = weight).finalize(preEnv = snap, nowIso = nowIso)

        dao.upsert(
            EventEntity(
                eventId          = event.eventId,
                createdAtEpochMs = Instant.parse(event.cause.captureTimestamp).toEpochMilli(),
                operatorId       = event.preEnv.operatorId,
                shiftSessionId   = event.preEnv.shiftSessionId,
                synced           = false,
                eventJson        = json.encodeToString(CiaerPlusEvent.serializer(), event),
            )
        )
        event
    }.fold(
        onSuccess = { SaveResult.Success(it) },
        onFailure = { SaveResult.Failure(it) },
    )

    private fun computeGraphWeight(draft: CaptureDraft): Double {
        var weight = 0.5
        if (draft.hypothesisConfirmed) weight += 0.20
        if (draft.outcomeTag in listOf(OutcomeTag.PREVENTED, OutcomeTag.RESOLVED)) weight += 0.15
        if (draft.srkLevel == SrkLevel.SKILL) weight -= 0.10
        return weight.coerceIn(0.10, 1.00)
    }
}
