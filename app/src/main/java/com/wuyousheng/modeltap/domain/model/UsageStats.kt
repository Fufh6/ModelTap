package com.wuyousheng.modeltap.domain.model

data class UsageStats(
    val totalSessions: Int = 0,
    val totalMessages: Int = 0,
    val userMessages: Int = 0,
    val assistantMessages: Int = 0,
    val todayMessages: Int = 0,
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val imageMessages: Int = 0,
    val modelUsage: List<ModelUsageStat> = emptyList(),
    val dailyUsage: List<DailyUsageStat> = emptyList()
)

data class ModelUsageStat(
    val modelId: String,
    val sessionCount: Int,
    val totalTokens: Int = 0
)

data class DailyUsageStat(
    val label: String,
    val totalTokens: Int,
    val imageCount: Int
)
