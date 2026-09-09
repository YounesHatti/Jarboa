package com.youneshatti.jarboa.data.attachment

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Conversations-compatible AESGCM media sharing. Never expose this URI to a browser. */
class EncryptedAttachment private constructor(
    val downloadUrl: String,
    val iv: ByteArray,
    val key: ByteArray,
) {
    val body: String get() = "aesgcm" + downloadUrl.removePrefix("https") + "#" + (iv + key).hex()
    val extension: String get() = URI(downloadUrl).path.substringAfterLast('.', "")
        .lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
    val fileName: String get() = "attachment.$extension"

    // Deliberately exclude URL/key material from diagnostics and generated data-class methods.
    override fun toString() = "EncryptedAttachment"

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        const val TAG_BYTES = 16

        fun create(downloadUrl: String): EncryptedAttachment {
            requireHttps(downloadUrl)
            val random = SecureRandom()
            return EncryptedAttachment(downloadUrl, ByteArray(12).also(random::nextBytes),
                ByteArray(32).also(random::nextBytes))
        }

        fun parse(body: String): EncryptedAttachment? = runCatching {
            if (body.length > 16_384) return null
            val lines = body.split('\n')
            if (lines.size !in 1..2) return null
            // Thumbnails from other clients are accepted but never decoded automatically.
            if (lines.size == 2 && !lines[1].matches(Regex("data:image/jpeg;base64,[A-Za-z0-9+/]+={0,2}"))) return null
            val uri = URI(lines[0])
            if (uri.scheme != "aesgcm") return null
            val fragment = uri.rawFragment ?: return null
            // Some older Conversations versions used a 16-byte IV.
            if (!fragment.matches(Regex("(?:[0-9a-fA-F]{88}|[0-9a-fA-F]{96})"))) return null
            val url = "https" + lines[0].substring("aesgcm".length).substringBefore('#')
            requireHttps(url)
            val bytes = fragment.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            EncryptedAttachment(url, bytes.copyOfRange(0, bytes.size - 32), bytes.takeLast(32).toByteArray())
        }.getOrNull()

        fun requireHttps(url: String) {
            require(url.length <= 8192)
            val uri = URI(url)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)) {
                "The attachment server must use a valid HTTPS address."
            }
        }
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

fun attachmentPreview(body: String): String = if (body.startsWith("aesgcm:", ignoreCase = true)) "Encrypted attachment" else body

/** Decryption output must remain private and temporary until doFinal verifies the tag. */
internal fun cryptAttachment(
    input: InputStream,
    output: OutputStream,
    attachment: EncryptedAttachment,
    encrypt: Boolean,
    checkActive: () -> Unit = {},
): Long {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
        SecretKeySpec(attachment.key, "AES"), GCMParameterSpec(128, attachment.iv))
    val limit = EncryptedAttachment.MAX_BYTES + if (encrypt) 0 else EncryptedAttachment.TAG_BYTES
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        checkActive()
        val count = input.read(buffer)
        if (count == -1) break
        total += count
        require(total <= limit) { "Attachments are limited to 25 MB." }
        cipher.update(buffer, 0, count)?.let(output::write)
    }
    checkActive()
    output.write(cipher.doFinal())
    return total
}
