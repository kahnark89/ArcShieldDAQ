package com.arcshield.app.sensory.channels

import com.arcshield.app.sensory.*
import com.arcshield.app.sensory.providers.OlfactoryAnnotationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Olfactory channel.
 *
 * Gen 1: annotation-based. Operator submits a quick-select chip ("burnt PVC",
 *        "normal", "metallic") or speaks via wake word ("smell that"). The most
 *        recent annotation within the capture window is attached to the snapshot.
 *
 * Gen 2: VOC sensor (mox or PID-based) via Bluetooth. Drop in a VocSensorProvider
 *        — channel API does not change; vocPpb field on snapshot becomes populated.
 *
 * Annotations are submitted by the SensoryCaptureManager via [submitAnnotation].
 */
class OlfactoryChannel(
    private val annotationProvider: OlfactoryAnnotationProvider
) : SensoryChannel<OlfactorySnapshot> {

    override val channelId: SensoryChannelId = SensoryChannelId.OLFACTORY

    private var _availability: ChannelAvailability = ChannelAvailability.AVAILABLE
    override val availability: ChannelAvailability get() = _availability

    /** Most recent annotation submitted by the operator (null = none this shift). */
    @Volatile private var latestAnnotation: String? = null
    @Volatile private var latestIntensity: Int? = null
    @Volatile private var latestWakeWord: Boolean = false
    @Volatile private var latestAnnotationTs: Long = 0L

    private val deltaFlow = MutableSharedFlow<SensoryDelta>(extraBufferCapacity = 16)

    override suspend fun initialize(): ChannelAvailability {
        _availability = annotationProvider.initialize()
        return _availability
    }

    override suspend fun captureBaseline(): OlfactorySnapshot? = snapshot()
    override suspend fun captureEvent(): OlfactorySnapshot? = snapshot()

    override fun significantDeltaStream(): Flow<SensoryDelta> = deltaFlow

    override suspend fun release() {
        annotationProvider.release()
        latestAnnotation = null
        latestIntensity = null
        _availability = ChannelAvailability.NOT_INITIALIZED
    }

    /**
     * Called by SensoryCaptureManager when the operator submits an olfactory annotation.
     * Updates the rolling latest-annotation cache. If [wakeWord] is true, the operator
     * actively flagged a smell event — emit a synthetic significant delta for trigger arbitration.
     */
    fun submitAnnotation(annotation: String, intensity: Int? = null, wakeWord: Boolean = false) {
        latestAnnotation = annotation
        latestIntensity = intensity
        latestWakeWord = wakeWord
        latestAnnotationTs = System.currentTimeMillis()
        annotationProvider.recordAnnotation(annotation, intensity, wakeWord)
        if (wakeWord) {
            // Wake-word smell call is itself a passive trigger.
            deltaFlow.tryEmit(
                OlfactoryDelta(
                    baselineAnnotation = null,
                    eventAnnotation = annotation,
                    vocPpbDelta = null,
                    magnitude = 1.0f,
                    significant = true
                )
            )
        }
    }

    private fun snapshot(): OlfactorySnapshot {
        // If the annotation is older than ANNOTATION_TTL_MS, treat as stale.
        val age = System.currentTimeMillis() - latestAnnotationTs
        val annotation = if (age < ANNOTATION_TTL_MS) latestAnnotation else null
        val intensity = if (age < ANNOTATION_TTL_MS) latestIntensity else null
        return OlfactorySnapshot(
            timestampMs = System.currentTimeMillis(),
            annotation = annotation,
            intensity = intensity,
            vocPpb = annotationProvider.currentVocPpb(),
            wakeWordTriggered = latestWakeWord && age < ANNOTATION_TTL_MS
        )
    }

    companion object {
        /** Annotations older than this are not attached to the snapshot. 5 min default. */
        const val ANNOTATION_TTL_MS = 5 * 60 * 1000L
    }
}
