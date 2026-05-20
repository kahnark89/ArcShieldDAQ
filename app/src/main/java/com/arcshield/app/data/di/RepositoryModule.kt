package com.arcshield.app.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// SensoryEventRepository is @Singleton with @Inject constructor — Hilt provides
// it automatically. This module is a placeholder for future repository bindings
// (e.g. swapping local vs. remote repositories behind an interface).
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
