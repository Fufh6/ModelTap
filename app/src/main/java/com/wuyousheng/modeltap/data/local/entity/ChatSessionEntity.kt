package com.wuyousheng.modeltap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val modelId: String,
    val systemPrompt: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
