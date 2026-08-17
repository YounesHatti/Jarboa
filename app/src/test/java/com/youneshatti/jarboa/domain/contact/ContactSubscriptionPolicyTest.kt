package com.youneshatti.jarboa.domain.contact

import com.youneshatti.jarboa.domain.model.ContactSubscriptionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactSubscriptionPolicyTest {
    @Test
    fun `mutual subscription takes precedence over a stale pending flag`() {
        assertEquals(
            ContactSubscriptionState.MUTUAL,
            contactSubscriptionState(
                canSeeContactPresence = true,
                contactCanSeeMyPresence = true,
                requestPending = true,
            ),
        )
    }

    @Test
    fun `outgoing request is reported while approval is pending`() {
        assertEquals(
            ContactSubscriptionState.REQUEST_SENT,
            contactSubscriptionState(
                canSeeContactPresence = false,
                contactCanSeeMyPresence = false,
                requestPending = true,
            ),
        )
    }

    @Test
    fun `single direction is visibly not mutual`() {
        assertEquals(
            ContactSubscriptionState.ONE_WAY,
            contactSubscriptionState(
                canSeeContactPresence = true,
                contactCanSeeMyPresence = false,
                requestPending = false,
            ),
        )
    }

    @Test
    fun `roster entry without approval remains added`() {
        assertEquals(
            ContactSubscriptionState.ADDED,
            contactSubscriptionState(
                canSeeContactPresence = false,
                contactCanSeeMyPresence = false,
                requestPending = false,
            ),
        )
    }
}
