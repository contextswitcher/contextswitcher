package com.contextswitcher.android.ui

import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskFileParser
import com.contextswitcher.tasks.TaskOrder
import com.contextswitcher.tasks.TaskRepository
import java.nio.file.Path
import java.util.TreeMap

/// One project-group section of the task list. `name` is `""` for the
/// root-level group (tasks directly under the synced repo, no subfolder).
data class TaskGroupUi(val name: String, val entries: List<TaskEntry>)

/// Scans `tasksDir` — the synced clone's root, which mirrors the desktop's
/// task-directory layout (`docs/decisions/0028-...`) — with :core's
/// [TaskRepository] (a plain [Runnable.run] executor and fresh
/// `ArrayList`/`HashSet`, no [TaskRepository.startWatching]: the phone reads
/// a snapshot after each sync rather than keeping a live watcher) and
/// returns the tasks grouped by folder.
///
/// Folders sort case-insensitively, the same order the desktop's default
/// grouped view uses; tasks within a folder use [TaskOrder.ALPHABETICAL] —
/// the only order that makes sense here, since a phone snapshot carries
/// neither a live `@cs_status` nor a meaningful file-modification time.
/// Kept as a plain `Path -> List<TaskGroupUi>` function (no Android/Compose
/// types) so it is unit-testable on the JVM without Robolectric.
// [impl->dsn~android-task-list~1]
fun buildTaskListModel(tasksDir: Path): List<TaskGroupUi> {
    val entries = ArrayList<TaskEntry>()
    val folders = HashSet<String>()
    val repository = TaskRepository(tasksDir, TaskFileParser(), Runnable::run, entries, folders)
    repository.scan()

    val grouped = TreeMap<String, MutableList<TaskEntry>>(String.CASE_INSENSITIVE_ORDER)
    for (folder in folders) {
        grouped.getOrPut(folder) { ArrayList() }
    }
    for (entry in entries) {
        grouped.getOrPut(entry.group()) { ArrayList() }.add(entry)
    }

    val comparator = TaskOrder.ALPHABETICAL.comparator({ null }, { 0L })
    return grouped.entries
        .filter { (name, groupEntries) -> groupEntries.isNotEmpty() || name.isNotEmpty() }
        .map { (name, groupEntries) -> TaskGroupUi(name, groupEntries.sortedWith(comparator)) }
}
