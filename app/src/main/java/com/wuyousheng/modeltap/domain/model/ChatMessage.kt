package com.wuyousheng.modeltap.domain.model

data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val role: MessageRole,
    val parts: List<MessagePart>,
    val usage: TokenUsage? = null,
    val createdAt: Long
)

data class GeneratedImage(
    val id: String,
    val sessionId: Long,
    val messageId: Long,
    val uri: String,
    val prompt: String,
    val createdAt: Long
)
