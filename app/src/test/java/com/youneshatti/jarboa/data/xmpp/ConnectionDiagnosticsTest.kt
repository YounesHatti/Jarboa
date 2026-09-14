package com.youneshatti.jarboa.data.xmpp

import org.junit.Assert.*
import org.junit.Test
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.packet.StreamError

class ConnectionDiagnosticsTest {
    @Test fun reportExcludesPayloadsAddressesPathsAndKeys() {
        val secret = "alice@example.org https://upload.example/private#secret-key /data/user/password"
        val error = IllegalArgumentException(secret, java.io.IOException(secret))
        error.stackTrace = arrayOf(
            StackTraceElement("org.jivesoftware.smackx.httpfileupload.provider.SlotProvider", "parse", secret, 123),
            StackTraceElement(secret, secret, secret, 456),
        )
        val report = safeConnectionFailure(error)
        assertTrue(report.contains("java.lang.IllegalArgumentException -> java.io.IOException"))
        assertTrue(report.contains("SlotProvider.parse"))
        assertFalse(report.contains(secret))
        assertFalse(report.contains("example.org"))
        assertFalse(report.contains("secret-key"))
        assertFalse(report.contains("/data/"))
    }

    @Test fun fixedProtocolConditionsRemainVisible() {
        val iq = XMPPException.XMPPErrorException(null, StanzaError.getBuilder(StanzaError.Condition.forbidden).build())
        assertTrue(safeConnectionFailure(iq).contains("IQ condition: forbidden"))
        val stream = XMPPException.StreamErrorException(StreamError(StreamError.Condition.policy_violation, null, null, null))
        assertTrue(safeConnectionFailure(stream).contains("Stream condition: policy_violation"))
    }

    @Test fun reconnectAndUploadErrorsDoNotEraseOriginalParserAndConnectionFailures() {
        var time = 0L
        val recorder = ConnectionDiagnostics { time }
        recorder.record(ConnectionDiagnosticEvent.STANZA_PARSE_FAILED, true, true, IllegalArgumentException("secret"))
        recorder.record(ConnectionDiagnosticEvent.CONNECTION_ERROR, false, false, java.io.IOException("secret"))
        repeat(30) {
            time += 1_000_000_000
            recorder.record(ConnectionDiagnosticEvent.LOGIN_WAITING, true, false)
        }
        recorder.record(ConnectionDiagnosticEvent.UPLOAD_FAILED, true, false, IllegalStateException("secret"))
        val report = recorder.report.value
        assertTrue(report.contains("Last parser failure:\n+0s STANZA_PARSE_FAILED"))
        assertTrue(report.contains("Last connection failure:\n+0s CONNECTION_ERROR"))
        assertTrue(report.contains("Last failure:\n+30s UPLOAD_FAILED"))
        assertEquals(12, report.substringAfter("Recent events (seconds since app session started):\n").lines().count { it.startsWith("+") })
        assertFalse(report.contains("secret"))
        recorder.clear()
        assertEquals("", recorder.report.value)
    }

    @Test fun authenticatedLoginWaitIncludesOnlyLibraryCodeLocations() {
        val recorder = ConnectionDiagnostics { 0L }
        recorder.record(
            ConnectionDiagnosticEvent.LOGIN_WAITING, true, true,
            waitingStack = arrayOf(StackTraceElement(
                "org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager", "discoverUploadService", "/private/file", 12,
            )),
        )
        assertTrue(recorder.report.value.contains("transport=true authenticated=true"))
        assertTrue(recorder.report.value.contains("HttpFileUploadManager.discoverUploadService"))
        assertFalse(recorder.report.value.contains("/private/file"))
    }
}
