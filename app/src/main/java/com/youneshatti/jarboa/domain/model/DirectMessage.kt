package com.youneshatti.jarboa.domain.model

data class DirectMessage(
    val id: String,
    val conversationJid: String,
    val senderJid: String,
    val body: String,
    val timestamp: Long,
    val outgoing: Boolean,
    val status: MessageStatus,
    val encryption: MessageEncryption,
    val senderDeviceId: Int? = null,
    val senderFingerprint: String? = null,
)

enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
}
