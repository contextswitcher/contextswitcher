package com.contextswitcher.android.sync

import com.contextswitcher.android.ui.buildTaskListModel
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/// Exercises [TaskRepoSync] against a real local git repo (a bare "remote"
/// plus a working clone used to push fixtures) instead of a real HTTPS
/// remote — JGit's local-filesystem transport drives the exact same clone
/// /fetch/reset code path, no network needed.
// [utest->dsn~android-task-repo-sync~1]
class TaskRepoSyncTest {

    @Test
    fun `clones a fresh repo and the tasks parse`(@TempDir tmp: File) {
        val bare = pushFixture(tmp, "tasks/first.md" to taskFixture("First task"))

        val target = File(tmp, "clone")
        val result = TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")

        assertThat(result).isEqualTo(SyncResult.Updated)
        val groups = buildTaskListModel(target.toPath())
        assertThat(groups.flatMap { it.entries }.map { entry -> entry.id() })
            .containsExactly("tasks/first")
    }

    @Test
    fun `re-sync after a remote change reports Updated and picks up the change`(@TempDir tmp: File) {
        val bare = pushFixture(tmp, "tasks/first.md" to taskFixture("First task"))
        val target = File(tmp, "clone")
        TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")

        pushMoreFixtures(tmp, bare, "tasks/second.md" to taskFixture("Second task"))

        val result = TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")

        assertThat(result).isEqualTo(SyncResult.Updated)
        val ids = buildTaskListModel(target.toPath()).flatMap { it.entries }.map { entry -> entry.id() }
        assertThat(ids).containsExactlyInAnyOrder("tasks/first", "tasks/second")
    }

    @Test
    fun `re-sync with no remote change reports UpToDate`(@TempDir tmp: File) {
        val bare = pushFixture(tmp, "tasks/first.md" to taskFixture("First task"))
        val target = File(tmp, "clone")
        TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")

        val result = TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")

        assertThat(result).isEqualTo(SyncResult.UpToDate)
    }

    @Test
    fun `a bogus URL reports Failed`(@TempDir tmp: File) {
        val result = TaskRepoSync().syncInto(File(tmp, "clone"), "file:///no/such/repo", "user", "token")

        assertThat(result).isInstanceOf(SyncResult.Failed::class.java)
    }

    // [utest->dsn~android-task-create~1]
    @Test
    fun `creating a task pushes it with the category's remote default`(@TempDir tmp: File) {
        val bare = pushFixture(tmp,
            "jabref/existing.md" to taskFixture("Existing"),
            "jabref/CONTEXTSWITCHER.md" to "---\nremote: devbox\n---\n")
        val target = File(tmp, "clone")
        TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")

        val first = TaskRepoSync().createTask(target, "user", "token", "jabref", "Fix the build / CI")
        val second = TaskRepoSync().createTask(target, "user", "token", "jabref", "Fix the build / CI")

        assertThat(first).isEqualTo(CreateResult.Created("jabref/fix-the-build-ci"))
        assertThat(second).isEqualTo(CreateResult.Created("jabref/fix-the-build-ci-2"))
        val check = File(tmp, "check")
        Git.cloneRepository().setURI(bare.absolutePath).setDirectory(check).call().close()
        val tasks = buildTaskListModel(check.toPath()).flatMap { it.entries }
            .filterIsInstance<com.contextswitcher.tasks.TaskEntry.Loaded>().associate { it.id() to it.task() }
        assertThat(tasks.keys).containsExactlyInAnyOrder("jabref/existing", "jabref/fix-the-build-ci", "jabref/fix-the-build-ci-2")
        assertThat(tasks.getValue("jabref/fix-the-build-ci").title()).isEqualTo("Fix the build / CI")
        assertThat(tasks.getValue("jabref/fix-the-build-ci").remote()).isEqualTo("devbox")
    }

    // [utest->dsn~android-task-create~1]
    @Test
    fun `a push rejected because the desktop pushed meanwhile is retried on top of it`(@TempDir tmp: File) {
        val bare = pushFixture(tmp, "work/first.md" to taskFixture("First"))
        val target = File(tmp, "clone")
        TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")
        var desktopPushed = false
        val sync = TaskRepoSync(beforePush = {
            if (!desktopPushed) {
                desktopPushed = true
                pushMoreFixtures(tmp, bare, "work/from-desktop.md" to taskFixture("From desktop"))
            }
        })

        val result = sync.createTask(target, "user", "token", "work", "From phone")

        assertThat(result).isEqualTo(CreateResult.Created("work/from-phone"))
        val check = File(tmp, "check")
        Git.cloneRepository().setURI(bare.absolutePath).setDirectory(check).call().close()
        val ids = buildTaskListModel(check.toPath()).flatMap { it.entries }.map { it.id() }
        assertThat(ids).containsExactlyInAnyOrder("work/first", "work/from-desktop", "work/from-phone")
    }

