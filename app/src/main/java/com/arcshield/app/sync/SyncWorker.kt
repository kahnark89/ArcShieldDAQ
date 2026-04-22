package com.arcshield.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arcshield.app.data.EventDao
import com.arcshield.app.data.schema.CiaerPlusEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json

/**
 * Drains pending rows out of the local Room queue into [LocalServerSink].
 * Runs under WorkManager with connectivity + battery-not-low constraints —
 * see [SyncScheduler] for the scheduling contract.
 *
 * Per-row handling: success marks the row `synced = 1`; failure leaves the
 * row for the next attempt. A single row failing does not block the rest
 * of the batch.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: EventDao,
    private val serverSink: LocalServerSink,
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = runCatching { dao.pendingSync(BATCH_LIMIT) }.getOrElse {
            Log.w(TAG, "dao.pendingSync failed", it); return Result.retry()
        }
        if (pending.isEmpty()) return Result.success()

        var anyFailure = false
        pending.forEach { row ->
            val event = runCatching {
                json.decodeFromString(CiaerPlusEvent.serializer(), row.eventJson)
            }.getOrElse {
                Log.w(TAG, "skip unparseable event ${row.eventId}", it)
                return@forEach
            }
            when (val res = serverSink.persist(event)) {
                is CorpusSink.Result.Success -> dao.markSynced(row.eventId)
                is CorpusSink.Result.Failure -> {
                    Log.w(TAG, "sync failed for ${row.eventId}", res.cause)
                    anyFailure = true
                }
            }
        }
        return if (anyFailure) Result.retry() else Result.success()
    }

    private companion object {
        const val TAG = "SyncWorker"
        const val BATCH_LIMIT = 50
    }
}
