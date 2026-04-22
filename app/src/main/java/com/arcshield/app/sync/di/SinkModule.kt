package com.arcshield.app.sync.di

import com.arcshield.app.sync.CorpusSink
import com.arcshield.app.sync.LocalSqliteSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton
import dagger.Provides

@Module
@InstallIn(SingletonComponent::class)
abstract class SinkModule {
    // Primary sink is the local SQLite queue. SyncService drains this into
    // LocalServerSink separately when the network is available.
    @Binds
    @Singleton
    abstract fun bindCorpusSink(impl: LocalSqliteSink): CorpusSink
}

@Module
@InstallIn(SingletonComponent::class)
object JsonModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = true
    }
}
