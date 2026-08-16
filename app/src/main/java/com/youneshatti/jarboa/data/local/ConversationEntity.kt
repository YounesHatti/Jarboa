package com.youneshatti.jarboa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val jid: String,
    val displayName: String,
    val latestPreview: String,
    val latestTimestamp: Long,
    val unreadCount: Int,
)

