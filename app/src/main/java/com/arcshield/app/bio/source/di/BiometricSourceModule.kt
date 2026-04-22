package com.arcshield.app.bio.source.di

import com.arcshield.app.bio.source.BiometricSource
import com.arcshield.app.bio.source.PixelWatchBiometrics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Gen 1 binding: [PixelWatchBiometrics] is the active biometric source.
 * Swap to [com.arcshield.app.bio.source.EmpaticaBiometrics] when the §7.1
 * validation sub-study hardware arrives — no downstream consumer changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricSourceModule {
    @Binds
    @Singleton
    abstract fun bindBiometricSource(impl: PixelWatchBiometrics): BiometricSource
}
