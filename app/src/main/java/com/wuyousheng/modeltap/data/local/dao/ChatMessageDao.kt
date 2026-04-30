package com.wuyousheng.modeltap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wuyousheng.modeltap.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

data class TokenStatsRow(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int?
)

data class DailyTokenStatsRow(
    val dayStart: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int?,
    val imageMessages: Int
)

data class ModelTokenStatsRow(
    val modelId: String,
    val sessionCount: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int?
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT :limit) ORDER BY createdAt ASC, id ASC")
    fun observeRecentMessages(sessionId: Long, limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    fun observeMessageCount(sessionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages")
    fun observeMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE createdAt >= :startTime AND createdAt < :endTime")
    fun observeMessageCountBetween(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = :role")
    fun observeMessageCountByRole(role: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = :role AND createdAt >= :startTime AND createdAt < :endTime")
    fun observeMessageCountByRoleBetween(role: String, startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE createdAt >= :startOfToday")
    fun observeMessageCountSince(startOfToday: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(promptTokens), 0) AS promptTokens, COALESCE(SUM(completionTokens), 0) AS completionTokens, SUM(totalTokens) AS totalTokens FROM chat_messages")
    fun observeTokenStats(): Flow<TokenStatsRow>

    @Query("SELECT COALESCE(SUM(promptTokens), 0) AS promptTokens, COALESCE(SUM(completionTokens), 0) AS completionTokens, SUM(totalTokens) AS totalTokens FROM chat_messages WHERE createdAt >= :startTime AND createdAt < :endTime")
    fun observeTokenStatsBetween(startTime: Long, endTime: Long): Flow<TokenStatsRow>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = :role AND (partsJson LIKE '%LocalImagePart%' OR partsJson LIKE '%RemoteImagePart%' OR partsJson LIKE '%\"local_image\"%' OR partsJson LIKE '%\"remote_image\"%')")
    fun observeImageMessageCount(role: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = :role AND createdAt >= :startTime AND createdAt < :endTime AND (partsJson LIKE '%LocalImagePart%' OR partsJson LIKE '%RemoteImagePart%' OR partsJson LIKE '%\"local_image\"%' OR partsJson LIKE '%\"remote_image\"%')")
    fun observeImageMessageCountBetween(role: String, startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT sessionId) FROM chat_messages WHERE createdAt >= :startTime AND createdAt < :endTime")
    fun observeActiveSessionCountBetween(startTime: Long, endTime: Long): Flow<Int>

    @Query(
        """
        SELECT ((((m.createdAt + :localOffsetMillis) / 86400000) * 86400000) - :localOffsetMillis) AS dayStart,
               COALESCE(SUM(m.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(m.completionTokens), 0) AS completionTokens,
               SUM(m.totalTokens) AS totalTokens,
               SUM(CASE WHEN m.role = :assistantRole AND (m.partsJson LIKE '%LocalImagePart%' OR m.partsJson LIKE '%RemoteImagePart%' OR m.partsJson LIKE '%"local_image"%' OR m.partsJson LIKE '%"remote_image"%') THEN 1 ELSE 0 END) AS imageMessages
        FROM chat_messages m
        WHERE m.createdAt >= :startTime AND m.createdAt < :endTime
        GROUP BY dayStart
        ORDER BY dayStart ASC
        """
    )
    fun observeDailyTokenStats(
        startTime: Long,
        endTime: Long,
        localOffsetMillis: Long,
        assistantRole: String
    ): Flow<List<DailyTokenStatsRow>>

    @Query(
        """
        SELECT s.modelId AS modelId,
               COUNT(DISTINCT s.id) AS sessionCount,
               COALESCE(SUM(m.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(m.completionTokens), 0) AS completionTokens,
               SUM(m.totalTokens) AS totalTokens
        FROM chat_sessions s
        LEFT JOIN chat_messages m ON m.sessionId = s.id
        GROUP BY s.modelId
        ORDER BY COALESCE(SUM(m.totalTokens), SUM(m.promptTokens) + SUM(m.completionTokens), 0) DESC, sessionCount DESC
        """
    )
    fun observeModelTokenStats(): Flow<List<ModelTokenStatsRow>>

    @Query(
        """
        SELECT COALESCE(NULLIF(s.modelId, ''), '未选择模型') AS modelId,
               COUNT(DISTINCT m.sessionId) AS sessionCount,
               COALESCE(SUM(m.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(m.completionTokens), 0) AS completionTokens,
               SUM(m.totalTokens) AS totalTokens
        FROM chat_messages m
        LEFT JOIN chat_sessions s ON s.id = m.sessionId
        WHERE m.createdAt >= :startTime AND m.createdAt < :endTime
        GROUP BY modelId
        ORDER BY COALESCE(SUM(m.totalTokens), SUM(m.promptTokens) + SUM(m.completionTokens), 0) DESC, sessionCount DESC
        """
    )
    fun observeModelTokenStatsBetween(startTime: Long, endTime: Long): Flow<List<ModelTokenStatsRow>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND role = :role ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMessageByRole(sessionId: Long, role: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLastMessage(sessionId: Long): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE role = :role AND (partsJson LIKE '%LocalImagePart%' OR partsJson LIKE '%RemoteImagePart%' OR partsJson LIKE '%\"local_image\"%' OR partsJson LIKE '%\"remote_image\"%') ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentImageMessages(role: String, limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE role = :role AND (partsJson LIKE '%LocalImagePart%' OR partsJson LIKE '%RemoteImagePart%' OR partsJson LIKE '%\"local_image\"%' OR partsJson LIKE '%\"remote_image\"%') ORDER BY createdAt DESC")
    fun observeAllImageMessages(role: String): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun delete(messageId: Long)
}
