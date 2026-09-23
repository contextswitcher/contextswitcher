package com.contextswitcher.terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TextFiles;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Last screen of a task's tmux window, kept across the teardown that ends
/// it: suspending or completing a task captures the pane just before the
/// window is killed, and selecting the task later shows that snapshot where
/// the live mirror would be — so "what did Claude say last?" is answered
/// without resuming the session.
///
/// One file per task under `<configDir>/snapshots/`, holding the
/// `capture-pane -e` output (with its SGR color escapes). Only the *visible*
/// screen is captured (no `-S` scrollback): it is what the mirror showed, and
/// it is what the user was looking at.
// [impl->dsn~terminal-suspend-snapshot~3]
public class PaneSnapshots {

    private final SshCommandRunner ssh;
    private final Path directory;

    public PaneSnapshots(SshCommandRunner ssh, Path directory) {
        this.ssh = ssh;
        this.directory = directory;
    }

    /// The remote argv dumping the window's visible screen **with color**:
    /// `-e` keeps the SGR escape sequences, so the snapshot renders in the
    /// same JediTermFX emulator — and thus the same colors — as the live
    /// mirror ([StringTtyConnector]).
    public static List<String> captureCommand(Task.TmuxConfig tmux) {
        return captureCommand(tmux, 0);
    }

    /// As [#captureCommand(Task.TmuxConfig)], plus the last `historyLines`
    /// lines of the window's scrollback above the screen (`-S -<n>`; tmux
    /// stops at the oldest line it keeps) — the phone's terminal page loads
    /// earlier output this way, step by step, only when scrolled to its top.
    // [impl->dsn~android-terminal-snapshot~3]
    public static List<String> captureCommand(Task.TmuxConfig tmux, int historyLines) {
        return historyLines <= 0
                ? List.of("tmux", "capture-pane", "-e", "-p", "-t", tmux.target())
                : List.of("tmux", "capture-pane", "-e", "-p", "-S", "-" + historyLines, "-t", tmux.target());
    }

    /// File name for a task id — ids carry the group folder (`jabref/fix-npe`),
    /// so every path separator has to go.
    static String fileName(String taskId) {
        return taskId.replaceAll("[^A-Za-z0-9._-]", "_") + ".txt";
    }

    /// The task's current screen, or null when there is no window to capture
    /// from (no `tmux:` section, no host) or the capture failed. Also the
    /// janitor's look at what the session ended on
    /// (`dsn~merged-task-cleanup~2`).
    public @Nullable String captureNow(Task task) {
        Task.TmuxConfig tmux = task.tmux();
        String host = TmuxHost.of(task);
        if (tmux == null || host == null) {
            return null;
        }
        SshCommandRunner.SshResult result = ssh.run(host, captureCommand(tmux));
        if (!result.ok() || result.stdout().isBlank()) {
            Logger.debug("No pane snapshot for {}: {}", task.id(), result.stderr().strip());
            return null;
        }
        return result.stdout();
    }

    /// Captures and stores the task's current screen. Best effort: a dead
    /// window, an unreachable remote, or an unwritable directory only logs —
    /// the suspend it precedes must not fail over a missing snapshot.
    public void capture(Task task) {
        String screen = captureNow(task);
        if (screen == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
            TextFiles.write(directory.resolve(fileName(task.id())), screen);
        } catch (IOException e) {
            Logger.warn("Cannot store the pane snapshot of {}: {}", task.id(), e.getMessage());
        }
    }

    /// The stored snapshot, or null when the task has none.
    public @Nullable String read(String taskId) {
        Path file = directory.resolve(fileName(taskId));
        try {
            return Files.exists(file) ? TextFiles.read(file) : null;
        } catch (IOException e) {
            Logger.warn("Cannot read the pane snapshot of {}: {}", taskId, e.getMessage());
            return null;
        }
    }
}
