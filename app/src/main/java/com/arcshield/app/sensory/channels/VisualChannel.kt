package com.arcshield.app.sensory.channels

import com.arcshield.app.sensory.*
import com.arcshield.app.sensory.providers.VisualFrameProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Visual channel — fully delegates frame acquisition to a [VisualFrameProvider].
 *
 * Gen 1: PhoneCameraFrameProvider wraps CameraX rear camera.
 * Gen 2: MetaGlassesFrameProvider wraps Meta Wearables Device Access Toolkit POV stream.
 * Swap is zero-code in this channel — only the provider implementation changes.
 *
 * This channel does NOT do gauge OCR. It captures a frame reference and lightweight
 * frame statistics (luminance, motion). Gauge digitization happens downstream via
 * Claude / Gemini vision API call on the saved frame_path.
 */
class VisualChannel(
    private val frameProvider: VisualFrameProvider
) : SensoryChannel<VisualSnapshot> {

    override val channelId: SensoryChannelId = SensoryChannelId.VISUAL

    private var _availability: ChannelAvailability = ChannelAvailability.NOT_INITIALIZED
    override val availability: ChannelAvailability get() = _availability

    override suspend fun initialize(): ChannelAvailability {
        _availability = frameProvider.initialize()
        return _availability
    }

    override suspend fun captureBaseline(): VisualSnapshot? {
        if (_availability != ChannelAvailability.AVAILABLE) return null
        return frameProvider.captureFrame(label = "baseline")
    }

    override suspend fun captureEvent(): VisualSnapshot? {
        if (_availability != ChannelAvailability.AVAILABLE) return null
        return frameProvider.captureFrame(label = "event")
    }

    /**
     * Frame-differencing dwell detection lives in the provider — it has continuous
     * access to the preview stream. Channel just relays the provider's flow.
     */
    override fun significantDeltaStream(): Flow<SensoryDelta> =
        frameProvider.significantMotionStream() ?: emptyFlow()

    override suspend fun release() {
        frameProvider.release()
        _availability = ChannelAvailability.NOT_INITIALIZED
    }
}
