package com.youneshatti.jarboa.ui

import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneshatti.jarboa.MainViewModel
import com.youneshatti.jarboa.data.attachment.EncryptedAttachment
import com.youneshatti.jarboa.domain.model.DirectMessage
import com.youneshatti.jarboa.domain.model.MessageEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun AttachButton(jid: String, viewModel: MainViewModel, enabled: Boolean) {
    var showPicker by remember { mutableStateOf(false) }
    val draft by viewModel.attachmentDraft.collectAsStateWithLifecycle()
    // Keep the recipient from the launch, even if a notification changes the visible chat.
    var pickerRecipient by rememberSaveable { mutableStateOf(jid) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.selectAttachment(pickerRecipient, it, true) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectAttachment(pickerRecipient, it, false) }
    }
    IconButton(onClick = { showPicker = true }, enabled = enabled && draft == null) {
        Icon(Icons.Default.Add, contentDescription = "Attach photo or file")
    }
    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false },
        title = { Text("Attach") },
        text = {
            Column {
                Text("Up to 25 MB, subject to your server's limit. Photos are resized and location metadata is removed. Files are sent unchanged.")
                TextButton(onClick = {
                    pickerRecipient = jid
                    showPicker = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Photo") }
                TextButton(onClick = {
                    pickerRecipient = jid
                    showPicker = false
                    filePicker.launch(arrayOf("*/*"))
                }) { Text("File") }
            }
        },
        confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
    )
}

@Composable
internal fun AttachmentDraftCard(jid: String, viewModel: MainViewModel, canSend: Boolean) {
    val current by viewModel.attachmentDraft.collectAsStateWithLifecycle()
    val draft = current ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (draft.jid != jid) Text("Attachment to ${draft.jid}", style = MaterialTheme.typography.labelMedium)
            draft.selected?.let { selected ->
                Text(selected.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium.forChatContent())
                Text("${(selected.size / 1024).coerceAtLeast(1)} KB", style = MaterialTheme.typography.labelSmall)
            }
            draft.status?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            if (draft.working) TransferProgress(draft.percent)
            Row {
                TextButton(onClick = viewModel::discardAttachment) { Text(if (draft.working) "Cancel transfer" else "Remove") }
                if (!draft.working && draft.selected != null) TextButton(
                    onClick = viewModel::sendAttachment,
                    enabled = canSend && draft.jid == jid,
                ) { Text("Send attachment") }
            }
        }
    }
}

@Composable
internal fun AttachmentContent(message: DirectMessage, viewModel: MainViewModel) {
    val attachment = remember(message.body) { EncryptedAttachment.parse(message.body) }
    val allowed = message.encryption == MessageEncryption.OMEMO_UNVERIFIED ||
        message.encryption == MessageEncryption.OMEMO_VERIFIED
    if (attachment == null || !allowed) {
        Text(if (!allowed) "Attachment unavailable: a trusted OMEMO message is required." else "Unsupported encrypted attachment.")
        return
    }
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val transfer = transfers[message.id]
    val cached = viewModel.cachedAttachment(message.body)
    var expanded by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.saveAttachment(message.body, it) }
    }
    val isImage = attachment.extension in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif")
    Text(if (isImage) "Photo" else attachment.fileName, style = MaterialTheme.typography.bodyLarge)
    if (cached != null) {
        if (isImage) PrivateImage(cached, Modifier.fillMaxWidth().heightIn(max = 240.dp).clickable { expanded = true })
        Text("${(cached.length() / 1024).coerceAtLeast(1)} KB · Stored in Jarboa", style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { save.launch(attachment.fileName) }) { Text("Save a copy") }
    } else {
        Text(transfer?.status ?: "Tap to download and verify", style = MaterialTheme.typography.bodySmall)
        if (transfer?.working == true) TransferProgress(transfer.percent)
        else TextButton(onClick = { viewModel.downloadAttachment(message) }) {
            Text(if (transfer == null) "Download" else "Retry download")
        }
    }
    if (expanded && cached != null) AlertDialog(
        onDismissRequest = { expanded = false },
        text = { PrivateImage(cached, Modifier.fillMaxWidth().heightIn(max = 520.dp), 1600) },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("Close") } },
    )
}

@Composable
private fun TransferProgress(percent: Int?) {
    if (percent == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
    else {
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text("$percent%", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PrivateImage(file: File, modifier: Modifier, maxDimension: Int = 640) {
    val image by produceState<ImageBitmap?>(initialValue = null, file.path, maxDimension) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val size = info.size
                    require(size.width.toLong() * size.height <= 64_000_000)
                    val scale = minOf(1.0, maxDimension.toDouble() / maxOf(size.width, size.height))
                    decoder.setTargetSize((size.width * scale).toInt().coerceAtLeast(1), (size.height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    image?.let { Image(it, contentDescription = "Decrypted photo", modifier = modifier, contentScale = ContentScale.Fit) }
        ?: Text("Image preview unavailable", style = MaterialTheme.typography.bodySmall)
}
