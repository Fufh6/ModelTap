package com.wuyousheng.modeltap.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wuyousheng.modeltap.data.local.dao.ChatMessageDao
import com.wuyousheng.modeltap.data.local.dao.ChatSessionDao
import com.wuyousheng.modeltap.data.local.entity.ChatMessageEntity
import com.wuyousheng.modeltap.data.local.entity.ChatSessionEntity

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE chat_messages
                    SET partsJson = '[{"type":"text","text":"旧版生图消息包含过大的内嵌图片数据，已隐藏。请重新生成图片。"}]'
                    WHERE partsJson LIKE '%data:image/%'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "codex-ai-chat.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
