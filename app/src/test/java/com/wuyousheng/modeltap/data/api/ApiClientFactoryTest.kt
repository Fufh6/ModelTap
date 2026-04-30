package com.wuyousheng.modeltap.data.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiClientFactoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getModels_usesBaseUrlPathWithoutDuplicatingV1() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        val api = ApiClientFactory.create(server.url("/v1/").toString(), "test-key")

        api.getModels()

        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun getModels_sendsBearerAuthorization() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        val api = ApiClientFactory.create(server.url("/v1/").toString(), "test-key")

        api.getModels()

        assertEquals("Bearer test-key", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun getModels_addsTrailingSlashWhenBaseUrlOmitsIt() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        val api = ApiClientFactory.create(server.url("/v1").toString().trimEnd('/'), "test-key")

        api.getModels()

        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun createChatCompletion_usesBaseUrlPathWithoutDuplicatingV1() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
        )
        val api = ApiClientFactory.create(server.url("/v1/").toString(), "test-key")

        api.createChatCompletion(
            ChatCompletionsRequest(
                model = "test-model",
                messages = emptyList()
            )
        )

        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }
}
