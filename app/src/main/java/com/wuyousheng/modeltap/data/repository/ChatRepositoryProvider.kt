package com.wuyousheng.modeltap.data.repository

import android.content.Context

object ChatRepositoryProvider {
    @Volatile
    private var instance: ChatRepository? = null

    fun get(context: Context): ChatRepository {
        return instance ?: synchronized(this) {
            instance ?: ChatRepository(context.applicationContext).also { instance = it }
        }
    }
}
