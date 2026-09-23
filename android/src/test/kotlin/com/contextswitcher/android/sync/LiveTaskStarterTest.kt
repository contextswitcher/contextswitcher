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
// [utest->dsn~android-live-task~1]
class LiveTaskStarterTest {

    private class Remote : SshCommandRunner {
        val commands = CopyOnWriteArrayList<String>()
        val pasted = CopyOnWriteArrayList<String>()

        override fun run(host: String, remoteCommand: List<String>): SshResult {
            val line = remoteCommand.joinToString(" ")
            commands.add("$host: $line")
            return when {
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
        assertThat(remote.pasted.joinToString()).contains("Fix the build").contains("git worktree for this task from the work subdirectory")

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
    fun `a category without a remote starts nothing`(@TempDir tmp: File) {
        val remote = Remote()
        File(tmp, ".git").mkdirs()
        val outcome = LiveTaskStarter(TaskRepoSync(), remote, MessageSender(remote, tmp.toPath()))
            .start(tmp, "u", "t", "work", "x", ClaudeMode.DEFAULT, false, {}) {}

        assertThat(outcome).isInstanceOf(LiveTaskStarter.Outcome.Failed::class.java)
        assertThat(remote.commands).isEmpty()
    }
}
