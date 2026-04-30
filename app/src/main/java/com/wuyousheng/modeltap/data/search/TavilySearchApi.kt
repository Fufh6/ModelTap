package com.wuyousheng.modeltap.data.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface TavilySearchApi {
    @POST("search")
    suspend fun search(@Body request: TavilySearchRequest): TavilySearchResponse
}

@Serializable
data class TavilySearchRequest(
    val query: String,
    @SerialName("max_results") val maxResults: Int = 5,
    @SerialName("search_depth") val searchDepth: String = "basic",
    @SerialName("include_answer") val includeAnswer: Boolean = true
)

@Serializable
data class TavilySearchResponse(
    val answer: String? = null,
    val results: List<TavilySearchResult> = emptyList()
)

@Serializable
data class TavilySearchResult(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null,
    val score: Double? = null
)
