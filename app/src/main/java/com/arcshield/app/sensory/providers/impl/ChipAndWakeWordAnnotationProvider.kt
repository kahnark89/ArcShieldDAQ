package com.arcshield.app.sensory.providers.impl

import com.arcshield.app.sensory.ChannelAvailability
import com.arcshield.app.sensory.providers.OlfactoryAnnotationProvider

/**
 * Gen 1 olfactory provider — stores the last annotation submitted via quick-select
 * chip UI or the "smell that" wake-word path.
 *
 * No hardware is involved. The annotation is set externally by the UI layer and
 * read by OlfactoryChannel when it captures a snapshot.
 *
 * Gen 2 replacement: VocSensorAnnotationProvider (Bluetooth VOC sensor).
 */
class ChipAndWakeWordAnnotationProvider : OlfactoryAnnotationProvider {

    @Volatile private var lastAnnotation: String? = null
    @Volatile private var lastIntensity: Int? = null
    @Volatile private var lastWakeWord: Boolean = false

    override suspend fun initialize(): ChannelAvailability = ChannelAvailability.AVAILABLE

    override fun recordAnnotation(annotation: String, intensity: Int?, wakeWord: Boolean) {
        lastAnnotation = annotation
        lastIntensity = intensity
        lastWakeWord = wakeWord
    }

    override fun currentVocPpb(): Float? = null

    override suspend fun release() {
        lastAnnotation = null
        lastIntensity = null
        lastWakeWord = false
    }

    companion object {
        val VOCABULARY = listOf("burnt PVC", "metallic", "smoke", "normal", "other")
    }
}
