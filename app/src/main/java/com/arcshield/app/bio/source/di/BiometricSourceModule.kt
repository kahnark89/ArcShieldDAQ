package com.arcshield.app.bio.source.di

import com.arcshield.app.bio.source.BiometricSource
import com.arcshield.app.bio.source.PolarBiometrics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [PolarBiometrics] as the active [BiometricSource]. Polar is the only
 * supported biometric path — Pixel Watch was rejected because the device only
 * surfaces HRV during sleep, which is fatal for the on-shift fusion trigger.
 *
 * If a research-grade EDA device (Empatica EmbracePlus or successor) is added
 * later, swap in a new implementation here; the rest of the app sees the
 * abstract [BiometricSource] only.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricSourceModule {
    @Binds
    @Singleton
    abstract fun bindBiometricSource(impl: PolarBiometrics): BiometricSource
}
