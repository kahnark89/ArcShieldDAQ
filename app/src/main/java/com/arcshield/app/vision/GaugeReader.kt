package com.arcshield.app.vision

import android.util.Log
import com.arcshield.app.data.schema.ReadSource
import com.arcshield.app.data.schema.SensorReading
import com.arcshield.app.llm.LlmProvider
import com.arcshield.app.llm.LlmProviderException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vision-based gauge reader that turns a Cause-moment camera frame into
 * structured [SensorReading]s for the CIAER+ schema.
 *
 * Thin adapter over [LlmProvider.readGauges]; the LLM provider does the
 * heavy lifting. This class just coerces types and handles null-safety so
 * capture flow never sees an exception — bad frames or API failures yield
 * an empty reading list and a log warning.
 */
@Singleton
class GaugeReader @Inject constructor(
    private val llm: LlmProvider?,
) {
    suspend fun read(imageBytes: ByteArray, hint: String = ""): List<SensorReading> {
        val provider = llm ?: return emptyList()
        return try {
            provider.readGauges(imageBytes, hint).map {
                SensorReading(
                    instrumentId = it.instrumentId,
                    value        = it.value,
                    unit         = it.unit,
                    confidence   = it.confidence.toDouble(),
                    readSource   = ReadSource.VISION_LLM,
                )
            }
        } catch (e: LlmProviderException) {
            Log.w(TAG, "gauge read failed", e)
            emptyList()
        }
    }

    private companion object { const val TAG = "GaugeReader" }
}
