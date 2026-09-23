package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Recreates a task's tmux window after it (or the whole server, e.g. on a
/// host reboot) died: new window named after the task in the recorded Claude
/// cwd, `claude --resume <sessionId>` typed into it, and the new immutable
/// window id reported back so the task file can be updated.
/// Works without a `claude:` section too — then the window is created
/// plainly (default cwd, nothing resumed), so a suspended task without a
/// recorded Claude session still gets its own window back on resume.
// [impl->dsn~tmux-resurrect~7]
public class TmuxResurrect {

    public record Resurrected(String windowId, boolean resumedClaude) {
    }

    private final SshCommandRunner ssh;
    private final boolean claudeAuto;

    public TmuxResurrect(SshCommandRunner ssh) {
        this(ssh, false);
    }

    /// `claudeAuto` resumes Claude with `--dangerously-skip-permissions`
    /// (settings `claudeAuto`), matching freshly launched sessions.
    // [impl->dsn~claude-auto-permissions~1]
    public TmuxResurrect(SshCommandRunner ssh, boolean claudeAuto) {
        this.ssh = ssh;
        this.claudeAuto = claudeAuto;
    }

    /// The remote argv renaming the config's window to `name`: renaming a task
    /// renames its window, so the tmux status line shows the same name as the
    /// task list.
    // [impl->dsn~task-rename-title~2]
    public static List<String> renameWindowCommand(Task.TmuxConfig tmux, String name) {
        return List.of("tmux", "rename-window", "-t", SshCommandRunner.quote(tmux.target()),
                SshCommandRunner.quote(name));
    }

    /// `|| claude` catches a recorded session id the remote no longer knows
    /// (its transcript rotated away, or the id belongs to another host or cwd):
    /// `--resume` then prints "No conversation found with session ID" and
    /// exits, and without the fallback the resurrected window sits at a bare
    /// shell prompt with nothing running in it. A fresh Claude in the recorded
    /// cwd is the closer approximation of what the task expects.
    /// It also fires when Claude is later left with a non-zero exit — a new
    /// session in the same window, which is the same repair.
    // [impl->dsn~claude-auto-permissions~1]
    static List<String> resumeCommand(String windowId, String sessionId, boolean auto) {
        return startCommand(windowId, sessionId, auto);
    }

    /// The command line typed into an existing window to bring Claude up:
    /// [#resumeCommand] when a session id is recorded, a plain `claude`
    /// otherwise.
    // [impl->dsn~start-claude-button~2]
    static List<String> startCommand(String windowId, @Nullable String sessionId, boolean auto) {
        String flags = auto ? " --dangerously-skip-permissions" : "";
        String line = sessionId == null ? "claude" + flags
                : "claude --resume " + sessionId + flags + " || claude" + flags;
        return List.of("tmux", "send-keys", "-t", "'" + windowId + "'", "'" + line + "'", "Enter");
    }

    /// Starts (or resumes) Claude in a window that already exists — the repair
    /// for a window whose Claude never came up, or exited. Returns true when
    /// the keys went out.
    // [impl->dsn~start-claude-button~2]
    public boolean startClaude(String remote, String windowId, @Nullable String sessionId) {
        return ssh.run(remote, startCommand(windowId, sessionId, claudeAuto)).ok();
    }

    /// Creates a fresh window (a new session when the base session is gone)
    /// in `cwd` with no Claude — the plain-shell choice of the remote-only
    /// auto-create popup. Returns the new immutable window id, or null with
    /// the failure logged.
    // [impl->dsn~remote-window-choice~6]
    public @Nullable String createWindow(String remote, String session, @Nullable String cwd, String name) {
        SshCommandRunner.SshResult created = ssh.run(remote, ClaudeWindowLauncher.newWindowCommand(session, cwd, name));
        if (!created.ok()) {
            created = ssh.run(remote, ClaudeWindowLauncher.newSessionCommand(session, cwd, name));
        }
        if (!created.ok() || created.stdout().isBlank()) {
            Logger.warn("Cannot create window for session {} on {}: {}",
                    session, remote, created.stderr().strip());
            return null;
        }
        return created.stdout().strip();
    }

    /// Returns the new window id, or null with the failure logged.
    public @Nullable Resurrected resurrect(String remote, Task.TmuxConfig tmux,
            Task.@Nullable ClaudeConfig claude, String windowName) {
        String cwd = claude == null ? null : claude.cwd();
        SshCommandRunner.SshResult created =
                ssh.run(remote, ClaudeWindowLauncher.newWindowCommand(tmux.session(), cwd, windowName));
        if (!created.ok()) {
            created = ssh.run(remote, ClaudeWindowLauncher.newSessionCommand(tmux.session(), cwd, windowName));
        }
        if (!created.ok() || created.stdout().isBlank()) {
            Logger.warn("Cannot resurrect window for session {} on {}: {}",
                    tmux.session(), remote, created.stderr().strip());
            return null;
        }
        String windowId = created.stdout().strip();
        boolean resumed = false;
        if (claude != null && claude.sessionId() != null) {
            resumed = ssh.run(remote, resumeCommand(windowId, claude.sessionId(), claudeAuto)).ok();
        }
        return new Resurrected(windowId, resumed);
    }
}
