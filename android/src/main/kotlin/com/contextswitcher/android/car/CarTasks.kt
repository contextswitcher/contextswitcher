package com.contextswitcher.android.car

import com.contextswitcher.android.ui.TaskGroupUi
import com.contextswitcher.android.watch.TaskWatchService
import com.contextswitcher.discovery.TmuxStatusPoller
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskStatus

/// A task the head unit can talk to, with its session's `@cs_status` (`null`: unknown).
data class CarTask(val task: Task, val status: String?)

/// The head unit's task list: only tasks a message can reach (not done, with a
/// remote and a tmux window), the ones waiting for the user first — the head
/// unit shows about six rows, so an alphabetical list would hide them.
/// `statuses` is keyed like [TmuxStatusPoller.key].
// [impl->dsn~android-auto-tasks~1]
fun carTasks(groups: List<TaskGroupUi>, statuses: Map<String, String>): List<CarTask> =
    groups.asSequence().flatMap { it.entries }
        .filterIsInstance<TaskEntry.Loaded>().map { it.task() }
        .filter { it.status() != TaskStatus.DONE && it.remote() != null && it.tmux() != null }
        // ponytail: a window recorded by name has no status here (the poll answers by id);
        // resolve names like TaskWatchService.resolveWindowId if such tasks sink too low.
        .map { task -> CarTask(task, task.tmux()!!.window()?.let { statuses[TmuxStatusPoller.key(task.remote()!!, it)] }) }
        .sortedWith(compareBy<CarTask> { rank(it.status) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.task.title() })
        .toList()

private fun rank(status: String?) = when (status?.lowercase()) {
    "attention", TmuxStatusPoller.LIMIT -> 0
    "waiting", "done" -> 1
    "working" -> 2
    else -> 3
}

/// One read-only status poll per remote; an unreachable remote just has no statuses.
fun pollStatuses(ssh: SshCommandRunner, groups: List<TaskGroupUi>): Map<String, String> {
    val statuses = HashMap<String, String>()
    carTasks(groups, emptyMap()).map { it.task.remote()!! }.distinct().forEach { remote ->
        runCatching { ssh.run(remote, TmuxStatusPoller.statusCommand()) }.getOrNull()?.takeIf { it.ok() }?.let {
            TmuxStatusPoller.parseInto(remote, it.stdout(), statuses, TaskWatchService.IDLE_THRESHOLD_SECONDS)
        }
    }
    return statuses
}
