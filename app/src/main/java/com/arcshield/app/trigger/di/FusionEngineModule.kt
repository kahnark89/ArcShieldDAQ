package com.arcshield.app.trigger.di

import com.arcshield.app.trigger.TriggerClock
import com.arcshield.app.trigger.WallTriggerClock
import com.arcshield.app.trigger.scorer.AcousticScorer
import com.arcshield.app.trigger.scorer.DefaultAcousticScorer
import com.arcshield.app.trigger.scorer.DefaultHrvScorer
import com.arcshield.app.trigger.scorer.GazeHandScorer
import com.arcshield.app.trigger.scorer.HrvScorer
import com.arcshield.app.trigger.scorer.NoOpGazeHandScorer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the three signal-scorer interfaces consumed by
 * [com.arcshield.app.trigger.FusionEngine].
 *
 *   - [HrvScorer]      → [DefaultHrvScorer]      (real, drives off PolarBiometrics)
 *   - [GazeHandScorer] → [NoOpGazeHandScorer]   (returns null until MediaPipe lands)
 *   - [AcousticScorer] → [DefaultAcousticScorer] (FFT z-score anomaly via AcousticChannel)
 *
 * Also provides the production [TriggerClock] backed by
 * `System.currentTimeMillis()`. Tests bypass DI entirely and pass a fake
 * clock directly into the [FusionEngine] constructor.
 *
 * When the gaze/hand or acoustic detectors are ready, swap the @Binds target
 * here — no other code changes required.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FusionEngineModule {
    @Binds @Singleton
    abstract fun bindHrvScorer(impl: DefaultHrvScorer): HrvScorer

    @Binds @Singleton
    abstract fun bindGazeHandScorer(impl: NoOpGazeHandScorer): GazeHandScorer

    @Binds @Singleton
    abstract fun bindAcousticScorer(impl: DefaultAcousticScorer): AcousticScorer

    companion object {
        @Provides @Singleton
        fun provideTriggerClock(): TriggerClock = WallTriggerClock
    }
}