    // [utest->dsn~android-task-create~1]
    @Test
    fun `a failed push leaves the clone a mirror of the remote`(@TempDir tmp: File) {
        val bare = pushFixture(tmp, "work/first.md" to taskFixture("First"))
        val target = File(tmp, "clone")
        TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")
        val sync = TaskRepoSync(beforePush = { bare.renameTo(File(tmp, "gone.git")) })

        val result = sync.createTask(target, "user", "token", "work", "Lost")

        assertThat(result).isInstanceOf(CreateResult.Failed::class.java)
        assertThat(File(target, "work/lost.md")).doesNotExist()
    }

    // [utest->dsn~android-queue-delete~1]
    @Test
    fun `deleting a queued message pushes the shorter queue, and the last one removes the file`(@TempDir tmp: File) {
        val queue = ".queues/work__first.yaml"
        val bare = pushFixture(tmp, "work/first.md" to taskFixture("First"),
            queue to "- keep me\n- drop me\n")
        val target = File(tmp, "clone")
        TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")

        assertThat(TaskRepoSync().deleteQueuedMessage(target, "user", "token", "work/first", "drop me")).isNull()
        assertThat(queueInRemote(tmp, bare, queue)).containsExactly("keep me")

        assertThat(TaskRepoSync().deleteQueuedMessage(target, "user", "token", "work/first", "keep me")).isNull()
        val check = File(tmp, "check-${System.nanoTime()}")
        Git.cloneRepository().setURI(bare.absolutePath).setDirectory(check).call().close()
        assertThat(File(check, queue)).doesNotExist()
    }

    // [utest->dsn~android-queue-delete~1]
    @Test
    fun `a message the desktop already removed needs no push`(@TempDir tmp: File) {
        val queue = ".queues/work__first.yaml"
        val bare = pushFixture(tmp, "work/first.md" to taskFixture("First"),
            queue to "- sent meanwhile\n- other\n")
        val target = File(tmp, "clone")
        TaskRepoSync().syncInto(target, bare.absolutePath, "user", "token")
        pushMoreFixtures(tmp, bare, queue to "- other\n")
        val head = Git.open(bare).use { it.repository.resolve("refs/heads/main") }

        assertThat(TaskRepoSync().deleteQueuedMessage(target, "user", "token", "work/first", "sent meanwhile")).isNull()

        assertThat(Git.open(bare).use { it.repository.resolve("refs/heads/main") }).isEqualTo(head)
        assertThat(queueInRemote(tmp, bare, queue)).containsExactly("other")
    }

    private fun queueInRemote(tmp: File, bare: File, queue: String): List<String> {
        val check = File(tmp, "check-${System.nanoTime()}")
        Git.cloneRepository().setURI(bare.absolutePath).setDirectory(check).call().close()
        return com.contextswitcher.queue.QueueFile.load(File(check, queue).toPath())
    }

    private fun taskFixture(title: String) = """
        ---
        title: $title
        status: active
        ---
        Notes.
    """.trimIndent()

    /// Bare "remote" repo, its first commit seeded by a plain `init` working
    /// copy (a bare repo with no commits yet has no `HEAD` to clone from).
    private fun pushFixture(tmp: File, vararg files: Pair<String, String>): File {
        val bare = File(tmp, "bare.git")
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        val seed = File(tmp, "seed-${System.nanoTime()}")
        Git.init().setDirectory(seed).setInitialBranch("main").call().use { git ->
            writeAndCommit(seed, git, files)
            git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(bare.absolutePath)).call()
            git.push().setRemote("origin").call()
        }
        return bare
    }

    /// Clones the bare repo fresh into a scratch working copy so the new
    /// commit builds on the existing history (a from-scratch `init` would
    /// push an unrelated history and be rejected as a non-fast-forward).
    private fun pushMoreFixtures(tmp: File, bare: File, vararg files: Pair<String, String>) {
        val seed = File(tmp, "seed-${System.nanoTime()}")
        Git.cloneRepository().setURI(bare.absolutePath).setDirectory(seed).call().use { git ->
            writeAndCommit(seed, git, files)
            git.push().setRemote("origin").call()
        }
    }

    private fun writeAndCommit(seed: File, git: Git, files: Array<out Pair<String, String>>) {
        for ((path, content) in files) {
            val file = File(seed, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            git.add().addFilepattern(path).call()
        }
        git.commit().setMessage("fixture").setAuthor("test", "test@example.com").call()
    }
}
