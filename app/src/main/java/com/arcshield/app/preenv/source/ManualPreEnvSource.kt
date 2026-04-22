package com.arcshield.app.preenv.source

import com.arcshield.app.preenv.AmbientTempSource
import com.arcshield.app.preenv.PreEnvPrefsStore
import com.arcshield.app.preenv.PreEnvSnapshot
import com.arcshield.app.preenv.PreEnvTracker
import com.arcshield.app.preenv.ShiftPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualPreEnvSource @Inject constructor(
    private val prefs:   PreEnvPrefsStore,
    private val tracker: PreEnvTracker,
) : PreEnvSource {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val snapshot: StateFlow<PreEnvSnapshot> = tracker.snapshotFlow.stateIn(
        scope        = scope,
        started      = SharingStarted.Eagerly,
        initialValue = emptySnapshot(),
    )

    override suspend fun currentSnapshot(): PreEnvSnapshot = tracker.now()

    override suspend fun recordBatchChange(batchId: String, startedAt: String?) {
        val epoch = startedAt?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis()
        prefs.recordBatch(batchId, epoch)
    }

    override suspend fun recordShiftStart(
        operatorId:     String,
        shiftSessionId: String,
        shiftStartedAt: String,
    ) {
        prefs.startShift(operatorId, shiftSessionId, Instant.parse(shiftStartedAt).toEpochMilli())
    }

    override suspend fun recordShiftEnd() {
        prefs.endShift()
    }

    private fun emptySnapshot() = PreEnvSnapshot(
        snapshotTimestamp   = ISO.format(Instant.now()),
        operatorId          = "",
        shiftPhase          = ShiftPhase.UNKNOWN,
        ambientTempSource   = AmbientTempSource.UNAVAILABLE,
        recentEventsSummary = "",
    )

    companion object {
        private val ISO = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    }
}
