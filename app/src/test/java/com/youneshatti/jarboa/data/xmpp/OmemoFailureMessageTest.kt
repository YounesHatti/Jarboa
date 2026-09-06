package com.youneshatti.jarboa.data.xmpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.security.NoSuchProviderException

class OmemoFailureMessageTest {
    @Test
    fun `unknown internal messages are not exposed`() {
        val message = userFacingOmemoFailure(IllegalStateException("k71"))

        assertFalse(message.contains("k71"))
        assertEquals(
            "OMEMO setup failed. The account stays connected, but sending is blocked until Jarboa can retry.",
            message,
        )
    }

    @Test
    fun `recognized nested storage errors are explained`() {
        val message = userFacingOmemoFailure(
            IllegalStateException("wrapper", IOException("private path")),
        )

        assertFalse(message.contains("private path"))
        assertEquals(
            "Jarboa could not read or write its local encryption keys. Sending is blocked.",
            message,
        )
    }

    @Test
    fun `node access warning does not expose server errors`() {
        val message = userFacingOmemoNodeAccessWarning(IllegalStateException("private server detail"))

        assertFalse(message.contains("private server detail"))
        assertEquals(
            "OMEMO is active. Jarboa could not confirm public device discovery on this server; " +
                "encrypted chats may require both people to add each other as contacts.",
            message,
        )
    }

    @Test
    fun `diagnostic code identifies crypto provider failure without exposing details`() {
        val error = IllegalStateException(
            "private wrapper",
            NoSuchProviderException("private implementation name"),
        )

        assertEquals(
            "OMEMO-INIT-PROVIDER",
            omemoFailureDiagnosticCode(OmemoFailureStage.INITIALIZATION, error),
        )
    }

    @Test
    fun `diagnostic code distinguishes manager state failure`() {
        assertEquals(
            "OMEMO-MGR-STATE",
            omemoFailureDiagnosticCode(
                OmemoFailureStage.MANAGER,
                IllegalStateException("private internal state"),
            ),
        )
    }

    @Test
    fun `linkage report includes exact missing runtime symbol`() {
        val report = omemoFailureDiagnosticReport(
            IllegalStateException(
                "private wrapper",
                NoSuchMethodError("No virtual method setup(Lorg/example/Key;)V"),
            ),
        )

        assertEquals(
            "Failure types: java.lang.IllegalStateException -> java.lang.NoSuchMethodError\n" +
                "Runtime symbol: No virtual method setup(Lorg.example.Key;)V",
            report,
        )
    }

    @Test
    fun `linkage report filters arbitrary characters and limits detail`() {
        val privateMessage = "missing @user@example.org C:\\private\\keys " + "x".repeat(400)
        val report = omemoFailureDiagnosticReport(NoClassDefFoundError(privateMessage))!!

        assertFalse(report.contains("@"))
        assertFalse(report.contains("\\"))
        assertFalse(report.contains("example.org"))
        assertFalse(report.contains("x".repeat(321)))
    }

    @Test
    fun `non linkage failures expose only their safe type`() {
        assertEquals(
            "Failure types: java.io.IOException",
            omemoFailureDiagnosticReport(IOException("private path")),
        )
    }
}
