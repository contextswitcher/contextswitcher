package com.contextswitcher.android.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.contextswitcher.android.MainActivity
import com.contextswitcher.android.R
import com.contextswitcher.android.provenance.PhoneProvenance
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.android.settings.SettingsStore
import com.contextswitcher.android.ssh.KnownHostsStore
import com.contextswitcher.android.ssh.SshjCommandRunner
import com.contextswitcher.android.voice.VoiceTour
import com.contextswitcher.discovery.ClaudeTranscript
import com.contextswitcher.discovery.TmuxStatusPoller
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.Task
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/// Watches the task opened last — its Claude session's `@cs_status` on the
/// remote — and notifies when a turn ends ([FinishWatch]), also while the app
/// is in the background. A foreground service, because Android stops plain
/// background polling within minutes; its required ongoing notification says
/// what is watched and offers *Stop*.
///
/// Polls the same read-only query as the desktop's running indicator
/// ([TmuxStatusPoller.statusCommand]) every [POLL_SECONDS] over its own SSH
/// connection. Never the desktop's whole poller: that one also types into
/// windows (auto-`continue`, update restarts), which must not happen twice.
// [impl->dsn~android-finish-notification~1]
class TaskWatchService : Service() {

    private data class Watched(val taskId: String, val title: String, val remote: String, val tmux: Task.TmuxConfig,
        val claudeCwd: String?, val sessionId: String?)

