package com.arcshield.app.capture.source

import com.arcshield.app.data.schema.PovSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for Meta Ray-Ban glasses POV capture. Real implementation lands when
 * hardware arrives; the rest of the app does not change when it does.
 */
@Singleton
class GlassesSource @Inject constructor() : CaptureSource {
    override val povSource: PovSource = PovSource.META_GLASSES
    override suspend fun captureFrame(): ByteArray? =
        throw NotImplementedError("GlassesSource is a stub until Meta glasses hardware is available.")
}
