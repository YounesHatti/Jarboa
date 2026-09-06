package com.youneshatti.jarboa.data.xmpp

import com.youneshatti.jarboa.data.security.OmemoTrustStore
import com.youneshatti.jarboa.domain.contact.contactSubscriptionState
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
import com.youneshatti.jarboa.domain.model.XmppContact
import com.youneshatti.jarboa.domain.security.OmemoSendPolicy
import com.youneshatti.jarboa.domain.security.OmemoSendSafety
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.StanzaBuilder
import org.jivesoftware.smack.roster.AbstractRosterListener
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.SubscribeListener
import org.jivesoftware.smack.sasl.SASLErrorException
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.TLSUtils
import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.pep.PepManager
import org.jivesoftware.smackx.pubsub.AccessModel
import org.jivesoftware.smackx.pubsub.PubSubException
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.security.NoSuchProviderException
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

class SmackXmppClient(
    private val trustStore: OmemoTrustStore,
    private val omemoStorageDirectory: File,
) : XmppClient {
    private val state = MutableStateFlow<XmppConnectionState>(XmppConnectionState.SignedOut)
    private val mutableOmemoState = MutableStateFlow(OmemoSessionState.Inactive)
    private val mutableContacts = MutableStateFlow<List<XmppContact>>(emptyList())
    private val eventChannel = Channel<XmppEvent>(Channel.UNLIMITED)
    private val connectionMutex = Mutex()
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: XMPPTCPConnection? = null
    private var omemoManager: OmemoManager? = null
    private var roster: Roster? = null

    override val connectionState: StateFlow<XmppConnectionState> = state.asStateFlow()
    override val omemoState: StateFlow<OmemoSessionState> = mutableOmemoState.asStateFlow()
    override val contacts: StateFlow<List<XmppContact>> = mutableContacts.asStateFlow()
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
                        // Request queued messages only after the OMEMO receive listener is ready.
                        .setSendPresence(false)
                        .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                    config.serverHost?.takeIf(String::isNotBlank)?.let { host ->
                        builder.setHost(host)
                    }
                    TLSUtils.setEnabledTlsProtocolsToRecommended(builder)

                    val newConnection = XMPPTCPConnection(builder.build())
                    connection = newConnection
                    addConnectionListener(newConnection)
                    val activeRoster = configureRoster(newConnection)
                    newConnection.connect()
                    if (!newConnection.isSecureConnection) {
                        throw SSLHandshakeException("The server did not establish a validated TLS session.")
                    }
                    configureMessaging(newConnection)
                    state.value = XmppConnectionState.Authenticating
                    newConnection.login()
                    ReconnectionManager.getInstanceFor(newConnection).enableAutomaticReconnection()
                    runCatching { refreshRoster(activeRoster, reload = !activeRoster.isLoaded) }
                    state.value = XmppConnectionState.Connected(newConnection.user.asBareJid().toString())

                    // OmemoManager registers an authenticated connection callback when it is
                    // created before login. A store or crypto failure in that callback then escapes
                    // from Smack's login() and tears down an otherwise valid XMPP session. Attach
                    // OMEMO only after authentication so prepare/initialize failures remain isolated.
                    prepareOmemoManager(newConnection)?.let(::initializeOmemo)
                    newConnection.sendStanza(Presence(Presence.Type.available))
                    Unit
                } catch (error: Throwable) {
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

    override suspend fun retryEncryption(): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val active = requireConnection()
            val manager = omemoManager ?: prepareOmemoManager(active)
            manager != null && initializeOmemo(manager)
        }
    }

    override suspend fun addContact(contactJid: String) = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val activeConnection = requireConnection()
            val contact = JidCreate.entityBareFrom(contactJid)
            require(activeConnection.user.asBareJid() != contact) { "You cannot add your own account as a contact." }
            val activeRoster = roster ?: configureRoster(activeConnection)
            if (!activeRoster.isLoaded) activeRoster.reloadAndWait()

            val entry = activeRoster.getEntry(contact)
            if (entry == null) {
                val displayName = contactJid.substringBefore('@')
                if (activeRoster.isSubscriptionPreApprovalSupported) {
                    activeRoster.preApproveAndCreateEntry(contact, displayName, emptyArray())
                } else {
                    activeRoster.createItemAndRequestSubscription(contact, displayName, emptyArray())
                }
            } else {
                if (activeRoster.isSubscriptionPreApprovalSupported && !entry.canSeeMyPresence()) {
                    activeRoster.preApprove(contact)
                }
                if (!entry.canSeeHisPresence() && !entry.isSubscriptionPending) {
                    activeRoster.sendSubscriptionRequest(contact)
                }
            }
            publishRosterContacts(activeRoster)
        }
    }

    override suspend fun sendDirectMessage(
        recipientJid: String,
        body: String,
        stanzaId: String,
    ): OmemoSendResult = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val activeConnection = requireConnection()
            val manager = requireReadyOmemoManager()
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
                    manager = requireReadyOmemoManager(),
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
                manager = requireReadyOmemoManager(),
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

    private fun configureRoster(activeConnection: XMPPTCPConnection): Roster {
        val activeRoster = Roster.getInstanceFor(activeConnection)
        roster = activeRoster
        activeRoster.subscriptionMode = Roster.SubscriptionMode.manual
        activeRoster.addSubscribeListener { from, _ ->
            val knownContact = roster === activeRoster && activeRoster.contains(from.asBareJid())
            if (knownContact) {
                SubscribeListener.SubscribeAnswer.ApproveAndAlsoRequestIfRequired
            } else {
                SubscribeListener.SubscribeAnswer.Deny
            }
        }
        activeRoster.addRosterListener(object : AbstractRosterListener() {
            override fun entriesAdded(addresses: Collection<Jid>) = scheduleRosterPublish(activeRoster)

            override fun entriesUpdated(addresses: Collection<Jid>) = scheduleRosterPublish(activeRoster)

            override fun entriesDeleted(addresses: Collection<Jid>) = scheduleRosterPublish(activeRoster)
        })
        return activeRoster
    }

    private fun scheduleRosterPublish(activeRoster: Roster) {
        clientScope.launch { publishRosterContacts(activeRoster) }
    }

    private fun refreshRoster(activeRoster: Roster, reload: Boolean) {
        if (reload) activeRoster.reloadAndWait()
        publishRosterContacts(activeRoster)
    }

    private fun publishRosterContacts(activeRoster: Roster) {
        if (roster !== activeRoster) return
        mutableContacts.value = activeRoster.entries
            .map { entry ->
                val jid = entry.jid.toString()
                XmppContact(
                    jid = jid,
                    displayName = entry.name?.trim()?.takeIf(String::isNotEmpty)
                        ?: jid.substringBefore('@'),
                    subscriptionState = contactSubscriptionState(
                        canSeeContactPresence = entry.canSeeHisPresence(),
                        contactCanSeeMyPresence = entry.canSeeMyPresence(),
                        requestPending = entry.isSubscriptionPending,
                    ),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
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
                if (this@SmackXmppClient.connection !== activeConnection) return
                val previousState = state.value
                val bound = connection.user?.asBareJid()?.toString() ?: return
                state.value = XmppConnectionState.Connected(bound)
                if (
                    previousState is XmppConnectionState.Reconnecting ||
                    previousState is XmppConnectionState.Disconnected ||
                    previousState is XmppConnectionState.Failed
                ) {
                    roster?.let { activeRoster ->
                        clientScope.launch {
                            connectionMutex.withLock {
                                if (this@SmackXmppClient.connection === activeConnection && activeConnection.isAuthenticated) {
                                    runCatching { refreshRoster(activeRoster, reload = !activeRoster.isLoaded) }
                                }
                            }
                        }
                    }
                    retryOmemoAfterReconnect(activeConnection)
                }
            }

            override fun connectionClosed() {
                if (connection !== activeConnection) return
                if (state.value !is XmppConnectionState.SignedOut) {
                    state.value = XmppConnectionState.Disconnected
                }
            }

            override fun connectionClosedOnError(error: Exception) {
                if (connection !== activeConnection) return
                mutableOmemoState.value = OmemoSessionState(
                    status = OmemoSessionStatus.FAILED,
                    detail = "Encryption is paused until Jarboa reconnects.",
                )
                state.value = error.toFailureState()
            }
        })
        ReconnectionManager.getInstanceFor(activeConnection).addReconnectionListener(
            object : ReconnectionListener {
                override fun reconnectingIn(seconds: Int) {
                    if (connection !== activeConnection) return
                    state.value = XmppConnectionState.Reconnecting(seconds)
                }

                override fun reconnectionFailed(error: Exception) {
                    if (connection !== activeConnection) return
                    state.value = error.toFailureState()
                }
            },
        )
    }

    private fun requireConnection(): XMPPTCPConnection = connection
        ?.takeIf { it.isConnected && it.isAuthenticated }
        ?: error("The XMPP connection is offline.")

    private fun prepareOmemoManager(activeConnection: XMPPTCPConnection): OmemoManager? = try {
        // Crypto setup belongs to the protected OMEMO phase, not Application.onCreate or login.
        OmemoBootstrap.initialize(omemoStorageDirectory)
        OmemoManager.getInstanceFor(activeConnection).also { manager ->
            manager.setTrustCallback(trustStore)
            manager.addOmemoMessageListener(omemoMessageListener)
            omemoManager = manager
        }
    } catch (error: Throwable) {
        mutableOmemoState.value = OmemoSessionState(
            status = OmemoSessionStatus.FAILED,
            detail = userFacingOmemoFailure(error),
            diagnosticCode = omemoFailureDiagnosticCode(OmemoFailureStage.MANAGER, error),
            diagnosticReport = omemoFailureDiagnosticReport(error),
        )
        null
    }

    private fun initializeOmemo(manager: OmemoManager): Boolean {
        if (manager !== omemoManager || connection?.isAuthenticated != true) return false
        mutableOmemoState.value = OmemoSessionState.Initializing
        val ownFingerprint = try {
            manager.initialize()
            manager.ownFingerprint?.toString()
                ?: error("OMEMO did not create a local identity.")
        } catch (error: Throwable) {
            mutableOmemoState.value = OmemoSessionState(
                status = OmemoSessionStatus.FAILED,
                detail = userFacingOmemoFailure(error),
                diagnosticCode = omemoFailureDiagnosticCode(OmemoFailureStage.INITIALIZATION, error),
                diagnosticReport = omemoFailureDiagnosticReport(error),
            )
            return false
        }

        val accessWarning = runCatching { ensurePublicOmemoNodes(manager) }
            .exceptionOrNull()
            ?.let(::userFacingOmemoNodeAccessWarning)
        mutableOmemoState.value = OmemoSessionState(
            status = OmemoSessionStatus.READY,
            ownFingerprint = ownFingerprint,
            detail = accessWarning,
        )
        return true
    }

    /**
     * Legacy Smack publishes first and relies on the server's default PEP access model. XEP-0384
     * requires these public key-discovery nodes to be open, so verify and normalize them without
     * turning an otherwise initialized cryptographic session into a global failure.
     */
    private fun ensurePublicOmemoNodes(manager: OmemoManager) {
        val activeConnection = requireConnection()
        val deviceId = manager.deviceId ?: error("OMEMO did not create a device ID.")
        val pubSubManager = PepManager.getInstanceFor(activeConnection).pepPubSubManager
        var firstFailure: Throwable? = null
        omemoPepNodeIds(deviceId).forEach { nodeId ->
            try {
                val node = pubSubManager.getLeafNode(nodeId)
                val configuration = node.nodeConfiguration
                if (configuration.accessModel != AccessModel.open) {
                    val update = configuration.fillableForm
                    update.setAccessModel(AccessModel.open)
                    node.sendConfigurationForm(update)
                }
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }
        firstFailure?.let { throw it }
    }

    private fun requireReadyOmemoManager(): OmemoManager {
        val activeConnection = requireConnection()
        val manager = omemoManager ?: prepareOmemoManager(activeConnection) ?: error(
            mutableOmemoState.value.detail ?: "OMEMO is unavailable. Nothing was sent.",
        )
        if (mutableOmemoState.value.status != OmemoSessionStatus.READY && !initializeOmemo(manager)) {
            error(mutableOmemoState.value.detail ?: "OMEMO is unavailable. Nothing was sent.")
        }
        return manager
    }

    private fun retryOmemoAfterReconnect(activeConnection: XMPPTCPConnection) {
        clientScope.launch {
            connectionMutex.withLock {
                if (connection !== activeConnection || !activeConnection.isAuthenticated) return@withLock
                val manager = omemoManager ?: prepareOmemoManager(activeConnection)
                manager?.let(::initializeOmemo)
                activeConnection.sendStanza(Presence(Presence.Type.available))
            }
        }
    }

    private fun OmemoContactSecurity.blockingMessage(): String = when {
        hasChangedIdentity -> "A contact device identity changed. Verify it before sending."
        status == OmemoContactStatus.UNAVAILABLE -> detail ?: "OMEMO is unavailable for this contact."
        usableDevices.isEmpty() -> "No trusted recipient OMEMO device is available. Nothing was sent."
        else -> "OMEMO is not ready. Nothing was sent."
    }

    private fun disconnectCurrent(resetOmemoState: Boolean = true) {
        val current = connection
        val currentOmemoManager = omemoManager
        connection = null
        omemoManager = null
        roster = null
        mutableContacts.value = emptyList()
        if (resetOmemoState) mutableOmemoState.value = OmemoSessionState.Inactive
        runCatching { currentOmemoManager?.stopStanzaAndPEPListeners() }
        current?.let { ReconnectionManager.getInstanceFor(it).disableAutomaticReconnection() }
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

    private fun Throwable.safeOmemoDetail(): String = userFacingOmemoFailure(this)

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
        is IllegalStateException -> "Jarboa could not finish setting up the XMPP session."
        else -> null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
    }
}

