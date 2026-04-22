package com.arcshield.app.preenv

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialBatchTracker @Inject constructor(
    private val prefs: PreEnvPrefsStore,
) {
    data class Batch(val id: String, val startedAtEpochMs: Long)

    val current: Flow<Batch?> = combine(
        prefs.batchId,
        prefs.batchStartedAtEpochMs,
    ) { id, startedAt ->
        if (id != null && startedAt != null) Batch(id, startedAt) else null
    }

    suspend fun record(batchId: String, startedAtEpochMs: Long = System.currentTimeMillis()) {
        prefs.recordBatch(batchId, startedAtEpochMs)
    }
}
