package com.arcshield.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EventDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