internal fun userFacingOmemoFailure(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    val recognized = causes.firstOrNull { cause ->
        cause is CorruptedOmemoKeyException ||
            cause is SmackException.NoResponseException ||
            cause is SmackException.NotConnectedException ||
            cause is SmackException.NotLoggedInException ||
            cause is XMPPException.XMPPErrorException ||
            cause is PubSubException.NotALeafNodeException ||
            cause is IOException
    } ?: causes.last()
    return when (recognized) {
        is CorruptedOmemoKeyException ->
            "Jarboa could not read its local encryption keys. Sign out once, then sign in again."
        is SmackException.NoResponseException ->
            "The server did not respond while Jarboa was setting up encryption. Jarboa will retry."
        is SmackException.NotConnectedException,
        is SmackException.NotLoggedInException,
        -> "The XMPP connection changed while Jarboa was setting up encryption. Jarboa will retry."
        is XMPPException.XMPPErrorException ->
            "The server rejected Jarboa's OMEMO setup. The account stays connected, but sending is blocked."
        is PubSubException.NotALeafNodeException ->
            "The server's OMEMO storage is incompatible with this Jarboa build. Sending is blocked."
        is IOException ->
            "Jarboa could not read or write its local encryption keys. Sending is blocked."
        else ->
            "OMEMO setup failed. The account stays connected, but sending is blocked until Jarboa can retry."
    }
}

