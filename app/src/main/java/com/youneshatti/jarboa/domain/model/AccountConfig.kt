package com.youneshatti.jarboa.domain.model

data class AccountConfig(
    val jid: String,
    val serverHost: String? = null,
    val serverPort: Int = DEFAULT_XMPP_PORT,
) {
    val domain: String
        get() = jid.substringAfter('@')

    val localpart: String
        get() = jid.substringBefore('@')

    companion object {
        const val DEFAULT_XMPP_PORT = 5222
    }
}

