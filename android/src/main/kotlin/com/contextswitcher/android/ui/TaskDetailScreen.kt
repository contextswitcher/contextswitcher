package com.contextswitcher.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.android.watch.TaskWatchService
import com.contextswitcher.discovery.TmuxDiscovery
import com.contextswitcher.discovery.TmuxSync
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.queue.QueueFile
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.Task
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/// From this window width on (Material's "expanded" size class, e.g. a tablet
/// in landscape) the app shows side by side what a phone pages through.
val EXPANDED_WIDTH: Dp = 840.dp

/// Tapping a task row (main list) opens this: the desktop's terminal/queue
/// columns as swipeable pager pages (side by side from [EXPANDED_WIDTH] on), system back returns to the list. The
/// task's effective remote and tmux target are [Task.remote] and
/// [Task.TmuxConfig.target] directly — unlike `intellij`/`browser`/
/// `folders`, a category's `CONTEXTSWITCHER.md` never supplies defaults for
/// `remote`/`tmux` ([Task#withCategoryDefaults]), so there is no resolution
/// helper to call here beyond the task's own fields.
// [impl->dsn~android-terminal-snapshot~3]
// [impl->dsn~android-queue-send~2]
// [impl->dsn~android-finish-notification~1]
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    queuesDir: Path,
    draftStore: DraftStore,
    ssh: SshCommandRunner,
    messageSender: MessageSender,
    onBack: () -> Unit,
    deleteQueuedMessage: ((String) -> String?)? = null,
    attachments: com.contextswitcher.android.queue.AttachmentStore? = null,
) {
    BackHandler(onBack = onBack)

    // The synced task file may name a window the desktop has replaced since
    // its last push (resume, restart: same Claude session, new window id).
    // [impl->dsn~android-window-lookup~1]
    var liveTask by remember(task.id()) { mutableStateOf(task) }
    val tmux = liveTask.tmux()
    val remote = liveTask.remote()
    val titles = listOf("Terminal", "Queue")
    val pagerState = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var queuedMessages by remember(task.id()) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(task.id()) {
        val synced = task.tmux()?.window()
        val host = task.remote()
        if (synced == null || !synced.startsWith("@") || host == null) {
            return@LaunchedEffect
        }
        val current = withContext(Dispatchers.IO) {
            runCatching { TmuxSync.currentWindowId(task, TmuxDiscovery(ssh).listSessions(host)) }.getOrNull()
        }
        if (current != null && current != synced) {
            liveTask = task.withTmuxWindow(current)
            snackbarHostState.showSnackbar("Window $synced is gone; using $current (the desktop has not pushed it yet)")
        }
    }

    // Opening a task makes it the watched one: its Claude session's finished
    // turn raises a notification, also once the app is in the background.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("Notifications are off: no alert when Claude finishes") }
        }
    }
    LaunchedEffect(task.id(), tmux) {
        if (tmux != null && remote != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            TaskWatchService.start(context, liveTask)
        }
    }
    DisposableEffect(task.id()) {
        TaskWatchService.taskOnScreen = task.id()
        onDispose { TaskWatchService.taskOnScreen = null }
    }

    LaunchedEffect(task.id()) {
        queuedMessages = withContext(Dispatchers.IO) {
            QueueFile.load(QueueFile.file(queuesDir, task.id()))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(task.title()) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        val terminal: @Composable (Modifier) -> Unit = { modifier ->
            if (tmux != null && remote != null) {
                key(tmux.window()) {
                    TerminalPage(liveTask, tmux, remote, ssh, snackbarHostState, modifier = modifier)
                }
            } else {
                NoLiveSessionPage(modifier = modifier)
            }
        }
        val queue: @Composable (Modifier) -> Unit = { modifier ->
            QueuePage(
                task = task,
                queuedMessages = queuedMessages,
                draftStore = draftStore,
                attachments = attachments,
                // Only a task with a window can be watched, and the watch sends them.
                enqueuedStore = if (tmux != null && remote != null) TaskWatchService.enqueuedStore(context) else null,
                send = { text ->
                    if (tmux == null || remote == null) {
                        "No remote tmux window configured for this task."
                    } else {
                        messageSender.send(remote, tmux.target(), text)
                    }
                },
                snackbarHostState = snackbarHostState,
                modifier = modifier,
                deleteQueued = deleteQueuedMessage?.let { delete ->
                    { message ->
                        delete(message).also {
                            // Either way the clone now mirrors the remote's queue.
                            queuedMessages = QueueFile.load(QueueFile.file(queuesDir, task.id()))
                        }
                    }
                },
            )
        }
        // Consumed, so the queue page's keyboard padding does not add the
        // navigation bar height a second time.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (maxWidth >= EXPANDED_WIDTH) {
                // [impl->dsn~android-large-screen~1]
                Row(modifier = Modifier.fillMaxSize()) {
                    terminal(Modifier.weight(0.6f).fillMaxHeight())
                    VerticalDivider()
                    queue(Modifier.weight(0.4f).fillMaxHeight())
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                        titles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title) },
                            )
                        }
                    }
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        if (page == 0) terminal(Modifier.fillMaxSize()) else queue(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
