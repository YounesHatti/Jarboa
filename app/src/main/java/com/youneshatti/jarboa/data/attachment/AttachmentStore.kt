package com.youneshatti.jarboa.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class SelectedAttachment(val file: File, val name: String, val extension: String) {
    val size: Long get() = file.length()
}

class AttachmentStore internal constructor(private val context: Context, private val http: AttachmentHttp = AttachmentHttp()) {
    private val root = File(context.noBackupFilesDir, "attachments").apply { mkdirs() }
    private val staged = File(root, "staged").apply { mkdirs() }
    private val downloaded = File(root, "downloaded").apply { mkdirs() }
    private val transferMutex = Mutex()

    init {
        // Unfinished transfers from a terminated process are never treated as complete.
        staged.listFiles()?.forEach { it.delete() }
        downloaded.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    suspend fun select(uri: Uri, photo: Boolean): SelectedAttachment = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Select an attachment with the Android picker." }
        val coroutine = currentCoroutineContext()
        var name = "attachment.bin"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                val nameColumn = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0) name = it.getString(nameColumn) ?: name
                val sizeColumn = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumn >= 0 && !it.isNull(sizeColumn)) {
                    require(it.getLong(sizeColumn) <= EncryptedAttachment.MAX_BYTES) { "Attachments are limited to 25 MB." }
                }
            }
        }
        name = name.filter { !it.isISOControl() && it !in "/\\" }.take(120).ifBlank { "attachment.bin" }
        val file = File(staged, UUID.randomUUID().toString())
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "The selected file cannot be read." }
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        require(total <= EncryptedAttachment.MAX_BYTES) { "Attachments are limited to 25 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (photo) {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val size = info.size
                    require(size.width.toLong() * size.height <= 64_000_000) { "This image is too large to process." }
                    val scale = minOf(1.0, 2048.0 / maxOf(size.width, size.height))
                    decoder.setTargetSize((size.width * scale).toInt().coerceAtLeast(1), (size.height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                try {
                    // Re-encoding strips location, EXIF and other source metadata; orientation is decoded first.
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                } finally { bitmap.recycle() }
                name = "Photo.jpg"
            }
            coroutine.ensureActive()
            SelectedAttachment(file, name, name.substringAfterLast('.', "bin").lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin")
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    suspend fun upload(
        selected: SelectedAttachment,
        requestSlot: suspend (String, Long) -> UploadSlot,
        progress: (String, Int?) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        transferMutex.withLock {
            val coroutine = currentCoroutineContext()
            require(selected.size <= EncryptedAttachment.MAX_BYTES && selected.file.isFile)
            progress("Requesting upload", null)
            // The upload service sees a random name and generic MIME type, not the original filename.
            val slot = requestSlot("${UUID.randomUUID()}.${selected.extension}", selected.size + EncryptedAttachment.TAG_BYTES)
            val attachment = EncryptedAttachment.create(slot.getUrl)
            val encrypted = File.createTempFile("upload-", ".part", staged)
            try {
                progress("Encrypting", null)
                selected.file.inputStream().use { input -> encrypted.outputStream().use { output ->
                    cryptAttachment(input, output, attachment, true) { coroutine.ensureActive() }
                } }
                http.upload(slot, encrypted) { progress("Uploading", it) }
                coroutine.ensureActive()
                cacheFile(attachment.body).also { destination ->
                    makeRoom(selected.size)
                    selected.file.copyTo(destination, overwrite = true)
                }
                attachment.body
            } finally { encrypted.delete() }
        }
    }

    fun cached(body: String): File? = cacheFile(body).takeIf { it.isFile }

    suspend fun download(body: String, progress: (String, Int?) -> Unit): File = withContext(Dispatchers.IO) {
        transferMutex.withLock {
            cached(body)?.let { return@withLock it }
            val attachment = requireNotNull(EncryptedAttachment.parse(body))
            val coroutine = currentCoroutineContext()
            val encrypted = File.createTempFile("download-", ".part", staged)
            val plain = File.createTempFile("verified-", ".part", downloaded)
            try {
                http.download(attachment.downloadUrl, encrypted) { progress("Downloading", it) }
                coroutine.ensureActive()
                progress("Verifying encryption", null)
                encrypted.inputStream().use { input -> plain.outputStream().use { output ->
                    cryptAttachment(input, output, attachment, false) { coroutine.ensureActive() }
                } }
                coroutine.ensureActive()
                makeRoom(plain.length())
                val destination = cacheFile(body)
                check(plain.renameTo(destination)) { "The attachment could not be stored." }
                destination
            } finally {
                encrypted.delete()
                plain.delete()
            }
        }
    }

    suspend fun saveCopy(body: String, uri: Uri) = withContext(Dispatchers.IO) {
        transferMutex.withLock {
            require(uri.scheme == "content")
            val file = checkNotNull(cached(body)) { "Download the attachment again before saving." }
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                checkNotNull(output) { "The selected destination cannot be written." }
                file.inputStream().use { it.copyTo(output) }
            }
        }
    }

    private fun cacheFile(body: String) = File(downloaded,
        MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).hex())

    private fun makeRoom(size: Long) {
        val files = downloaded.listFiles()?.filter { !it.name.endsWith(".part") }?.sortedBy { it.lastModified() }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total + size <= 100L * 1024 * 1024) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        transferMutex.withLock {
            staged.listFiles()?.forEach { check(it.delete()) }
            downloaded.listFiles()?.forEach { check(it.delete()) }
        }
    }
}
