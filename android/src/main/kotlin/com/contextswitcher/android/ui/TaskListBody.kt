package com.contextswitcher.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contextswitcher.discovery.TmuxStatusPoller
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskStatus
import com.contextswitcher.tasks.TaskTags
import kotlinx.coroutines.launch

/// The task list's body: group header rows (sticky while their group scrolls)
/// followed by their task rows, in
/// `groups` order — [buildTaskListModel] already ordered them. Tapping a
/// loaded task's row opens its detail pager ([onTaskClick]); a failed row
/// has nothing to open. [listState] belongs to the caller, so the scroll
/// position outlives this list while a task is open in its place.
/// Tapping a group header lists all groups; picking one scrolls to it.
// [impl->dsn~android-task-list~1]
// [impl->dsn~android-task-list-navigation~1]
@Composable
fun TaskListBody(
    groups: List<TaskGroupUi>,
    modifier: Modifier = Modifier,
    onTaskClick: (Task) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    statuses: Map<String, String> = emptyMap(),
) {
    var pickingGroup by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        for (group in groups) {
            stickyHeader { GroupHeader(group.name) { pickingGroup = true } }
            items(group.entries) { entry -> TaskRow(entry, statuses, onTaskClick) }
        }
    }
    if (pickingGroup) {
        AlertDialog(
            onDismissRequest = { pickingGroup = false },
            title = { Text("Go to category") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    for (group in groups) {
                        TextButton(onClick = {
                            pickingGroup = false
                            scope.launch { listState.scrollToItem(headerIndex(groups, group.name)) }
                        }, modifier = Modifier.fillMaxWidth()) { Text(groupLabel(group.name)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingGroup = false }) { Text("Cancel") } },
        )
    }
}

private fun groupLabel(name: String) = name.ifEmpty { "(ungrouped)" }

@Composable
private fun GroupHeader(name: String, onClick: () -> Unit) {
    Text(
        text = groupLabel(name),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        // Opaque: as a sticky header it lies over the rows scrolling beneath it.
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(top = 16.dp, bottom = 4.dp, start = 8.dp),
    )
}

/// A task's row: [TaskEntry.Failed] renders in red with the file name and
/// parse error, mirroring the desktop's corrupt-row behavior; a loaded task
/// shows title, status (suspended dimmed), and its tag chips.
@Composable
private fun TaskRow(entry: TaskEntry, statuses: Map<String, String>, onTaskClick: (Task) -> Unit) {
    when (entry) {
        is TaskEntry.Failed -> Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(text = entry.fileName, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text(text = entry.message, color = MaterialTheme.colorScheme.error)
        }
        is TaskEntry.Loaded -> {
            val task = entry.task()
            val dimmed = task.status() == TaskStatus.SUSPENDED
            val textColor = if (dimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else Color.Unspecified
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onTaskClick(task) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    RunningDot(sessionStatus(task, statuses))
                    // The title takes what the status leaves, so a long one wraps instead of squeezing it; a whole prompt is cut at three lines.
                    Text(text = task.title(), color = textColor, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(text = task.status().yaml(), color = textColor, modifier = Modifier.padding(start = 8.dp))
                }
                if (task.tags().isNotEmpty()) {
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        for (tag in task.tags()) {
                            TagChip(tag)
                        }
                    }
                }
            }
        }
    }
}

/// The session's `@cs_status` from the poll's map (keyed like
/// [TmuxStatusPoller.key]); `null` for a task without a remote window, or a
/// window recorded by name (the poll answers by id).
fun sessionStatus(task: Task, statuses: Map<String, String>): String? {
    val remote = task.remote() ?: return null
    val window = task.tmux()?.window() ?: return null
    return statuses[TmuxStatusPoller.key(remote, window)]
}

/// The desktop row's running dot (`dsn~task-running-indicator~7`) in
/// claude-semaphore's colours: orange working, red whenever the session needs
/// the user, muted when done. Unknown keeps the slot so titles stay aligned.
// [impl->dsn~android-running-dot~1]
fun runningDotColor(status: String?): Color? = when (status?.lowercase()) {
    "working" -> SEMAPHORE_WORKING
    "waiting", "attention", TmuxStatusPoller.LIMIT -> SEMAPHORE_NEEDS_YOU
    "done" -> Color.Gray
    else -> null
}

private val SEMAPHORE_WORKING = Color(0xFFFF8A00)
private val SEMAPHORE_NEEDS_YOU = Color(0xFFFF1010)

@Composable
private fun RunningDot(status: String?) {
    Text(text = "●", color = runningDotColor(status) ?: Color.Transparent, modifier = Modifier.padding(end = 6.dp))
}

@Composable
private fun TagChip(tag: String) {
    val color = Color(android.graphics.Color.parseColor(TaskTags.autoColor(tag)))
    Text(
        text = tag,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .padding(end = 4.dp)
            .background(color = color, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
