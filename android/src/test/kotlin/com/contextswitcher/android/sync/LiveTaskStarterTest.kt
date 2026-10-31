package com.contextswitcher.android.sync

import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.queue.ClaudeMode
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.ssh.SshCommandRunner.SshResult
import com.contextswitcher.tasks.TaskEntry
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/// A live task from the phone: window in the remote's usual session and the
/// category's workspace, the task file pushed with that window, Claude
/// started with the desktop's prompt.
// [utest->dsn~android-live-task~4]
class LiveTaskStarterTest {

    private class Remote(val unreachable: String? = null) : SshCommandRunner {
        val commands = CopyOnWriteArrayList<String>()
        val pasted = CopyOnWriteArrayList<String>()

        override fun run(host: String, remoteCommand: List<String>): SshResult {
            val line = remoteCommand.joinToString(" ")
            commands.add("$host: $line")
            return when {
                unreachable != null -> SshResult(-1, "", unreachable)
                "new-window" in remoteCommand -> SshResult(0, "@77\n", "")
                "capture-pane" in remoteCommand -> SshResult(0, pasted.joinToString("\n"), "")
                else -> SshResult(0, "", "")
            }
        }

        override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray): SshResult {
            commands.add("$host: " + remoteCommand.joinToString(" "))
            pasted.add(String(input))
            return SshResult(0, "", "")
        }
    }

    @Test
    fun `starts Claude in a new window and pushes the task with that window`(@TempDir tmp: File) {
        val bare = File(tmp, "bare.git")
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        val seed = File(tmp, "seed")
        Git.init().setDirectory(seed).setInitialBranch("main").call().use { git ->
            File(seed, "work").mkdirs()
            File(seed, "work/CONTEXTSWITCHER.md").writeText("---\nremote: me@box\nworkspacesRoot: /ws\nrepo: https://github.com/o/r\n---\n")
            File(seed, "work/older.md").writeText("---\ntitle: Older\nstatus: active\nremote: me@box\ntmux:\n  session: main\n  window: '@3'\n---\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("seed").setAuthor("t", "t@x").call()
            git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(bare.absolutePath)).call()
            git.push().setRemote("origin").call()
        }
        val clone = File(tmp, "clone")
        TaskRepoSync().syncInto(clone, bare.absolutePath, "u", "t")
        val remote = Remote()
        val sync = TaskRepoSync()
        val createdIds = mutableListOf<String>()

        val outcome = LiveTaskStarter(sync, remote, MessageSender(remote, File(tmp, "att").toPath()))
            .start(clone, "u", "t", "work", "Fix the build", ClaudeMode.DEFAULT, skipPermissions = true, onStep = {}) { createdIds += it }

        val taskId = "work/${LocalDate.now()}-fix-the-build"
        assertThat(outcome).isEqualTo(LiveTaskStarter.Outcome.Started(taskId, "@77"))
        assertThat(createdIds).containsExactly(taskId)
        assertThat(remote.commands).anyMatch { it.contains("new-window -t 'main:' -c '/ws'") }
        assertThat(remote.commands).anyMatch { it.contains("claude --dangerously-skip-permissions") }
        assertThat(remote.pasted.joinToString()).contains("Fix the build").contains("git worktree for this task from the primary clone at /ws/r")

        val check = File(tmp, "check")
        Git.cloneRepository().setURI(bare.absolutePath).setDirectory(check).call().close()
        val task = buildTaskListModel(check.toPath()).flatMap { it.entries }.filterIsInstance<TaskEntry.Loaded>()
            .map { it.task() }.single { it.id() == taskId }
        assertThat(task.remote()).isEqualTo("me@box")
        assertThat(task.tmux()?.session()).isEqualTo("main")
        assertThat(task.tmux()?.window()).isEqualTo("@77")
        assertThat(task.claude()?.cwd()).isEqualTo("/ws")
    }

    @Test
    fun `a category without a remote falls back to the first remote a task names`(@TempDir tmp: File) {
        File(tmp, ".git").mkdirs()
        File(tmp, "notes").mkdirs()
        File(tmp, "notes/CONTEXTSWITCHER.md").writeText("---\nrepo: https://github.com/o/r\n---\n")
        File(tmp, "work").mkdirs()
        File(tmp, "work/older.md").writeText("---\ntitle: Older\nstatus: active\nremote: me@box\n---\n")
        val starter = LiveTaskStarter(TaskRepoSync(), Remote(), MessageSender(Remote(), tmp.toPath()))

        assertThat(starter.remoteOf(tmp, "notes")).isEqualTo("me@box")
        assertThat(starter.remoteOf(tmp, "")).isEqualTo("me@box")
    }

    @Test
    fun `without any remote the dialog has none and the start fails`(@TempDir tmp: File) {
        val remote = Remote()
        File(tmp, ".git").mkdirs()
        val starter = LiveTaskStarter(TaskRepoSync(), remote, MessageSender(remote, tmp.toPath()))
        val outcome = starter.start(tmp, "u", "t", "work", "x", ClaudeMode.DEFAULT, false, {}) {}

        assertThat(starter.remoteOf(tmp, "work")).isNull()
        assertThat(outcome).isInstanceOf(LiveTaskStarter.Outcome.Failed::class.java)
        assertThat(remote.commands).isEmpty()
    }

    @Test
    fun `a window that cannot be created reports the remote's error, not just the headline`(@TempDir tmp: File) {
        val remote = Remote(unreachable = "Cannot connect to me@box: Connection refused")
        File(tmp, ".git").mkdirs()
        File(tmp, "work").mkdirs()
        File(tmp, "work/CONTEXTSWITCHER.md").writeText("---\nremote: me@box\nworkspacesRoot: ~/ws\n---\n")
        val starter = LiveTaskStarter(TaskRepoSync(), remote, MessageSender(remote, tmp.toPath()))
        val outcome = starter.start(tmp, "u", "t", "work", "x", ClaudeMode.DEFAULT, false, {}) {}

        assertThat(outcome).isInstanceOf(LiveTaskStarter.Outcome.Failed::class.java)
        assertThat((outcome as LiveTaskStarter.Outcome.Failed).message)
            .isEqualTo("Cannot create a tmux window in session 0 on me@box:\nCannot connect to me@box: Connection refused")
        // The workspaces root is `mkdir -p`ed with the tilde outside the quotes, so the
        // remote shell expands it (the desktop's 2026-10-12 fix, `quotePath`).
        assertThat(remote.commands).anyMatch { it.endsWith("mkdir -p ~/'ws'") }
    }
}
