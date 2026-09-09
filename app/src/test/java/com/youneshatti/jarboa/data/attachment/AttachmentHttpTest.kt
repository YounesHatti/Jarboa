package com.youneshatti.jarboa.data.attachment

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AttachmentHttpTest {
    @Test fun refusesRedirectAndOversizedResponse() = runBlocking {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val client = AttachmentHttp(OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .followRedirects(false).followSslRedirects(false).build())
        val destination = Files.createTempFile("jarboa-test-", ".part").toFile()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://127.0.0.1/private"))
            assertTrue(runCatching { client.download(server.url("/redirect").toString(), destination) {} }.isFailure)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody("x").setHeader("Content-Length", EncryptedAttachment.MAX_BYTES + 17))
            assertTrue(runCatching { client.download(server.url("/large").toString(), destination) {} }.isFailure)
            assertEquals(0, destination.length())
        } finally { destination.delete(); server.shutdown() }
    }

    @Test fun productionClientBlocksPrivateServerBeforeConnecting() = runBlocking {
        val server = MockWebServer()
        server.start()
        val destination = Files.createTempFile("jarboa-test-", ".part").toFile()
        try {
            assertTrue(runCatching { AttachmentHttp().download("https://127.0.0.1:${server.port}/secret", destination) {} }.isFailure)
            assertEquals(0, server.requestCount)
            assertEquals(0, destination.length())
        } finally { destination.delete(); server.shutdown() }
    }
}
