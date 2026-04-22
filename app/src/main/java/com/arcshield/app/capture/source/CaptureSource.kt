package com.arcshield.app.capture.source

import com.arcshield.app.data.schema.PovSource

/**
 * Abstraction over point-of-view camera sources.
 *
 * Gen 1 default is [PhoneCameraSource] (CameraX on the phone).
 * [GlassesSource] is a stub that activates when Meta glasses hardware lands.
 * Downstream consumers (CauseScreen frame capture, GaugeReader) do not know
 * which physical device produced a given frame — they only see JPEG bytes.
 */
interface CaptureSource {
    val povSource: PovSource

    /**
     * Capture a single JPEG frame. Returns null if no frame is available,
     * e.g. the camera has not been bound to a lifecycle yet. Implementations
     * that require on-screen preview binding (phone) document that contract
     * in their own KDoc.
     */
    suspend fun captureFrame(): ByteArray?
}
