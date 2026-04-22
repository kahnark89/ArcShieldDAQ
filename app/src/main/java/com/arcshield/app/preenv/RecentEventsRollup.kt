package com.arcshield.app.preenv

import com.arcshield.app.data.EventDao
import com.arcshield.app.data.schema.CiaerPlusEvent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles a short natural-language summary of recent events this shift.
 *
 * Gen 1 is template-based: enumerates the last N events' action_type +
 * outcome_tag. LLM-summarized phrasing is a later enhancement.
 */
@Singleton
class RecentEventsRollup @Inject constructor(
    private val prefs: PreEnvPrefsStore,
    private val dao:   EventDao,
    private val json:  Json,
) {
    suspend fun summary(): String {
        val sessionId = prefs.shiftSessionId.first() ?: return NO_SESSION
        val events    = dao.recentForSession(sessionId, MAX_EVENTS)
        if (events.isEmpty()) return NO_PRIOR
        return events.joinToString(separator = "; ") { row ->
            runCatching {
                val decoded = json.decodeFromString(CiaerPlusEvent.serializer(), row.eventJson)
                "${decoded.action.actionType} → ${decoded.result.outcomeTag.name.lowercase()}"
            }.getOrDefault("(unreadable event)")
        }
    }

    companion object {
        private const val MAX_EVENTS = 5
        private const val NO_SESSION = "No active shift session."
        private const val NO_PRIOR   = "No prior events recorded this shift."
    }
}
