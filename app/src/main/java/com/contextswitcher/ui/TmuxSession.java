package com.contextswitcher.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.contextswitcher.discovery.TmuxStatusPoller;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.terminal.PtyTtyConnector;
import com.contextswitcher.terminal.TmuxHost;
import com.contextswitcher.terminal.TmuxMirrorCommands;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.util.Duration;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// A live mirror of a task's tmux window (MADR 0007) and its whole connection
/// lifecycle — on a remote host, or in this machine's own tmux server for a
/// locally mirrored session (`dsn~terminal-local-mirror~2`).
///
/// One pty per (host, base session): the pty attaches a **grouped** mirror
/// session ([TmuxMirrorCommands]), so the mirror's current window is
/// independent of the real session's. Selecting another window of the same
/// session only sends a `select-window` over a plain ssh side channel
/// ([#select]) â no reconnect; anything else is a fresh session. The mirror
/// session stays behind on the remote when the app quits (detached, no
/// processes of its own) â harmless by design. An unexpected pty exit
/// auto-reconnects with backoff (https://github.com/contextswitcher/contextswitcher-private/issues/36); [#detach] is intentional and does not.
// [impl->dsn~terminal-pane~14]
// [impl->dsn~terminal-reconnect~2]
final class TmuxSession implements TerminalSession {

    /// How long a confirmed select is trusted before the next re-show verifies
    /// it again. The list rebuilds re-apply the selection every few seconds;
    /// without this each rebuild spent an ssh round-trip re-selecting the
    /// window that is already current, competing for the six ssh slots with
    /// the switch the user is waiting for. Drift the session cannot see (a
    /// window resurrected elsewhere) is still corrected, just one interval later.
    private static final long SELECT_VERIFY_INTERVAL_MS = 10_000;

    /// Give up auto-reconnecting after this many consecutive attempts (the user
    /// can re-select the task to start over).
    private static final int MAX_RECONNECT_ATTEMPTS = 8;

    /// A connection alive at least this long counts as stable.
    private static final long STABLE_UPTIME_MS = 20_000;

    /// How long [#uncoverOnRedraw] waits for the mirror's repaint before
    /// lifting the cover anyway. Long enough for a redraw that is already on
    /// the wire, short enough not to read as a hang.
    private static final double REDRAW_WAIT_MS = 750;

    private final ExecutorService executor;

    /// The mirror's side channel, host-routed ([HostCommandRunner]): the same
    /// commands go over ssh for a remote and through a local shell for a
    /// locally mirrored session.
    private final SshCommandRunner ssh;

    private final View view;

    private final String remote;

    /// The window the user is viewing. The same-session fast path moves it
    /// too, so an unexpected pty exit (e.g. after the PC sleeps and ssh
    /// keepalive kills the stale link) re-attaches the window in view â not
    /// the one of the last full attach.
    private Task.TmuxConfig tmux;

    /// Serializes async outcomes: a result only applies while its generation
    /// is still the latest â a re-attach and [#detach] both move it on.
    private final AtomicLong generation = new AtomicLong();

    /// Coalesces window selects: only the newest one reaches the remote.
    /// `ssh` runs share six global slots (`ProcessSshRunner.MAX_CONCURRENT`),
    /// so a burst of clicks otherwise queues one `select-window` per click and
    /// the mirror visibly walks through every window on the way to the last.
    private final AtomicLong selectSequence = new AtomicLong();

    /// `connectionKey|window` of the last `select-window` the remote confirmed,
    /// with the time it did â see [#SELECT_VERIFY_INTERVAL_MS].
    private @Nullable String confirmedSelect;
    private long confirmedSelectMs;

    private @Nullable JediTermFxWidget widget;
    private @Nullable PtyProcess pty;
    private @Nullable PtyTtyConnector connector;
    private @Nullable String connectionKey;
    private @Nullable String currentWindow;

    /// Consecutive failed/short-lived reconnects since the last stable
    /// connection or user selection; drives the backoff and the give-up cap.
    private int reconnectAttempts;

    /// When the current connection's terminal was installed (`currentTimeMillis`);
    /// a connection that stayed up longer than [#STABLE_UPTIME_MS] resets the
    /// backoff, so a genuine later drop gets the full retry budget.
    private long connectStartMs;

    TmuxSession(ExecutorService executor, SshCommandRunner ssh, View view, String remote,
            Task.TmuxConfig tmux) {
        this.executor = executor;
        this.ssh = ssh;
        this.view = view;
        this.remote = remote;
        this.tmux = tmux;
    }

