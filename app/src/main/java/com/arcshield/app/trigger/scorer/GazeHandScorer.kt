package com.arcshield.app.trigger.scorer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps live POV-camera frames to gaze fixation and hand-object interaction
 * scores per detection-spec §3.1 / §3.2.
 *
 * No production implementation exists yet — gaze estimation needs OpenGaze
 * or MLKit Face Mesh, and hand-pose needs MediaPipe Hands. Both depend on
 * a real [com.arcshield.app.sensory.providers.VisualFrameProvider]
 * implementation (target: PhoneCameraFrameProvider) which is queued for
 * the next session. Until then [NoOpGazeHandScorer] returns null for both
 * channels and the fusion engine sees a constant 0 contribution from them.
 */
interface GazeHandScorer {
    suspend fun gaze(): Float?
    suspend fun hand(): Float?
}

@Singleton
class NoOpGazeHandScorer @Inject constructor() : GazeHandScorer {
    override suspend fun gaze(): Float? = null
    override suspend fun hand(): Float? = null
}
