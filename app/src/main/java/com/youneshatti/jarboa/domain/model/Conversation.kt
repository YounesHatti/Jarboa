package com.youneshatti.jarboa.domain.model

data class Conversation(
    val jid: String,
    val displayName: String,
    val latestPreview: String,
    val latestTimestamp: Long,
    val unreadCount: Int,
)

