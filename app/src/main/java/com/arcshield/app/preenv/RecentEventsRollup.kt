package com.arcshield.app.preenv

import javax.inject.Inject
import javax.inject.Singleton

// Gen 1 stub. Wires to Room event DB in Phase 3; LLM summarization is later.
@Singleton
class RecentEventsRollup @Inject constructor() {
    suspend fun summary(): String = "No prior events recorded this shift."
}
