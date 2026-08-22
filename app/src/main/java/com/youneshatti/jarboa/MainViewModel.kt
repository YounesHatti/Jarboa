package com.youneshatti.jarboa

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.Conversation
import com.youneshatti.jarboa.domain.model.DirectMessage
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import com.youneshatti.jarboa.domain.validation.JidValidationResult
import com.youneshatti.jarboa.domain.validation.XmppAddressValidator
import com.youneshatti.jarboa.service.XmppConnectionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val mutableNotificationPrivacy = MutableStateFlow(container.settingsStore.hideNotificationContent)

    val signedIn: StateFlow<Boolean> = mutableSignedIn.asStateFlow()
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    val error: StateFlow<String?> = mutableError.asStateFlow()
    val selectedConversation: StateFlow<String?> = mutableSelectedConversation.asStateFlow()
    val hideNotificationContent: StateFlow<Boolean> = mutableNotificationPrivacy.asStateFlow()
    val connectionState: StateFlow<XmppConnectionState> = container.xmppClient.connectionState
    val conversations: StateFlow<List<Conversation>> = container.messageRepository.conversations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

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
            }.onFailure { failure ->
                mutableError.value = failure.message ?: "Jarboa could not sign in. Check the account and server."
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
        viewModelScope.launch { container.messageRepository.markConversationRead(normalized) }
    }

    fun closeConversation() {
        mutableSelectedConversation.value = null
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

    fun setHideNotificationContent(hidden: Boolean) {
        container.settingsStore.hideNotificationContent = hidden
        mutableNotificationPrivacy.value = hidden
    }

    fun dismissError() {
        mutableError.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            mutableBusy.value = true
            XmppConnectionService.stop(getApplication())
            runCatching { container.signOut() }
            mutableSelectedConversation.value = null
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
