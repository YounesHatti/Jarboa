package com.youneshatti.jarboa.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.youneshatti.jarboa.MainActivity
import com.youneshatti.jarboa.R
import com.youneshatti.jarboa.settings.SettingsStore

class JarboaNotifier(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CONNECTION_CHANNEL_ID,
                    context.getString(R.string.connection_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps the XMPP connection visible while Jarboa listens for messages."
                    setShowBadge(false)
                },
                NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    context.getString(R.string.message_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "New direct messages"
                },
            ),
        )
    }

    fun connectionNotification(): Notification = NotificationCompat.Builder(context, CONNECTION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_jarboa_monochrome)
        .setContentTitle(context.getString(R.string.connection_notification_title))
        .setContentText(context.getString(R.string.connection_notification_text))
        .setContentIntent(openAppIntent())
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setSilent(true)
        .build()

    fun notifyIncoming(senderJid: String, body: String) {
        val private = settingsStore.hideNotificationContent
        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarboa_monochrome)
            .setContentTitle(if (private) "New Jarboa message" else senderJid)
            .setContentText(if (private) "Open Jarboa to read it." else body)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(if (private) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager.notify(senderJid.hashCode() and Int.MAX_VALUE, notification)
    }

    fun cancelAll() = manager.cancelAll()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CONNECTION_CHANNEL_ID = "connection"
        const val MESSAGE_CHANNEL_ID = "messages"
        const val CONNECTION_NOTIFICATION_ID = 1001
    }
}