internal enum class OmemoFailureStage(val code: String) {
    MANAGER("MGR"),
    INITIALIZATION("INIT"),
}

/**
 * Returns a stable, non-sensitive code that can be reported from the Settings screen. Exception
 * messages are deliberately excluded because they may contain server or local filesystem details.
 */
internal fun omemoFailureDiagnosticCode(stage: OmemoFailureStage, error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    val category = when {
        causes.any { it is CorruptedOmemoKeyException } -> "KEYS"
        causes.any { it is NoSuchProviderException || it is ClassNotFoundException } -> "PROVIDER"
        causes.any { it is NoClassDefFoundError || it is ExceptionInInitializerError || it is LinkageError } -> "LINKAGE"
        causes.any { it is SmackException.NoResponseException } -> "TIMEOUT"
        causes.any { it is SmackException.NotConnectedException || it is SmackException.NotLoggedInException } ->
            "CONNECTION"
        causes.any { it is PubSubException.NotALeafNodeException } -> "PUBSUB-NODE"
        causes.any { it is XMPPException.XMPPErrorException } -> "SERVER"
        causes.any { it is IOException || it is SecurityException } -> "STORAGE"
        causes.any { it is IllegalStateException || it is AssertionError } -> "STATE"
        else -> "UNEXPECTED"
    }
    return "OMEMO-${stage.code}-$category"
}

