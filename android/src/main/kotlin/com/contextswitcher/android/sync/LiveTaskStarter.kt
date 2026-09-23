package com.contextswitcher.android.sync

import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.queue.ClaudeMode
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.switching.ClaudeWindowLauncher
import com.contextswitcher.tasks.GroupConfig
import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskFileParser
import com.contextswitcher.tasks.TaskRepository
import java.io.File
import java.time.LocalDate

/// The phone's *Add remote Claude*: what the desktop's live-task creation
/// does (`dsn~task-create-live~8`), from the synced clone. The category's
/// `CONTEXTSWITCHER.md` gives remote, working directory and repository; a
/// fresh tmux window in the remote's usual session gets Claude with the
/// desktop's start prompt, model and effort switched first.
/// The task file — the desktop's live-task file plus the `tmux:` section — is
/// pushed the moment the window exists ([onCreated]), before Claude is up, so
/// the list shows it while the rest of the start runs (a quarter minute).
// [impl->dsn~android-live-task~1]
class LiveTaskStarter(
    private val sync: TaskRepoSync,
    private val ssh: SshCommandRunner,
    private val sender: MessageSender,
) {

    sealed interface Outcome {
        data class Started(val taskId: String, val windowId: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /// The remote a live task in `category` would run on, or null when its
    /// `CONTEXTSWITCHER.md` names none (then only a plain task is possible).
    fun remoteOf(dir: File, category: String): String? = config(dir, category).remote()

    fun start(
        dir: File,
        username: String,
        token: String,
        category: String,
        description: String,
        mode: ClaudeMode,
        skipPermissions: Boolean,
        onStep: (String) -> Unit,
        onCreated: (taskId: String) -> Unit,
    ): Outcome {
        val config = config(dir, category)
        val remote = config.remote() ?: return Outcome.Failed("The category names no remote in its ${TaskRepository.GROUP_CONFIG_FILE_NAME}")
        val workdir = config.resolveWorkdir()
        val session = usualSession(dir, remote)
        val prompt = ClaudeWindowLauncher.liveTaskPrompt(description, config.repo(), config.bootstrapWorktree(), category, null)
        if (workdir != null) {
            onStep("Preparing the workspace …")
            // So `tmux new-window -c` succeeds on a fresh workspaces root.
            ssh.run(remote, listOf("mkdir", "-p", "'$workdir'"))
        }
        var created: CreateResult? = null
        val windowId = ClaudeWindowLauncher(ssh, sender, skipPermissions).launch(
            remote, session, description, prompt, workdir, mode, onStep,
            { id ->
                onStep("Pushing the task …")
                val result = sync.createTaskFile(dir, username, token, category, "${LocalDate.now()}-$description") {
                    TaskFileParser.withTmuxSection(
                        TaskFileParser.addUrlsFrom(ClaudeWindowLauncher.liveTaskContent(description, remote, workdir), description),
                        session, id, "created ${LocalDate.now()}",
                    )
                }
                created = result
                if (result is CreateResult.Created) {
                    onCreated(result.taskId)
                }
            },
        ) ?: return Outcome.Failed("Cannot create a tmux window in session $session on $remote")
        return when (val result = created) {
            is CreateResult.Created -> Outcome.Started(result.taskId, windowId)
            is CreateResult.Failed -> Outcome.Failed("Claude runs in window $windowId on $remote, but the task was not pushed: ${result.message}")
            null -> Outcome.Failed("Window $windowId on $remote was not reported")
        }
    }

    private fun config(dir: File, category: String): GroupConfig {
        val file = File(dir, (if (category.isEmpty()) "" else "$category/") + TaskRepository.GROUP_CONFIG_FILE_NAME)
        return if (category.isNotEmpty() && file.isFile) TaskFileParser().parseGroupConfig(file.readText()) else GroupConfig.EMPTY
    }

    /// The tmux session the remote's tasks live in, like the desktop's
    /// `usualSession`: the first task on `remote` with one, else `0`.
    private fun usualSession(dir: File, remote: String): String =
        runCatching { buildTaskListModel(dir.toPath()) }.getOrDefault(emptyList())
            .flatMap { it.entries }
            .filterIsInstance<TaskEntry.Loaded>()
            .map { it.task() }
            .firstOrNull { it.remote() == remote && it.tmux() != null }
            ?.tmux()?.session() ?: "0"
}
