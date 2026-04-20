package com.arcshield.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.arcshield.app.llm.LlmProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE         = "arcshield_secure_prefs"
private const val KEY_PROVIDER_TYPE  = "configured_provider_type"
private fun apiKeyPref(type: LlmProviderType) = "api_key_${type.name.lowercase()}"

/**
 * Encrypted storage for LLM API keys, backed by Android Keystore via
 * [EncryptedSharedPreferences].
 *
 * Keys are hardware-bound where the device supports it (StrongBox or TEE).
 * Backup is explicitly excluded in data_extraction_rules.xml — keys never
 * leave the device.
 *
 * Only the active provider type is stored here alongside its key. The type
 * is not sensitive but keeping it in the same encrypted store avoids a
 * separate prefs file and keeps the "which provider am I using" logic atomic.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { buildEncryptedPrefs() }

    /** Save an API key for the given provider and mark it as the active provider. */
    fun saveApiKey(type: LlmProviderType, key: String) {
        prefs.edit()
            .putString(apiKeyPref(type), key)
            .putString(KEY_PROVIDER_TYPE, type.name)
            .apply()
    }

    /** Retrieve the stored API key for [type], or null if not set. */
    fun getApiKey(type: LlmProviderType): String? =
        prefs.getString(apiKeyPref(type), null)?.takeIf { it.isNotBlank() }

    /** The provider type the user configured, or null if onboarding has not been completed. */
    fun getConfiguredProviderType(): LlmProviderType? {
        val name = prefs.getString(KEY_PROVIDER_TYPE, null) ?: return null
        return LlmProviderType.entries.firstOrNull { it.name == name }
    }

    /** True if a provider type and its key are both present. */
    fun isConfigured(): Boolean {
        val type = getConfiguredProviderType() ?: return false
        return getApiKey(type) != null
    }

    /** Remove all stored keys and clear the configured provider. Used in settings reset. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
