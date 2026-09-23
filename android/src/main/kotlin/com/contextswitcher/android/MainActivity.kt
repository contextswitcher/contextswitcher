@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.contextswitcher.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contextswitcher.android.provenance.PhoneProvenance
import com.contextswitcher.android.queue.AttachmentStore
import com.contextswitcher.android.queue.DraftStore
import com.contextswitcher.android.settings.RepoSettings
import com.contextswitcher.android.settings.SettingsStore
import com.contextswitcher.android.ssh.KnownHostsStore
import com.contextswitcher.android.ssh.SshjCommandRunner
import com.contextswitcher.android.sync.CreateResult
import com.contextswitcher.android.sync.LiveTaskStarter
import com.contextswitcher.android.sync.SyncResult
import com.contextswitcher.android.sync.TaskRepoSync
import com.contextswitcher.android.ui.AddTaskDialog
import com.contextswitcher.android.ui.BuildCommitLine
import com.contextswitcher.android.ui.EXPANDED_WIDTH
import com.contextswitcher.android.ui.TaskDetailScreen
import com.contextswitcher.android.ui.TaskGroupUi
import com.contextswitcher.android.ui.TaskListBody
import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.android.update.ApkInstaller
import com.contextswitcher.android.update.AppUpdates
import com.contextswitcher.android.watch.TaskWatchService
import com.contextswitcher.queue.ClaudeMode
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.tasks.Task
import com.contextswitcher.tasks.TaskEntry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface Screen {
    data object Settings : Screen
    data object TaskList : Screen
    data class TaskDetail(val task: Task) : Screen
}

/// Saves the open screen across Android killing the backgrounded app. A
/// [Screen.TaskDetail] is saved as its task id and re-read from the synced
/// repo on restore; a task gone from the repo meanwhile falls back to the list.
internal fun screenSaver(repoDir: File): Saver<Screen, String> = Saver(
    save = {
        when (it) {
            Screen.Settings -> "settings"
            Screen.TaskList -> "tasklist"
            is Screen.TaskDetail -> "task:" + it.task.id()
        }
    },
    restore = { saved ->
        when {
            saved == "settings" -> Screen.Settings
            saved.startsWith("task:") -> findTask(repoDir, saved.removePrefix("task:"))?.let { Screen.TaskDetail(it) } ?: Screen.TaskList
            else -> Screen.TaskList
        }
    },
)

private fun findTask(repoDir: File, id: String): Task? =
    try {
        buildTaskListModel(repoDir.toPath()).asSequence()
            .flatMap { it.entries }
            .filterIsInstance<TaskEntry.Loaded>()
            .map { it.task() }
            .firstOrNull { it.id() == id }
    } catch (e: IOException) {
        null
    }

