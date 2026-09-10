package com.youneshatti.jarboa.data.attachment

import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal enum class AttachmentSendStage(val label: String) {
    CHECKING("Encryption check"), REQUESTING("Upload request"), ENCRYPTING("File encryption"),
    UPLOADING("File upload"), STORING("Local storage"), SENDING("Encrypted message send"),
}

/** Only fixed local text and numeric HTTP codes may reach the diagnostic UI. */
internal class AttachmentTransferFailure(message: String) : IOException(message)

internal fun safeTransferFailure(error: IOException) = AttachmentTransferFailure(when (error) {
    is SSLException -> "TLS verification or handshake failed."
    is UnknownHostException -> "The upload address could not be resolved or was blocked by the network safety check."
    is InterruptedIOException -> "The transfer timed out or was interrupted."
    else -> "The network transfer failed."
})

internal fun attachmentSendFailure(stage: AttachmentSendStage, error: Exception): String {
    val detail = if (error is AttachmentTransferFailure) " ${error.message}" else ""
    return "${stage.label} failed.$detail Nothing was sent in plaintext. Please retry."
}
