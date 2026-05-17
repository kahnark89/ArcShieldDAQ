package com.arcshield.app.sensory.di

import android.content.Context
import com.arcshield.app.sensory.SensoryCaptureManager
import com.arcshield.app.sensory.channels.AcousticChannel
import com.arcshield.app.sensory.channels.OlfactoryChannel
import com.arcshield.app.sensory.channels.ThermalChannel
import com.arcshield.app.sensory.channels.VibrationChannel
import com.arcshield.app.sensory.channels.VisualChannel
import com.arcshield.app.sensory.providers.OlfactoryAnnotationProvider
import com.arcshield.app.sensory.providers.ThermalAmbientProvider
import com.arcshield.app.sensory.providers.VisualFrameProvider
import com.arcshield.app.sensory.providers.impl.ChipAndWakeWordAnnotationProvider
import com.arcshield.app.sensory.providers.impl.OpenMeteoThermalProvider
import com.arcshield.app.sensory.providers.impl.PhoneCameraFrameProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SensoryModule {

    @Provides @Singleton
    fun provideAcousticChannel(@ApplicationContext context: Context): AcousticChannel =
        AcousticChannel(context)

    @Provides @Singleton
    fun provideOlfactoryProvider(): OlfactoryAnnotationProvider =
        ChipAndWakeWordAnnotationProvider()

    @Provides @Singleton
    fun provideThermalProvider(): ThermalAmbientProvider =
        OpenMeteoThermalProvider()

    @Provides @Singleton
    fun provideVisualProvider(@ApplicationContext context: Context): VisualFrameProvider =
        PhoneCameraFrameProvider(context)

    @Provides @Singleton
    fun provideSensoryCaptureManager(
        @ApplicationContext context: Context,
        acousticChannel: AcousticChannel,
        visualProvider: VisualFrameProvider,
        olfactoryProvider: OlfactoryAnnotationProvider,
        thermalProvider: ThermalAmbientProvider,
    ): SensoryCaptureManager = SensoryCaptureManager(
        acousticChannel  = acousticChannel,
        vibrationChannel = VibrationChannel(context),
        visualChannel    = VisualChannel(visualProvider),
        olfactoryChannel = OlfactoryChannel(olfactoryProvider),
        thermalChannel   = ThermalChannel(thermalProvider),
    )
}
