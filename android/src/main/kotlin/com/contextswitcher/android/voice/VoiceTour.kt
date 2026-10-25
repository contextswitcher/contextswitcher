package com.contextswitcher.android.voice

import com.contextswitcher.android.car.CarTask
import com.contextswitcher.android.car.carTasks
import com.contextswitcher.android.car.pollStatuses
import com.contextswitcher.android.ui.TaskGroupUi
import com.contextswitcher.discovery.ClaudeTranscript
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.Task

/// A spoken walk through one category's tasks: the overview ("what is up in
/// JabRef?"), then task by task — title, status, Claude's last answer — with a
/// dictated reply sent into the task's own session. The tasks keep their
/// sessions; the tour is only the phone stepping through them. Blocking SSH
/// reads: call off the main thread.
// [impl->dsn~android-voice-tour~1]
class VoiceTour(val category: String, tasks: List<CarTask>, private val ssh: SshCommandRunner) {

    var tasks: List<CarTask> = tasks
        private set
    var index = 0
        private set
    val current: CarTask? get() = tasks.getOrNull(index)
    val hasNext: Boolean get() = index + 1 < tasks.size
    val hasPrevious: Boolean get() = index > 0

    fun next(): Boolean = hasNext.also { if (it) index++ }
    fun previous(): Boolean = hasPrevious.also { if (it) index-- }

    /// "What is up": how many tasks, and which wait for the user.
    fun overview(): String {
        if (tasks.isEmpty()) {
            return "$category: no task with a Claude session."
        }
        val waiting = tasks.filter { it.status?.lowercase() in WAITING }.map { it.task.title() }
        val working = tasks.count { it.status?.lowercase() == "working" }
        return buildString {
            append("$category: ${count(tasks.size, "task")}. ")
            append(if (waiting.isEmpty()) "None waits for you." else "Waiting for you: ${waiting.joinToString(", ")}.")
            if (working > 0) append(" Working: $working.")
        }
    }

    /// The current task's spoken card: position, title, status, last answer.
    fun announcement(): String {
        val entry = current ?: return overview()
        return "Task ${index + 1} of ${tasks.size}: ${entry.task.title()}. Status: ${entry.status ?: "unknown"}. " +
            (lastAnswer(entry.task)?.let { "Claude said: $it" } ?: "No answer from Claude yet.")
    }

    /// Claude's last answer in the task's session, read from its transcript
    /// (`ClaudeTranscript`); null without a session id or when unreachable.
    fun lastAnswer(task: Task): String? {
        val claude = task.claude() ?: return null
        val session = claude.sessionId() ?: return null
        val host = task.remote() ?: return null
        return runCatching { ClaudeTranscript(ssh).lastAnswer(host, claude.cwd(), session) }.getOrNull()
    }

    /// Re-reads the statuses; true when the current task's changed.
    fun refreshStatuses(groups: List<TaskGroupUi>): Boolean {
        val before = current?.status
        val statuses = pollStatuses(ssh, groups.filter { it.name == category })
        val id = current?.task?.id()
        tasks = tasks.map { it.copy(status = it.task.tmux()?.window()?.let { w -> statuses[com.contextswitcher.discovery.TmuxStatusPoller.key(it.task.remote()!!, w)] }) }
        index = tasks.indexOfFirst { it.task.id() == id }.coerceAtLeast(0)
        return current?.status != before
    }

    companion object {
        private val WAITING = setOf("waiting", "attention", "done", com.contextswitcher.discovery.TmuxStatusPoller.LIMIT)

        /// Asks Claude for an answer that reads well aloud; prepended to every dictated reply.
        const val SPOKEN_REPLY_HINT = "(Voice mode: I am listening, not reading. Answer in a few short sentences of prose, no tables, code or diffs.) "

        fun spokenReply(dictated: String) = SPOKEN_REPLY_HINT + dictated

        /// The tour over one category: its reachable tasks in `carTasks` order
        /// (waiting ones first), with one status poll.
        fun of(category: String, groups: List<TaskGroupUi>, ssh: SshCommandRunner): VoiceTour {
            val own = groups.filter { it.name == category }
            return VoiceTour(category, carTasks(own, pollStatuses(ssh, own)), ssh)
        }

        /// The categories a tour can walk: those with a reachable task.
        fun categories(groups: List<TaskGroupUi>): List<String> =
            groups.filter { carTasks(listOf(it), emptyMap()).isNotEmpty() }.map { it.name }

        private fun count(n: Int, noun: String) = if (n == 1) "one $noun" else "$n ${noun}s"
    }
}
