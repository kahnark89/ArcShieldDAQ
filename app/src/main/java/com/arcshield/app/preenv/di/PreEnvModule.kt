package com.arcshield.app.preenv.di

import com.arcshield.app.preenv.source.ManualPreEnvSource
import com.arcshield.app.preenv.source.PreEnvSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreEnvModule {
    @Binds
    @Singleton
    abstract fun bindPreEnvSource(impl: ManualPreEnvSource): PreEnvSource
}
