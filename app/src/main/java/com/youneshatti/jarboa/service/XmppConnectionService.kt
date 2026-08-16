package com.youneshatti.jarboa.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.youneshatti.jarboa.JarboaApplication
import com.youneshatti.jarboa.notifications.JarboaNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class XmppConnectionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val application = application as JarboaApplication
        val notifier = JarboaNotifier(this, application.container.settingsStore)
        notifier.createChannels()
        startForeground(JarboaNotifier.CONNECTION_NOTIFICATION_ID, notifier.connectionNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as JarboaApplication).container
        serviceScope.launch {
            if (!container.accountStore.hasAccount()) {
                stopSelf()
                return@launch
            }
            runCatching { container.connectSavedAccount() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, XmppConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, XmppConnectionService::class.java))
        }
    }
}