/**
 * Exposes only runtime type information and, for linkage failures, the missing runtime symbol.
 * Arbitrary exception messages are never included: they can contain JIDs, server text, or paths.
 */
internal fun omemoFailureDiagnosticReport(error: Throwable): String? {
    val causes = generateSequence(error) { it.cause }.take(MAX_DIAGNOSTIC_CAUSES).toList()
    val linkage = causes.firstOrNull { it is LinkageError } as? LinkageError
    val causeTypes = causes.joinToString(" -> ") { it.javaClass.name }
    val symbol = when (linkage) {
        is NoClassDefFoundError,
        is NoSuchMethodError,
        is NoSuchFieldError,
        is AbstractMethodError,
        is VerifyError,
        is UnsupportedClassVersionError,
        is ClassFormatError,
        -> linkage.message?.toSafeRuntimeSymbol()
        else -> null
    }
    return buildString {
        append("Failure types: ")
        append(causeTypes)
        if (!symbol.isNullOrBlank()) {
            append('\n')
            append("Runtime symbol: ")
            append(symbol)
        }
    }
}

private fun String.toSafeRuntimeSymbol(): String {
    val relevantDetail = substringBefore(" (declaration of").trim()
    if (
        relevantDetail.contains('@') ||
        relevantDetail.contains('\\') ||
        WINDOWS_PATH_PATTERN.containsMatchIn(relevantDetail) ||
        relevantDetail.contains("/data/")
    ) {
        return "Runtime supplied unsafe detail; value redacted"
    }
    val safe = buildString(relevantDetail.length.coerceAtMost(MAX_DIAGNOSTIC_SYMBOL_LENGTH)) {
        relevantDetail.take(MAX_DIAGNOSTIC_SYMBOL_LENGTH).forEach { character ->
            append(
                when {
                    character.isLetterOrDigit() -> character
                    character == '/' -> '.'
                    character in SAFE_DIAGNOSTIC_SYMBOL_CHARACTERS -> character
                    else -> '?'
                },
            )
        }
    }
    return safe.trim()
}

private const val MAX_DIAGNOSTIC_CAUSES = 5
private const val MAX_DIAGNOSTIC_SYMBOL_LENGTH = 320
private const val SAFE_DIAGNOSTIC_SYMBOL_CHARACTERS = " _.$;:<>[]()',-"
private val WINDOWS_PATH_PATTERN = Regex("[A-Za-z]:[/\\\\]")

internal fun userFacingOmemoNodeAccessWarning(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    return if (causes.any { it is SmackException.NotConnectedException }) {
        "OMEMO is active. Jarboa will verify public device discovery after reconnecting."
    } else {
        "OMEMO is active. Jarboa could not confirm public device discovery on this server; " +
            "encrypted chats may require both people to add each other as contacts."
    }
}

internal fun omemoPepNodeIds(deviceId: Int): List<String> = listOf(
    OmemoConstants.PEP_NODE_DEVICE_LIST,
    OmemoConstants.PEP_NODE_BUNDLE_FROM_DEVICE_ID(deviceId),
)
