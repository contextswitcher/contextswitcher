package com.contextswitcher.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextOverflow
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
    queueOnDesktop: ((String) -> String?)? = null,
    attachments: com.contextswitcher.android.queue.AttachmentStore? = null,
    previousTask: Task? = null,
    nextTask: Task? = null,
    startAtQueue: Boolean = false,
    onOpenTask: (task: Task, atQueue: Boolean) -> Unit = { _, _ -> },
) {
    BackHandler(onBack = onBack)

    // The synced task file may name a window the desktop has replaced since
    // its last push (resume, restart: same Claude session, new window id).
    // [impl->dsn~android-window-lookup~1]
    var liveTask by remember(task.id()) { mutableStateOf(task) }
    val tmux = liveTask.tmux()
    val remote = liveTask.remote()
    val titles = listOf("Terminal", "Queue")
    // Swiping past Terminal or Queue lands on a page naming the neighbouring
    // task in the list, which then opens: paging quickly through the tasks.
    // [impl->dsn~android-task-swipe~1]
    val firstPage = if (previousTask != null) 1 else 0
    val pageCount = firstPage + titles.size + (if (nextTask != null) 1 else 0)
    val pagerState = rememberPagerState(initialPage = firstPage + if (startAtQueue) 1 else 0, pageCount = { pageCount })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            when (page - firstPage) {
                -1 -> previousTask?.let { onOpenTask(it, true) }
                titles.size -> nextTask?.let { onOpenTask(it, false) }
            }
        }
    }
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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
            // Each return to the foreground: a start the background refused is retried.
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                TaskWatchService.start(context, liveTask)
            }
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
                // A long title (a pasted log line) would push the content off screen:
                // two lines, a tap shows all of it, scrollable.
                title = {
                    var expanded by remember(task.id()) { mutableStateOf(false) }
                    Text(
                        task.title(),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .then(if (expanded) Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()) else Modifier),
                    )
                },
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
                transferToDesktop = queueOnDesktop?.let { queue ->
                    { message ->
                        queue(message).also {
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
                    val tab = (pagerState.currentPage - firstPage).coerceIn(0, titles.size - 1)
                    PrimaryTabRow(selectedTabIndex = tab) {
                        titles.forEachIndexed { index, title ->
                            Tab(
                                selected = tab == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(firstPage + index) } },
                                text = { Text(title) },
                            )
                        }
                    }
                    // One page beyond the viewport stays composed, so the terminal keeps
                    // its loaded output and reading position while the queue is shown.
                    HorizontalPager(state = pagerState, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                        when (page - firstPage) {
                            -1 -> NeighbourPage("Previous task", previousTask)
                            0 -> terminal(Modifier.fillMaxSize())
                            1 -> queue(Modifier.fillMaxSize())
                            else -> NeighbourPage("Next task", nextTask)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeighbourPage(label: String, task: Task?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(task?.title() ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
}
