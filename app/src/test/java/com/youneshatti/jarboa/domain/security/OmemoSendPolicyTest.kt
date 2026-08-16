package com.youneshatti.jarboa.domain.security

import org.junit.Assert.assertEquals
import org.junit.Test

class OmemoSendPolicyTest {
    @Test
    fun `requires ciphertext for at least one recipient device`() {
        assertEquals(
            OmemoSendSafety.NO_ENCRYPTED_RECIPIENT,
            OmemoSendPolicy.evaluate(setOf(1), setOf(1), emptySet()),
        )
    }

    @Test
    fun `blocks when an accepted device could not be encrypted`() {
        assertEquals(
            OmemoSendSafety.ACCEPTED_DEVICE_SKIPPED,
            OmemoSendPolicy.evaluate(setOf(1, 2), setOf(2), emptySet()),
        )
    }

    @Test
    fun `allows an explicitly rejected device to be skipped`() {
        assertEquals(
            OmemoSendSafety.SAFE,
            OmemoSendPolicy.evaluate(setOf(1, 2), setOf(2), setOf(2)),
        )
    }
}
