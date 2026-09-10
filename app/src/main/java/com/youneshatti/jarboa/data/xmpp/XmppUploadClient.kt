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
internal class XmppUploadClient {
    private val mutex = Mutex()
    private var cachedConnection = WeakReference<XMPPConnection>(null)
    private var cachedService: DomainBareJid? = null

    suspend fun request(connection: XMPPConnection, filename: String, size: Long): UploadSlot {
        var step = "Service discovery"
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
                        val candidates = items.items.filter { it.node == null }
                            .mapNotNull { it.entityID.asDomainBareJidIfPossible() }
                        findResponsiveUploadService(listOf(connection.xmppServiceDomain) + candidates) { address ->
                            runInterruptible(Dispatchers.IO) {
                                val info = disco.discoverInfo(address)
                                info.containsFeature(HttpFileUploadManager.NAMESPACE) ||
                                    info.containsFeature(HttpFileUploadManager.NAMESPACE_0_2)
                            }
                        } ?: throw AttachmentTransferFailure("No responding file-upload service was found. Please retry.")
                    }
                    cachedService = service
                    step = "Slot request"
                    // Smack handles namespace version, advertised size limits and slot parsing.
                    val slot = try {
                        runInterruptible(Dispatchers.IO) {
                            upload.requestSlot(filename, size, "application/octet-stream", service)
                        }
                    } catch (error: Exception) { cachedService = null; throw error }
                    UploadSlot(slot.putUrl.toString(), slot.getUrl.toString(), slot.headers)
                }
            } ?: throw AttachmentTransferFailure("$step timed out.")
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: AttachmentTransferFailure) { throw error
        } catch (_: SmackException.NoResponseException) {
            throw AttachmentTransferFailure("$step: the server did not respond.")
        } catch (_: SmackException.NotConnectedException) {
            throw AttachmentTransferFailure("$step: the XMPP connection was interrupted.")
        } catch (error: XMPPException.XMPPErrorException) {
            // Enum only: never include the server's free text, URLs or stanza contents.
            throw AttachmentTransferFailure("$step: server returned ${error.stanzaError.condition}.")
        } catch (_: IllegalArgumentException) {
            throw AttachmentTransferFailure("$step: the service rejected the file size or returned invalid upload information.")
        } catch (_: Exception) {
            throw AttachmentTransferFailure("$step could not be completed.")
        }
    }
}
