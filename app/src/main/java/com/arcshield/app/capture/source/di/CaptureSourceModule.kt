package com.arcshield.app.capture.source.di

import com.arcshield.app.capture.source.CaptureSource
import com.arcshield.app.capture.source.PhoneCameraSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureSourceModule {
    // Gen 1 default. ConfigScreen toggle to GlassesSource comes later.
    @Binds
    @Singleton
    abstract fun bindCaptureSource(impl: PhoneCameraSource): CaptureSource
}
