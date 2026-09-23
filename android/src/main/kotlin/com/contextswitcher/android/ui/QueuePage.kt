package com.contextswitcher.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.contextswitcher.android.queue.AttachmentStore
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.queue.Attachments
import com.contextswitcher.tasks.Task
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/// The task detail pager's queue page: the phone's drafts for the task, the
/// task's pending queued messages — read-only from the synced clone (the
/// clone is a hard-reset mirror, `TaskRepoSync`; a queued message ticked off
/// on the desktop shows up after the next sync, never written to here) — and
/// a compose box that pastes new text straight into the task's tmux window
/// via `send`. No attachments (the compose box is plain text).
///
/// Unsent text becomes a draft the way the desktop's add box queues it: when
/// the box loses focus (a tap on the page's empty space) and on *Save draft*.
/// The box's own text is written to [DraftStore] on every change, so leaving
/// the page, rotating, or the app being restarted (an update, a force stop)
/// brings it back as it was — screen state alone does not survive an update.
///
/// A desktop-queued message has *Delete* when [deleteQueued] is given (a touch
/// screen has no hover to reveal it): after a confirmation it runs off the main
/// thread and returns null on success, else the error for the Snackbar.
///
/// With [attachments], *Photo* (the system photo picker, no permission) and
/// *Camera* (the camera app, writing through `FileProvider`) store an image and
/// append its `[image: …]` marker to the box; the send uploads it.
// [impl->dsn~android-queue-send~2]
// [impl->dsn~android-queue-delete~1]
// [impl->dsn~android-message-images~1]
// [impl->dsn~android-enqueue~1]
// [impl->dsn~android-message-drafts~3]
@Composable
fun QueuePage(
    task: Task,
    queuedMessages: List<String>,
    draftStore: DraftStore,
    send: suspend (String) -> String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    deleteQueued: (suspend (String) -> String?)? = null,
    attachments: AttachmentStore? = null,
    enqueuedStore: DraftStore? = null,
) {
    var draft by remember(task.id()) { mutableStateOf(runCatching { draftStore.loadUnsent(task.id()) }.getOrDefault("")) }
    var drafts by remember(task.id()) { mutableStateOf(draftStore.load(task.id())) }
    var enqueued by remember(task.id()) { mutableStateOf(enqueuedStore?.load(task.id()) ?: emptyList()) }
    var attachMenu by remember { mutableStateOf(false) }
    // The watch service sends and removes enqueued messages; follow its progress.
    LaunchedEffect(task.id(), enqueuedStore) {
        while (enqueuedStore != null) {
            delay(2_000)
            enqueued = withContext(Dispatchers.IO) { enqueuedStore.load(task.id()) }
        }
    }
    var sending by remember(task.id()) { mutableStateOf(false) }
    var hadFocus by remember(task.id()) { mutableStateOf(false) }
    var confirmDelete by remember(task.id()) { mutableStateOf<String?>(null) }
    var deleting by remember(task.id()) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ponytail: a disk write on the main thread per keystroke — fine for a
    // few-KB text file; debounce if typing ever stutters.
    fun changeDraft(text: String) {
        draft = text
        try {
            draftStore.saveUnsent(task.id(), text)
        } catch (e: IOException) {
            scope.launch { snackbarHostState.showSnackbar("Cannot store the typed text: ${e.message}") }
        }
    }

    fun keepDraft() {
        if (draft.isBlank()) {
            return
        }
        try {
            drafts = draftStore.add(task.id(), draft)
            changeDraft("")
        } catch (e: IOException) {
            scope.launch { snackbarHostState.showSnackbar("Cannot keep the draft: ${e.message}") }
        }
    }

    fun removeDraft(text: String, failure: String): Boolean =
        try {
            drafts = draftStore.remove(task.id(), text)
            true
        } catch (e: IOException) {
            scope.launch { snackbarHostState.showSnackbar("$failure: ${e.message}") }
            false
        }

    val boxFocus = remember { FocusRequester() }
    val context = LocalContext.current

    fun attach(name: String, open: () -> InputStream, afterwards: () -> Unit = {}) {
        val store = attachments ?: return
        scope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { store.store(name, open) }.also { afterwards() } }
            stored.onSuccess { file ->
                val marker = Attachments.marker(file.toPath())
                changeDraft(if (draft.isBlank()) marker else draft.trimEnd() + "\n" + marker)
            }.onFailure { snackbarHostState.showSnackbar("Cannot attach the image: ${it.message}") }
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

    /// Puts `text` into the message box for editing — whatever the box held
    /// is kept as a draft first — and focuses the box, so the keyboard opens.
    fun edit(text: String) {
        keepDraft()
        changeDraft(text)
        boxFocus.requestFocus()
    }

    fun sendText(text: String, onSent: () -> Unit) {
        sending = true
        scope.launch {
            val error = withContext(Dispatchers.IO) { send(text) }
            // Before the Snackbar: showSnackbar suspends until it is dismissed.
            sending = false
            if (error != null) {
                snackbarHostState.showSnackbar("Send failed: $error")
            } else {
                onSent()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // The app is edge-to-edge, so the keyboard overlays the page
            // instead of shrinking it; without this it covers Send.
            .imePadding()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .padding(8.dp),
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            // [impl->dsn~android-enqueue~1]
            if (enqueuedStore != null && enqueued.isNotEmpty()) {
                item { SectionHeader("Sent when Claude is idle") }
                items(enqueued) { text ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                enqueued = enqueuedStore.remove(task.id(), text)
                                edit(text)
                            }) {
                                Text("Edit")
                            }
                            TextButton(onClick = { enqueued = enqueuedStore.remove(task.id(), text) }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
            if (drafts.isNotEmpty()) {
                item { SectionHeader("Drafts on this phone") }
                items(drafts) { text ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (removeDraft(text, "Cannot edit the draft")) {
                                        edit(text)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                if (removeDraft(text, "Cannot edit the draft")) {
                                    edit(text)
                                }
                            }) {
                                Text("Edit")
                            }
                            TextButton(onClick = { removeDraft(text, "Cannot delete the draft") }) {
                                Text("Delete")
                            }
                            TextButton(
                                onClick = { sendText(text) { removeDraft(text, "Sent, but cannot remove the draft") } },
                                enabled = !sending,
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Queued on the desktop") }
            if (queuedMessages.isEmpty()) {
                item { Text(text = "No pending messages", modifier = Modifier.padding(8.dp)) }
            } else {
                items(queuedMessages) { message ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                        // Edit copies: the desktop's message stays until deleted.
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { edit(message) }) {
                                Text("Edit")
                            }
                            if (deleteQueued != null) {
                                TextButton(onClick = { confirmDelete = message }, enabled = !deleting) {
                                    Text(if (deleting) "Deleting…" else "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { changeDraft(it) },
            label = { Text("Message") },
            // Taller text scrolls inside the box, so the buttons below stay on screen.
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .focusRequester(boxFocus)
                .onFocusChanged { state ->
                    if (hadFocus && !state.isFocused) {
                        keepDraft()
                    }
                    hadFocus = state.isFocused
                },
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            // Photo and Camera share one menu: the row has room for four controls.
            if (attachments != null) {
                Box {
                    TextButton(onClick = { attachMenu = true }) {
                        Text("Attach")
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("Photo") }, onClick = {
                            attachMenu = false
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text("Camera") }, onClick = {
                            attachMenu = false
                            try {
                                val raw = attachments.newCameraFile()
                                cameraFile = raw.path
                                takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", raw))
                            } catch (e: Exception) {
                                cameraFile?.let { File(it).delete() }
                                cameraFile = null
                                scope.launch { snackbarHostState.showSnackbar("Cannot start the camera: ${e.message}") }
                            }
                        })
                    }
                }
            }
            TextButton(onClick = { keepDraft() }, enabled = draft.isNotBlank()) {
                Text("Save draft")
            }
            if (enqueuedStore != null) {
                TextButton(
                    onClick = {
                        try {
                            enqueued = enqueuedStore.add(task.id(), draft)
                            changeDraft("")
                        } catch (e: IOException) {
                            scope.launch { snackbarHostState.showSnackbar("Cannot enqueue: ${e.message}") }
                        }
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Text("Enqueue")
                }
            }
            Button(
                onClick = { sendText(draft) { changeDraft("") } },
                enabled = !sending && draft.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(if (sending) "Sending…" else "Send")
            }
        }
    }
    val pending = confirmDelete
    if (pending != null && deleteQueued != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete queued message?") },
            text = { Text("It is removed from the desktop's queue for this task.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    deleting = true
                    scope.launch {
                        val error = withContext(Dispatchers.IO) { deleteQueued(pending) }
                        deleting = false
                        if (error != null) {
                            snackbarHostState.showSnackbar("Delete failed: $error")
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}
