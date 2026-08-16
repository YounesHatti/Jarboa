package com.youneshatti.jarboa.data.xmpp

import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface XmppClient {
    val connectionState: StateFlow<XmppConnectionState>
    val events: Flow<XmppEvent>

    suspend fun connect(config: AccountConfig, password: CharArray)
    suspend fun disconnect()
    suspend fun sendDirectMessage(recipientJid: String, body: String, stanzaId: String)
}

sealed interface XmppEvent {
    data class IncomingMessage(
        val senderJid: String,
        val stanzaId: String?,
        val body: String,
        val timestamp: Long,
    ) : XmppEvent

    data class DeliveryReceipt(val stanzaId: String) : XmppEvent
}
