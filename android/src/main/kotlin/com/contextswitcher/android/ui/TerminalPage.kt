package com.contextswitcher.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextswitcher.android.terminal.SgrParser
import com.contextswitcher.ssh.SshCommandRunner
import com.contextswitcher.tasks.Task
import com.contextswitcher.discovery.ClaudeUpdateRestart
import com.contextswitcher.discovery.TmuxStatusPoller
import com.contextswitcher.terminal.PaneSnapshots
import com.contextswitcher.terminal.TmuxMirrorCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AUTO_REFRESH_INTERVAL_MS = 5_000L

/// Scrollback lines fetched per "load earlier" step, and at most in total.
private const val HISTORY_STEP = 500
private const val HISTORY_MAX = 20_000

/// How far above the end the scroll position may be and still count as "at the end".
private const val FOLLOW_SLACK_PX = 24

/// The task detail pager's terminal page: `tmux capture-pane -e` on the
/// task's remote, parsed by [SgrParser] into colored, monospace text. A
/// manual refresh (disabled while a capture runs, CLAUDE.md's async-button
/// convention) plus a 5s auto-refresh while the page is visible — a
/// polling snapshot, not a live stream (a later step).
/// A capture failure shows a Snackbar and keeps the last good snapshot.
///
/// Like a terminal, the page sits at the end of the screen — where Claude's
/// latest output and its prompt are — and follows it across refreshes;
/// after the user scrolls up to read, it stays put until they scroll back
/// down to the end.
///
/// Scrollback is fetched on demand only, so the 5 s poll stays a screenful:
/// reaching the top of what is loaded (or *Earlier*) captures another
/// [HISTORY_STEP] lines of the window's history, keeping the reading position,
/// until tmux has no more. While scrolled up the poll pauses — nothing moves
/// under the reader — and back at the end the history is dropped again.
// [impl->dsn~android-terminal-snapshot~3]
@Composable
fun TerminalPage(
    task: Task,
    tmux: Task.TmuxConfig,
    remote: String,
    ssh: SshCommandRunner,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    var text by remember(task.id()) { mutableStateOf(AnnotatedString("")) }
    var followEnd by remember(task.id()) { mutableStateOf(true) }
    var sendingKey by remember(task.id()) { mutableStateOf(false) }
    var updatePending by remember(task.id()) { mutableStateOf(false) }
    var restarting by remember(task.id()) { mutableStateOf(false) }
    var history by remember(task.id()) { mutableStateOf(0) }
    var historyExhausted by remember(task.id()) { mutableStateOf(false) }
    var loadingEarlier by remember(task.id()) { mutableStateOf(false) }
    var refreshing by remember(task.id()) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        // Back at the end, loaded history goes: the capture is a screenful again.
        if (followEnd && !loadingEarlier && history > 0) {
            history = 0
            historyExhausted = false
        }
        refreshing = true
        try {
            val lines = history
            val result = withContext(Dispatchers.IO) { ssh.run(remote, PaneSnapshots.captureCommand(tmux, lines)) }
            if (result.ok()) {
                // A pane taller than its content captures blank lines below
                // it; without them the end of the page is the last output.
                updatePending = TmuxStatusPoller.updatePendingIn(result.stdout())
                val parsed = SgrParser.parse(result.stdout())
                text = parsed.subSequence(0, parsed.text.trimEnd().length)
            } else {
                snackbarHostState.showSnackbar(
                    "Cannot capture the pane: ${result.stderr().ifBlank { "exit ${result.exitCode()}" }}")
            }
        } finally {
            refreshing = false
        }
    }

    /// Captures [HISTORY_STEP] more lines of scrollback. `jumpToLoaded`
    /// (the *Earlier* button) shows the start of what was loaded; otherwise
    /// (the reader reached the top) the line they were reading stays put.
    suspend fun loadEarlier(jumpToLoaded: Boolean) {
        if (refreshing || loadingEarlier || historyExhausted || history >= HISTORY_MAX) {
            return
        }
        loadingEarlier = true
        try {
            val maxBefore = scrollState.maxValue
            val valueBefore = scrollState.value
            val linesBefore = text.text.lines().size
            history += HISTORY_STEP
            refresh()
            if (text.text.lines().size <= linesBefore) {
                historyExhausted = true
                return
            }
            val max = withTimeoutOrNull(2_000) {
                snapshotFlow { scrollState.maxValue }.first { it != maxBefore && it != Int.MAX_VALUE }
            } ?: return
            scrollState.scrollTo(if (jumpToLoaded) 0 else valueBefore + (max - maxBefore))
        } finally {
            loadingEarlier = false
        }
    }

    // While the page is visible, poll every 5s — but not while the user reads
    // further up; the effect (and its loop) cancels when the composable
    // leaves composition (page swiped away).
    LaunchedEffect(task.id()) {
        while (true) {
            if (followEnd && !loadingEarlier) {
                refresh()
            }
            delay(AUTO_REFRESH_INTERVAL_MS)
        }
    }

    // Reaching the top of what is loaded fetches earlier output.
    LaunchedEffect(scrollState) {
        snapshotFlow {
            scrollState.value == 0 && scrollState.maxValue in (FOLLOW_SLACK_PX + 1) until Int.MAX_VALUE &&
                !followEnd && !loadingEarlier
        }.collect { atTop -> if (atTop) loadEarlier(jumpToLoaded = false) }
    }

    // maxValue is Int.MAX_VALUE until the text is first measured.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue }.collect { max ->
            // A load restores its own position once the longer text is measured.
            if (followEnd && !loadingEarlier && max != Int.MAX_VALUE) {
                scrollState.scrollTo(max)
            }
        }
    }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { value ->
            if (scrollState.maxValue != Int.MAX_VALUE) {
                followEnd = value >= scrollState.maxValue - FOLLOW_SLACK_PX
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.padding(horizontal = 4.dp)) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
            TextButton(onClick = { scope.launch { loadEarlier(jumpToLoaded = true) } }, enabled = !refreshing && !historyExhausted && history < HISTORY_MAX) {
                Text("Earlier", color = if (historyExhausted) Color.Gray else Color.White)
            }
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !refreshing) {
                Text(if (refreshing) "Refreshing…" else "Refresh", color = Color.White)
            }
        }
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                color = Color(0xFFCCCCCC),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState),
            )
        }
        // The desktop terminal bar's keys, for a phone without them. The
        // capture right after shows what the key did.
        // [impl->dsn~android-terminal-interrupt~1]
        // Wraps onto a second line where the keys do not fit the width.
        FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            // Only while Claude's footer asks for it; the desktop restarts on
            // its own, the phone on request (dsn~android-claude-restart~1).
            if (updatePending || restarting) {
                TextButton(
                    onClick = {
                        restarting = true
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) {
                                ClaudeUpdateRestart.restart(ssh, remote, tmux.target()) { Thread.sleep(it) }
                            }
                            restarting = false
                            refresh()
                            snackbarHostState.showSnackbar(restartMessage(outcome))
                        }
                    },
                    enabled = !restarting,
                ) {
                    Text(if (restarting) "Restarting…" else "Restart Claude", color = Color.White)
                }
            }
            // Claude's selection prompts (checklists, numbered choices) are
            // driven by these; a sent message only works for numbers.
            // [impl->dsn~android-terminal-keys~1]
            for ((label, key) in TERMINAL_KEYS) {
                TextButton(
                    onClick = {
                        sendingKey = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ssh.run(remote, TmuxMirrorCommands.keyCommand(SshCommandRunner.quote(tmux.target()), key))
                            }
                            sendingKey = false
                            if (result.ok()) {
                                refresh()
                            } else {
                                snackbarHostState.showSnackbar(
                                    "Cannot send $label: ${result.stderr().ifBlank { "exit ${result.exitCode()}" }}")
                            }
                        }
                    },
                    enabled = !sendingKey,
                ) {
                    Text(label, color = Color.White)
                }
            }
        }
    }
}

/// The bar's keys, label to tmux key name: Ctrl+C interrupts
/// (`dsn~android-terminal-interrupt~1`), the rest answer Claude's prompts.
/// Esc, not Ctrl+C, sits at the right edge, where a thumb lands by accident.
internal val TERMINAL_KEYS = listOf(
    "↑" to "Up", "↓" to "Down", "Space" to "Space", "Enter" to "Enter", "Tab" to "Tab", "Ctrl+C" to "C-c", "Esc" to "Escape",
)

// [impl->dsn~android-claude-restart~1]
internal fun restartMessage(outcome: ClaudeUpdateRestart.Outcome): String = when (outcome) {
    is ClaudeUpdateRestart.Outcome.Restarted ->
        if (outcome.sessionId() == null) "Claude restarted (continuing the last conversation)" else "Claude restarted on the new version"
    is ClaudeUpdateRestart.Outcome.NotIdle ->
        "Not restarted: Claude is ${outcome.status() ?: "not reporting a status"} — restart once its turn is over"
    is ClaudeUpdateRestart.Outcome.DidNotQuit -> "Claude did not quit on /exit — not resumed"
    is ClaudeUpdateRestart.Outcome.Failed -> outcome.message()
}
