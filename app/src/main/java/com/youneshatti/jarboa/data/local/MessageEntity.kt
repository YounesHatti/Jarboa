package com.youneshatti.jarboa.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.youneshatti.jarboa.domain.model.MessageStatus

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["jid"],
            childColumns = ["conversationJid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationJid", "timestamp"]),
        Index(value = ["stanzaId"], unique = true),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val stanzaId: String?,
    val conversationJid: String,
    val senderJid: String,
    val body: String,
    val timestamp: Long,
    val outgoing: Boolean,
    val status: MessageStatus,
)

