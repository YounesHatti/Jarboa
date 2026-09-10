package com.youneshatti.jarboa.data.attachment

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UploadSlot(val putUrl: String, val getUrl: String, val headers: Map<String, String>) {
    override fun toString() = "UploadSlot"
}

internal fun isPublicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return false
    val b = address.address.map { it.toInt() and 255 }
    return when (b.size) {
        4 -> b[0] !in listOf(0, 127) && b[0] < 224 &&
            !(b[0] == 100 && b[1] in 64..127) && !(b[0] == 169 && b[1] == 254) &&
            !(b[0] == 198 && b[1] in 18..19)
        16 -> (b[0] and 0xe0) == 0x20 // Global unicast only; excludes ULA and local translation.
        else -> false
    }
}

internal fun checkLiteralAddress(host: String) {
    // OkHttp routes IP literals directly without invoking Dns.lookup.
    if (':' in host || host.matches(Regex("[0-9.]+"))) {
        if (!isPublicAddress(InetAddress.getByName(host))) {
            throw UnknownHostException("Attachment host is not a public internet address")
        }
    }
}

/** Dedicated client: no cookies, credentials, redirects, cleartext or LAN destinations. */
class AttachmentHttp internal constructor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            checkLiteralAddress(chain.request().url.host)
            chain.proceed(chain.request())
        }
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) })
                    throw UnknownHostException("Attachment host is not a public internet address")
            }
        })
        .followRedirects(false).followSslRedirects(false)
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES)
        .build(),
) {
    suspend fun upload(slot: UploadSlot, file: File, progress: (Int) -> Unit) {
        EncryptedAttachment.requireHttps(slot.putUrl)
        EncryptedAttachment.requireHttps(slot.getUrl)
        val request = Request.Builder().url(slot.putUrl)
        slot.headers.forEach { (name, value) ->
            val cleanName = name.replace("\r", "").replace("\n", "")
            if (cleanName.lowercase() in setOf("authorization", "cookie", "expires")) {
                request.addHeader(cleanName, value.replace("\r", "").replace("\n", ""))
            }
        }
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        sink.write(buffer, 0, count)
                        written += count
                        progress((written * 100 / file.length().coerceAtLeast(1)).toInt())
                    }
                }
            }
        }
        execute(request.put(body).build()) { response ->
            if (response.code !in listOf(200, 201, 204)) {
                throw AttachmentTransferFailure("The upload server returned HTTP ${response.code}.")
            }
        }
    }

    suspend fun download(url: String, destination: File, progress: (Int?) -> Unit) {
        EncryptedAttachment.requireHttps(url)
        execute(Request.Builder().url(url).header("Accept-Encoding", "identity").build(),
            onCancelled = { destination.delete() }) { response ->
            check(response.code == 200) { "The attachment is unavailable or has expired on the server." }
            val body = checkNotNull(response.body)
            val limit = EncryptedAttachment.MAX_BYTES + EncryptedAttachment.TAG_BYTES
            val length = body.contentLength()
            require(length <= limit) { "Attachments are limited to 25 MB." }
            body.byteStream().use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= limit) { "Attachments are limited to 25 MB." }
                    output.write(buffer, 0, count)
                    progress(if (length > 0) (total * 100 / length).toInt() else null)
                }
            } }
        }
    }

    private suspend fun execute(request: Request, onCancelled: () -> Unit = {}, consume: (Response) -> Unit): Unit =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) onCancelled()
                    continuation.resumeWithException(safeTransferFailure(e))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            check(!call.isCanceled())
                            consume(it)
                        }
                    }
                    if (call.isCanceled()) onCancelled()
                    result.fold(
                        onSuccess = { continuation.resume(Unit) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }
            })
        }
}
