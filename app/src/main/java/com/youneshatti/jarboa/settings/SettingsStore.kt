package com.youneshatti.jarboa.settings

import android.content.Context

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var hideNotificationContent: Boolean
        get() = preferences.getBoolean(KEY_HIDE_NOTIFICATION_CONTENT, true)
        set(value) = preferences.edit().putBoolean(KEY_HIDE_NOTIFICATION_CONTENT, value).apply()

    private companion object {
        const val KEY_HIDE_NOTIFICATION_CONTENT = "hide_notification_content"
    }
}

