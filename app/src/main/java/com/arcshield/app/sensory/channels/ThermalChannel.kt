package com.arcshield.app.sensory.channels

import com.arcshield.app.sensory.*
import com.arcshield.app.sensory.providers.ThermalAmbientProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Thermal channel.
 *
 * Gen 1: ambient outdoor temperature from Open-Meteo (lat/lon → temp). The PPVC
 *        Line 1 facility is open-bay; ambient outdoor temperature is a useful
 *        floor-temperature proxy. Surface temp is null on Gen 1.
 *
 * Gen 2: Bluetooth IR thermometer (e.g., FLIR One, dedicated wearable IR). Drop
 *        in a SurfaceIrProvider — surfaceTempF becomes populated.
 *
 * Annotations route from SensoryCaptureManager via [submitAnnotation] — same pattern
 * as olfactory. Use for things the operator notices about heat but doesn't have a
 * sensor to quantify ("barrel runs hot today").
 */
class ThermalChannel(
    private val ambientProvider: ThermalAmbientProvider
) : SensoryChannel<ThermalSnapshot> {

    override val channelId: SensoryChannelId = SensoryChannelId.THERMAL

    private var _availability: ChannelAvailability = ChannelAvailability.NOT_INITIALIZED
    override val availability: ChannelAvailability get() = _availability

    @Volatile private var latestAnnotation: String? = null
    @Volatile private var latestAnnotationTs: Long = 0L

    override suspend fun initialize(): ChannelAvailability {
        _availability = ambientProvider.initialize()
        return _availability
    }

    override suspend fun captureBaseline(): ThermalSnapshot? = snapshot()
    override suspend fun captureEvent(): ThermalSnapshot? = snapshot()

    /** No passive thermal stream on Gen 1 — proxy values change slowly. */
    override fun significantDeltaStream(): Flow<SensoryDelta> = emptyFlow()

    override suspend fun release() {
        ambientProvider.release()
        latestAnnotation = null
        _availability = ChannelAvailability.NOT_INITIALIZED
    }

    /** Operator submitted a thermal annotation. */
    fun submitAnnotation(annotation: String) {
        latestAnnotation = annotation
        latestAnnotationTs = System.currentTimeMillis()
    }

    private suspend fun snapshot(): ThermalSnapshot {
        val ambient = ambientProvider.currentAmbientTempF()
        val age = System.currentTimeMillis() - latestAnnotationTs
        val annotation = if (age < ANNOTATION_TTL_MS) latestAnnotation else null
        return ThermalSnapshot(
            timestampMs = System.currentTimeMillis(),
            ambientTempF = ambient,
            surfaceTempF = null,  // Gen 2 will populate via SurfaceIrProvider
            annotation = annotation,
            sourceLabel = ambientProvider.sourceLabel()
        )
    }

    companion object {
        const val ANNOTATION_TTL_MS = 15 * 60 * 1000L  // thermal context changes slowly
    }
}
