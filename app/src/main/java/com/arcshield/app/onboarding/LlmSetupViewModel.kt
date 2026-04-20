package com.arcshield.app.onboarding

import androidx.lifecycle.ViewModel
import com.arcshield.app.llm.LlmProviderType
import com.arcshield.app.security.ApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LlmSetupState(
    val selectedProvider: LlmProviderType? = null,
    val apiKey: String = "",
    val saved: Boolean = false,
)

@HiltViewModel
class LlmSetupViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LlmSetupState())
    val state: StateFlow<LlmSetupState> = _state.asStateFlow()

    val isFormValid: Boolean
        get() = _state.value.selectedProvider != null && _state.value.apiKey.isNotBlank()

    fun selectProvider(type: LlmProviderType) {
        _state.update { it.copy(selectedProvider = type, apiKey = "") }
    }

    fun onApiKeyChange(key: String) {
        _state.update { it.copy(apiKey = key) }
    }

    fun saveAndProceed() {
        val provider = _state.value.selectedProvider ?: return
        val key      = _state.value.apiKey.trim()
        if (key.isBlank()) return
        apiKeyStore.saveApiKey(provider, key)
        _state.update { it.copy(saved = true) }
    }
}
