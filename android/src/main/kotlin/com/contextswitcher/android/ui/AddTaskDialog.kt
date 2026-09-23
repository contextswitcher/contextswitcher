package com.contextswitcher.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contextswitcher.queue.ClaudeMode

/// *Add task*: a category picker (default [initialCategory]) and a title.
/// When the picked category names a remote ([remoteOf]) and [onStartClaude]
/// is given, *Start Claude on <remote>* (on by default, like the desktop's
/// default button for such a category) offers model, effort and skipping
/// permission prompts, and *Start* hands those over instead of adding a plain
/// task; [progress] shows the running step.
/// The dialog stays open while [adding] and after a failure ([error]), so a
/// title typed on a weak connection is never lost; the caller closes it on
/// success. Input lives in `rememberSaveable` like the settings form.
// [impl->dsn~android-task-create~1]
// [impl->dsn~android-live-task~1]
@Composable
fun AddTaskDialog(
    categories: List<String>,
    initialCategory: String,
    adding: Boolean,
    error: String?,
    onAdd: (category: String, title: String) -> Unit,
    onDismiss: () -> Unit,
    remoteOf: (category: String) -> String? = { null },
    onStartClaude: ((category: String, title: String, mode: ClaudeMode, skipPermissions: Boolean) -> Unit)? = null,
    initialSkipPermissions: Boolean = false,
    progress: String? = null,
) {
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var title by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var startClaude by rememberSaveable { mutableStateOf(true) }
    var model by rememberSaveable { mutableStateOf<String?>(null) }
    var effort by rememberSaveable { mutableStateOf<String?>(null) }
    var skipPermissions by rememberSaveable { mutableStateOf(initialSkipPermissions) }
    val remote = if (onStartClaude == null) null else remoteOf(category)
    val live = remote != null && startClaude
    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text("Add task") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { picking = true }, enabled = !adding, modifier = Modifier.fillMaxWidth()) {
                        Text(categoryLabel(category))
                    }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        for (name in categories) {
                            DropdownMenuItem(text = { Text(categoryLabel(name)) }, onClick = {
                                category = name
                                picking = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    enabled = !adding,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (remote != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = startClaude, onCheckedChange = { startClaude = it }, enabled = !adding)
                        Text("Start Claude on $remote")
                    }
                    if (startClaude) {
                        Row {
                            Picker("Model", model, ClaudeMode.MODELS, enabled = !adding) { model = it }
                            Picker("Effort", effort, ClaudeMode.EFFORTS, enabled = !adding) { effort = it }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = skipPermissions, onCheckedChange = { skipPermissions = it }, enabled = !adding)
                            Text("Skip permission prompts")
                        }
                    }
                }
                if (adding && progress != null) {
                    Text(text = progress, modifier = Modifier.padding(top = 8.dp))
                }
                if (error != null) {
                    Text(text = error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (live && onStartClaude != null) {
                        onStartClaude(category, title.trim(), ClaudeMode(model, effort), skipPermissions)
                    } else {
                        onAdd(category, title.trim())
                    }
                },
                enabled = !adding && title.isNotBlank(),
            ) {
                Text(if (adding) (if (live) "Starting…" else "Adding…") else (if (live) "Start" else "Add"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancel") }
        },
    )
}

private fun categoryLabel(name: String) = name.ifEmpty { "(no category)" }

/// A `label: value` button opening `options`; null (the first entry) leaves
/// the session's own setting.
@Composable
private fun Picker(label: String, value: String?, options: List<String>, enabled: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { Text("$label: ${value ?: "as is"}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in listOf<String?>(null) + options) {
                DropdownMenuItem(text = { Text(option ?: "as is") }, onClick = {
                    onPick(option)
                    open = false
                })
            }
        }
    }
}
