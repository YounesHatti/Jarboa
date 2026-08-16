package com.youneshatti.jarboa.domain.security

import com.youneshatti.jarboa.domain.model.OmemoTrustLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class OmemoTrustPolicyTest {
    private val first = "aa".repeat(32)
    private val second = "bb".repeat(32)

    @Test
    fun `first seen identity is usable but unverified`() {
        assertEquals(
            OmemoIdentityRecord(first, OmemoTrustLevel.UNVERIFIED),
            OmemoTrustPolicy.observe(null, first.uppercase()),
        )
    }

    @Test
    fun `known identity retains an explicit decision`() {
        val verified = OmemoIdentityRecord(first, OmemoTrustLevel.VERIFIED)
        assertEquals(verified, OmemoTrustPolicy.observe(verified, first))
    }

    @Test
    fun `changed identity blocks until a new decision`() {
        val changed = OmemoTrustPolicy.observe(
            OmemoIdentityRecord(first, OmemoTrustLevel.VERIFIED),
            second,
        )
        assertEquals(OmemoTrustLevel.CHANGED, changed.trust)
        assertEquals(second, changed.fingerprint)
        assertEquals(first, changed.previousFingerprint)
    }

    @Test
    fun `verification accepts the currently observed replacement`() {
        val changed = OmemoIdentityRecord(second, OmemoTrustLevel.CHANGED, first)
        val verified = OmemoTrustPolicy.decide(changed, second, OmemoTrustLevel.VERIFIED)
        assertEquals(OmemoTrustLevel.VERIFIED, verified.trust)
        assertEquals(second, verified.fingerprint)
        assertEquals(first, verified.previousFingerprint)
    }
}
