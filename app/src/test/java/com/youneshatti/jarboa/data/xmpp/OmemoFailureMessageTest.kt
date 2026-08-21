package com.youneshatti.jarboa.data.xmpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

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
}