class MainActivity : ComponentActivity() {
    /// The task a tapped notification asks to open; consumed by the screen flow.
    private val openTaskRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTaskRequest.value = intent?.getStringExtra(TaskWatchService.EXTRA_TASK_ID)
        val settingsStore = SettingsStore(applicationContext)
        val repoDir = File(filesDir, "taskrepo")
        val knownHosts = KnownHostsStore(File(filesDir, "known_hosts.properties"))
        val ssh = SshjCommandRunner({ settingsStore.loadSshPrivateKey() }, knownHosts)
        val messageSender = MessageSender(ssh, File(filesDir, "attachments").toPath())
        PhoneProvenance.install(this, messageSender, ssh)
        val updates = AppUpdates({ settingsStore.load()?.effectiveUpdateToken.orEmpty() }, BuildConfig.GIT_COMMIT)
        setContent {
            MaterialTheme {
                Surface {
                    // The report first, alone: a crash at start would otherwise
                    // take it down again before it could be read.
                    LastCrashDialog {
                        ContextSwitcherApp(settingsStore, repoDir, knownHosts, ssh, messageSender, openTaskRequest, updates)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(TaskWatchService.EXTRA_TASK_ID)?.let { openTaskRequest.value = it }
    }

    override fun onStart() {
        super.onStart()
        TaskWatchService.appStarted = true
    }

    override fun onStop() {
        TaskWatchService.appStarted = false
        super.onStop()
    }
}

/// Owns the screen flow: no repo URL configured yet -> [Screen.Settings],
/// else [Screen.TaskList]; tapping a task row opens [Screen.TaskDetail] —
/// beside the list from [EXPANDED_WIDTH] on, instead of it on a phone.
/// Saving settings switches to the task list and triggers the first sync.
// [impl->dsn~android-task-list~1]
@Composable
private fun ContextSwitcherApp(
    settingsStore: SettingsStore,
    repoDir: File,
    knownHosts: KnownHostsStore,
    ssh: com.contextswitcher.ssh.SshCommandRunner,
    messageSender: MessageSender,
    openTaskRequest: androidx.compose.runtime.MutableState<String?> = mutableStateOf(null),
    updates: AppUpdates? = null,
) {
    // Up here, not in the list: on a phone an open task replaces the list,
    // and back must return to where the user left it, not to the top.
    val taskListState = rememberLazyListState()
    var screen by rememberSaveable(stateSaver = screenSaver(repoDir)) {
        mutableStateOf<Screen>(if (settingsStore.load() == null) Screen.Settings else Screen.TaskList)
    }
    // A tapped finish notification names its task; a task gone from the repo
    // meanwhile leaves the screen as it is.
    val requestedTaskId = openTaskRequest.value
    LaunchedEffect(requestedTaskId) {
        if (requestedTaskId != null) {
            withContext(Dispatchers.IO) { findTask(repoDir, requestedTaskId) }?.let { screen = Screen.TaskDetail(it) }
            openTaskRequest.value = null
        }
    }
    when (val current = screen) {
        Screen.Settings -> SettingsScreen(
            initial = settingsStore.load() ?: RepoSettings("", "", ""),
            initialSshKey = settingsStore.loadSshPrivateKey(),
            onSaved = { settings, sshKey ->
                settingsStore.save(settings)
                settingsStore.saveSshPrivateKey(sshKey)
                screen = Screen.TaskList
            },
            onClearKnownHosts = { knownHosts.clear() },
            // Nothing to go back to before the first save: back leaves the app then.
            onCancel = if (settingsStore.load() == null) null else ({ screen = Screen.TaskList }),
        )
        Screen.TaskList, is Screen.TaskDetail -> {
            val detailTask = (current as? Screen.TaskDetail)?.task
            val list: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier) {
                    TaskListScreen(
                        settingsStore = settingsStore,
                        repoDir = repoDir,
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenTask = { task -> screen = Screen.TaskDetail(task) },
                        updates = updates,
                        liveTasks = remember { LiveTaskStarter(TaskRepoSync(), ssh, messageSender) },
                        listState = taskListState,
                    )
                }
            }
            val detail: @Composable (Task) -> Unit = { task ->
                // Keyed, so switching tasks in the two-pane layout starts the
                // detail afresh, as reopening it from the phone's list does.
                key(task.id()) {
                    TaskDetailScreen(
                        task = task,
                        queuesDir = repoDir.toPath().resolve(".queues"),
                        draftStore = DraftStore(repoDir.parentFile.toPath().resolve("drafts")),
                        attachments = AttachmentStore(File(repoDir.parentFile, "attachments")),
                        ssh = ssh,
                        messageSender = messageSender,
                        onBack = { screen = Screen.TaskList },
                        deleteQueuedMessage = { message ->
                            settingsStore.load()?.let { settings ->
                                TaskRepoSync().deleteQueuedMessage(repoDir, settings.username, settings.token, task.id(), message)
                            } ?: "No task repo configured"
                        },
                    )
                }
            }
            BoxWithConstraints {
                if (maxWidth >= EXPANDED_WIDTH) {
                    // [impl->dsn~android-large-screen~1]
                    Row(modifier = Modifier.fillMaxSize()) {
                        list(Modifier.width(360.dp).fillMaxHeight())
                        VerticalDivider()
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (detailTask == null) Text("Select a task") else detail(detailTask)
                        }
                    }
                } else if (detailTask == null) {
                    list(Modifier.fillMaxSize())
                } else {
                    detail(detailTask)
                }
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    initial: RepoSettings,
    initialSshKey: String,
    onSaved: (RepoSettings, String) -> Unit,
    onClearKnownHosts: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    // System back discards the edits, like leaving without saving.
    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }
    var url by rememberSaveable { mutableStateOf(initial.url) }
    var username by rememberSaveable { mutableStateOf(initial.username) }
    var token by rememberSaveable { mutableStateOf(initial.token) }
    var updateToken by rememberSaveable { mutableStateOf(initial.updateToken) }
    var sshKey by rememberSaveable { mutableStateOf(initialSshKey) }
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(text = "Task repo settings", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Repo URL") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username (any value works for a GitHub PAT)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = updateToken,
            onValueChange = { updateToken = it },
            label = { Text("Update token (reads ContextSwitcher; blank: Token)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(text = "SSH settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
        OutlinedTextField(
            value = sshKey,
            onValueChange = { sshKey = it },
            label = { Text("SSH private key (OpenSSH format, no passphrase)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 4,
            // A pasted OpenSSH key is ~50 lines; capped, it scrolls inside the
            // field instead of pushing the save button off the screen.
            maxLines = 8,
        )
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(onClick = { onSaved(RepoSettings(url, username, token, updateToken), sshKey) }) {
                Text("Save + Sync now")
            }
            TextButton(onClick = onClearKnownHosts, modifier = Modifier.padding(start = 8.dp)) {
                Text("Clear known hosts")
            }
        }
        BuildCommitLine(modifier = Modifier.padding(top = 24.dp))
    }
}

/// The sync button is disabled for the round-trip (mirroring the desktop's
/// async-single-shot-button convention, CLAUDE.md) and a failure shows in a
/// Snackbar while the previously loaded `groups` stays on screen.
@Composable
internal fun TaskListScreen(
    settingsStore: SettingsStore,
    repoDir: File,
    onOpenSettings: () -> Unit,
    onOpenTask: (Task) -> Unit,
    updates: AppUpdates? = null,
    liveTasks: LiveTaskStarter? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    var groups by remember { mutableStateOf<List<TaskGroupUi>>(emptyList()) }
    var update by remember { mutableStateOf<AppUpdates.Check?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateFailureShown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var syncing by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var addingTask by rememberSaveable { mutableStateOf(false) }
    var taskBeingAdded by remember { mutableStateOf(false) }
    var addTaskError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // [impl->dsn~android-update-hint~3]
    fun checkForUpdate() {
        if (updates == null || settingsStore.load()?.effectiveUpdateToken.isNullOrBlank()) {
            return
        }
        scope.launch {
            val checked = withContext(Dispatchers.IO) { updates.check() }
            update = checked
            if (checked is AppUpdates.Check.Failed && !updateFailureShown) {
                updateFailureShown = true
                snackbarHostState.showSnackbar("Update check failed: ${checked.message}")
            }
        }
    }

    fun sync() {
        val settings = settingsStore.load() ?: return
        syncing = true
        lastError = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                TaskRepoSync().syncInto(repoDir, settings.url, settings.username, settings.token)
            }
            when (outcome) {
                is SyncResult.Failed -> {
                    lastError = "Sync failed: ${outcome.message}"
                    snackbarHostState.showSnackbar(lastError!!)
                }
                SyncResult.UpToDate, SyncResult.Updated -> {
                    // The task repo may just have brought provenance.yaml.
                    PhoneProvenance.syncSoon(context)
                    try {
                        groups = withContext(Dispatchers.IO) { buildTaskListModel(repoDir.toPath()) }
                    } catch (e: IOException) {
                        lastError = "Cannot read tasks: ${e.message}"
                        snackbarHostState.showSnackbar(lastError!!)
                    }
                }
            }
            syncing = false
            checkForUpdate()
        }
    }

    // [impl->dsn~android-task-create~1]
    fun addTask(category: String, title: String) {
        val settings = settingsStore.load() ?: return
        taskBeingAdded = true
        addTaskError = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                TaskRepoSync().createTask(repoDir, settings.username, settings.token, category, title)
            }
            taskBeingAdded = false
            when (outcome) {
                is CreateResult.Failed -> addTaskError = outcome.message
                is CreateResult.Created -> {
                    addingTask = false
                    try {
                        groups = withContext(Dispatchers.IO) { buildTaskListModel(repoDir.toPath()) }
                    } catch (e: IOException) {
                        lastError = "Cannot read tasks: ${e.message}"
                    }
                    snackbarHostState.showSnackbar("Task added")
                }
            }
        }
    }

    var addProgress by remember { mutableStateOf<String?>(null) }

    // [impl->dsn~android-live-task~1]
    fun startClaudeTask(category: String, title: String, mode: ClaudeMode, skipPermissions: Boolean) {
        val settings = settingsStore.load() ?: return
        val starter = liveTasks ?: return
        settingsStore.saveSkipPermissions(skipPermissions)
        taskBeingAdded = true
        addTaskError = null
        addProgress = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                starter.start(repoDir, settings.username, settings.token, category, title, mode, skipPermissions,
                    onStep = { addProgress = it },
                    onCreated = {
                        // The task is in the repo: the list can show it while Claude starts.
                        addingTask = false
                        groups = runCatching { buildTaskListModel(repoDir.toPath()) }.getOrDefault(groups)
                    })
            }
            taskBeingAdded = false
            addProgress = null
            when (outcome) {
                is LiveTaskStarter.Outcome.Started -> snackbarHostState.showSnackbar("Claude started in window ${outcome.windowId}")
                is LiveTaskStarter.Outcome.Failed ->
                    if (addingTask) addTaskError = outcome.message else snackbarHostState.showSnackbar(outcome.message)
            }
        }
    }

