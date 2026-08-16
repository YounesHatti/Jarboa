package com.youneshatti.jarboa.data.message

import androidx.room.withTransaction
import com.youneshatti.jarboa.data.local.ConversationEntity
import com.youneshatti.jarboa.data.local.JarboaDatabase
import com.youneshatti.jarboa.data.local.MessageEntity
import com.youneshatti.jarboa.domain.model.Conversation
import com.youneshatti.jarboa.domain.model.DirectMessage
import com.youneshatti.jarboa.domain.model.MessageEncryption
import com.youneshatti.jarboa.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MessageRepository(private val database: JarboaDatabase) {
    val conversations: Flow<List<Conversation>> = database.conversationDao().observeAll()
        .map { entities -> entities.map(ConversationEntity::toDomain) }

    fun messages(jid: String): Flow<List<DirectMessage>> =
        database.messageDao().observeForConversation(jid).map { entities ->
            entities.map(MessageEntity::toDomain)
        }

    suspend fun prepareOutgoing(recipientJid: String, senderJid: String, body: String): DirectMessage {
        val timestamp = System.currentTimeMillis()
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            stanzaId = null,
            conversationJid = recipientJid,
            senderJid = senderJid,
            body = body,
            timestamp = timestamp,
            outgoing = true,
            status = MessageStatus.PENDING,
            encryption = MessageEncryption.OMEMO_UNVERIFIED,
        )
        val messageWithStanzaId = message.copy(stanzaId = message.id)
        database.withTransaction {
            val existing = database.conversationDao().find(recipientJid)
            database.conversationDao().upsert(
                ConversationEntity(
                    jid = recipientJid,
                    displayName = existing?.displayName ?: recipientJid.substringBefore('@'),
                    latestPreview = body,
                    latestTimestamp = timestamp,
                    unreadCount = existing?.unreadCount ?: 0,
                ),
            )
            database.messageDao().upsert(messageWithStanzaId)
        }
        return messageWithStanzaId.toDomain()
    }

    suspend fun recordIncoming(
        senderJid: String,
        stanzaId: String?,
        body: String,
        timestamp: Long,
        encryption: MessageEncryption,
        senderDeviceId: Int?,
        senderFingerprint: String?,
    ): Boolean {
        val message = MessageEntity(
            id = stanzaId ?: UUID.randomUUID().toString(),
            stanzaId = stanzaId,
            conversationJid = senderJid,
            senderJid = senderJid,
            body = body,
            timestamp = timestamp,
            outgoing = false,
            status = MessageStatus.DELIVERED,
            encryption = encryption,
            senderDeviceId = senderDeviceId,
            senderFingerprint = senderFingerprint,
        )
        return database.withTransaction {
            stanzaId?.let { id ->
                database.messageDao().findByStanzaId(id)?.let {
                    return@withTransaction false
                }
            }
            val existing = database.conversationDao().find(senderJid)
            database.conversationDao().upsert(
                ConversationEntity(
                    jid = senderJid,
                    displayName = existing?.displayName ?: senderJid.substringBefore('@'),
                    latestPreview = body,
                    latestTimestamp = timestamp,
                    unreadCount = (existing?.unreadCount ?: 0) + 1,
                ),
            )
            database.messageDao().upsert(message)
            true
        }
    }

    suspend fun markSent(id: String, encryption: MessageEncryption) =
        database.messageDao().markSent(id, encryption)

    suspend fun markFailed(id: String) = database.messageDao().markFailed(id)

    suspend fun markDelivered(stanzaId: String) {
        database.messageDao().markDelivered(stanzaId)
    }

    suspend fun markConversationRead(jid: String) {
        database.conversationDao().markRead(jid)
    }
}

private fun ConversationEntity.toDomain() = Conversation(
    jid = jid,
    displayName = displayName,
    latestPreview = latestPreview,
    latestTimestamp = latestTimestamp,
    unreadCount = unreadCount,
)

private fun MessageEntity.toDomain() = DirectMessage(
    id = id,
    conversationJid = conversationJid,
    senderJid = senderJid,
    body = body,
    timestamp = timestamp,
    outgoing = outgoing,
    status = status,
    encryption = encryption,
    senderDeviceId = senderDeviceId,
    senderFingerprint = senderFingerprint,
)
