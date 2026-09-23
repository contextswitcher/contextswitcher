package com.contextswitcher.android.provenance

import android.content.Context
import android.os.Build
import android.util.Log
import com.contextswitcher.android.settings.SettingsStore
import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.provenance.ProvenanceConfig
import com.contextswitcher.provenance.ProvenanceRecorder
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.TaskEntry
import com.contextswitcher.tasks.TaskFileParser
import com.contextswitcher.tasks.TaskRepository
import java.io.File
import java.time.Clock
import java.util.concurrent.Executors

/// The phone's provenance wiring (MADR 0037): records every message a
/// [MessageSender] delivers into `filesDir/provenance` once `provenance.yaml`
/// in the synced task repo named a repository and its copy exists, and syncs
/// that copy after each record and each task sync, one round at a time.
// [impl->dsn~provenance-record~1]
// [impl->dsn~provenance-repository~1]
object PhoneProvenance {

    private val rounds = Executors.newSingleThreadExecutor()
    private val sync = ProvenanceSync()

    private fun dir(context: Context) = File(context.filesDir, "provenance")
    private fun taskRepo(context: Context) = File(context.filesDir, "taskrepo")

    fun install(context: Context, sender: MessageSender, ssh: SshCommandRunner) {
        val app = context.applicationContext
        sender.setDeliveryListener(ProvenanceRecorder(
            { dir(app).takeIf { File(it, ".git").isDirectory }?.toPath() },
            "android-${Build.MODEL}", ssh,
            { host, target ->
                val tasks = runCatching { buildTaskListModel(taskRepo(app).toPath()) }.getOrDefault(emptyList())
                    .flatMap { it.entries }.filterIsInstance<TaskEntry.Loaded>().map { it.task() }
                ProvenanceRecorder.taskIn(tasks, host, target)
            },
            { category -> categoryRepo(taskRepo(app), category) },
            Clock.systemUTC(),
            { syncSoon(app) },
        ))
    }

    /// One round in the background; a failure only logs, the next round retries.
    fun syncSoon(context: Context) {
        val app = context.applicationContext
        rounds.execute {
            val repo = ProvenanceConfig.repository(taskRepo(app).toPath()) ?: return@execute
            val settings = SettingsStore(app).load() ?: return@execute
            sync.sync(dir(app), repo, settings.username, settings.token)?.let { Log.w("ContextSwitcher", "Provenance sync: $it") }
        }
    }

    private fun categoryRepo(taskRepo: File, category: String): String? {
        val file = File(taskRepo, "$category/${TaskRepository.GROUP_CONFIG_FILE_NAME}")
        return if (category.isEmpty() || !file.isFile) null else runCatching { TaskFileParser().parseGroupConfig(file.readText()).repo() }.getOrNull()
    }
}
