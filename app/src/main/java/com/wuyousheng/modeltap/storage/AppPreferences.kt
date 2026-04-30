package com.wuyousheng.modeltap.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wuyousheng.modeltap.domain.model.ApiConfig
import com.wuyousheng.modeltap.domain.model.ApiKeyEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

fun ApiConfig.normalized(): ApiConfig {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val normalizedModel = selectedModel.trim()
    val isTavilyModelConfig = isTavilyBaseUrl(normalizedBaseUrl) ||
        normalizedModel.equals("web-search", ignoreCase = true)
    return copy(
        baseUrl = if (isTavilyModelConfig) "" else normalizedBaseUrl,
        apiKey = if (isTavilyModelConfig) "" else apiKey.trim(),
        assistantName = assistantName.trim().ifBlank { "助手" },
        selectedModel = if (isTavilyModelConfig) "" else normalizedModel,
        tavilyApiKey = tavilyApiKey.trim(),
        contextMemory = contextMemory.ifBlank { "长期" },
        reasoningStrength = reasoningStrength.ifBlank { "中" },
        replyStyle = replyStyle.ifBlank { "平衡" },
        maxTokens = maxTokens.coerceAtLeast(0),
        contextWindow = contextWindow.coerceIn(4096, 131072)
    )
}

fun isTavilyBaseUrl(baseUrl: String): Boolean {
    val normalized = normalizeBaseUrl(baseUrl)
    return normalized.contains("api.tavily.com", ignoreCase = true)
}

fun normalizeBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""
    return "$trimmed/"
}

