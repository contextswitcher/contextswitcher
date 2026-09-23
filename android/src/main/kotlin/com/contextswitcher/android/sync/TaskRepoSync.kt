package com.contextswitcher.android.sync

import com.contextswitcher.queue.QueueFile
import com.contextswitcher.tasks.TaskFileParser
import com.contextswitcher.tasks.TaskRepository
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/// Outcome of [TaskRepoSync.syncInto], crossing the UI boundary without an
/// exception: [Failed] carries a message fit to show in a Snackbar.
sealed interface SyncResult {
    data object UpToDate : SyncResult
    data object Updated : SyncResult
    data class Failed(val message: String) : SyncResult
}

/// Outcome of [TaskRepoSync.createTask]; [Created] carries the new task's id.
sealed interface CreateResult {
    data class Created(val taskId: String) : CreateResult
    data class Failed(val message: String) : CreateResult
}

/// Sync of the task backup repo (MADR 0011's remote) into local app-private
/// storage, over HTTPS with a username/token only — ssh deploy keys are P6
/// (docs/decisions/0028-jgit-task-repo-clone-on-android.md).
/// The phone's writes ([createTask], [deleteQueuedMessage]) are pushed at once
/// or dropped, so no local commit outlives a call: re-syncing an existing clone is fetch +
/// **hard reset** to the remote's default branch, never a merge — that is the
/// honest operation for a mirror, not a destructive one.
/// [beforePush] runs between a new task's commit and its push; tests use it to
/// let another machine push in between.
// [impl->dsn~android-task-repo-sync~1]
class TaskRepoSync(private val beforePush: () -> Unit = {}) {

