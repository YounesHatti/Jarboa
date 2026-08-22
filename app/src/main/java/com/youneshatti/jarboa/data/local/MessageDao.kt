package com.youneshatti.jarboa.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationJid = :jid ORDER BY timestamp ASC")
    fun observeForConversation(jid: String): Flow<List<MessageEntity>>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE stanzaId = :stanzaId LIMIT 1")
    suspend fun findByStanzaId(stanzaId: String): MessageEntity?

    @Query("UPDATE messages SET status = 'SENT' WHERE id = :id AND status = 'PENDING'")
    suspend fun markSent(id: String)

    @Query("UPDATE messages SET status = 'FAILED' WHERE id = :id AND status != 'DELIVERED'")
    suspend fun markFailed(id: String)

    @Query("UPDATE messages SET status = 'DELIVERED' WHERE stanzaId = :stanzaId")
    suspend fun markDelivered(stanzaId: String)
}
