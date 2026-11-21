package com.contextswitcher.android.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
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
            if (remoteCommand.firstOrNull() == "grep") {
                return SshResult(0, """{"type":"assistant","message":{"content":[{"type":"text","text":"All tests pass."}]}}""", "")
            }
            if (remoteCommand.firstOrNull() == "tmux") {
                lookups.add(remoteCommand)
                return SshResult(0, "@17\n", "")
            }
            return SshResult(0, "@17|${statuses.removeFirst()}|3|opus|high|abc|claude\n@18|working|1||||claude\n" +
                (if (updatePending) "update|@17\n" else ""), "")
        }

        override fun runWithInput(host: String, remoteCommand: List<String>, input: ByteArray) = run(host, remoteCommand)
    }

    private fun task(window: String, claude: Task.ClaudeConfig? = null) = Task("jabref/fix-npe", "Fix the NPE", TaskStatus.ACTIVE, "koppor@example.org",
        Task.TmuxConfig("jabref", window), null, null, null, claude, null, emptyList(), emptyList(), emptyList(), null, null, "")

    private fun alerts(): List<Notification> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
            .filter { it.channelId == TaskWatchService.CHANNEL_ALERT }

    /// What the car fires: the alert's reply action with `spoken` as the dictated text.
    private fun replyIntent(alert: Notification, spoken: String): Intent {
        val reply = NotificationCompat.getInvisibleActions(alert)
            .single { it.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY }
        return Intent(shadowOf(reply.actionIntent).savedIntent).also {
            RemoteInput.addResultsToIntent(reply.remoteInputs!!, it, Bundle().apply { putCharSequence(TaskWatchService.KEY_REPLY, spoken) })
        }
    }

    private fun <T> sendingThrough(send: (String, String) -> String?, block: () -> T): T {
        TaskWatchService.messageSender = { _, ssh ->
            object : com.contextswitcher.queue.MessageSender(ssh, context.filesDir.toPath()) {
                override fun send(remote: String, target: String, text: String): String? = send(target, text)
            }
        }
        try {
            return block()
        } finally {
            TaskWatchService.messageSender = { c, ssh -> com.contextswitcher.queue.MessageSender(ssh, java.io.File(c.filesDir, "attachments").toPath()) }
        }
    }

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

    /// "title: text" per alert; a finished one is a message, so its title is the task and its text Claude's answer.
    private fun alertTitles(): List<String> =
        alerts().map { "${it.extras.getString(Notification.EXTRA_TITLE)}: ${it.extras.getString(Notification.EXTRA_TEXT)}" }

    @Test
    fun `a finished turn raises an alert naming the task`() {
        val service = watch(task("@17"), Remote("working", "working", "waiting"))

        repeat(3) { service.pollOnce() }

        assertThat(alertTitles()).containsExactly("Fix the NPE: Claude finished.")
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

        assertThat(alertTitles()).containsExactly("Fix the NPE: Claude finished.")
        assertThat(remote.lookups).hasSize(1)
        assertThat(remote.lookups[0]).contains("'jabref:claude'")
    }

    // [utest->dsn~android-claude-restart~1]
    @Test
    fun `the alert says when a Claude update waits for a restart`() {
        val service = watch(task("@17"), Remote("working", "waiting", updatePending = true))

        repeat(2) { service.pollOnce() }

        assertThat(alerts().single().extras.getString(Notification.EXTRA_SUB_TEXT))
            .isEqualTo("Claude finished · update pending: Restart Claude")
    }

    // [utest->dsn~android-auto-reply~1]
    @Test
    fun `the finished alert is a message from the task with Claude's answer and the car's two actions`() {
        val service = watch(task("@17", Task.ClaudeConfig("/home/koppor/jabref", "stale", null)), Remote("working", "waiting"))

        repeat(2) { service.pollOnce() }

        val alert = alerts().single()
        val message = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(alert)!!.messages.single()
        assertThat(message.person?.name).isEqualTo("Fix the NPE")
        assertThat(message.text).isEqualTo("All tests pass.")
        assertThat(NotificationCompat.getInvisibleActions(alert).map { it.semanticAction }).containsExactly(
            NotificationCompat.Action.SEMANTIC_ACTION_REPLY, NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
        assertThat(NotificationCompat.getInvisibleActions(alert)).allMatch { !it.showsUserInterface }
    }

    // [utest->dsn~android-auto-reply~1]
    @Test
    fun `a question mid-turn offers no reply`() {
        val service = watch(task("@17"), Remote("working", "attention"))

        repeat(2) { service.pollOnce() }

        assertThat(NotificationCompat.getInvisibleActions(alerts().single())).isEmpty()
    }

    // [utest->dsn~android-auto-reply~1]
    @Test
    fun `a spoken reply goes into the task's window behind the voice hint and clears the alert`() {
        val sent = mutableListOf<String>()
        sendingThrough({ target, text -> sent += "$target:$text"; null }) {
            val service = watch(task("claude"), Remote("working", "waiting"))
            repeat(2) { service.pollOnce() }

            service.handleReply(replyIntent(alerts().single(), "merge it"))

            assertThat(sent).containsExactly("@17:" + com.contextswitcher.android.voice.VoiceTour.SPOKEN_REPLY_HINT + "merge it")
            assertThat(alerts()).isEmpty()
        }
    }

    // [utest->dsn~android-auto-reply~1]
    @Test
    fun `a reply that cannot be sent is kept in the phone's queue`() {
        sendingThrough({ _, _ -> "no route to host" }) {
            val service = watch(task("@17"), Remote("working", "waiting"))
            repeat(2) { service.pollOnce() }

            service.handleReply(replyIntent(alerts().single(), "merge it"))

            assertThat(TaskWatchService.enqueuedStore(context).load("jabref/fix-npe")).hasSize(1)
            assertThat(alertTitles()).containsExactly("Reply not sent, kept on the phone: no route to host")
        }
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
