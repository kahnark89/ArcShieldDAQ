package com.arcshield.app.data.schema

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Schema/Kotlin round-trip for the new TriggerContext sub-object. The
 * CIAER+ schema is the single source of truth; this test pins the Kotlin
 * surface to the snake_case JSON shape declared in
 * `schema/ciaer_plus_v1.json` under `#/$defs/TriggerContext`.
 */
class TriggerContextSerializationTest {

    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = false }

    @Test fun `populated TriggerContext round-trips through JSON unchanged`() {
        val original = TriggerContext(
            triggerType      = TriggerType.AUTOMATIC,
            confidenceScore  = 0.78,
            signalScores     = SignalScores(gaze = 0.9, hand = 0.6, hrv = 0.5, acoustic = 0.3),
            temporalModifier = 1.05,
            operationalMode  = OperationalMode.STEADY,
            thresholdAtFire  = 0.75,
            sensorContext    = TriggerSensorContext(
                videoClipUrl = "file:///cache/clips/foo.mp4",
                audioClipUrl = null,
                biometricSnapshot = BiometricSnapshot(hrBpm = 88.0, hrvRmssdMs = 32.4),
            ),
            feedback         = TriggerFeedback(operatorValidated = true, notes = "good catch"),
        )
        val text = json.encodeToString(TriggerContext.serializer(), original)
        val parsed = json.decodeFromString(TriggerContext.serializer(), text)
        assertEquals(original, parsed)
    }

    @Test fun `JSON keys are snake_case matching the schema`() {
        val ctx = TriggerContext(
            triggerType     = TriggerType.MANUAL,
            confidenceScore = 0.5,
        )
        val text = json.encodeToString(TriggerContext.serializer(), ctx)
        // Spot-check that we did not regress to camelCase keys.
        assertNotNull("trigger_type missing", Regex("\"trigger_type\"").find(text))
        assertNotNull("confidence_score missing", Regex("\"confidence_score\"").find(text))
        // And that absent optional fields are not emitted (encodeDefaults=false).
        assertEquals(null, Regex("\"sensor_context\"").find(text))
    }

    @Test fun `Cause now carries trigger_context`() {
        val cause = Cause(
            captureTimestamp = "2026-05-17T12:34:56Z",
            triggerSource    = TriggerSource.MULTIMODAL,
            triggerChannels  = listOf(TriggerChannel.FUSION_THRESHOLD),
            triggerContext   = TriggerContext(
                triggerType     = TriggerType.AUTOMATIC,
                confidenceScore = 0.81,
            ),
        )
        val text = json.encodeToString(Cause.serializer(), cause)
        val parsed = json.decodeFromString(Cause.serializer(), text)
        assertEquals(cause, parsed)
        assertNotNull("trigger_context not serialized on Cause", Regex("\"trigger_context\"").find(text))
    }
}
