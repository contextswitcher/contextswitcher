package com.contextswitcher.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.contextswitcher.queue.SendResult;
import com.contextswitcher.terminal.LocalClaudeLauncher;
import com.contextswitcher.terminal.PtyTtyConnector;
import com.contextswitcher.terminal.TmuxMirrorCommands;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import javafx.scene.control.Button;
import org.jspecify.annotations.Nullable;

/// A Claude session the **app** owns, in a ConPTY on this machine — on
/// Windows, where there is no tmux to mirror (`dsn~terminal-owned-session~3`).
/// No tmux is involved, so none of the mirror machinery is: no window to
/// select, no mirror to repair, and no reconnect — closing the app ends the
/// process, and the way back is a resume. The pane keeps one per task across
/// selections: [#detach] only stops showing it, [#close] ends it.
// [impl->dsn~terminal-owned-session~3]
final class OwnedSession implements TerminalSession {

    /// The pause between a pasted message and its `Enter` — the tmux route's
    /// `run-shell 'sleep 1'`, for the same reason.
    private static final long SUBMIT_PAUSE_MS = 1_000;

    /// `^U`, readline's kill-line — one line of a multi-line draft per send.
    private static final String KILL_LINE = String.valueOf((char) 0x15);

    private final String taskId;
    private final PtyProcess process;
    private final JediTermFxWidget widget;
    private final View view;

    private OwnedSession(String taskId, PtyProcess process, JediTermFxWidget widget, View view) {
        this.taskId = taskId;
        this.process = process;
        this.widget = widget;
        this.view = view;
    }

    /// Starts `command` in a ConPTY rooted at `cwd`, shown in a terminal `view`
    /// builds.
    static OwnedSession start(String taskId, List<String> command, String cwd, View view)
            throws IOException {
        // Same reason as the launcher's: a category's workspaces root may
        // not exist yet, and a pty refuses a missing working directory.
        Files.createDirectories(Path.of(cwd));
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        PtyProcess process = new PtyProcessBuilder()
                .setCommand(command.toArray(String[]::new))
                .setEnvironment(environment)
                .setDirectory(cwd)
                .setInitialColumns(120)
                .setInitialRows(32)
                .setConsole(false)
                .setUseWinConPty(true)
                .start();
        JediTermFxWidget widget = view.liveWidget(new PtyTtyConnector(process, command), false);
        return new OwnedSession(taskId, process, widget, view);
    }

    boolean isAlive() {
        return process.isAlive();
    }

    /// Types `text` into the session and submits it — the message queue's
    /// delivery for a chat no tmux holds. Blocking, for the background
    /// executor: the text goes in as one bracketed paste, and the `Enter`
    /// follows after [#SUBMIT_PAUSE_MS], since an `Enter` in the paste's own
    /// burst lands as a newline in Claude's input box.
    SendResult send(String text) {
        // The starter, not the connector: it serializes writes on the
        // emulator's own thread, where the widget's keystrokes go too.
        var starter = widget.getTerminalStarter();
        starter.sendString(LocalClaudeLauncher.ownedPaste(text), true);
        try {
            Thread.sleep(SUBMIT_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SendResult.Failed("Interrupted before the message was submitted.");
        }
        starter.sendString("\r", true);
        return new SendResult.Sent();
    }

    /// Ends the process and its terminal — application shutdown, or a session
    /// found dead. The app owns the process; leaving it behind would orphan a
    /// Claude with no terminal attached to it.
    void close() {
        // Ends the process too: the widget closes its PtyTtyConnector.
        // [impl->dsn~terminal-process-close~1]
        widget.close();
    }

    @Override
    public JediTermFxWidget widget() {
        return widget;
    }

    /// Straight into the pty: the copy-mode trap that sends the mirror's
    /// kills over the side channel does not exist without tmux.
    @Override
    public void clearInput() {
        widget.getTerminalStarter().sendString(KILL_LINE.repeat(TmuxMirrorCommands.CLEAR_INPUT_KILLS), true);
        view.focusTerminal();
    }

    /// Straight into the pty: ETX, the byte a terminal sends for Ctrl+C.
    // [impl->dsn~terminal-interrupt~1]
    @Override
    public void interrupt() {
        widget.getTerminalStarter().sendString(String.valueOf((char) 0x03), true);
        view.focusTerminal();
    }

    /// Straight into the pty, `Enter` after [#SUBMIT_PAUSE_MS] like [#send].
    // [impl->dsn~terminal-accept-suggestion~1]
    @Override
    public void acceptSuggestion() {
        var starter = widget.getTerminalStarter();
        starter.sendString("\t", true);
        CompletableFuture.delayedExecutor(SUBMIT_PAUSE_MS, TimeUnit.MILLISECONDS)
                .execute(() -> starter.sendString("\r", true));
        view.focusTerminal();
    }

    /// No tmux copy-mode: the widget scrolled its own history, so the way back
    /// is local.
    @Override
    public void jumpToBottom() {
        widget.getTerminalPanel().scrollToShowAllOutput();
        view.focusTerminal();
    }

    /// The task id goes to the wired opener — there is no remote or tmux
    /// config to find the task by.
    @Override
    public void showDiff() {
        view.openOwnedDiff(taskId);
    }

    @Override
    public void showFiles(Button source) {
        view.showLocalFiles(source);
    }

    /// Both have a local route: a diff run on this machine, the scratchpads of
    /// its own Claude sessions.
    @Override
    public boolean offersDiffAndFiles() {
        return true;
    }

    /// An owned session publishes no tmux options.
    @Override
    public @Nullable String statusKey() {
        return null;
    }

    @Override
    public @Nullable ReplySource replySource() {
        return null;
    }

    /// Its files are on this machine already.
    @Override
    public @Nullable String downloadHost() {
        return null;
    }

    @Override
    public void selectShownTask() {
        TerminalPane.noLiveMirror();
    }

    /// Left alone: re-attaching would mean killing the Claude it hosts.
    // ponytail: an owned session keeps the old theme's colors until it is
    // restarted; rebuild its widget on the live pty if that ever matters.
    // [impl->dsn~terminal-theme~2]
    @Override
    public void retheme() {
    }

    /// Only stops being shown — the process keeps running in the pane's
    /// sessions until it exits or the app quits.
    @Override
    public void detach() {
    }
}
