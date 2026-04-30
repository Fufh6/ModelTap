package com.wuyousheng.modeltap.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStore private constructor(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(createPreferences(context))

    fun readApiKey(): String {
        return preferences.getString(API_KEY, "").orEmpty()
    }

    fun readTavilyApiKey(): String {
        return preferences.getString(TAVILY_API_KEY, "").orEmpty()
    }

    fun saveApiKey(apiKey: String) {
        preferences.edit().putString(API_KEY, apiKey).apply()
    }

    fun saveTavilyApiKey(apiKey: String) {
        preferences.edit().putString(TAVILY_API_KEY, apiKey).apply()
    }

    fun readApiKeyEntry(entryId: String): String {
        return preferences.getString(apiKeyEntryKey(entryId), "").orEmpty()
    }

    fun saveApiKeyEntry(entryId: String, apiKey: String) {
        preferences.edit().putString(apiKeyEntryKey(entryId), apiKey).apply()
    }

    fun deleteApiKeyEntry(entryId: String) {
        preferences.edit().remove(apiKeyEntryKey(entryId)).apply()
    }

    companion object {
        private const val API_KEY = "api_key"
        private const val TAVILY_API_KEY = "tavily_api_key"
        private const val API_KEY_ENTRY_PREFIX = "api_key_entry_"

        private fun apiKeyEntryKey(entryId: String): String {
            return API_KEY_ENTRY_PREFIX + entryId
        }

        internal fun forPreferences(preferences: SharedPreferences): SecureApiKeyStore {
            return SecureApiKeyStore(preferences)
        }

        private fun createPreferences(context: Context): SharedPreferences {
            return EncryptedSharedPreferences.create(
                context.applicationContext,
                "secure_api_key_store",
                MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
