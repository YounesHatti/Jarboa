package com.youneshatti.jarboa.data.xmpp

import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.FailureReason
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.AbstractConnectionListener
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ReconnectionManager
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.StanzaBuilder
import org.jivesoftware.smack.sasl.SASLErrorException
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.TLSUtils
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

class SmackXmppClient : XmppClient {
    private val state = MutableStateFlow<XmppConnectionState>(XmppConnectionState.SignedOut)
    private val eventChannel = Channel<XmppEvent>(Channel.UNLIMITED)
    private val connectionMutex = Mutex()
    private var connection: XMPPTCPConnection? = null

    override val connectionState: StateFlow<XmppConnectionState> = state.asStateFlow()
    override val events: Flow<XmppEvent> = eventChannel.receiveAsFlow()

    override suspend fun connect(config: AccountConfig, password: CharArray) =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                disconnectCurrent()
                state.value = XmppConnectionState.Connecting
                try {
                    XMPPTCPConnection.setUseStreamManagementDefault(true)
                    XMPPTCPConnection.setUseStreamManagementResumptionDefault(true)

                    val passwordString = password.concatToString()
                    val builder = XMPPTCPConnectionConfiguration.builder()
                        .setXmppDomain(JidCreate.domainBareFrom(config.domain))
                        .setUsernameAndPassword(config.localpart, passwordString)
                        .setPort(config.serverPort)
                        .setResource(Resourcepart.from("Jarboa"))
                        .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
                        .setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
                        .setCompressionEnabled(false)
                        .setSendPresence(true)
                        .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                    config.serverHost?.takeIf(String::isNotBlank)?.let { host ->
                        builder.setHost(host)
                    }
                    TLSUtils.setEnabledTlsProtocolsToRecommended(builder)

                    val newConnection = XMPPTCPConnection(builder.build())
                    connection = newConnection
                    addConnectionListener(newConnection)
                    newConnection.connect()
                    if (!newConnection.isSecureConnection) {
                        throw SSLHandshakeException("The server did not establish a validated TLS session.")
                    }
                    configureMessaging(newConnection)
                    state.value = XmppConnectionState.Authenticating
                    newConnection.login()
                    ReconnectionManager.getInstanceFor(newConnection).enableAutomaticReconnection()
                    state.value = XmppConnectionState.Connected(newConnection.user.asBareJid().toString())
                } catch (error: Throwable) {
                    disconnectCurrent()
                    state.value = error.toFailureState()
                    throw error
                } finally {
                    password.fill('\u0000')
                }
            }
        }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            state.value = XmppConnectionState.SignedOut
            disconnectCurrent()
        }
    }

    override suspend fun sendDirectMessage(recipientJid: String, body: String, stanzaId: String) =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                val activeConnection = connection
                    ?.takeIf { it.isConnected && it.isAuthenticated }
                    ?: error("The XMPP connection is offline.")
                val recipient = JidCreate.entityBareFrom(recipientJid)
                val builder = StanzaBuilder.buildMessage(stanzaId)
                    .to(recipient)
                    .ofType(Message.Type.chat)
                    .setBody(body)
                DeliveryReceiptRequest.addTo(builder)
                ChatManager.getInstanceFor(activeConnection).chatWith(recipient).send(builder.build())
            }
        }

    private fun configureMessaging(activeConnection: XMPPTCPConnection) {
        ChatManager.getInstanceFor(activeConnection).addIncomingListener { from, message, _ ->
            val body = message.body?.takeIf(String::isNotBlank) ?: return@addIncomingListener
            eventChannel.trySend(
                XmppEvent.IncomingMessage(
                    senderJid = from.toString(),
                    stanzaId = message.stanzaId,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }

        DeliveryReceiptManager.getInstanceFor(activeConnection).apply {
            autoReceiptMode = DeliveryReceiptManager.AutoReceiptMode.ifIsSubscribed
            addReceiptReceivedListener { _, _, receiptId, _ ->
                receiptId?.let { eventChannel.trySend(XmppEvent.DeliveryReceipt(it)) }
            }
        }
    }

    private fun addConnectionListener(activeConnection: XMPPTCPConnection) {
        activeConnection.addConnectionListener(object : AbstractConnectionListener() {
            override fun authenticated(connection: XMPPConnection, resumed: Boolean) {
                val bound = connection.user?.asBareJid()?.toString() ?: return
                state.value = XmppConnectionState.Connected(bound)
            }

            override fun connectionClosed() {
                if (state.value !is XmppConnectionState.SignedOut) {
                    state.value = XmppConnectionState.Disconnected
                }
            }

            override fun connectionClosedOnError(error: Exception) {
                state.value = error.toFailureState()
            }

            override fun reconnectingIn(seconds: Int) {
                state.value = XmppConnectionState.Reconnecting(seconds)
            }

            override fun reconnectionFailed(error: Exception) {
                state.value = error.toFailureState()
            }
        })
    }

    private fun disconnectCurrent() {
        val current = connection
        connection = null
        if (current?.isConnected == true) runCatching { current.disconnect() }
    }

    private fun Throwable.toFailureState(): XmppConnectionState.Failed {
        val root = generateSequence(this) { it.cause }.last()
        val reason = when (root) {
            is SASLErrorException -> FailureReason.AUTHENTICATION
            is SSLHandshakeException,
            is CertificateException,
            is SmackException.SmackCertificateException,
            is SmackException.SecurityRequiredException,
            is SmackException.SecurityNotPossibleException,
            -> FailureReason.TLS
            is UnknownHostException -> FailureReason.DNS
            is SmackException.ConnectionException,
            is SmackException.NoResponseException,
            is SmackException.NotConnectedException,
            -> FailureReason.NETWORK
            is XMPPException.XMPPErrorException -> FailureReason.SERVER
            is IllegalArgumentException -> FailureReason.INVALID_ACCOUNT
            else -> FailureReason.UNKNOWN
        }
        return XmppConnectionState.Failed(reason = reason, detail = safeDetail(root))
    }

    private fun safeDetail(error: Throwable): String? = when (error) {
        is SASLErrorException -> "The server rejected the account credentials."
        is SSLHandshakeException,
        is CertificateException,
        is SmackException.SmackCertificateException,
        is SmackException.SecurityRequiredException,
        is SmackException.SecurityNotPossibleException,
        -> "The server certificate or TLS configuration could not be validated."
        is UnknownHostException -> "The XMPP server address could not be resolved."
        is SmackException.NoResponseException -> "The XMPP server did not respond in time."
        is SmackException.NotConnectedException -> "The XMPP connection is offline."
        is XMPPException.XMPPErrorException -> "The XMPP server rejected the request."
        is IllegalArgumentException -> "The account or server setting is invalid."
        else -> null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
    }
}
