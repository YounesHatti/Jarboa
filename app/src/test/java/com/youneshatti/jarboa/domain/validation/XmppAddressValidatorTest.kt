package com.youneshatti.jarboa.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmppAddressValidatorTest {
    @Test
    fun `normalizes domain while preserving localpart`() {
        val result = XmppAddressValidator.validate("  Alice@EXAMPLE.ORG ")

        assertEquals(JidValidationResult.Valid("Alice@example.org"), result)
    }

    @Test
    fun `rejects resource-bearing JID`() {
        val result = XmppAddressValidator.validate("alice@example.org/phone")

        assertTrue(result is JidValidationResult.Invalid)
    }

    @Test
    fun `rejects missing localpart`() {
        val result = XmppAddressValidator.validate("@example.org")

        assertTrue(result is JidValidationResult.Invalid)
    }

    @Test
    fun `converts internationalized domain to ASCII`() {
        val result = XmppAddressValidator.validate("alice@bücher.example")

        assertEquals(JidValidationResult.Valid("alice@xn--bcher-kva.example"), result)
    }
}
