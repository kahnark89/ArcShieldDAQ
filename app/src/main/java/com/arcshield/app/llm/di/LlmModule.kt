package com.arcshield.app.llm.di

import com.arcshield.app.llm.ClaudeLlmProvider
import com.arcshield.app.llm.GeminiLlmProvider
import com.arcshield.app.llm.LlmProvider
import com.arcshield.app.llm.LlmProviderType
import com.arcshield.app.security.ApiKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    /**
     * Provides the active [LlmProvider] based on the provider type and API key
     * stored in [ApiKeyStore]. Called once at app start; the provider is a
     * singleton for the lifetime of the process.
     *
     * If no provider has been configured yet (first launch before onboarding
     * completes) this returns null. Callers that need the provider before
     * onboarding should check for null and redirect to [LlmSetupScreen].
     */
    @Provides
    @Singleton
    fun provideLlmProvider(apiKeyStore: ApiKeyStore): LlmProvider? {
        val type = apiKeyStore.getConfiguredProviderType() ?: return null
        val key  = apiKeyStore.getApiKey(type) ?: return null
        return when (type) {
            LlmProviderType.CLAUDE -> ClaudeLlmProvider(key)
            LlmProviderType.GEMINI -> GeminiLlmProvider(key)
        }
    }
}