fun isValidHttpsBaseUrl(baseUrl: String): Boolean {
    val normalized = normalizeBaseUrl(baseUrl)
    if (normalized.isBlank()) return false
    return runCatching {
        val uri = URI(normalized)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

class AppPreferences(
    private val context: Context,
    private val secureApiKeyStore: SecureApiKeyStore = SecureApiKeyStore(context)
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val configFlow: Flow<ApiConfig> = context.dataStore.data.transform { preferences ->
        val legacyApiKey = preferences[API_KEY].orEmpty()
        var secureApiKey = secureApiKeyStore.readApiKey()
        val tavilyApiKey = secureApiKeyStore.readTavilyApiKey()
        if (secureApiKey.isBlank() && legacyApiKey.isNotBlank()) {
            secureApiKey = legacyApiKey.trim()
            secureApiKeyStore.saveApiKey(secureApiKey)
            context.dataStore.edit { it -= API_KEY }
        }
        emit(
            ApiConfig(
                baseUrl = preferences[BASE_URL].orEmpty(),
                apiKey = secureApiKey.ifBlank { legacyApiKey },
                selectedModel = preferences[SELECTED_MODEL].orEmpty(),
                assistantName = preferences[ASSISTANT_NAME].orEmpty().ifBlank { "助手" },
                temperature = preferences[TEMPERATURE] ?: 0.7f,
                maxTokens = preferences[MAX_TOKENS]?.coerceAtLeast(0) ?: 1024,
                topP = preferences[TOP_P] ?: 1.0f,
                systemPrompt = preferences[SYSTEM_PROMPT].orEmpty(),
                webSearchEnabled = preferences[WEB_SEARCH_ENABLED] ?: false,
                tavilyApiKey = tavilyApiKey,
                contextMemory = preferences[CONTEXT_MEMORY] ?: "长期",
                reasoningStrength = preferences[REASONING_STRENGTH] ?: "中",
                replyStyle = preferences[REPLY_STYLE] ?: "平衡",
                contextWindow = preferences[CONTEXT_WINDOW] ?: 32768
            )
        )
    }

    val apiKeyEntriesFlow: Flow<List<ApiKeyEntry>> = context.dataStore.data.map { preferences ->
        val raw = preferences[API_KEY_ENTRIES].orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<ApiKeyEntry>>(raw) }
                .getOrDefault(emptyList())
                .map { entry ->
                    val secureApiKey = secureApiKeyStore.readApiKeyEntry(entry.id)
                    entry.copy(apiKey = secureApiKey.ifBlank { entry.apiKey })
                }
        }
    }

    val privacyNoticeAcceptedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PRIVACY_NOTICE_ACCEPTED] ?: false
    }

    suspend fun acceptPrivacyNotice() {
        context.dataStore.edit { preferences ->
            preferences[PRIVACY_NOTICE_ACCEPTED] = true
        }
    }

    suspend fun saveApiKeyEntry(entry: ApiKeyEntry, makeCurrent: Boolean = true) {
        val storedApiKey = entry.apiKey.trim()
        if (storedApiKey.isNotBlank()) {
            secureApiKeyStore.saveApiKeyEntry(entry.id, storedApiKey)
        }
        val normalizedEntry = entry.copy(
            baseUrl = normalizeBaseUrl(entry.baseUrl),
            apiKey = "",
            selectedModel = entry.selectedModel.trim(),
            name = entry.name.trim().ifBlank { entry.groupId },
            updatedAt = System.currentTimeMillis()
        )
        val entries = apiKeyEntriesFlow.first().toMutableList()
        val index = entries.indexOfFirst { it.id == normalizedEntry.id }
        if (index >= 0) {
            entries[index] = normalizedEntry
        } else {
            entries += normalizedEntry
        }
        context.dataStore.edit { preferences ->
            preferences[API_KEY_ENTRIES] = json.encodeToString(entries.map { it.copy(apiKey = "") })
        }
        if (makeCurrent) {
            val current = configFlow.first()
            saveConfig(
                current.copy(
                    baseUrl = normalizedEntry.baseUrl,
                    apiKey = storedApiKey,
                    selectedModel = normalizedEntry.selectedModel.ifBlank { current.selectedModel }
                )
            )
        }
    }

    suspend fun deleteApiKeyEntry(entryId: String) {
        val entries = apiKeyEntriesFlow.first().filterNot { it.id == entryId }
        secureApiKeyStore.deleteApiKeyEntry(entryId)
        context.dataStore.edit { preferences ->
            preferences[API_KEY_ENTRIES] = json.encodeToString(entries.map { it.copy(apiKey = "") })
        }
    }

    suspend fun migrateApiKeyEntriesToSecureStore() {
        val entries = apiKeyEntriesFlow.first()
        var changed = false
        val sanitized = entries.map { entry ->
            if (entry.apiKey.isNotBlank()) {
                secureApiKeyStore.saveApiKeyEntry(entry.id, entry.apiKey.trim())
                changed = true
            }
            entry.copy(apiKey = "")
        }
        if (changed) {
            context.dataStore.edit { preferences ->
                preferences[API_KEY_ENTRIES] = json.encodeToString(sanitized)
            }
        }
    }

    suspend fun saveConfig(config: ApiConfig) {
        val normalized = config.normalized()
        secureApiKeyStore.saveApiKey(normalized.apiKey)
        secureApiKeyStore.saveTavilyApiKey(normalized.tavilyApiKey)
        context.dataStore.edit { preferences ->
            preferences[BASE_URL] = normalized.baseUrl
            preferences -= API_KEY
            preferences[SELECTED_MODEL] = normalized.selectedModel
            preferences[ASSISTANT_NAME] = normalized.assistantName
            preferences[TEMPERATURE] = normalized.temperature
            preferences[MAX_TOKENS] = normalized.maxTokens
            preferences[TOP_P] = normalized.topP
            preferences[SYSTEM_PROMPT] = normalized.systemPrompt
            preferences[WEB_SEARCH_ENABLED] = normalized.webSearchEnabled
            preferences[CONTEXT_MEMORY] = normalized.contextMemory
            preferences[REASONING_STRENGTH] = normalized.reasoningStrength
            preferences[REPLY_STYLE] = normalized.replyStyle
            preferences[CONTEXT_WINDOW] = normalized.contextWindow
        }
    }

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        private val TEMPERATURE = floatPreferencesKey("temperature")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val TOP_P = floatPreferencesKey("top_p")
        private val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        private val CONTEXT_MEMORY = stringPreferencesKey("context_memory")
        private val REASONING_STRENGTH = stringPreferencesKey("reasoning_strength")
        private val REPLY_STYLE = stringPreferencesKey("reply_style")
        private val CONTEXT_WINDOW = intPreferencesKey("context_window")
        private val API_KEY_ENTRIES = stringPreferencesKey("api_key_entries")
        private val PRIVACY_NOTICE_ACCEPTED = booleanPreferencesKey("privacy_notice_accepted")
    }
}
