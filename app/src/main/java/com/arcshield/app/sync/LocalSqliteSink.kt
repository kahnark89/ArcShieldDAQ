package com.arcshield.app.sync

import com.arcshield.app.data.EventDao
import com.arcshield.app.data.EventEntity
import com.arcshield.app.data.schema.CiaerPlusEvent
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSqliteSink @Inject constructor(
    private val dao:  EventDao,
    private val json: Json,
) : CorpusSink {

    override suspend fun persist(event: CiaerPlusEvent): CorpusSink.Result = try {
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
        CorpusSink.Result.Success
    } catch (t: Throwable) {
        CorpusSink.Result.Failure(t)
    }
}
