package com.youneshatti.jarboa.domain.model

sealed interface XmppConnectionState {
    data object SignedOut : XmppConnectionState
    data object Disconnected : XmppConnectionState
    data object Connecting : XmppConnectionState
    data object Authenticating : XmppConnectionState
    data class Connected(val boundJid: String) : XmppConnectionState
    data class Reconnecting(val secondsUntilRetry: Int) : XmppConnectionState
    data class Failed(val reason: FailureReason, val detail: String? = null) : XmppConnectionState
}

enum class FailureReason {
    INVALID_ACCOUNT,
    AUTHENTICATION,
    TLS,
    DNS,
    NETWORK,
    SERVER,
    UNKNOWN,
}

