package com.wuyousheng.modeltap.data.api

import com.wuyousheng.modeltap.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
object ApiClientFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    fun create(baseUrl: String, apiKey: String): OpenAiCompatibleApi {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = HttpLoggingInterceptor.Level.BASIC
            }
            clientBuilder.addInterceptor(logging)
        }

        val client = clientBuilder.build()

        return Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiCompatibleApi::class.java)
    }

    private fun ensureTrailingSlash(baseUrl: String): String {
        return if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
    }
}
