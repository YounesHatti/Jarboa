package com.youneshatti.jarboa.data.attachment

import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.*
import org.junit.Test

class AttachmentSendFailureTest {
    @Test fun stagesAreDistinctAndNeverExposeRawExceptionText() {
        val secret = "https://upload.example/private?token=secret#encryption-key"
        val reports = AttachmentSendStage.entries.map {
            attachmentSendFailure(it, IllegalStateException(secret))
        }
        assertEquals(AttachmentSendStage.entries.size, reports.toSet().size)
        assertTrue(reports.none { "secret" in it || "https:" in it })
        assertTrue(reports.all { "Nothing was sent in plaintext" in it })
    }

    @Test fun networkFailuresHaveSafeUsefulCategories() {
        assertTrue(safeTransferFailure(SSLException("secret")).message!!.contains("TLS"))
        val report = attachmentSendFailure(AttachmentSendStage.UPLOADING,
            safeTransferFailure(UnknownHostException("private-token")))
        assertTrue(report.contains("resolved"))
        assertFalse(report.contains("private-token"))
        assertTrue(attachmentSendFailure(AttachmentSendStage.UPLOADING,
            AttachmentTransferFailure("The upload server returned HTTP 413.")).contains("413"))
    }
}
