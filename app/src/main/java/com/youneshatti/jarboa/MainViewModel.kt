package com.youneshatti.jarboa

import android.app.Application
import android.net.Uri
import com.youneshatti.jarboa.data.attachment.SelectedAttachment
import com.youneshatti.jarboa.data.attachment.EncryptedAttachment
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.Conversation
import com.youneshatti.jarboa.domain.model.DirectMessage
import com.youneshatti.jarboa.domain.model.MessageStatus
import com.youneshatti.jarboa.domain.model.OmemoContactSecurity
import com.youneshatti.jarboa.domain.model.OmemoContactStatus
import com.youneshatti.jarboa.domain.model.OmemoSessionState
import com.youneshatti.jarboa.domain.model.OmemoSessionStatus
import com.youneshatti.jarboa.domain.model.OmemoTrustLevel
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import com.youneshatti.jarboa.domain.model.XmppContact
import com.youneshatti.jarboa.domain.validation.JidValidationResult
import com.youneshatti.jarboa.domain.validation.XmppAddressValidator
import com.youneshatti.jarboa.service.XmppConnectionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val mutableSignedIn = MutableStateFlow(container.accountStore.hasAccount())
    private val mutableBusy = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableSelectedConversation = MutableStateFlow<String?>(null)
    private val foreground = MutableStateFlow(false)
    private val contactSecurityRefresh = MutableStateFlow(0L)
    private val mutableContactSecurity = MutableStateFlow<OmemoContactSecurity?>(null)
    private val mutableNotificationPrivacy = MutableStateFlow(container.settingsStore.hideNotificationContent)
    private val mutableAttachmentDraft = MutableStateFlow<AttachmentDraft?>(null)
    private val mutableTransfers = MutableStateFlow<Map<String, AttachmentTransfer>>(emptyMap())
    private var attachmentJob: Job? = null
    private val downloadJobs = mutableMapOf<String, Job>()
    val attachmentDraft = mutableAttachmentDraft.asStateFlow()
    val transfers = mutableTransfers.asStateFlow()

    val signedIn: StateFlow<Boolean> = mutableSignedIn.asStateFlow()
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    val error: StateFlow<String?> = mutableError.asStateFlow()
    val selectedConversation: StateFlow<String?> = mutableSelectedConversation.asStateFlow()
    val contactSecurity: StateFlow<OmemoContactSecurity?> = mutableContactSecurity.asStateFlow()
    val hideNotificationContent: StateFlow<Boolean> = mutableNotificationPrivacy.asStateFlow()
    val connectionState: StateFlow<XmppConnectionState> = container.xmppClient.connectionState
    val omemoState: StateFlow<OmemoSessionState> = container.xmppClient.omemoState
    val contacts: StateFlow<List<XmppContact>> = container.xmppClient.contacts
    val conversations: StateFlow<List<Conversation>> = container.messageRepository.conversations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            observeContactSecurity(
                requests = combine(
                    mutableSelectedConversation, foreground, connectionState, contactSecurityRefresh,
                ) { jid, visible, connection, refresh ->
                    if (jid == null || !visible) null else ContactSecurityRequest(
                        jid = jid,
                        ready = connection is XmppConnectionState.Connected,
                        refresh = refresh,
                    )
                },
                prepare = { jid ->
                    try {
                        container.xmppClient.addContact(jid)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Roster updates must not prevent independent OMEMO discovery.
                    }
                },
                load = container.xmppClient::loadContactSecurity,
                publish = { mutableContactSecurity.value = it },
            )
        }
        viewModelScope.launch {
            omemoState.map { it.status }.distinctUntilChanged().collect { status ->
                if (status == OmemoSessionStatus.READY &&
                    mutableContactSecurity.value?.status == OmemoContactStatus.UNAVAILABLE
                ) {
                    mutableSelectedConversation.value?.let(::refreshContactSecurity)
                }
            }
        }
    }

    fun messages(jid: String): Flow<List<DirectMessage>> = container.messageRepository.messages(jid)

    fun signIn(jid: String, password: String, serverHost: String, serverPort: String) {
        if (mutableBusy.value) return
        val normalized = when (val validation = XmppAddressValidator.validate(jid)) {
            is JidValidationResult.Valid -> validation.normalizedJid
            is JidValidationResult.Invalid -> {
                mutableError.value = validation.message
                return
            }
        }
        if (password.isEmpty()) {
            mutableError.value = "Enter your XMPP password."
            return
        }
        val port = serverPort.toIntOrNull()
        if (port == null || port !in 1..65535) {
            mutableError.value = "Enter a valid server port."
            return
        }

        viewModelScope.launch {
            mutableBusy.value = true
            mutableError.value = null
            val result = runCatching {
                container.signIn(
                    AccountConfig(
                        jid = normalized,
                        serverHost = serverHost.trim().ifBlank { null },
                        serverPort = port,
                    ),
                    password.toCharArray(),
                )
            }
            result.onSuccess {
                mutableSignedIn.value = true
                XmppConnectionService.start(getApplication())
            }.onFailure {
                mutableError.value = signInFailureMessage(connectionState.value)
            }
            mutableBusy.value = false
        }
    }

    fun openConversation(jid: String) {
        val normalized = when (val validation = XmppAddressValidator.validate(jid)) {
            is JidValidationResult.Valid -> validation.normalizedJid
            is JidValidationResult.Invalid -> {
                mutableError.value = validation.message
                return
            }
        }
        mutableSelectedConversation.value = normalized
        updateVisibleConversation()
    }

    fun openNotificationConversation(jid: String) {
        // The launcher Activity is exported. An external intent must not add a contact.
        val normalized = (XmppAddressValidator.validate(jid) as? JidValidationResult.Valid)
            ?.normalizedJid ?: return
        viewModelScope.launch {
            if (signedIn.value && container.messageRepository.hasConversation(normalized)) {
                openConversation(normalized)
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground.value = value
        updateVisibleConversation()
    }

    private fun updateVisibleConversation() {
        container.setVisibleConversation(if (foreground.value) mutableSelectedConversation.value else null)
    }

    fun markConversationRead(jid: String) {
        viewModelScope.launch { container.markConversationRead(jid) }
    }

    fun closeConversation() {
        mutableSelectedConversation.value = null
        updateVisibleConversation()
        mutableContactSecurity.value = null
    }

    fun refreshContactSecurity(jid: String) {
        if (mutableSelectedConversation.value != jid) return
        contactSecurityRefresh.value += 1
    }

    fun setDeviceTrust(jid: String, deviceId: Int, fingerprint: String, trust: OmemoTrustLevel) {
        viewModelScope.launch {
            runCatching { container.xmppClient.setDeviceTrust(jid, deviceId, fingerprint, trust) }
                .onSuccess { refreshContactSecurity(jid) }
                .onFailure { failure ->
                    mutableError.value = failure.message ?: "The OMEMO trust decision could not be saved."
                }
        }
    }

    fun sendMessage(recipientJid: String, body: String) {
        val message = body.trim()
        if (message.isEmpty()) return
        viewModelScope.launch {
            runCatching { container.sendMessage(recipientJid, message) }
                .onFailure { failure ->
                    mutableError.value = failure.message ?: "The message could not be sent."
                }
        }
    }

    fun retryMessage(message: DirectMessage) {
        if (!message.outgoing || message.status != MessageStatus.FAILED) return
        viewModelScope.launch {
            runCatching { container.retryMessage(message) }
                .onFailure { failure ->
                    mutableError.value = failure.message ?: "The message could not be sent again."
                }
        }
    }

    fun setHideNotificationContent(hidden: Boolean) {
        container.settingsStore.hideNotificationContent = hidden
        mutableNotificationPrivacy.value = hidden
    }

    fun selectAttachment(jid: String, uri: Uri, photo: Boolean) {
        if (attachmentJob?.isActive == true || mutableBusy.value || !signedIn.value) return
        attachmentJob = viewModelScope.launch {
            mutableAttachmentDraft.value?.selected?.file?.let { withContext(Dispatchers.IO) { it.delete() } }
            mutableAttachmentDraft.value = AttachmentDraft(jid, status = "Preparing attachment", working = true)
            try {
                val selected = container.attachmentStore.select(uri, photo)
                mutableAttachmentDraft.value = AttachmentDraft(jid, selected = selected)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableAttachmentDraft.value = null
                mutableError.value = "This attachment could not be prepared. Choose a readable file or photo up to 25 MB."
            }
        }
    }

    fun sendAttachment() {
        val draft = mutableAttachmentDraft.value ?: return
        val selected = draft.selected ?: return
        if (attachmentJob?.isActive == true || mutableBusy.value) return
        attachmentJob = viewModelScope.launch {
            mutableAttachmentDraft.value = draft.copy(working = true, status = "Checking encryption")
            try {
                check(container.xmppClient.loadContactSecurity(draft.jid).canSend)
                val body = container.attachmentStore.upload(selected, container.xmppClient::requestUploadSlot) { status, percent ->
                    mutableAttachmentDraft.update { it?.copy(status = status, percent = percent) }
                }
                // The ordinary OMEMO send path checks device trust again after the upload.
                // Once queued, retries use the existing message rather than uploading a second copy.
                try { container.sendMessage(draft.jid, body) }
                finally {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { selected.file.delete() }
                    mutableAttachmentDraft.value = null
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableAttachmentDraft.update { it?.copy(working = false, status = "Could not send. Check encryption and your server's upload limit, then retry.", percent = null) }
                if (mutableAttachmentDraft.value == null) mutableError.value = "The attachment was uploaded, but its message could not be sent. Use Try again on the message."
            }
        }
    }

    fun discardAttachment() {
        viewModelScope.launch {
            attachmentJob?.cancelAndJoin()
            mutableAttachmentDraft.value?.selected?.file?.let { withContext(Dispatchers.IO) { it.delete() } }
            mutableAttachmentDraft.value = null
        }
    }

    fun cachedAttachment(body: String): File? = container.attachmentStore.cached(body)

    fun downloadAttachment(message: DirectMessage) {
        if (downloadJobs[message.id]?.isActive == true || mutableBusy.value || !signedIn.value) return
        if (message.encryption !in setOf(
                com.youneshatti.jarboa.domain.model.MessageEncryption.OMEMO_UNVERIFIED,
                com.youneshatti.jarboa.domain.model.MessageEncryption.OMEMO_VERIFIED,
            ) || EncryptedAttachment.parse(message.body) == null) return
        downloadJobs[message.id] = viewModelScope.launch {
            mutableTransfers.update { it + (message.id to AttachmentTransfer("Waiting", working = true)) }
            try {
                container.attachmentStore.download(message.body) { status, percent ->
                    mutableTransfers.update { it + (message.id to AttachmentTransfer(status, percent, true)) }
                }
                mutableTransfers.update { it + (message.id to AttachmentTransfer("Downloaded")) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableTransfers.update { it + (message.id to AttachmentTransfer("Download failed or encryption could not be verified. Tap to retry.")) }
            }
        }
    }

    fun saveAttachment(body: String, uri: Uri) {
        if (mutableBusy.value || !signedIn.value) return
        // Track exports alongside downloads so sign-out waits before erasing private files.
        val id = "save:$uri"
        downloadJobs[id] = viewModelScope.launch {
            try { container.attachmentStore.saveCopy(body, uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = "The file could not be saved. Download it again and choose another destination." }
        }
    }

    fun retryEncryption() {
        if (mutableBusy.value) return
        viewModelScope.launch {
            mutableBusy.value = true
            mutableError.value = null
            runCatching { container.retryEncryption() }
                .onSuccess { ready ->
                    if (ready) {
                        mutableSelectedConversation.value?.let(::refreshContactSecurity)
                    } else {
                        mutableError.value = omemoState.value.detail ?: "Encryption is not ready yet."
                    }
                }
                .onFailure {
                    mutableError.value = (connectionState.value as? XmppConnectionState.Failed)?.detail
                        ?: omemoState.value.detail ?: "Jarboa could not retry encryption."
                }
            mutableBusy.value = false
        }
    }

    fun dismissError() {
        mutableError.value = null
    }

    fun signOut() {
        container.setVisibleConversation(null)
        viewModelScope.launch {
            mutableBusy.value = true
            attachmentJob?.cancelAndJoin()
            downloadJobs.values.toList().forEach { it.cancelAndJoin() }
            downloadJobs.clear()
            mutableAttachmentDraft.value = null
            mutableTransfers.value = emptyMap()
            XmppConnectionService.stop(getApplication())
            runCatching { container.signOut() }
            mutableSelectedConversation.value = null
            mutableContactSecurity.value = null
            mutableSignedIn.value = false
            mutableBusy.value = false
        }
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(application, container) as T
        }
    }
}

data class AttachmentDraft(
    val jid: String,
    val selected: SelectedAttachment? = null,
    val status: String? = null,
    val percent: Int? = null,
    val working: Boolean = false,
)

data class AttachmentTransfer(val status: String, val percent: Int? = null, val working: Boolean = false)

internal fun signInFailureMessage(connectionState: XmppConnectionState): String =
    (connectionState as? XmppConnectionState.Failed)?.detail
        ?: "Jarboa could not sign in. Check the account and server."
