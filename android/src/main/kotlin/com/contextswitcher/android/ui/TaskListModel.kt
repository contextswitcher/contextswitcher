package com.contextswitcher.android.ui

import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskFileParser
import com.contextswitcher.tasks.TaskOrder
import com.contextswitcher.tasks.TaskRepository
import com.contextswitcher.tasks.TaskStatus
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Date
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
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

/// The group whose header is pinned at the top of [TaskListBody] when its
/// `LazyColumn`'s first visible item is [firstVisibleItemIndex]: each group
/// occupies one header slot plus one per entry, in `groups` order. The
/// *Add task* dialog preselects it, so a long list scrolled to a category
/// adds there and not to the alphabetically first one. `""` for no groups.
// [impl->dsn~android-task-create~2]
fun groupAtTop(groups: List<TaskGroupUi>, firstVisibleItemIndex: Int): String {
    var index = firstVisibleItemIndex
    for (group in groups) {
        index -= 1 + group.entries.size
        if (index < 0) return group.name
    }
    return groups.lastOrNull()?.name ?: ""
}

/// The `LazyColumn` index of [name]'s group header in [TaskListBody] — the
/// inverse of [groupAtTop]; 0 when no group has that name.
// [impl->dsn~android-task-list-navigation~1]
fun headerIndex(groups: List<TaskGroupUi>, name: String): Int {
    var index = 0
    for (group in groups) {
        if (group.name == name) return index
        index += 1 + group.entries.size
    }
    return 0
}

/// The task list narrowed to [query] (case-insensitive): a group whose name
/// matches keeps all its tasks, otherwise only the tasks whose title or a tag
/// matches (a failed row by its file name); groups left empty are dropped.
/// A blank query returns [groups] unchanged.
// [impl->dsn~android-task-list-navigation~1]
fun filterTaskList(groups: List<TaskGroupUi>, query: String): List<TaskGroupUi> {
    val q = query.trim()
    if (q.isEmpty()) return groups
    fun matches(entry: TaskEntry) = when (entry) {
        is TaskEntry.Loaded -> entry.task().title().contains(q, ignoreCase = true) ||
            entry.task().tags().any { it.contains(q, ignoreCase = true) }
        is TaskEntry.Failed -> entry.fileName.contains(q, ignoreCase = true)
    }
    return groups.mapNotNull { group ->
        if (group.name.contains(q, ignoreCase = true)) group
        else group.entries.filter(::matches).takeIf { it.isNotEmpty() }?.let { TaskGroupUi(group.name, it) }
    }
}

/// The desktop's *Show running tasks only* and its *Also show suspended tasks
/// of the last 14 days*, as one cycling top-bar button.
// [impl->dsn~android-status-filter~1]
enum class StatusFilter(val label: String) {
    ALL("All"), ACTIVE("Active"), RECENT("Active+$RECENT_SUSPENDED_DAYS");

    fun next() = entries[(ordinal + 1) % entries.size]
}

const val RECENT_SUSPENDED_DAYS = 14L

/// [groups] narrowed by [filter]: [StatusFilter.ACTIVE] keeps active tasks,
/// [StatusFilter.RECENT] also suspended ones whose id is in [recentIds]; error
/// rows always stay, groups left empty are dropped.
// [impl->dsn~android-status-filter~1]
fun filterByStatus(groups: List<TaskGroupUi>, filter: StatusFilter, recentIds: Set<String>): List<TaskGroupUi> {
    if (filter == StatusFilter.ALL) return groups
    fun keep(entry: TaskEntry) = entry !is TaskEntry.Loaded || entry.task().status() == TaskStatus.ACTIVE ||
        (filter == StatusFilter.RECENT && entry.task().status() == TaskStatus.SUSPENDED && entry.id() in recentIds)
    return groups.mapNotNull { group -> group.entries.filter(::keep).takeIf { it.isNotEmpty() }?.let { TaskGroupUi(group.name, it) } }
}

/// The ids of the tasks whose file a commit of the last [days] days touched.
/// The desktop reads the file's modification time; on the phone that is the
/// clone's or pull's time, so the commit history answers instead (first
/// parent only: a merge's other side is walked as its own commits).
// [impl->dsn~android-status-filter~1]
fun recentlyChangedIds(repoDir: File, days: Long = RECENT_SUSPENDED_DAYS, now: Instant = Instant.now()): Set<String> {
    val ids = HashSet<String>()
    Git.open(repoDir).use { git ->
        val repo = git.repository
        val head = repo.resolve("HEAD") ?: return ids
        RevWalk(repo).use { walk ->
            walk.markStart(walk.parseCommit(head))
            walk.revFilter = CommitTimeRevFilter.after(Date.from(now.minus(Duration.ofDays(days))))
            TreeWalk(repo).use { tree ->
                tree.isRecursive = true
                tree.filter = TreeFilter.ANY_DIFF
                for (commit in walk) {
                    tree.reset()
                    if (commit.parentCount > 0) tree.addTree(commit.getParent(0).also(walk::parseHeaders).tree)
                    else tree.addTree(EmptyTreeIterator())
                    tree.addTree(commit.tree)
                    while (tree.next()) {
                        tree.pathString.takeIf { it.endsWith(".md") }?.let { ids.add(it.removeSuffix(".md")) }
                    }
                }
            }
        }
    }
    return ids
}
