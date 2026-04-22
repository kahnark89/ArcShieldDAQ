package com.arcshield.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY created_at DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE shift_session_id = :sessionId ORDER BY created_at DESC LIMIT :limit")
    suspend fun recentForSession(sessionId: String, limit: Int): List<EventEntity>

    @Query("SELECT * FROM events WHERE synced = 0 ORDER BY created_at ASC LIMIT :limit")
    suspend fun pendingSync(limit: Int): List<EventEntity>

    @Query("UPDATE events SET synced = 1 WHERE event_id = :eventId")
    suspend fun markSynced(eventId: String)

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int
}
