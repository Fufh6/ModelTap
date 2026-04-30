package com.wuyousheng.modeltap.domain.model

data class TokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)
