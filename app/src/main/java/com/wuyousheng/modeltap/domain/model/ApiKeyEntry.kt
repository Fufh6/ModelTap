package com.wuyousheng.modeltap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyEntry(
    val id: String,
    val groupId: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val selectedModel: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
