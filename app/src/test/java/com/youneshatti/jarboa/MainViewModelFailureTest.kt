package com.youneshatti.jarboa

import com.youneshatti.jarboa.domain.model.FailureReason
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainViewModelFailureTest {
    @Test
    fun `sign in uses the sanitized connection failure detail`() {
        val message = signInFailureMessage(
            XmppConnectionState.Failed(
                reason = FailureReason.NETWORK,
                detail = "The XMPP server did not respond in time.",
            ),
        )

        assertEquals("The XMPP server did not respond in time.", message)
    }

    @Test
    fun `sign in never falls back to a raw exception message`() {
        val message = signInFailureMessage(XmppConnectionState.Connecting)

        assertFalse(message.contains("k71"))
        assertEquals("Jarboa could not sign in. Check the account and server.", message)
    }
}
