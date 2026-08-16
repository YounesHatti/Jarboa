package com.youneshatti.jarboa.data.xmpp

import com.youneshatti.jarboa.data.security.OmemoTrustStore
import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.FailureReason
import com.youneshatti.jarboa.domain.model.MessageEncryption
import com.youneshatti.jarboa.domain.model.OmemoContactSecurity
import com.youneshatti.jarboa.domain.model.OmemoContactStatus
import com.youneshatti.jarboa.domain.model.OmemoSendResult
import com.youneshatti.jarboa.domain.model.OmemoSessionState
import com.youneshatti.jarboa.domain.model.OmemoSessionStatus
import com.youneshatti.jarboa.domain.model.OmemoTrustLevel
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import com.youneshatti.jarboa.domain.security.OmemoSendPolicy
import com.youneshatti.jarboa.domain.security.OmemoSendSafety
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
import org.jivesoftware.smack.ReconnectionListener
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.StanzaBuilder
import org.jivesoftware.smack.sasl.SASLErrorException
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.TLSUtils
import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

class SmackXmppClient(
    private val trustStore: OmemoTrustStore,
) : XmppClient {
    private val state = MutableStateFlow<XmppConnectionState>(XmppConnectionState.SignedOut)
    private val mutableOmemoState = MutableStateFlow(OmemoSessionState.Inactive)
    private val eventChannel = Channel<XmppEvent>(Channel.UNLIMITED)
    private val connectionMutex = Mutex()
    private var connection: XMPPTCPConnection? = null
    private var omemoManager: OmemoManager? = null

    override val connectionState: StateFlow<XmppConnectionState> = state.asStateFlow()
    override val omemoState: StateFlow<OmemoSessionState> = mutableOmemoState.asStateFlow()
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

                    mutableOmemoState.value = OmemoSessionState.Initializing
                    val manager = OmemoManager.getInstanceFor(newConnection)
                    manager.setTrustCallback(trustStore)
                    manager.addOmemoMessageListener(omemoMessageListener)
                    manager.initialize()
                    omemoManager = manager
                    val ownFingerprint = manager.ownFingerprint.toString()
                    mutableOmemoState.value = OmemoSessionState(
                        status = OmemoSessionStatus.READY,
                        ownFingerprint = ownFingerprint,
                    )

                    ReconnectionManager.getInstanceFor(newConnection).enableAutomaticReconnection()
                    state.value = XmppConnectionState.Connected(newConnection.user.asBareJid().toString())
                } catch (error: Throwable) {
                    mutableOmemoState.value = OmemoSessionState(
                        status = OmemoSessionStatus.FAILED,
                        detail = error.safeOmemoDetail(),
                    )
                    disconnectCurrent(resetOmemoState = false)
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

    override suspend fun sendDirectMessage(
        recipientJid: String,
        body: String,
        stanzaId: String,
    ): OmemoSendResult = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val activeConnection = requireConnection()
            val manager = requireOmemoManager()
            val recipient = JidCreate.entityBareFrom(recipientJid)
            val security = loadContactSecurity(manager, recipient, refresh = true)
            check(security.canSend) { security.blockingMessage() }

            val encrypted = manager.encrypt(recipient, body)
            val skippedRecipientIds = encrypted.skippedDevices.keys
                .filter { it.jid == recipient }
                .mapTo(mutableSetOf()) { it.deviceId }
            val intendedRecipientIds = encrypted.intendedDevices
                .filter { it.jid == recipient }
                .mapTo(mutableSetOf()) { it.deviceId }
            val rejectedIds = security.devices
                .filter { it.trust == OmemoTrustLevel.REJECTED }
                .mapTo(mutableSetOf()) { it.deviceId }
            when (OmemoSendPolicy.evaluate(intendedRecipientIds, skippedRecipientIds, rejectedIds)) {
                OmemoSendSafety.NO_ENCRYPTED_RECIPIENT -> error(
                    "No recipient OMEMO device accepted this message. Nothing was sent.",
                )
                OmemoSendSafety.ACCEPTED_DEVICE_SKIPPED -> error(
                    "Encryption failed for a recipient device. Nothing was sent.",
                )
                OmemoSendSafety.SAFE -> Unit
            }

            val builder = StanzaBuilder.buildMessage(stanzaId)
            DeliveryReceiptRequest.addTo(builder)
            val stanza = encrypted.buildMessage(builder, recipient)
            ChatManager.getInstanceFor(activeConnection).chatWith(recipient).send(stanza)
            OmemoSendResult(
                encryption = if (security.allUsableDevicesVerified) {
                    MessageEncryption.OMEMO_VERIFIED
                } else {
                    MessageEncryption.OMEMO_UNVERIFIED
                },
            )
        }
    }

    override suspend fun loadContactSecurity(recipientJid: String): OmemoContactSecurity =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                requireConnection()
                loadContactSecurity(
                    manager = requireOmemoManager(),
                    recipient = JidCreate.entityBareFrom(recipientJid),
                    refresh = true,
                )
            }
        }

    override suspend fun setDeviceTrust(
        recipientJid: String,
        deviceId: Int,
        fingerprint: String,
        trust: OmemoTrustLevel,
    ): OmemoContactSecurity = withContext(Dispatchers.IO) {
        require(trust == OmemoTrustLevel.VERIFIED || trust == OmemoTrustLevel.REJECTED)
        connectionMutex.withLock {
            requireConnection()
            trustStore.decide(recipientJid, deviceId, fingerprint, trust)
            loadContactSecurity(
                manager = requireOmemoManager(),
                recipient = JidCreate.entityBareFrom(recipientJid),
                refresh = false,
            )
        }
    }

    private fun loadContactSecurity(
        manager: OmemoManager,
        recipient: BareJid,
        refresh: Boolean,
    ): OmemoContactSecurity = try {
        if (refresh) manager.requestDeviceListUpdateFor(recipient)
        val devices = manager.getActiveFingerprints(recipient)
            .map { (device, fingerprint) ->
                trustStore.deviceInfo(recipient.toString(), device.deviceId, fingerprint.toString())
            }
            .sortedBy { it.deviceId }
        if (devices.isEmpty()) {
            OmemoContactSecurity(
                jid = recipient.toString(),
                status = OmemoContactStatus.UNAVAILABLE,
                detail = "This contact has not published any compatible OMEMO devices.",
            )
        } else {
            OmemoContactSecurity(
                jid = recipient.toString(),
                status = OmemoContactStatus.READY,
                devices = devices,
            )
        }
    } catch (error: Throwable) {
        OmemoContactSecurity(
            jid = recipient.toString(),
            status = OmemoContactStatus.UNAVAILABLE,
            detail = error.safeOmemoDetail(),
        )
    }

    private fun configureMessaging(activeConnection: XMPPTCPConnection) {
        ChatManager.getInstanceFor(activeConnection).addIncomingListener { from, message, _ ->
            if (message.extensions.any { it is OmemoElement }) return@addIncomingListener
            val body = message.body?.takeIf(String::isNotBlank) ?: return@addIncomingListener
            eventChannel.trySend(
                XmppEvent.IncomingMessage(
                    senderJid = from.toString(),
                    stanzaId = message.stanzaId,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    encryption = MessageEncryption.UNENCRYPTED_INCOMING,
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

    private val omemoMessageListener = object : OmemoMessageListener {
        override fun onOmemoMessageReceived(stanza: Stanza, decryptedMessage: OmemoMessage.Received) {
            val body = decryptedMessage.body?.takeIf(String::isNotBlank) ?: return
            val sender = decryptedMessage.senderDevice
            val fingerprint = decryptedMessage.sendersFingerprint.toString()
            val identity = trustStore.deviceInfo(sender.jid.toString(), sender.deviceId, fingerprint)
            val encryption = when (identity.trust) {
                OmemoTrustLevel.VERIFIED -> MessageEncryption.OMEMO_VERIFIED
                OmemoTrustLevel.CHANGED -> MessageEncryption.OMEMO_KEY_CHANGED
                OmemoTrustLevel.UNVERIFIED,
                OmemoTrustLevel.REJECTED,
                -> MessageEncryption.OMEMO_UNVERIFIED
            }
            eventChannel.trySend(
                XmppEvent.IncomingMessage(
                    senderJid = sender.jid.toString(),
                    stanzaId = stanza.stanzaId,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    encryption = encryption,
                    senderDeviceId = sender.deviceId,
                    senderFingerprint = fingerprint,
                ),
            )
        }

        override fun onOmemoCarbonCopyReceived(
            direction: CarbonExtension.Direction,
            carbonCopy: Message,
            wrappingMessage: Message,
            decryptedCarbonCopy: OmemoMessage.Received,
        ) {
            // Carbon history synchronization is intentionally deferred until its direction and
            // de-duplication behavior can be tested across multiple server implementations.
        }
    }

    private fun addConnectionListener(activeConnection: XMPPTCPConnection) {
        activeConnection.addConnectionListener(object : AbstractConnectionListener() {
            override fun authenticated(connection: XMPPConnection, resumed: Boolean) {
                if (mutableOmemoState.value.status != OmemoSessionStatus.READY) return
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
        })
        ReconnectionManager.getInstanceFor(activeConnection).addReconnectionListener(
            object : ReconnectionListener {
                override fun reconnectingIn(seconds: Int) {
                    state.value = XmppConnectionState.Reconnecting(seconds)
                }

                override fun reconnectionFailed(error: Exception) {
                    state.value = error.toFailureState()
                }
            },
        )
    }

    private fun requireConnection(): XMPPTCPConnection = connection
        ?.takeIf { it.isConnected && it.isAuthenticated }
        ?: error("The XMPP connection is offline.")

    private fun requireOmemoManager(): OmemoManager = omemoManager
        ?.takeIf { mutableOmemoState.value.status == OmemoSessionStatus.READY }
        ?: error("OMEMO is not ready. Nothing was sent.")

    private fun OmemoContactSecurity.blockingMessage(): String = when {
        hasChangedIdentity -> "A contact device identity changed. Verify it before sending."
        status == OmemoContactStatus.UNAVAILABLE -> detail ?: "OMEMO is unavailable for this contact."
        usableDevices.isEmpty() -> "No trusted recipient OMEMO device is available. Nothing was sent."
        else -> "OMEMO is not ready. Nothing was sent."
    }

    private fun disconnectCurrent(resetOmemoState: Boolean = true) {
        val current = connection
        connection = null
        omemoManager = null
        if (resetOmemoState) mutableOmemoState.value = OmemoSessionState.Inactive
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

    private fun Throwable.safeOmemoDetail(): String = when (this) {
        is SmackException.NoResponseException -> "The server did not respond while checking OMEMO devices."
        is SmackException.NotConnectedException -> "The XMPP connection went offline while checking OMEMO."
        is XMPPException.XMPPErrorException -> "The server does not currently provide the OMEMO data Jarboa needs."
        else -> message?.take(180) ?: "OMEMO could not be initialized safely."
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
        is IllegalStateException -> error.message?.take(180)
        else -> null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
    }
}