    /// The fast path of a selection: when this mirror's pty already serves
    /// `target`'s host and base session, a `select-window` moves it to
    /// `target`'s window and the answer is true; false means a fresh mirror is
    /// needed. The select is re-issued even for the window this session
    /// believes current (the mirror's real current window can drift from that
    /// memory), unless the remote confirmed it moments ago. A user selection,
    /// so the reconnect budget resets.
    // [impl->dsn~terminal-pane~14]
    boolean select(String remote, Task.TmuxConfig target) {
        String key = TmuxMirrorCommands.connectionKey(remote, target);
        PtyProcess process = pty;
        if (!key.equals(connectionKey) || process == null || !process.isAlive()) {
            return false;
        }
        reconnectAttempts = 0;
        tmux = target;
        view.liftBlank();
        String window = target.window();
        if (window == null) {
            return true;
        }
        // Re-issue select-window even when the target matches our
        // remembered currentWindow: that memory can drift from the
        // mirror's *real* current window (a select that failed and
        // left the value advanced, a window resurrected under another
        // session, anything that moved the pty out from under us), and
        // then keystrokes would go to whichever window the mirror is
        // actually on â not the selected task's. The select is
        // idempotent and is itself the correction, so verifying is
        // cheaper than a separate `display-message` round-trip first.
        boolean believedChange = TmuxMirrorCommands.changedWindow(currentWindow, target) != null;
        currentWindow = window;
        // Blank the terminal while the mirror switches windows so the
        // previous window's content can't be mistaken for the newly
        // selected task's; the live view returns once the select-window
        // round-trip completes (the actual lag the blanking covers).
        // Only for a believed change, so re-selecting the same task
        // (the common verify case) doesn't blink.
        if (believedChange) {
            view.cover(window);
        }
        String select = key + "|" + window;
        if (!believedChange && select.equals(confirmedSelect)
                && System.currentTimeMillis() - confirmedSelectMs < SELECT_VERIFY_INTERVAL_MS) {
            // A list rebuild re-applying the selection the remote just
            // confirmed â no round-trip, no ssh slot taken.
            return true;
        }
        long mySelect = selectSequence.incrementAndGet();
        executor.execute(() -> {
            // A newer selection is already on its way: running this one
            // would switch the mirror to a window the user has left
            // behind, and the ssh slots are the scarce resource the
            // newer select is waiting for.
            if (selectSequence.get() != mySelect) {
                return;
            }
            SshCommandRunner.SshResult selected =
                    ssh.run(remote, TmuxMirrorCommands.selectWindowCommand(target));
            Platform.runLater(() -> {
                // Not guarded by the generation: background list rebuilds
                // re-apply the selection every few seconds, and with an ssh
                // round-trip slower than that cadence no select would ever
                // return "current" and the overlay would stay up over a live
                // window. A select's outcome applies as long as the session
                // still shows its connection and window.
                if (!key.equals(connectionKey) || !window.equals(currentWindow)) {
                    return;
                }
                // What the mirror says it is *showing* decides, not the
                // exit code: the select rides with a `display-message`
                // that prints the window it ended on, and a mismatch
                // means the pane would be showing another task's
                // session while reporting success â the one failure
                // this pane cannot see for itself. A tmux that printed
                // nothing yields null = "cannot tell", which is not
                // taken for a mismatch.
                // [impl->dsn~terminal-pane~14]
                String shown = TmuxMirrorCommands.selectedWindow(selected.stdout());
                boolean onTarget = shown == null || shown.equals(window);
                if (selected.ok() && onTarget) {
                    confirmedSelect = select;
                    confirmedSelectMs = System.currentTimeMillis();
                    uncoverOnRedraw(key, window);
                    return;
                }
                confirmedSelect = null;
                // The select failed or did not take â uncovering would
                // put the *previous* task's window back on screen as if
                // it were this one's, and every later selection would
                // fail the same way, so every task would mirror one
                // frozen window. Re-attach instead of giving up: the
                // usual cause is a mirror stranded by a base-session
                // restart, which the full attach repairs
                // ([TmuxMirrorCommands#repairMirrorCommand]); a window
                // that is really gone ends in a placeholder there.
                Logger.info("Mirror select of {} on {} {} â re-attaching",
                        window, remote, selected.ok()
                                ? "left the mirror on " + shown
                                : "failed (" + selected.stderr().strip() + ")");
                attach();
            });
        });
        return true;
    }

