package com.wuyousheng.modeltap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wuyousheng.modeltap.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

data class ModelUsageRow(
    val modelId: String,
    val sessionCount: Int
)

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavoriteSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT COUNT(*) FROM chat_sessions WHERE isFavorite = 1")
    fun observeFavoriteSessionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_sessions")
    fun observeSessionCount(): Flow<Int>

    @Query("SELECT modelId, COUNT(*) AS sessionCount FROM chat_sessions GROUP BY modelId ORDER BY sessionCount DESC")
    fun observeModelUsage(): Flow<List<ModelUsageRow>>

    @Insert
    suspend fun insert(session: ChatSessionEntity): Long

    @Update
    suspend fun update(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: Long): Flow<ChatSessionEntity?>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun get(sessionId: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): ChatSessionEntity?

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun delete(sessionId: Long)

    @Query("DELETE FROM chat_sessions WHERE id IN (:sessionIds)")
    suspend fun deleteSessions(sessionIds: List<Long>)

    @Query("UPDATE chat_sessions SET isFavorite = :isFavorite WHERE id = :sessionId")
    suspend fun setFavorite(sessionId: Long, isFavorite: Boolean)
}
