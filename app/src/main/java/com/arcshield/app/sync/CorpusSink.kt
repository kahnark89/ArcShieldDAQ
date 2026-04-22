package com.arcshield.app.sync

import com.arcshield.app.data.schema.CiaerPlusEvent

/**
 * Abstraction over event corpus persistence.
 *
 * Implementations:
 *  - [LocalSqliteSink]: always-on local Room write, queued for later upload
 *  - [LocalServerSink]: pushes to on-premise Python server when reachable
 *
 * Gen 1 writes to LocalSqliteSink unconditionally. The SyncService worker
 * drains the Room queue into LocalServerSink when connectivity allows.
 * A future CloudSink slots in without touching capture-side code.
 */
interface CorpusSink {
    suspend fun persist(event: CiaerPlusEvent): Result

    sealed class Result {
        data object Success : Result()
        data class Failure(val cause: Throwable) : Result()
    }
}