    /// Lifts the "Switchingâ¦" cover once the mirror has actually repainted â
    /// the pty delivering output â rather than when the `select-window` reply
    /// comes back over the ssh side channel. The reply only says tmux accepted
    /// the select; the redraw travels the pty a moment later, so uncovering on
    /// the reply flashes the previous task's window (field report 2026-09-10).
    /// A timer backs the wait up, so a select that changes nothing on screen
    /// (the mirror was already on that window, and tmux redraws nothing) can
    /// never leave the cover stuck. Both routes carry the same guard the reply
    /// itself has â the session must still show this select's connection and
    /// window â and not the select sequence number, which the background list
    /// rebuilds bump every few seconds and would leave the cover up for good.
    // [impl->dsn~terminal-pane~14]
    private void uncoverOnRedraw(String key, String window) {
        Runnable uncover = () -> {
            if (key.equals(connectionKey) && window.equals(currentWindow)) {
                view.uncover();
            }
        };
        PtyTtyConnector active = connector;
        if (active == null) {
            uncover.run();
            return;
        }
        active.onNextOutput(() -> Platform.runLater(uncover));
        PauseTransition fallback = new PauseTransition(Duration.millis(REDRAW_WAIT_MS));
        fallback.setOnFinished(event -> uncover.run());
        fallback.play();
    }

