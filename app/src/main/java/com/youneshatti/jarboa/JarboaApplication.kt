package com.youneshatti.jarboa

import android.app.Application
import androidx.room.Room
import com.youneshatti.jarboa.data.local.JarboaDatabase
import com.youneshatti.jarboa.data.message.MessageRepository
import com.youneshatti.jarboa.data.security.SecureAccountStore
import com.youneshatti.jarboa.data.xmpp.SmackXmppClient
import com.youneshatti.jarboa.data.xmpp.XmppEvent
import com.youneshatti.jarboa.domain.model.AccountConfig
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import com.youneshatti.jarboa.notifications.JarboaNotifier
import com.youneshatti.jarboa.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarboaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(
        application,
        JarboaDatabase::class.java,
        "jarboa.db",
    ).build()

    val accountStore = SecureAccountStore(application)
    val settingsStore = SettingsStore(application)
    val messageRepository = MessageRepository(database)
    val xmppClient = SmackXmppClient()
    private val notifier = JarboaNotifier(application, settingsStore)

    init {
        notifier.createChannels()
        applicationScope.launch {
            xmppClient.events.collect { event ->
                when (event) {
                    is XmppEvent.IncomingMessage -> {
                        val isNew = messageRepository.recordIncoming(
                            senderJid = event.senderJid,
                            stanzaId = event.stanzaId,
                            body = event.body,
                            timestamp = event.timestamp,
                        )
                        if (isNew) notifier.notifyIncoming(event.senderJid, event.body)
                    }
                    is XmppEvent.DeliveryReceipt -> messageRepository.markDelivered(event.stanzaId)
                }
            }
        }
    }

    suspend fun signIn(config: AccountConfig, password: CharArray) {
        val connectionPassword = password.copyOf()
        try {
            xmppClient.connect(config, connectionPassword)
            try {
                withContext(Dispatchers.IO) { accountStore.save(config, password) }
            } catch (error: Throwable) {
                xmppClient.disconnect()
                throw error
            }
        } finally {
            connectionPassword.fill('\u0000')
            password.fill('\u0000')
        }
    }

    suspend fun connectSavedAccount(): Boolean {
        if (xmppClient.connectionState.value is XmppConnectionState.Connected) return true
        val stored = accountStore.load() ?: return false
        return try {
            xmppClient.connect(stored.config, stored.password)
            true
        } finally {
            stored.password.fill('\u0000')
        }
    }

    suspend fun sendMessage(recipientJid: String, body: String) {
        val senderJid = when (val current = xmppClient.connectionState.value) {
            is XmppConnectionState.Connected -> current.boundJid
            else -> accountStore.load()?.let { stored ->
                stored.password.fill('\u0000')
                stored.config.jid
            } ?: error("No signed-in account.")
        }
        val local = messageRepository.prepareOutgoing(recipientJid, senderJid, body)
        try {
            xmppClient.sendDirectMessage(recipientJid, body, local.id)
            messageRepository.markSent(local.id)
        } catch (error: Throwable) {
            messageRepository.markFailed(local.id)
            throw error
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        xmppClient.disconnect()
        accountStore.clear()
        database.clearAllTables()
        notifier.cancelAll()
    }
}
