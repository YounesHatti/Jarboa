package com.youneshatti.jarboa.data.xmpp

import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.MessageEncryption
import com.youneshatti.jarboa.domain.model.OmemoSendResult
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import com.youneshatti.jarboa.domain.model.XmppContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface XmppClient {
    val connectionState: StateFlow<XmppConnectionState>
    val contacts: StateFlow<List<XmppContact>>
    val events: Flow<XmppEvent>

    suspend fun connect(config: AccountConfig, password: CharArray)
    suspend fun disconnect()
    suspend fun addContact(contactJid: String)
    suspend fun sendDirectMessage(recipientJid: String, body: String, stanzaId: String): OmemoSendResult
}

sealed interface XmppEvent {
    data class IncomingMessage(
        val senderJid: String,
        val stanzaId: String?,
        val body: String,
        val timestamp: Long,
        val encryption: MessageEncryption,
        val senderDeviceId: Int? = null,
        val senderFingerprint: String? = null,
    ) : XmppEvent

    data class DeliveryReceipt(val stanzaId: String) : XmppEvent
}