    /// Opens a fresh pty attach under a new generation â the first attach of
    /// a selection, an auto-reconnect, a select that did not take, a live
    /// theme switch.
    void attach() {
        Task.TmuxConfig target = tmux;
        // A focus request from the previous selection is spent: this attach is
        // the pane following the selection, not the user asking for the
        // keyboard. `show` runs before its caller's `focusTerminal`, so a
        // request meant for *this* attach still arrives after the reset.
        view.clearFocusRequest();
        String key = TmuxMirrorCommands.connectionKey(remote, target);
        teardown();
        long myGeneration = generation.incrementAndGet();
        view.showStatus("Connecting to %s â¦".formatted(key));
        List<String> command = TmuxMirrorCommands.attachCommand(remote, target);
        executor.execute(() -> {
            // A mirror stranded by a base-session restart re-attaches by name
            // and never re-groups, so the attach's own `select-window` fails
            // silently and the pty lands on the mirror's arbitrary current
            // window â another task's Claude session. Repair it first, and
            // stop here when the task's window turns out to be gone.
            SshCommandRunner.SshResult repair =
                    ssh.run(remote, TmuxMirrorCommands.repairMirrorCommand(target));
            if (TmuxMirrorCommands.windowGone(repair)) {
                Platform.runLater(() -> {
                    if (generation.get() == myGeneration) {
                        view.end("Window %s on %s is gone â Switch resurrects it."
                                .formatted(target.window(), remote));
                    }
                });
                return;
            }
            try {
                Map<String, String> environment = new HashMap<>(System.getenv());
                environment.put("TERM", "xterm-256color");
                PtyProcess process = new PtyProcessBuilder()
                        .setCommand(command.toArray(String[]::new))
                        .setEnvironment(environment)
                        .setInitialColumns(120)
                        .setInitialRows(32)
                        .setConsole(false)
                        .setUseWinConPty(true)
                        .start();
                Platform.runLater(() -> {
                    if (generation.get() != myGeneration) {
                        process.destroy();
                        return;
                    }
                    connect(process, command, key, target.window(), myGeneration);
                });
            } catch (Exception e) {
                Logger.warn("Cannot start terminal mirror {}: {}", command, e.getMessage());
                Platform.runLater(() -> {
                    if (generation.get() == myGeneration) {
                        view.end("Cannot connect: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void connect(PtyProcess process, List<String> command, String key,
            @Nullable String window, long myGeneration) {
        PtyTtyConnector ttyConnector = new PtyTtyConnector(process, command);
        // [impl->dsn~terminal-markdown-copy~1]
        JediTermFxWidget terminal = view.liveWidget(ttyConnector, true);
        terminal.getTerminal().addApplicationTitleListener(text ->
                Platform.runLater(() -> {
                    if (generation.get() == myGeneration) {
                        view.setTitle(text == null || text.isBlank() ? "" : "â " + text);
                    }
                }));
        // On an unexpected pty exit (still the current connection), auto-
        // reconnect with backoff (https://github.com/contextswitcher/contextswitcher-private/issues/36); an intentional teardown bumped the
        // generation, so this no-ops in that case.
        executor.execute(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Platform.runLater(() -> {
                if (generation.get() != myGeneration) {
                    return;
                }
                if (System.currentTimeMillis() - connectStartMs > STABLE_UPTIME_MS) {
                    reconnectAttempts = 0;
                }
                scheduleReconnect();
            });
        });
        this.widget = terminal;
        this.pty = process;
        this.connector = ttyConnector;
        this.connectionKey = key;
        this.currentWindow = window;
        this.connectStartMs = System.currentTimeMillis();
        view.showTerminal(terminal);
        // The canvas â not the outer pane â carries the key handlers and the
        // cursor; focusing it draws the solid (blinking) cursor and routes
        // typing to the tty. JediTermFX already refocuses the canvas on click;
        // the attach takes the keyboard only when someone asked for it
        // (`focusTerminal` before the attach completed) or when nothing holds
        // it â which is the case of the attach that replaced its own focused
        // canvas (an auto-reconnect while the user was typing here). An attach
        // runs without a user action too (auto-reconnect, failed-select
        // re-attach) and a task row's play keeps the keyboard on the list, so
        // an unasked grab would steal it mid-word or mid-arrow-key.
        // [impl->dsn~terminal-pane~14]
        view.focusNewTerminal(terminal);
    }

    /// Schedules the next auto-reconnect (https://github.com/contextswitcher/contextswitcher-private/issues/36) after an exponential backoff
    /// (1, 2, 4, â¦ capped at 30 s), showing a countdown-style message. Gives
    /// up after [#MAX_RECONNECT_ATTEMPTS] consecutive tries. Must run on the
    /// FX thread; the sleep runs on the executor and re-checks the generation
    /// so a selection or a detach during the wait cancels the reconnect.
    // [impl->dsn~terminal-reconnect~2]
    private void scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            giveUp();
            return;
        }
        int attempt = ++reconnectAttempts;
        long gen = generation.get();
        long delayMs = Math.min(30_000L, 1000L << Math.min(attempt - 1, 5));
        view.showStatus("Connection lost â reconnecting (attempt %d/%d)â¦"
                .formatted(attempt, MAX_RECONNECT_ATTEMPTS));
        executor.execute(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Platform.runLater(() -> {
                if (generation.get() == gen) {
                    attach();
                }
            });
        });
    }

    /// The give-up state: stop auto-reconnecting and invite a fresh selection.
    /// The session stays the pane's, so the bar's side-channel commands still
    /// reach the window.
    // [impl->dsn~terminal-reconnect~2]
    private void giveUp() {
        teardown();
        reconnectAttempts = 0;
        view.showStatus("Connection lost â select the task again to reconnect.");
    }

    /// Ends the connection (idempotent). Bumps the generation first: the
    /// teardown is intentional, so the pty exit it causes must not be taken
    /// for a lost connection and schedule an auto-reconnect â at shutdown that
    /// reconnect hits an already-shut-down executor
    /// (`RejectedExecutionException`). An attach takes its own generation
    /// *after* this.
    private void teardown() {
        generation.incrementAndGet();
        JediTermFxWidget terminal = widget;
        PtyProcess process = pty;
        widget = null;
        pty = null;
        connector = null;
        connectionKey = null;
        currentWindow = null;
        // A fresh attach lands on whatever window the mirror opens on, so
        // nothing this session confirmed earlier can be trusted afterwards.
        confirmedSelect = null;
        if (terminal != null) {
            // Ends the process too: the widget closes its PtyTtyConnector.
            // [impl->dsn~terminal-process-close~1]
            terminal.close();
        } else if (process != null) {
            process.destroy();
        }
    }

    @Override
    public void detach() {
        teardown();
    }

    @Override
    public @Nullable JediTermFxWidget widget() {
        return widget;
    }

    /// Discards whatever is typed but not yet sent, over the ssh side
    /// channel: leaves copy-mode first, then sends `^U` (kill line) â the
    /// readline convention a shell and Claude Code's input both honour â
    /// repeatedly, since one `^U` takes only one line of a multi-line
    /// draft ([TmuxMirrorCommands#clearInputCommand]). Not written to the
    /// pty: while the pane is in copy-mode (the mouse wheel puts it there),
    /// pty-written `^U` scrolls instead of reaching the application, which
    /// made the button dead exactly after scrolling. No `^A`/`^E` homing:
    /// Claude Code's `^U` kills the whole line whatever the cursor column,
    /// and its `^E` is bound elsewhere and swallows a following `^U`.
    // [impl->dsn~terminal-clear-input~3]
    @Override
    public void clearInput() {
        Task.TmuxConfig target = tmux;
        executor.execute(() -> ssh.run(remote, TmuxMirrorCommands.clearInputCommand(target)));
        view.focusTerminal();
    }

    /// `C-c` over the ssh side channel, for the copy-mode reason [#clearInput]
    /// gives ([TmuxMirrorCommands#interruptCommand(Task.TmuxConfig)]).
    // [impl->dsn~terminal-interrupt~1]
    @Override
    public void interrupt() {
        Task.TmuxConfig target = tmux;
        executor.execute(() -> ssh.run(remote, TmuxMirrorCommands.interruptCommand(target)));
        view.focusTerminal();
    }

    /// `Tab` then `Enter` over the ssh side channel, for the copy-mode
    /// reason [#clearInput] gives ([TmuxMirrorCommands#acceptSuggestionCommand]).
    // [impl->dsn~terminal-accept-suggestion~1]
    @Override
    public void acceptSuggestion() {
        Task.TmuxConfig target = tmux;
        executor.execute(() -> ssh.run(remote, TmuxMirrorCommands.acceptSuggestionCommand(target)));
        view.focusTerminal();
    }

    /// Cancels copy-mode over the ssh side channel (same route as the
    /// window switch) and puts the keyboard back into the terminal.
    // [impl->dsn~terminal-jump-to-bottom~1]
    @Override
    public void jumpToBottom() {
        Task.TmuxConfig target = tmux;
        executor.execute(() -> ssh.run(remote, TmuxMirrorCommands.cancelCopyModeCommand(target)));
        view.focusTerminal();
    }

    /// Hands the mirrored remote + tmux config to the wired diff opener.
    // [impl->dsn~terminal-diff-window~2]
    @Override
    public void showDiff() {
        view.openDiff(remote, tmux);
    }

    // [impl->dsn~generated-file-download~2]
    @Override
    public void showFiles(Button source) {
        view.showRemoteFiles(source, remote);
    }

    /// The diff window and the generated-file listing both work over ssh, so
    /// a locally mirrored session (`dsn~terminal-local-mirror~2`) has nothing
    /// for them to reach â better greyed out than failing against a host
    /// called "(local)".
    // [impl->dsn~terminal-local-mirror~2]
    @Override
    public boolean offersDiffAndFiles() {
        return !TmuxHost.isLocal(remote);
    }

    @Override
    public String statusKey() {
        return TmuxStatusPoller.key(remote, tmux.window());
    }

    // [impl->dsn~terminal-markdown-copy~1]
    @Override
    public @Nullable ReplySource replySource() {
        Task.ClaudeConfig claude = view.claudeOf(remote, tmux);
        String sessionId = claude == null ? null : claude.sessionId();
        return claude == null || sessionId == null ? null : new ReplySource(remote, claude, sessionId);
    }

    // [impl->dsn~terminal-file-links~1]
    @Override
    public @Nullable String downloadHost() {
        return TmuxHost.isLocal(remote) ? null : remote;
    }

    /// Answers "whose terminal is this?" by **asking the mirror**, not by
    /// repeating what the pane was asked to show: the one failure the pane
    /// cannot see for itself is a mirror sitting on another window than the
    /// selected task's (`dsn~terminal-pane~14`), and a local memory would
    /// confirm exactly that lie. So the window comes from a
    /// `display-message` round-trip and the pane selects whichever task owns
    /// it â the list then agrees with the screen, and a jump to a *different*
    /// task is the mismatch made visible.
    // [impl->dsn~terminal-mirrored-task~1]
    @Override
    public void selectShownTask() {
        Task.TmuxConfig target = tmux;
        executor.execute(() -> {
            SshCommandRunner.SshResult result =
                    ssh.run(remote, TmuxMirrorCommands.currentWindowCommand(target));
            String window = result.ok() ? TmuxMirrorCommands.selectedWindow(result.stdout()) : null;
            Platform.runLater(() -> {
                if (window == null) {
                    // Deliberately not falling back to the requested window:
                    // that is the claim under suspicion, and dressing it as an
                    // answer would confirm the very mismatch being checked.
                    Alerts.wrapping(Alert.AlertType.INFORMATION,
                            "Cannot ask %s which window the mirror shows:%n%s"
                                    .formatted(remote, result.stderr().strip())).show();
                    return;
                }
                view.selectTaskOfWindow(remote, window);
            });
        });
    }

    /// A JediTermFX widget takes its colors from the settings provider it was
    /// **built** with, so the mirror is re-attached rather than restyled:
    /// cheap and lossless, since the session and its scrollback live in the
    /// remote tmux, not here.
    // [impl->dsn~terminal-theme~2]
    @Override
    public void retheme() {
        attach();
    }
}