    private var watched: Watched? = null
    private var windowId: String? = null
    private var finishWatch = FinishWatch()
    private var delivery = EnqueuedDelivery()
    private var poller: ScheduledExecutorService? = null
    private val ssh: SshCommandRunner by lazy { sshFactory(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REPLY || intent?.action == ACTION_READ) {
            // Off the main thread (ssh); a service started only for this ends with it.
            thread(name = "alert-reply") {
                handleReply(intent)
                if (watched == null) stopSelf()
            }
            return START_NOT_STICKY
        }
        val next = intent?.let(::watchedOf)
        if (next == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannels(this)
        ServiceCompat.startForeground(this, ONGOING_ID, ongoing(next, "starting"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (next != watched) {
            poller?.shutdownNow()
            synchronized(this) {
                watched = next
                windowId = next.tmux.window?.takeIf { it.startsWith("@") }
                finishWatch = FinishWatch()
                delivery = EnqueuedDelivery()
            }
            if (autoPoll) {
                poller = Executors.newSingleThreadScheduledExecutor().also {
                    it.scheduleWithFixedDelay(::pollOnce, 0, POLL_SECONDS, TimeUnit.SECONDS)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        poller?.shutdownNow()
        super.onDestroy()
    }

    /// One poll: read the statuses, update the ongoing notification, alert on
    /// a finished turn. Synchronized — the scheduler thread and tests call it.
    @Synchronized
    internal fun pollOnce() {
        val task = watched ?: return
        try {
            val window = windowId ?: resolveWindowId(task)?.also { windowId = it }
            if (window == null) {
                showOngoing(task, "cannot find the tmux window")
                return
            }
            val result = ssh.run(task.remote, TmuxStatusPoller.statusCommand())
            if (!result.ok()) {
                showOngoing(task, "cannot reach ${task.remote}")
                return
            }
            val statuses = HashMap<String, String>()
            TmuxStatusPoller.parseInto(task.remote, result.stdout(), statuses, IDLE_THRESHOLD_SECONDS)
            val status = statuses[TmuxStatusPoller.key(task.remote, window)]
            showOngoing(task, status ?: "no status")
            val updatePending = window in TmuxStatusPoller.parseUpdatePending(result.stdout())
            finishWatch.update(status)?.let { alert ->
                val sessions = HashMap<String, String>()
                TmuxStatusPoller.parseSessionIdsInto(task.remote, result.stdout(), sessions)
                alert(task, window, alert, updatePending, sessions[TmuxStatusPoller.key(task.remote, window)] ?: task.sessionId)
            }
            sendEnqueued(task, window, status)
        } catch (e: RuntimeException) {
            // The scheduler silently stops a task whose run throws; keep polling.
            showOngoing(task, "error: ${e.message}")
        }
    }

    /// Sends the task's oldest message enqueued on the phone once Claude is
    /// idle ([EnqueuedDelivery]); a failure keeps it and says so.
    // [impl->dsn~android-enqueue~1]
    private fun sendEnqueued(task: Watched, window: String, status: String?) {
        val store = enqueuedStore(this)
        val next = store.load(task.taskId).firstOrNull() ?: return
        if (!delivery.ready(status)) {
            return
        }
        delivery.delivered()
        val error = messageSender(this, ssh).send(task.remote, window, next)
        if (error == null) {
            store.remove(task.taskId, next)
        } else {
            post(task.taskId, ALERT_ID, NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Enqueued message not sent")
                .setContentText("${task.title}: $error")
                .setAutoCancel(true)
                .setContentIntent(openTask(this, task.taskId))
                .build())
        }
    }

    /// A task naming its window (`session:name`) instead of recording its id.
    private fun resolveWindowId(task: Watched): String? {
        val result = ssh.run(task.remote, listOf("tmux", "display-message", "-p", "-t",
            SshCommandRunner.quote(task.tmux.target()), SshCommandRunner.quote("#{window_id}")))
        return result.stdout().trim().takeIf { result.ok() && it.startsWith("@") }
    }

    private fun showOngoing(task: Watched, state: String) = post(null, ONGOING_ID, ongoing(task, state))

    /// Posts unless notifications are denied; ContextCompat answers for
    /// Android versions before the permission existed, too.
    private fun post(tag: String?, id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        NotificationManagerCompat.from(this).notify(tag, id, notification)
    }

    private fun ongoing(task: Watched, state: String) =
        NotificationCompat.Builder(this, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Watching: ${task.title}")
            .setContentText("$state · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openTask(this, task.taskId))
            .addAction(0, "Stop", PendingIntent.getService(this, 0,
                Intent(this, TaskWatchService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .build()

    private fun alert(task: Watched, window: String, alert: Alert, updatePending: Boolean, sessionId: String?) {
        if (appStarted && taskOnScreen == task.taskId) {
            return
        }
        // [impl->dsn~android-claude-restart~1]
        val update = if (updatePending) " · update pending: Restart Claude" else ""
        val builder = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(alert.title)
            .setContentText(task.title + update)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openTask(this, task.taskId))
        if (alert == Alert.FINISHED) {
            asMessage(builder, task, window, alert.title + update, sessionId)
        }
        post(task.taskId, ALERT_ID, builder.build())
    }

    /// Turns the finished alert into a message from the task: Claude's last
    /// answer as the text, which Android Auto reads out, with the reply and
    /// mark-as-read actions it asks of a messaging notification. Both are
    /// invisible on the phone, so a reply is always a spoken one. Only for a
    /// finished turn — a reply typed into a permission prompt would pick an option.
    // [impl->dsn~android-auto-reply~1]
    private fun asMessage(builder: NotificationCompat.Builder, task: Watched, window: String, header: String, sessionId: String?) {
        val answer = if (task.claudeCwd == null || sessionId == null) null
            else runCatching { ClaudeTranscript(ssh).lastAnswer(task.remote, task.claudeCwd, sessionId) }.getOrNull()
        fun action(action: String, flags: Int) = PendingIntent.getService(this, task.taskId.hashCode(),
            Intent(this, TaskWatchService::class.java).setAction(action)
                .putExtra(EXTRA_TASK_ID, task.taskId).putExtra(EXTRA_REMOTE, task.remote).putExtra(EXTRA_WINDOW, window),
            flags or PendingIntent.FLAG_UPDATE_CURRENT)
        builder
            .setSubText(header)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // ponytail: the answer as written, Markdown included, cut at one TTS utterance; strip the marks if the car reads them out.
            .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
                .addMessage((answer ?: "Claude finished.").take(MAX_SPOKEN_CHARS), System.currentTimeMillis(),
                    Person.Builder().setName(task.title).build()))
            // Mutable: Android Auto writes the dictated text into the intent.
            .addInvisibleAction(NotificationCompat.Action.Builder(0, "Reply", action(ACTION_REPLY, PendingIntent.FLAG_MUTABLE))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build())
                .build())
            .addInvisibleAction(NotificationCompat.Action.Builder(0, "Mark as read", action(ACTION_READ, PendingIntent.FLAG_IMMUTABLE))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build())
    }

    /// The alert's reply or mark-as-read action: the alert goes, and a reply
    /// is sent into the task's window behind the spoken-answer hint — Claude
    /// is idle, the alert said so. A reply that cannot be sent joins the
    /// phone's enqueued messages. Synchronized with the poll: one ssh runner.
    // [impl->dsn~android-auto-reply~1]
    @Synchronized
    internal fun handleReply(intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        NotificationManagerCompat.from(this).cancel(taskId, ALERT_ID)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
        if (intent.action != ACTION_REPLY || text.isNullOrBlank()) {
            return
        }
        val message = VoiceTour.spokenReply(text)
        val error = runCatching {
            messageSender(this, ssh).send(intent.getStringExtra(EXTRA_REMOTE)!!, intent.getStringExtra(EXTRA_WINDOW)!!, message)
        }.getOrElse { it.message ?: it.toString() } ?: return
        runCatching { enqueuedStore(this).add(taskId, message) }
        post(taskId, ALERT_ID, NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Reply not sent, kept on the phone")
            .setContentText(error)
            .setAutoCancel(true)
            .setContentIntent(openTask(this, taskId))
            .build())
    }

    companion object {
        const val EXTRA_TASK_ID = "com.contextswitcher.android.TASK_ID"
        internal const val CHANNEL_WATCH = "watch"
        internal const val CHANNEL_ALERT = "claude-finished"
        internal const val ONGOING_ID = 1
        internal const val ALERT_ID = 2
        private const val ACTION_STOP = "com.contextswitcher.android.watch.STOP"
        private const val ACTION_REPLY = "com.contextswitcher.android.watch.REPLY"
        private const val ACTION_READ = "com.contextswitcher.android.watch.READ"
        internal const val KEY_REPLY = "reply"
        /// What one text-to-speech utterance takes (`TextToSpeech.getMaxSpeechInputLength`).
        private const val MAX_SPOKEN_CHARS = 4000
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_REMOTE = "remote"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_WINDOW = "window"
        private const val EXTRA_CWD = "claudeCwd"
        private const val EXTRA_SESSION_ID = "claudeSessionId"
        private const val POLL_SECONDS = 10L

        /// How long a `working` window may stay silent before it counts as
        /// finished — far above the desktop's indicator threshold, since a
        /// quiet build step must not raise "Claude finished"; it only matters
        /// when the `Stop` hook did not fire.
        internal const val IDLE_THRESHOLD_SECONDS = 120L

        /// Whether the app is on screen, and which task's detail it shows:
        /// no alert for the task the user is looking at.
        @Volatile var appStarted = false
        @Volatile var taskOnScreen: String? = null

        /// Test seams: the SSH runner, and whether a scheduler polls at all.
        @Volatile internal var sshFactory: (Context) -> SshCommandRunner = { context ->
            SshjCommandRunner({ SettingsStore(context).loadSshPrivateKey() },
                KnownHostsStore(File(context.filesDir, "known_hosts.properties")),
                jumpHosts = { SettingsStore(context).loadJumpHosts() })
        }
        @Volatile internal var autoPoll = true

        /// Messages enqueued on the phone per task, sent by the watch when Claude is idle.
        fun enqueuedStore(context: Context) = DraftStore(File(context.filesDir, "enqueued").toPath())

        @Volatile internal var messageSender: (Context, SshCommandRunner) -> MessageSender = { context, ssh ->
            MessageSender(ssh, File(context.filesDir, "attachments").toPath())
                .also { PhoneProvenance.install(context, it, ssh) }
        }

        /// Watches `task` from now on, replacing any earlier watch; a task
        /// without a remote tmux window is not watchable and changes nothing.
        /// Call while the app is visible — Android refuses to start a
        /// foreground service from the background.
        fun start(context: Context, task: Task) {
            intentFor(context, task)?.let { ContextCompat.startForegroundService(context, it) }
        }

        internal fun intentFor(context: Context, task: Task): Intent? {
            val tmux = task.tmux() ?: return null
            val remote = task.remote() ?: return null
            return Intent(context, TaskWatchService::class.java)
                .putExtra(EXTRA_TASK_ID, task.id())
                .putExtra(EXTRA_TITLE, task.title())
                .putExtra(EXTRA_REMOTE, remote)
                .putExtra(EXTRA_SESSION, tmux.session())
                .putExtra(EXTRA_WINDOW, tmux.window())
                .putExtra(EXTRA_CWD, task.claude()?.cwd())
                .putExtra(EXTRA_SESSION_ID, task.claude()?.sessionId())
        }

        private fun watchedOf(intent: Intent): Watched? {
            val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return null
            val remote = intent.getStringExtra(EXTRA_REMOTE) ?: return null
            val session = intent.getStringExtra(EXTRA_SESSION) ?: return null
            return Watched(id, intent.getStringExtra(EXTRA_TITLE) ?: id, remote,
                Task.TmuxConfig(session, intent.getStringExtra(EXTRA_WINDOW)),
                intent.getStringExtra(EXTRA_CWD), intent.getStringExtra(EXTRA_SESSION_ID))
        }

        private fun openTask(context: Context, taskId: String): PendingIntent =
            PendingIntent.getActivity(context, taskId.hashCode(),
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun createChannels(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
                NotificationChannel(CHANNEL_WATCH, "Watching a task", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_ALERT, "Claude finished", NotificationManager.IMPORTANCE_HIGH),
            ))
        }
    }
}
