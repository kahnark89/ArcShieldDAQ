package com.arcshield.app.sensory.providers

import com.arcshield.app.sensory.ChannelAvailability
import com.arcshield.app.sensory.SensoryDelta
import com.arcshield.app.sensory.VisualSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Provider abstraction for visual frame acquisition.
 *
 * Gen 1 implementation: PhoneCameraFrameProvider (CameraX rear camera).
 * Gen 2 implementation: MetaGlassesFrameProvider (Wearables Device Access Toolkit).
 *
 * Substitution requires zero changes to VisualChannel or anything upstream.
 * Concrete implementations live outside the sensory module — they are wired
 * in at the Application / DI layer.
 */
interface VisualFrameProvider {

    /** Acquire camera, request permissions, prepare for frame capture. */
    suspend fun initialize(): ChannelAvailability

    /**
     * Capture one frame, persist to local storage, return a VisualSnapshot
     * with the saved frame_path. The downstream Vision API call (Claude / Gemini
     * for gauge OCR) happens at event assembly time, not here.
     */
    suspend fun captureFrame(label: String): VisualSnapshot?

    /**
     * Optional: stream of significant motion / dwell events from the live preview.
     * Implementations that don't support continuous preview return null.
     * Frame-differencing dwell detection runs here; the channel just relays.
     */
    fun significantMotionStream(): Flow<SensoryDelta>?

    /** Release camera, stop preview. */
    suspend fun release()
}
