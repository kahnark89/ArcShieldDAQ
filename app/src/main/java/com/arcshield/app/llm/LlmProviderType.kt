package com.arcshield.app.llm

enum class LlmProviderType(
    val displayName: String,
    val keyHint: String,
) {
    CLAUDE(
        displayName = "Claude (Anthropic)",
        keyHint     = "sk-ant-…",
    ),
    GEMINI(
        displayName = "Gemini (Google)",
        keyHint     = "AIza…",
    ),
}
