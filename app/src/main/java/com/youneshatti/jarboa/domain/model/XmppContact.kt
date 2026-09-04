package com.youneshatti.jarboa.domain.model

data class XmppContact(
    val jid: String,
    val displayName: String,
    val subscriptionState: ContactSubscriptionState,
)

enum class ContactSubscriptionState {
    MUTUAL,
    REQUEST_SENT,
    ONE_WAY,
    ADDED,
}
