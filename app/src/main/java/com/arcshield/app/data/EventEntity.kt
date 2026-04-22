package com.arcshield.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row layout for captured events. The full CIAER+ JSON is stored as a blob
 * in [eventJson] — querying by structured fields uses the indexed top-level
 * columns (operator_id, shift_session_id) to keep reads cheap. A few hundred
 * events per shift does not justify normalizing nested arrays into tables.
 */
@Entity(
    tableName = "events",
    indices = [
        Index("shift_session_id"),
        Index("synced"),
        Index("created_at"),
    ],
)
data class EventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")         val eventId: String,
    @ColumnInfo(name = "created_at")       val createdAtEpochMs: Long,
    @ColumnInfo(name = "operator_id")      val operatorId: String,
    @ColumnInfo(name = "shift_session_id") val shiftSessionId: String?,
    @ColumnInfo(name = "synced")           val synced: Boolean = false,
    @ColumnInfo(name = "event_json")       val eventJson: String,
)
