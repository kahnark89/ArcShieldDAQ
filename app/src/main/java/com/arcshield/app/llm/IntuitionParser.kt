package com.arcshield.app.llm

import android.util.Log
import com.arcshield.app.data.schema.KnowledgeSource
import com.arcshield.app.data.schema.SrkLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Extracts structured Intuition fields from a free-form voice transcript
 * using whichever [LlmProvider] is configured. The LLM is asked for a
 * strict JSON response; on parse or API failure the parser returns an
 * empty [ParsedIntuition] so the UI falls back to manual entry.
 *
 * This lives in the llm/ package (not voice/) because it depends on a
 * multimodal-capable provider — Gen 2 may use the same provider that
 * handled the Cause frame.
 */
@Singleton
class IntuitionParser @Inject constructor(
    private val llm: LlmProvider?,
) {
    data class ParsedIntuition(
        val srkLevel:         SrkLevel?         = null,
        val causalHypothesis: String?           = null,
        val projection:       String?           = null,
        val confidenceLevel:  Double?           = null,
        val knowledgeSource:  KnowledgeSource?  = null,
    )

    suspend fun parse(transcript: String): ParsedIntuition {
        if (transcript.isBlank() || llm == null) return ParsedIntuition()
        return try {
            // Provider does not expose a text-only chat method today; Gen 1
            // routes through describeVisualAnchor by encoding the prompt as
            // a 1x1 placeholder would be wrong. For now, parse locally with
            // cheap heuristics until the LlmProvider interface gains a
            // dedicated chat method — deferred to avoid over-abstracting.
            heuristic(transcript)
        } catch (e: Throwable) {
            Log.w(TAG, "intuition parse failed", e)
            ParsedIntuition()
        }
    }

    /**
     * Conservative heuristic parser: extracts signal words the operator
     * typically uses on Line 1. Always safe to overwrite — returns nulls
     * for fields it cannot confidently determine.
     */
    private fun heuristic(t: String): ParsedIntuition {
        val lower = t.lowercase()
        val srk = when {
            "always" in lower || "every time" in lower || "procedure" in lower -> SrkLevel.RULE
            "gut" in lower   || "feel" in lower || "something's off" in lower  -> SrkLevel.KNOWLEDGE
            "habit" in lower || "muscle memory" in lower                       -> SrkLevel.SKILL
            else -> null
        }
        val knowledge = when {
            "learned from" in lower || "taught me" in lower  -> KnowledgeSource.LEARNED_FROM_COLLEAGUE
            "training" in lower || "manual says" in lower    -> KnowledgeSource.FORMAL_TRAINING
            "i remember" in lower || "last time" in lower    -> KnowledgeSource.PERSONAL_EXPERIENCE
            else -> null
        }
        val confidence = when {
            "sure" in lower || "certain" in lower            -> 0.9
            "probably" in lower || "likely" in lower         -> 0.7
            "maybe" in lower  || "might" in lower            -> 0.4
            else -> null
        }
        return ParsedIntuition(
            srkLevel         = srk,
            causalHypothesis = t.trim().takeIf { it.isNotBlank() },
            confidenceLevel  = confidence,
            knowledgeSource  = knowledge,
        )
    }

    private companion object {
        const val TAG = "IntuitionParser"
        @Suppress("unused")
        val JSON = Json { ignoreUnknownKeys = true }
    }

    @Suppress("unused")
    @Serializable
    private data class LlmIntuitionResponse(
        @SerialName("srk_level")         val srkLevel: String? = null,
        @SerialName("causal_hypothesis") val causalHypothesis: String? = null,
        @SerialName("projection")        val projection: String? = null,
        @SerialName("confidence_level")  val confidenceLevel: Double? = null,
        @SerialName("knowledge_source")  val knowledgeSource: String? = null,
    )
}
