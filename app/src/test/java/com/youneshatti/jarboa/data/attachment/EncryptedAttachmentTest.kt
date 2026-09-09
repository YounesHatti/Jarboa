package com.youneshatti.jarboa.data.attachment

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import javax.crypto.AEADBadTagException

class EncryptedAttachmentTest {
    @Test fun aes256GcmMatchesKnownVectorAndRejectsModifiedTag() {
        val attachment = EncryptedAttachment.parse("aesgcm://example.org/test.bin#" + "00".repeat(44))!!
        val encrypted = ByteArrayOutputStream()
        cryptAttachment(ByteArray(16).inputStream(), encrypted, attachment, true)
        assertEquals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919", encrypted.toByteArray().hex())
        val decrypted = ByteArrayOutputStream()
        cryptAttachment(encrypted.toByteArray().inputStream(), decrypted, attachment, false)
        assertArrayEquals(ByteArray(16), decrypted.toByteArray())
        val changed = encrypted.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) {
            cryptAttachment(changed.inputStream(), ByteArrayOutputStream(), attachment, false)
        }
        assertThrows(AEADBadTagException::class.java) {
            cryptAttachment(changed.copyOf(10).inputStream(), ByteArrayOutputStream(), attachment, false)
        }
    }

    @Test fun keysAreFreshAndUriDoesNotLeakIntoHttpOrDiagnostics() {
        val first = EncryptedAttachment.create("https://example.org/a.jpg?token=opaque")
        val second = EncryptedAttachment.create(first.downloadUrl)
        assertNotEquals(first.body, second.body)
        val parsed = EncryptedAttachment.parse(first.body)!!
        assertArrayEquals(first.key, parsed.key)
        assertArrayEquals(first.iv, parsed.iv)
        assertEquals(first.downloadUrl, parsed.downloadUrl)
        assertFalse(parsed.downloadUrl.contains('#'))
        assertFalse(parsed.toString().contains(first.key.hex()))
        assertEquals("Encrypted attachment", attachmentPreview(first.body))
    }

    @Test fun parserRejectsAmbiguousAndUnsafeMessages() {
        val secret = "01".repeat(44)
        for (body in listOf("https://example.org/a#$secret", "aesgcm://user:pass@example.org/a#$secret",
            "aesgcm://example.org/a#1234", "aesgcm://example.org/a#$secret\nhello",
            "hello aesgcm://example.org/a#$secret", "aesgcm://example.org:99999/a#$secret")) {
            assertNull(body, EncryptedAttachment.parse(body))
        }
        assertNotNull(EncryptedAttachment.parse("aesgcm://example.org/a#" + "01".repeat(48)))
        assertNotNull(EncryptedAttachment.parse("aesgcm://example.org/a#$secret\ndata:image/jpeg;base64,YQ=="))
        assertThrows(IllegalArgumentException::class.java) { EncryptedAttachment.create("http://example.org/a") }
        assertThrows(IllegalArgumentException::class.java) { EncryptedAttachment.create("https://example.org/a#secret") }
    }

    @Test fun networkPolicyRejectsLanLoopbackAndSharedAddressSpace() {
        for (host in listOf("127.0.0.1", "0.0.0.0", "10.0.2.2", "192.168.1.1", "172.16.0.1", "169.254.169.254",
            "100.64.0.1", "224.0.0.1", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1")) {
            assertFalse(host, isPublicAddress(InetAddress.getByName(host)))
            assertThrows(java.net.UnknownHostException::class.java) { checkLiteralAddress(host) }
        }
        assertTrue(isPublicAddress(InetAddress.getByName("1.1.1.1")))
        checkLiteralAddress("1.1.1.1")
        checkLiteralAddress("example.org") // Names are checked by the DNS policy, not resolved here.
        assertTrue(isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
