package com.wuyousheng.modeltap.domain.model

data class ApiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val selectedModel: String = "",
    val assistantName: String = "助手",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val topP: Float = 1.0f,
    val systemPrompt: String = "",
    val webSearchEnabled: Boolean = false,
    val tavilyApiKey: String = "",
    val contextMemory: String = "长期",
    val reasoningStrength: String = "中",
    val replyStyle: String = "平衡",
    val contextWindow: Int = 32768
)