    fun installUpdate() {
        val source = updates ?: return
        updating = true
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    val apk = File(context.cacheDir, "update.apk")
                    source.download(apk)
                    ApkInstaller.install(context, apk)
                }.exceptionOrNull()
            }
            updating = false
            failure?.let { snackbarHostState.showSnackbar("Update failed: ${it.message}") }
        }
    }

    LaunchedEffect(Unit) {
        // Show the last synced snapshot right away: after Android killed the
        // app, the list is otherwise empty until the network round-trip ends.
        if (File(repoDir, ".git").isDirectory) {
            try {
                groups = withContext(Dispatchers.IO) { buildTaskListModel(repoDir.toPath()) }
            } catch (e: IOException) {
                // The sync below reports the unreadable repo.
            }
        }
        sync()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        bottomBar = { BuildCommitLine(modifier = Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            if (File(repoDir, ".git").isDirectory) {
                FloatingActionButton(onClick = {
                    addTaskError = null
                    addingTask = true
                }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("ContextSwitcher") },
                actions = {
                    if (update is AppUpdates.Check.Available) {
                        TextButton(onClick = { installUpdate() }, enabled = !updating) { Text(if (updating) "Updating…" else "Update") }
                    }
                    TextButton(onClick = { sync() }, enabled = !syncing) { Text(if (syncing) "Syncing…" else "Sync") }
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                },
            )
        },
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyTaskList(syncing, lastError, onRetry = { sync() }, modifier = Modifier.padding(padding))
        } else {
            TaskListBody(groups, modifier = Modifier.padding(padding), onTaskClick = onOpenTask, listState = listState)
        }
    }

    if (addingTask) {
        val categories = groups.map { it.name }.ifEmpty { listOf("") }
        AddTaskDialog(
            categories = categories,
            initialCategory = categories.firstOrNull { it.isNotEmpty() } ?: categories.first(),
            adding = taskBeingAdded,
            error = addTaskError,
            onAdd = { category, title -> addTask(category, title) },
            onDismiss = { addingTask = false },
            remoteOf = { category -> liveTasks?.remoteOf(repoDir, category) },
            onStartClaude = if (liveTasks == null) null else ::startClaudeTask,
            initialSkipPermissions = remember { settingsStore.loadSkipPermissions() },
            progress = addProgress,
        )
    }
}

/// With nothing synced yet the list would be blank once the Snackbar is gone,
/// so the failure stays on screen next to a Retry button.
@Composable
internal fun EmptyTaskList(syncing: Boolean, error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = if (syncing) "Syncing…" else error ?: "No tasks")
        Button(onClick = onRetry, enabled = !syncing, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}
