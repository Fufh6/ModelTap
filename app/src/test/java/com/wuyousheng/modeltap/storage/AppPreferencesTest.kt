package com.wuyousheng.modeltap.storage

import com.wuyousheng.modeltap.domain.model.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun normalizeBaseUrl_trimsSpacesAndAddsTrailingSlash() {
        assertEquals("https://example.com/v1/", normalizeBaseUrl("  https://example.com/v1  "))
    }

    @Test
    fun normalizeBaseUrl_removesDuplicateTrailingSlashes() {
        assertEquals("https://example.com/v1/", normalizeBaseUrl("https://example.com/v1///"))
    }

    @Test
    fun normalizeBaseUrl_keepsBlankAsBlank() {
        assertEquals("", normalizeBaseUrl("   "))
    }

    @Test
    fun isValidHttpsBaseUrl_acceptsHttpsUrl() {
        assertTrue(isValidHttpsBaseUrl("https://example.com/v1"))
    }

    @Test
    fun isValidHttpsBaseUrl_rejectsBlankUrl() {
        assertFalse(isValidHttpsBaseUrl("   "))
    }

    @Test
    fun isValidHttpsBaseUrl_rejectsHttpUrl() {
        assertFalse(isValidHttpsBaseUrl("http://example.com/v1"))
    }

    @Test
    fun isValidHttpsBaseUrl_rejectsInvalidText() {
        assertFalse(isValidHttpsBaseUrl("not a url"))
    }

    @Test
    fun normalized_trimsEndpointKeyAndModel() {
        val config = ApiConfig(
            baseUrl = "  https://example.com/v1///  ",
            apiKey = "  test-key  ",
            selectedModel = "  test-model  ",
            assistantName = "  小助手  ",
            temperature = 0.2f,
            maxTokens = 2048,
            topP = 0.8f,
            systemPrompt = "  keep prompt spacing  ",
            webSearchEnabled = true,
            tavilyApiKey = "  tvly-key  "
        )

        val normalized = config.normalized()

        assertEquals("https://example.com/v1/", normalized.baseUrl)
        assertEquals("test-key", normalized.apiKey)
        assertEquals("test-model", normalized.selectedModel)
        assertEquals("小助手", normalized.assistantName)
        assertEquals(0.2f, normalized.temperature)
        assertEquals(2048, normalized.maxTokens)
        assertEquals(0.8f, normalized.topP)
        assertEquals("  keep prompt spacing  ", normalized.systemPrompt)
        assertTrue(normalized.webSearchEnabled)
        assertEquals("tvly-key", normalized.tavilyApiKey)
    }

    @Test
    fun normalized_removesLegacyTavilyModelConfig() {
        val config = ApiConfig(
            baseUrl = "https://api.tavily.com/",
            apiKey = "tvly-key",
            selectedModel = "web-search",
            tavilyApiKey = "tvly-key"
        )

        val normalized = config.normalized()

        assertEquals("", normalized.baseUrl)
        assertEquals("", normalized.apiKey)
        assertEquals("", normalized.selectedModel)
        assertEquals("tvly-key", normalized.tavilyApiKey)
    }

    @Test
    fun normalized_keepsZeroMaxTokensForUnlimitedOutput() {
        val config = ApiConfig(maxTokens = 0)

        val normalized = config.normalized()

        assertEquals(0, normalized.maxTokens)
    }

    @Test
    fun normalized_clampsNegativeMaxTokensToUnlimitedOutput() {
        val config = ApiConfig(maxTokens = -1)

        val normalized = config.normalized()

        assertEquals(0, normalized.maxTokens)
    }
}
