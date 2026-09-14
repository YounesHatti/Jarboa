package com.youneshatti.jarboa.data.xmpp

import com.youneshatti.jarboa.data.attachment.AttachmentTransferFailure
import com.youneshatti.jarboa.data.attachment.UploadSlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import org.jxmpp.jid.DomainBareJid
import java.lang.ref.WeakReference

/** Upload discovery/slot IQs do not mutate OMEMO and must not hold its connection lock. */
internal class XmppUploadClient(
    private val diagnose: (XMPPConnection, ConnectionDiagnosticEvent, Throwable?) -> Unit = { _, _, _ -> },
) {
    private val mutex = Mutex()
    private var cachedConnection = WeakReference<XMPPConnection>(null)
    private var cachedService: DomainBareJid? = null

    suspend fun request(connection: XMPPConnection, filename: String, size: Long): UploadSlot {
        var step = "Service discovery"
        diagnose(connection, ConnectionDiagnosticEvent.UPLOAD_DISCOVERY, null)
        try {
            return withTimeoutOrNull(60_000) {
                mutex.withLock {
                    if (cachedConnection.get() !== connection) {
                        cachedConnection = WeakReference(connection)
                        cachedService = null
                    }
                    val upload = HttpFileUploadManager.getInstanceFor(connection)
                    val service = cachedService ?: upload.defaultUploadService?.address ?: run {
                        val disco = ServiceDiscoveryManager.getInstanceFor(connection)
                        val items = runInterruptible(Dispatchers.IO) { disco.discoverItems(connection.xmppServiceDomain) }
                        // Only server-advertised, node-less domain services are candidates. No guessed hosts.
                        val candidates = items.items.filter { it.node.isNullOrEmpty() }
                            .mapNotNull { it.entityID as? DomainBareJid }
                        findResponsiveUploadService(
                            listOf(connection.xmppServiceDomain) + candidates,
                            onFailure = { error -> diagnose(connection, ConnectionDiagnosticEvent.UPLOAD_PROBE_FAILED, error) },
                        ) { address ->
                            runInterruptible(Dispatchers.IO) {
                                val info = disco.discoverInfo(address)
                                info.containsFeature(HttpFileUploadManager.NAMESPACE) ||
                                    info.containsFeature(HttpFileUploadManager.NAMESPACE_0_2)
                            }
                        } ?: throw AttachmentTransferFailure("No responding file-upload service was found. Please retry.")
                    }
                    cachedService = service
                    step = "Slot request"
                    diagnose(connection, ConnectionDiagnosticEvent.UPLOAD_SLOT, null)
                    // Smack handles namespace version, advertised size limits and slot parsing.
                    val slot = try {
                        runInterruptible(Dispatchers.IO) {
                            upload.requestSlot(filename, size, "application/octet-stream", service)
                        }
                    } catch (error: Exception) { cachedService = null; throw error }
                    diagnose(connection, ConnectionDiagnosticEvent.UPLOAD_SLOT_RECEIVED, null)
                    UploadSlot(slot.putUrl.toString(), slot.getUrl.toString(), slot.headers)
                }
            } ?: throw java.util.concurrent.TimeoutException()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            diagnose(connection, ConnectionDiagnosticEvent.UPLOAD_FAILED, error)
            throw when (error) {
                is AttachmentTransferFailure -> error
                is java.util.concurrent.TimeoutException -> AttachmentTransferFailure("$step timed out.")
                is SmackException.NoResponseException -> AttachmentTransferFailure("$step: the server did not respond.")
                is SmackException.NotConnectedException -> AttachmentTransferFailure("$step: the XMPP connection was interrupted.")
                // Enum only: never include the server's free text, URLs or stanza contents.
                is XMPPException.XMPPErrorException -> AttachmentTransferFailure("$step: server returned ${error.stanzaError.condition}.")
                is IllegalArgumentException -> AttachmentTransferFailure("$step: the service rejected the file size or returned invalid upload information.")
                else -> AttachmentTransferFailure("$step could not be completed. See Connection report in Settings.")
            }
        }
    }
}
