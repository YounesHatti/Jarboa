package com.youneshatti.jarboa.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
            .setContentIntent(openAppIntent(senderJid))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(if (private) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager.notify(senderJid, MESSAGE_NOTIFICATION_ID, notification)
    }

    fun cancelIncoming(senderJid: String) {
        manager.cancel(senderJid, MESSAGE_NOTIFICATION_ID)
        // Clear notifications posted by versions before tagged chat notifications.
        val legacyId = senderJid.hashCode() and Int.MAX_VALUE
        if (legacyId != CONNECTION_NOTIFICATION_ID) manager.cancel(legacyId)
    }

    fun cancelAll() = manager.cancelAll()

    private fun openAppIntent(jid: String? = null): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        launchIntent(context, jid),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_CONVERSATION = "com.youneshatti.jarboa.CONVERSATION"
        private const val MESSAGE_NOTIFICATION_ID = 1

        internal fun launchIntent(context: Context, jid: String?): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (jid != null) {
                    // Intent extras do not distinguish PendingIntents; data does.
                    data = Uri.Builder().scheme("jarboa").authority("chat").appendPath(jid).build()
                    putExtra(EXTRA_CONVERSATION, jid)
                }
            }

        const val CONNECTION_CHANNEL_ID = "connection"
        const val MESSAGE_CHANNEL_ID = "messages"
        const val CONNECTION_NOTIFICATION_ID = 1001
    }
}
