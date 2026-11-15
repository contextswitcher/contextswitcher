package com.contextswitcher.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.contextswitcher.android.queue.AttachmentStore
import com.contextswitcher.queue.Attachments
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/// *Attach* with *Photo* (the system photo picker, no permission) and *Camera*
/// (the camera app, writing through `FileProvider`): stores the image in
/// [attachments] and hands its `[image: …]` marker to [onMarker]; the send
/// uploads it. Shared by the queue page and the Add-task dialog.
// [impl->dsn~android-message-images~1]
@Composable
fun AttachMenu(
    attachments: AttachmentStore,
    onMarker: (String) -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun attach(name: String, input: () -> InputStream, afterwards: () -> Unit = {}) {
        scope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { attachments.store(name, input) }.also { afterwards() } }
            stored.onSuccess { onMarker(Attachments.marker(it.toPath())) }
                .onFailure { onError("Cannot attach the image: ${it.message}") }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
            attach(name, { context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open the image") })
        }
    }
    // Saveable: the camera app in front may get the app killed meanwhile.
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val raw = cameraFile?.let(::File) ?: return@rememberLauncherForActivityResult
        cameraFile = null
        if (taken) {
            attach("camera", { raw.inputStream() }) { raw.delete() }
        } else {
            raw.delete()
        }
    }

    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { Text("Attach") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Photo") }, onClick = {
                open = false
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            DropdownMenuItem(text = { Text("Camera") }, onClick = {
                open = false
                try {
                    val raw = attachments.newCameraFile()
                    cameraFile = raw.path
                    takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", raw))
                } catch (e: Exception) {
                    cameraFile?.let { File(it).delete() }
                    cameraFile = null
                    onError("Cannot start the camera: ${e.message}")
                }
            })
        }
    }
}

/// `text` with `marker` on a line of its own at the end.
internal fun appendMarker(text: String, marker: String) = if (text.isBlank()) marker else text.trimEnd() + "\n" + marker
