package com.youneshatti.jarboa.domain.contact

import com.youneshatti.jarboa.domain.model.ContactSubscriptionState

internal fun contactSubscriptionState(
    canSeeContactPresence: Boolean,
    contactCanSeeMyPresence: Boolean,
    requestPending: Boolean,
): ContactSubscriptionState = when {
    canSeeContactPresence && contactCanSeeMyPresence -> ContactSubscriptionState.MUTUAL
    requestPending -> ContactSubscriptionState.REQUEST_SENT
    canSeeContactPresence || contactCanSeeMyPresence -> ContactSubscriptionState.ONE_WAY
    else -> ContactSubscriptionState.ADDED
}
