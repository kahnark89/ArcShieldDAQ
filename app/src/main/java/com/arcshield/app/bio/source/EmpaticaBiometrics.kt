package com.arcshield.app.bio.source

import com.arcshield.app.data.schema.BiometricSnapshot
import com.arcshield.app.data.schema.SourceDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empatica EmbracePlus source — reserved for the §7.1 EDA validation sub-study.
 * Gen 1 deployment uses [PixelWatchBiometrics] exclusively. This stub exists so
 * the config toggle and DI graph can reference the symbol without changing
 * downstream consumers when the device arrives.
 */
@Singleton
class EmpaticaBiometrics @Inject constructor() : BiometricSource {
    override suspend fun currentSnapshot(): BiometricSnapshot =
        BiometricSnapshot(sourceDevice = SourceDevice.EMPATICA_EMBRACEPLUS)
}
