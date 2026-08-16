package com.youneshatti.jarboa.data.security

import java.nio.ByteBuffer
import java.util.Base64

object EncryptedEnvelopeCodec {
    private const val VERSION: Byte = 1
    private const val IV_SIZE = 12

    fun encode(iv: ByteArray, ciphertext: ByteArray): String {
        require(iv.size == IV_SIZE) { "AES-GCM requires a 96-bit nonce." }
        require(ciphertext.isNotEmpty()) { "Ciphertext cannot be empty." }
        val packed = ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.getEncoder().encodeToString(packed)
    }

    fun decode(encoded: String): EncryptedEnvelope {
        val packed = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Credential data is not valid Base64.", it) }
        require(packed.size > 2 + IV_SIZE) { "Credential data is truncated." }
        val buffer = ByteBuffer.wrap(packed)
        require(buffer.get() == VERSION) { "Unsupported credential data version." }
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength == IV_SIZE && buffer.remaining() > ivLength) { "Credential nonce is invalid." }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        packed.fill(0)
        return EncryptedEnvelope(iv, ciphertext)
    }
}

data class EncryptedEnvelope(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

