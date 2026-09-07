package com.youneshatti.jarboa

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youneshatti.jarboa.data.local.JarboaDatabase
import com.youneshatti.jarboa.domain.model.MessageEncryption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeTest {
    @Test
    fun preOmemoHistoryIsPreservedAndNeverRelabeledEncrypted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ci-upgrade.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        // The v0.1.4/v0.2.10 entity schema, including its keys and indexes.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE conversations (jid TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, latestPreview TEXT NOT NULL, latestTimestamp INTEGER NOT NULL, unreadCount INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE messages (id TEXT NOT NULL PRIMARY KEY, stanzaId TEXT, conversationJid TEXT NOT NULL, senderJid TEXT NOT NULL, body TEXT NOT NULL, timestamp INTEGER NOT NULL, outgoing INTEGER NOT NULL, status TEXT NOT NULL, FOREIGN KEY(conversationJid) REFERENCES conversations(jid) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_messages_conversationJid_timestamp ON messages(conversationJid, timestamp)")
            old.execSQL("CREATE UNIQUE INDEX index_messages_stanzaId ON messages(stanzaId)")
            old.execSQL("INSERT INTO conversations VALUES ('bob@jarboa.test','Bob','Existing message',1,0)")
            old.execSQL("INSERT INTO messages VALUES ('old-id','old-stanza','bob@jarboa.test','alice@jarboa.test','Existing message',1,1,'SENT')")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, JarboaDatabase::class.java, name)
            .addMigrations(JarboaDatabase.MIGRATION_1_2).build()
        try {
            val message = upgraded.messageDao().findByStanzaId("old-stanza")
            assertNotNull(message)
            assertEquals("Existing message", message!!.body)
            assertEquals(MessageEncryption.LEGACY_PLAINTEXT, message.encryption)
            assertEquals("Bob", upgraded.conversationDao().find("bob@jarboa.test")!!.displayName)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