    /// Clones `url` into `dir` when it holds no repo yet, else fetches and
    /// hard-resets the existing clone to the remote's default branch.
    fun syncInto(dir: File, url: String, username: String, token: String): SyncResult {
        val credentials = UsernamePasswordCredentialsProvider(username, token)
        return try {
            if (File(dir, ".git").isDirectory) {
                fetchAndReset(dir, credentials)
            } else {
                dir.mkdirs()
                Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(dir)
                    .setCredentialsProvider(credentials)
                    .call()
                    .close()
                SyncResult.Updated
            }
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /// The local branch name checked out by the initial clone never changes
    /// (nothing on the phone ever switches or commits), so `repository
    /// .branch` still names the remote's default branch — no extra lookup or
    /// stored marker needed to find `refs/remotes/origin/<branch>`.
    private fun fetchAndReset(dir: File, credentials: UsernamePasswordCredentialsProvider): SyncResult {
        FileRepositoryBuilder().setGitDir(File(dir, ".git")).build().use { repository ->
            Git(repository).use { git ->
                git.fetch().setCredentialsProvider(credentials).call()
                val before = repository.resolve("HEAD")
                val after = resetToRemote(repository, git)
                    ?: return SyncResult.Failed("Remote branch '${repository.branch}' not found after fetch")
                return if (after == before) SyncResult.UpToDate else SyncResult.Updated
            }
        }
    }

    /// Hard-resets to the last fetched `origin/<branch>`; null when that ref is missing.
    private fun resetToRemote(repository: Repository, git: Git): org.eclipse.jgit.lib.ObjectId? {
        val after = repository.resolve("refs/remotes/origin/${repository.branch}") ?: return null
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(after.name).call()
        return after
    }

    /// Adds a task titled `title` to `category` (a top-level folder, empty for
    /// the root) of the synced clone in `dir` and pushes it, so the desktop
    /// pulls it on its next start. The clone is brought to the remote first;
    /// a push rejected because another machine pushed meanwhile is retried once
    /// on top of that push — a new file cannot conflict with anything. On any
    /// failure the local commit is dropped again, keeping the clone a mirror.
    /// File name and content follow the desktop's *Add task…* for a title: the
    /// slugged title (`-2`, `-3`… when taken) and compact frontmatter carrying
    /// the category's `remote` default.
    // [impl->dsn~android-task-create~1]
    fun createTask(dir: File, username: String, token: String, category: String, title: String): CreateResult =
        createTaskFile(dir, username, token, category, title) { prefix ->
            val configFile = File(dir, prefix + TaskRepository.GROUP_CONFIG_FILE_NAME)
            val remote = if (configFile.isFile) TaskFileParser().parseGroupConfig(configFile.readText()).remote() else null
            TaskFileParser.addUrlsFrom(TaskFileParser.newTaskContent(title, remote, null, false), title)
        }

    /// As [createTask], but with the file's `name` (slugged; `-2`… when taken)
    /// and `content` (given the category's folder prefix, read after the
    /// clone was brought to the remote) chosen by the caller — the live task
    /// of `dsn~android-live-task~1` writes the desktop's live-task file.
    // [impl->dsn~android-task-create~1]
    fun createTaskFile(
        dir: File,
        username: String,
        token: String,
        category: String,
        name: String,
        content: (prefix: String) -> String,
    ): CreateResult {
        var taskId = ""
        val error = pushChange(dir, username, token) { git ->
            taskId = commitNewTask(dir, git, category, name, content)
            true
        }
        return if (error == null) CreateResult.Created(taskId) else CreateResult.Failed(error)
    }

    /// Removes the first queued message equal to `message` from `taskId`'s queue
    /// file (`.queues/`, the desktop's queue) and pushes that, the same way
    /// [createTask] pushes a new task. A message already gone from the fetched
    /// queue — the desktop sent or deleted it meanwhile — needs no commit.
    /// Null on success, else the error to show.
    // [impl->dsn~android-queue-delete~1]
    fun deleteQueuedMessage(dir: File, username: String, token: String, taskId: String, message: String): String? =
        pushChange(dir, username, token) { git ->
            val queueFile = QueueFile.file(dir.toPath().resolve(".queues"), taskId)
            val messages = QueueFile.load(queueFile).toMutableList()
            if (!messages.remove(message)) {
                false
            } else {
                QueueFile.save(queueFile, messages)
                val path = dir.toPath().relativize(queueFile).toString().replace(File.separatorChar, '/')
                // update, not add: an emptied queue deletes the file.
                git.add().setUpdate(true).addFilepattern(path).call()
                commit(git, "Delete a queued message of $taskId from Android")
                true
            }
        }

    /// The phone's write-back: fetch and hard-reset, let `change` write and
    /// commit (false: nothing to change), push. A push rejected as
    /// non-fast-forward (another machine pushed in between) runs `change` once
    /// more on top of that push; any failure resets the clone to the remote
    /// again, so no local commit survives. Null on success, else the error.
    private fun pushChange(dir: File, username: String, token: String, change: (Git) -> Boolean): String? {
        if (!File(dir, ".git").isDirectory) {
            return "Sync the task repo first"
        }
        val credentials = UsernamePasswordCredentialsProvider(username, token)
        FileRepositoryBuilder().setGitDir(File(dir, ".git")).build().use { repository ->
            Git(repository).use { git ->
                return try {
                    repeat(2) {
                        git.fetch().setCredentialsProvider(credentials).call()
                        resetToRemote(repository, git)
                            ?: return "Remote branch '${repository.branch}' not found after fetch"
                        if (!change(git)) {
                            return null
                        }
                        beforePush()
                        val update = git.push().setCredentialsProvider(credentials).call()
                            .flatMap { it.remoteUpdates }.single()
                        if (update.status == RemoteRefUpdate.Status.OK) {
                            return null
                        }
                        resetToRemote(repository, git)
                        if (update.status != RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                            return "Push rejected: ${update.message ?: update.status}"
                        }
                    }
                    "Push rejected twice: the task repo keeps changing"
                } catch (e: Exception) {
                    runCatching { resetToRemote(repository, git) }
                    e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    private fun commit(git: Git, message: String) {
        val author = PersonIdent("ContextSwitcher Android", "android@contextswitcher.invalid")
        git.commit().setMessage(message).setAuthor(author).setCommitter(author).call()
    }

    private fun commitNewTask(dir: File, git: Git, category: String, name: String, content: (String) -> String): String {
        val prefix = if (category.isEmpty()) "" else "$category/"
        // slug, not newTaskFileName: a "/" in the title must not pick a folder.
        val base = prefix + TaskFileParser.slug(name).ifEmpty { "task" }
        var taskId = base
        var i = 2
        while (File(dir, "$taskId.md").exists()) {
            taskId = "$base-${i++}"
        }
        val file = File(dir, "$taskId.md")
        file.parentFile?.mkdirs()
        file.writeText(content(prefix))
        git.add().addFilepattern("$taskId.md").call()
        commit(git, "Add task $taskId from Android")
        return taskId
    }
}
