package com.contextswitcher.android.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.ssh.SshCommandRunner.SshResult
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/// The watch service turns the remote's status readings into the alert the
/// user sees, on the real notification path.
// [utest->dsn~android-finish-notification~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskWatchServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /// Answers the status query with the next scripted `@cs_status` of window
    /// `@17`, and a window-name lookup with `@17`.
    private class Remote(vararg statuses: String, val updatePending: Boolean = false) : SshCommandRunner {
        val statuses = ArrayDeque(statuses.toList())
        val lookups = mutableListOf<List<String>>()

        override fun run(host: String, remoteCommand: List<String>): SshResult {
            if (remoteCommand.firstOrNull() == "tmux") {
                lookups.add(remoteCommand)
                return SshResult(0, "@17\n", "")
            }
            return SshResult(0, "@17|${statuses.removeFirst()}|3|opus|high|abc|claude\n@18|working|1||||claude\n" +
                (if (updatePending) "update|@17\n" else ""), "")
        }

        override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray) = run(host, remoteCommand)
    }

    private fun task(window: String) = Task("jabref/fix-npe", "Fix the NPE", TaskStatus.ACTIVE, "koppor@example.org",
        Task.TmuxConfig("jabref", window), null, null, null, null, null, emptyList(), emptyList(), emptyList(), null, null, "")

    @Before
    fun setUp() {
        TaskWatchService.autoPoll = false
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        TaskWatchService.autoPoll = true
        TaskWatchService.appStarted = false
        TaskWatchService.taskOnScreen = null
    }

    private fun watch(task: Task, remote: Remote): TaskWatchService {
        TaskWatchService.sshFactory = { remote }
        return Robolectric.buildService(TaskWatchService::class.java, TaskWatchService.intentFor(context, task))
            .create().startCommand(0, 1).get()
    }

    private fun alertTitles(): List<String> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
            .filter { it.channelId == TaskWatchService.CHANNEL_ALERT }
            .map { "${it.extras.getString(Notification.EXTRA_TITLE)}: ${it.extras.getString(Notification.EXTRA_TEXT)}" }

    @Test
    fun `a finished turn raises an alert naming the task`() {
        val service = watch(task("@17"), Remote("working", "working", "waiting"))

        repeat(3) { service.pollOnce() }

        assertThat(alertTitles()).containsExactly("Claude finished: Fix the NPE")
    }

    @Test
    fun `a question mid-turn alerts that Claude needs the user`() {
        val service = watch(task("@17"), Remote("working", "attention"))

        repeat(2) { service.pollOnce() }

        assertThat(alertTitles()).containsExactly("Claude needs you: Fix the NPE")
    }

    @Test
    fun `no alert for the task the user is looking at`() {
        TaskWatchService.appStarted = true
        TaskWatchService.taskOnScreen = "jabref/fix-npe"
        val service = watch(task("@17"), Remote("working", "waiting"))

        repeat(2) { service.pollOnce() }

        assertThat(alertTitles()).isEmpty()
    }

    @Test
    fun `a window recorded by name is looked up once`() {
        val remote = Remote("working", "done")
        val service = watch(task("claude"), remote)

        repeat(2) { service.pollOnce() }

        assertThat(alertTitles()).containsExactly("Claude finished: Fix the NPE")
        assertThat(remote.lookups).hasSize(1)
        assertThat(remote.lookups[0]).contains("'jabref:claude'")
    }

    // [utest->dsn~android-claude-restart~1]
    @Test
    fun `the alert says when a Claude update waits for a restart`() {
        val service = watch(task("@17"), Remote("working", "waiting", updatePending = true))

        repeat(2) { service.pollOnce() }

        assertThat(alertTitles()).containsExactly("Claude finished: Fix the NPE · update pending: Restart Claude")
    }

    // [utest->dsn~android-enqueue~1]
    @Test
    fun `enqueued messages go out one per idle turn and leave the phone queue`() {
        val store = TaskWatchService.enqueuedStore(context)
        store.add("jabref/fix-npe", "first")
        store.add("jabref/fix-npe", "second")
        val sent = mutableListOf<String>()
        TaskWatchService.messageSender = { _, ssh ->
            object : com.contextswitcher.queue.MessageSender(ssh, context.filesDir.toPath()) {
                override fun send(remote: String, target: String, text: String): String? {
                    sent += "$target:$text"
                    return null
                }
            }
        }
        try {
            val service = watch(task("@17"), Remote("working", "waiting", "waiting", "working", "waiting"))

            repeat(3) { service.pollOnce() }
            assertThat(sent).containsExactly("@17:first")
            repeat(2) { service.pollOnce() }

            assertThat(sent).containsExactly("@17:first", "@17:second")
            assertThat(store.load("jabref/fix-npe")).isEmpty()
        } finally {
            TaskWatchService.messageSender = { c, ssh -> com.contextswitcher.queue.MessageSender(ssh, java.io.File(c.filesDir, "attachments").toPath()) }
        }
    }
}
