package com.contextswitcher.android.queue

import com.contextswitcher.queue.QueueFile
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/// Messages kept on the phone for a task, to send later — the phone's stand-in
/// for the desktop's queue, which it cannot write: the synced task repo is a
/// hard-reset mirror (MADR 0028), so a queue file edited there would be gone
/// with the next sync. One file per task in the desktop queue's YAML list
/// format ([QueueFile]), under app-private storage; never synced, so the
/// desktop does not see these drafts.
///
/// Also holds the text still in a task's message box, so an app restart —
/// an update replacing the APK, a force stop — never loses what was typed.
// [impl->dsn~android-message-drafts~3]
class DraftStore(private val dir: Path) {

    fun load(taskId: String): List<String> = QueueFile.load(QueueFile.file(dir, taskId))

    /// Appends `text` and returns the task's drafts. Blank text and a text
    /// already kept are not added twice — a draft is kept whenever the compose
    /// box loses focus, so the same text can arrive more than once.
    fun add(taskId: String, text: String): List<String> {
        val drafts = load(taskId)
        if (text.isBlank() || text in drafts) {
            return drafts
        }
        return (drafts + text).also { QueueFile.save(QueueFile.file(dir, taskId), it) }
    }

    /// Removes the first draft equal to `text` and returns the task's drafts.
    fun remove(taskId: String, text: String): List<String> {
        val drafts = load(taskId).toMutableList()
        if (drafts.remove(text)) {
            QueueFile.save(QueueFile.file(dir, taskId), drafts)
        }
        return drafts
    }

    /// The text left in the task's message box, `""` when there is none.
    fun loadUnsent(taskId: String): String =
        unsentFile(taskId).let { if (it.exists()) it.readText() else "" }

    /// Stores the message box's current text; a blank box removes the file.
    fun saveUnsent(taskId: String, text: String) {
        val file = unsentFile(taskId)
        if (text.isBlank()) {
            file.deleteIfExists()
        } else {
            file.parent.createDirectories()
            file.writeText(text)
        }
    }

    private fun unsentFile(taskId: String): Path = dir.resolve(taskId.replace("/", "__") + ".unsent.txt")
}
