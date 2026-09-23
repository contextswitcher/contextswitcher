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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskStatus
import com.contextswitcher.tasks.TaskTags

/// The task list's body: group header rows (sticky while their group scrolls)
/// followed by their task rows, in
/// `groups` order — [buildTaskListModel] already ordered them. Tapping a
/// loaded task's row opens its detail pager ([onTaskClick]); a failed row
/// has nothing to open. [listState] belongs to the caller, so the scroll
/// position outlives this list while a task is open in its place.
// [impl->dsn~android-task-list~1]
@Composable
fun TaskListBody(
    groups: List<TaskGroupUi>,
    modifier: Modifier = Modifier,
    onTaskClick: (Task) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        for (group in groups) {
            stickyHeader { GroupHeader(group.name) }
            items(group.entries) { entry -> TaskRow(entry, onTaskClick) }
        }
    }
}

@Composable
private fun GroupHeader(name: String) {
    Text(
        text = name.ifEmpty { "(ungrouped)" },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        // Opaque: as a sticky header it lies over the rows scrolling beneath it.
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(top = 16.dp, bottom = 4.dp, start = 8.dp),
    )
}

/// A task's row: [TaskEntry.Failed] renders in red with the file name and
/// parse error, mirroring the desktop's corrupt-row behavior; a loaded task
/// shows title, status (suspended dimmed), and its tag chips.
@Composable
private fun TaskRow(entry: TaskEntry, onTaskClick: (Task) -> Unit) {
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
                    Text(text = task.title(), color = textColor)
                    Text(text = task.status().yaml(), color = textColor)
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
