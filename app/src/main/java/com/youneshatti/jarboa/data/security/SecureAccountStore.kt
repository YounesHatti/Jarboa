package com.youneshatti.jarboa.data.security

import android.content.Context
import com.youneshatti.jarboa.domain.model.AccountConfig
import java.nio.ByteBuffer
import java.nio.CharBuffer

class SecureAccountStore(
    context: Context,
    private val cipher: AndroidCredentialCipher = AndroidCredentialCipher(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(config: AccountConfig, password: CharArray) {
        val passwordBytes = encode(password)
        try {
            val encryptedPassword = cipher.encrypt(passwordBytes)
            preferences.edit()
                .putString(KEY_JID, config.jid)
                .putString(KEY_HOST, config.serverHost)
                .putInt(KEY_PORT, config.serverPort)
                .putString(KEY_PASSWORD, encryptedPassword)
                .apply()
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun load(): StoredAccount? {
        val jid = preferences.getString(KEY_JID, null) ?: return null
        val encryptedPassword = preferences.getString(KEY_PASSWORD, null) ?: return null
        val passwordBytes = cipher.decrypt(encryptedPassword)
        return try {
            StoredAccount(
                config = AccountConfig(
                    jid = jid,
                    serverHost = preferences.getString(KEY_HOST, null),
                    serverPort = preferences.getInt(KEY_PORT, AccountConfig.DEFAULT_XMPP_PORT),
                ),
                password = decode(passwordBytes),
            )
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun hasAccount(): Boolean = preferences.contains(KEY_JID) && preferences.contains(KEY_PASSWORD)

    fun clear() {
        preferences.edit().clear().apply()
        cipher.deleteKey()
    }

    private fun encode(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        return ByteArray(buffer.remaining()).also(buffer::get)
    }

    private fun decode(bytes: ByteArray): CharArray {
        val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        return CharArray(buffer.remaining()).also(buffer::get)
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_account"
        const val KEY_JID = "jid"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PASSWORD = "password"
    }
}

data class StoredAccount(
    val config: AccountConfig,
    val password: CharArray,
)

