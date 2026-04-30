package com.wuyousheng.modeltap.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SecureApiKeyStoreTest {
    @Test
    fun saveApiKey_persistsKey() {
        val store = SecureApiKeyStore.forPreferences(FakeSharedPreferences())

        store.saveApiKey("  test-key  ")

        assertEquals("  test-key  ", store.readApiKey())
    }
}
