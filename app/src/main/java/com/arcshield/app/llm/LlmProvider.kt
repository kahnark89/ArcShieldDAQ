package com.arcshield.app.llm

/**
 * Abstraction over multimodal LLM providers used for gauge reading and
 * visual anchor description.
 *
 * The rest of the app does not know which provider is active. Gen 1
 * implementations: [ClaudeLlmProvider], [GeminiLlmProvider].
 *
 * See: ArcShield whitepaper v10.1 §5 (hardware/provider abstraction principle).
 */
interface LlmProvider {

    val type: LlmProviderType

    /**
     * Analyse a camera frame and return all visible instrument readings.
     *
     * [imageBytes] — JPEG or PNG bytes of the frame.
     * [hint]       — Optional natural-language hint about what instruments
     *                are expected in frame (improves accuracy on dense panels).
     *
     * Returns a list of [GaugeReading]. Empty list if no instruments are
     * confidently detected. Never throws for a successful HTTP response —
     * parse failures return an empty list with a logged warning.
     *
     * @throws LlmProviderException on network error or non-2xx HTTP response.
     */
    suspend fun readGauges(imageBytes: ByteArray, hint: String = ""): List<GaugeReading>

    /**
     * Generate a short natural-language description of the visual element
     * that most likely drew the operator's gaze in the supplied frame.
     *
     * Maps to [cause.visual_anchor_description] in the CIAER+ schema.
     *
     * @throws LlmProviderException on network error or non-2xx HTTP response.
     */
    suspend fun describeVisualAnchor(imageBytes: ByteArray): String
}

/**
 * A single instrument reading extracted from a camera frame.
 *
 * This is the intermediate representation between the LLM response and
 * [com.arcshield.app.schema.SensorReading]. The capture state machine
 * converts these to SensorReading objects before writing to the event.
 */
data class GaugeReading(
    val instrumentId: String,
    val value: String,
    val unit: String?,
    val confidence: Float,
)

class LlmProviderException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
