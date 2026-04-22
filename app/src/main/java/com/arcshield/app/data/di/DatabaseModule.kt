package com.arcshield.app.data.di

import android.content.Context
import androidx.room.Room
import com.arcshield.app.data.EventDao
import com.arcshield.app.data.EventDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EventDatabase =
        Room.databaseBuilder(context, EventDatabase::class.java, "arcshield_events.db").build()

    @Provides
    fun provideEventDao(db: EventDatabase): EventDao = db.eventDao()
}
