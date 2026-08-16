package com.youneshatti.jarboa.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class EncryptedEnvelopeCodecTest {
    @Test
    fun `round trips nonce and ciphertext`() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = byteArrayOf(12, 34, 56, 78)

        val decoded = EncryptedEnvelopeCodec.decode(EncryptedEnvelopeCodec.encode(iv, ciphertext))

        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ciphertext, decoded.ciphertext)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid base64`() {
        EncryptedEnvelopeCodec.decode("not base64")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid nonce length`() {
        EncryptedEnvelopeCodec.encode(ByteArray(8), byteArrayOf(1))
    }
}
