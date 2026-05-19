package com.arcshield.app.trigger

import com.arcshield.app.data.EventDao
import com.arcshield.app.data.schema.CiaerPlusEvent
import com.arcshield.app.data.schema.OperationalMode
import com.arcshield.app.data.schema.OutcomeTag
import com.arcshield.app.preenv.ShiftPhase
import com.arcshield.app.preenv.source.PreEnvSource
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies the operator's current operational mode per detection-spec §5.5
 * and §7.7. Drives mode-based threshold adjustment:
 *
 *   SETUP           → 0.70  (STARTUP shift phase — naturally noisy, raise the bar)
 *   STEADY          → 0.65  (normal steady-state production)
 *   TROUBLESHOOTING → 0.60  (WORSE or NO_CHANGE outcome in the last 10 minutes)
 */
@Singleton
class OperationalModeDetector @Inject constructor(
    private val preEnvSource: PreEnvSource,
    private val dao: EventDao,
    private val json: Json,
) {
    suspend fun currentMode(): OperationalMode {
        val snap = preEnvSource.currentSnapshot()
        if (snap.shiftPhase == ShiftPhase.STARTUP) return OperationalMode.SETUP

        val sessionId = snap.shiftSessionId ?: return OperationalMode.STEADY
        val recent = dao.recentForSession(sessionId, 3)
        val tenMinAgo = System.currentTimeMillis() - TEN_MIN_MS
        val hasTroubleshooting = recent.any { entity ->
            if (entity.createdAtEpochMs < tenMinAgo) return@any false
            runCatching {
                val event = json.decodeFromString(CiaerPlusEvent.serializer(), entity.eventJson)
                event.result.outcomeTag in listOf(OutcomeTag.WORSE, OutcomeTag.NO_CHANGE)
            }.getOrDefault(false)
        }
        return if (hasTroubleshooting) OperationalMode.TROUBLESHOOTING else OperationalMode.STEADY
    }

    companion object {
        private const val TEN_MIN_MS = 10 * 60 * 1000L
    }
}
