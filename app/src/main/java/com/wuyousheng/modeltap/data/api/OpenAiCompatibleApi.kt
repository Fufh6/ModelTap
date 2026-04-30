package com.wuyousheng.modeltap.data.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAiCompatibleApi {
    @GET("models")
    suspend fun getModels(): ModelsResponse

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionsRequest
    ): ChatCompletionsResponse

    @Streaming
    @POST("chat/completions")
    suspend fun streamChatCompletion(
        @Body request: ChatCompletionsRequest
    ): ResponseBody

    @Streaming
    @POST("chat/completions")
    fun streamChatCompletionCall(
        @Body request: ChatCompletionsRequest
    ): Call<ResponseBody>
}
