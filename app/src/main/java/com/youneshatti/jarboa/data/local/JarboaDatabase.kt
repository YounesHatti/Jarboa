package com.youneshatti.jarboa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class JarboaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN encryption TEXT NOT NULL DEFAULT 'LEGACY_PLAINTEXT'",
                )
                database.execSQL("ALTER TABLE messages ADD COLUMN senderDeviceId INTEGER")
                database.execSQL("ALTER TABLE messages ADD COLUMN senderFingerprint TEXT")
            }
        }
    }
}
