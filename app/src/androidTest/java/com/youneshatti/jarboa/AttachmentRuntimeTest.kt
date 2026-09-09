package com.youneshatti.jarboa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youneshatti.jarboa.data.attachment.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class AttachmentRuntimeTest {
    @Test fun encryptedUploadAndVerifiedDownloadOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        // Only this test client allows its local fixture; production DNS blocks local destinations.
        val http = AttachmentHttp(OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .followRedirects(false).followSslRedirects(false).build())
        val store = AttachmentStore(context, http)
        val source = File(context.cacheDir, "attachment-fixture.txt")
        source.writeText("Private file contents العربية")
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            val body = store.upload(SelectedAttachment(source, "private-filename.txt", "txt"), { name, size ->
                assertFalse(name.contains("private-filename"))
                assertEquals(source.length() + 16, size)
                UploadSlot(server.url("/put").toString(), server.url("/get/$name").toString(),
                    mapOf("Authorization" to "test-token", "X-Leak" to "must-not-be-sent"))
            }) { _, _ -> }
            val uploaded = server.takeRequest()
            assertEquals("PUT", uploaded.method)
            assertEquals("test-token", uploaded.getHeader("Authorization"))
            assertNull(uploaded.getHeader("X-Leak"))
            val ciphertext = uploaded.body.readByteArray()
            assertFalse(ciphertext.contentEquals(source.readBytes()))
            val attachment = EncryptedAttachment.parse(body)!!
            assertFalse(uploaded.requestUrl.toString().contains(attachment.key.hex()))
            store.clear()
            assertNull(store.cached(body))
            server.enqueue(MockResponse().setBody(Buffer().write(ciphertext)))
            val file = store.download(body) { _, _ -> }
            assertArrayEquals(source.readBytes(), file.readBytes())
            val downloaded = server.takeRequest()
            assertNull(downloaded.getHeader("Authorization"))
            assertNull(downloaded.requestUrl!!.fragment)
            assertFalse(downloaded.requestUrl.toString().contains(attachment.key.hex()))

            store.clear()
            val altered = ciphertext.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            server.enqueue(MockResponse().setBody(Buffer().write(altered)))
            assertTrue(runCatching { store.download(body) { _, _ -> } }.isFailure)
            assertNull("Unauthenticated plaintext must never be made available", store.cached(body))
            assertFalse(File(context.noBackupFilesDir, "attachments/downloaded").listFiles().orEmpty().any { it.isFile })

            val vector = EncryptedAttachment.parse("aesgcm://example.org/a#" + "00".repeat(44))!!
            val encrypted = ByteArrayOutputStream()
            cryptAttachment(ByteArray(16).inputStream(), encrypted, vector, true)
            assertEquals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919", encrypted.toByteArray().hex())
        } finally {
            store.clear()
            source.delete()
            server.shutdown()
        }
    }
}
