package com.contextswitcher.switching;

import java.util.List;
import java.util.Locale;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.terminal.PaneSnapshots;
import com.contextswitcher.terminal.TmuxHost;
import org.jspecify.annotations.Nullable;

/// Ends the task's tmux window when the task is suspended, completed or
/// deleted (MADR 0009): `tmux kill-window -t <target>`, or `kill-session` for
/// a session-only config. On its remote host, or in this machine's own tmux
/// server for a local Claude session — [TmuxHost] names the one, the runner
/// routes the command there (`dsn~terminal-local-mirror~2`). A window or session that is already gone counts as
/// success — the goal state is "no live window", and the task's `claude:`
/// section carries what resurrection needs on resume.
///
/// The window's last screen is captured just before it dies
/// ([PaneSnapshots]), so the suspended task can still show what it was
/// doing.
///
/// A window that turns out to host *another* task's Claude session is left
/// alone ([TmuxWindowOwnership]) — the one outcome here that cannot be undone.
// [impl->dsn~task-suspend~6]
// [impl->dsn~terminal-suspend-snapshot~3]
public class TmuxKillAction implements SwitchAction {

    private final SshCommandRunner ssh;
    private final @Nullable PaneSnapshots snapshots;

    /// Without a snapshot store — nothing is kept of the dying window.
    public TmuxKillAction(SshCommandRunner ssh) {
        this(ssh, null);
    }

    public TmuxKillAction(SshCommandRunner ssh, @Nullable PaneSnapshots snapshots) {
        this.ssh = ssh;
        this.snapshots = snapshots;
    }

    @Override
    public String name() {
        return "tmux";
    }

    @Override
    public boolean isConfigured(Task task) {
        return TmuxHost.of(task) != null;
    }

    /// The remote argv for ending the given tmux config.
    public static List<String> remoteCommand(Task.TmuxConfig tmux) {
        if (tmux.window() == null) {
            return List.of("tmux", "kill-session", "-t", tmux.session());
        }
        return List.of("tmux", "kill-window", "-t", tmux.target());
    }

    @Override
    public ActionResult run(Task task) {
        Task.TmuxConfig tmux = task.tmux();
        String remote = TmuxHost.of(task);
        if (tmux == null || remote == null) {
            return ActionResult.failure("tmux not configured");
        }
        // A recorded window id can name a window that meanwhile belongs to
        // another task ([TmuxWindowOwnership]) — and unlike a wrong *focus*,
        // a wrong kill cannot be taken back: it ends a session someone is
        // working in. The snapshot is skipped along with it, since capturing
        // that pane would file another task's screen as this task's last one.
        // The task file's `window:` is already gone by now (the suspend
        // rewrites it before dispatching), so the task resurrects cleanly on
        // its next switch instead of pointing at the foreign window again.
        // [impl->dsn~tmux-window-ownership~4]
        // No window-name belt here (null), unlike the focus path: the id stays
        // the sole evidence where the mistake cannot be taken back — a task
        // file copied to start a new task carries the old title along with the
        // old window id, and the belt would let it end the original's window.
        String owner = TmuxWindowOwnership.foreignOwner(ssh, remote, tmux, task.claude(), null);
        if (owner != null) {
            return ActionResult.failure(
                    "%s now hosts Claude session %s — not ending another task's window"
                            .formatted(tmux.window(), owner));
        }
        // Before the kill — afterwards there is nothing left to capture.
        // [impl->dsn~terminal-suspend-snapshot~3]
        if (snapshots != null) {
            snapshots.capture(task);
        }
        SshCommandRunner.SshResult result = ssh.run(remote, remoteCommand(tmux));
        if (result.ok()) {
            return ActionResult.success("ended %s on %s".formatted(tmux.target(), remote));
        }
        String stderr = result.stderr().toLowerCase(Locale.ROOT);
        // "can't find window @17" / "session not found" / "no server running":
        // nothing left to end — the suspend goal is already met.
        if (stderr.contains("can't find") || stderr.contains("cannot find")
                || stderr.contains("not found") || stderr.contains("no server running")) {
            return ActionResult.success("already gone");
        }
        String detail = result.stderr().isBlank() ? "ssh exit " + result.exitCode() : result.stderr().strip();
        return ActionResult.failure(detail);
    }
}
