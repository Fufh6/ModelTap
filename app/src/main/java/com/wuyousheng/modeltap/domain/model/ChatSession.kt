package com.wuyousheng.modeltap.domain.model

data class ChatSession(
    val id: Long,
    val title: String,
    val modelId: String,
    val systemPrompt: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
