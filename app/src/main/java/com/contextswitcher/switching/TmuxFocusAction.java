package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import com.contextswitcher.terminal.TmuxHost;
import com.contextswitcher.terminal.TmuxMirrorCommands;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Focuses the task's tmux window — on its remote host, or in this machine's
/// own tmux server for a local Claude session ([TmuxHost], the runner routes
/// the command there): `select-window` on the base session, then `switch-client -c <client>` for every client **not**
/// attached to a mirror session ([TmuxMirrorCommands]).
///
/// `switch-client` without `-c`, run from a detached ssh, makes tmux pick the
/// *most recently active* client — frequently the app's own mirror pty (the
/// user just clicked in the embedded terminal). That yanked the mirror client
/// onto the base session: the embedded terminal then tracked the base
/// session's current window, and every later mirror `select-window` succeeded
/// invisibly on the no-longer-watched mirror session.
///
/// The select target is always `session:window` — a bare window id (`@17`)
/// is ambiguous once the window is linked into a grouped mirror session too.
// [impl->dsn~tmux-focus-action~3]
public class TmuxFocusAction implements SwitchAction {

    /// Notified with the new window id when a dead window was resurrected,
    /// so the task file can be updated.
    public interface ResurrectListener {
        void onResurrected(Task task, String newWindowId);
    }

    private final SshCommandRunner ssh;
    private final TmuxResurrect resurrect;
    private final ResurrectListener resurrectListener;

    public TmuxFocusAction(SshCommandRunner ssh, TmuxResurrect resurrect,
            ResurrectListener resurrectListener) {
        this.ssh = ssh;
        this.resurrect = resurrect;
        this.resurrectListener = resurrectListener;
    }

    @Override
    public String name() {
        return "tmux";
    }

    @Override
    public boolean isConfigured(Task task) {
        return TmuxHost.of(task) != null;
    }

    /// The remote argv for the given tmux config. The leading `select-window`
    /// (or `has-session` for a window-less config) also carries the failure
    /// signal: a dead window or server fails it and the `&&` chain, which is
    /// what triggers the resurrect fallback — the client loop itself exits 0
    /// even when no eligible client is attached (focusing is best-effort).
    public static List<String> remoteCommand(Task.TmuxConfig tmux) {
        List<String> command = new ArrayList<>();
        command.add("tmux");
        if (tmux.window() != null) {
            command.add("select-window");
            command.add("-t");
            command.add(tmux.session() + ":" + tmux.window());
        } else {
            command.add("has-session");
            command.add("-t");
            command.add(tmux.session());
        }
        command.addAll(List.of("&&",
                "tmux", "list-clients", "-F", "'#{session_name}=#{client_name}'",
                "|", "grep", "-v", "'^" + TmuxMirrorCommands.MIRROR_PREFIX + "'",
                "|", "cut", "-d", "=", "-f", "2-",
                "|", "xargs", "-r", "-I", "C",
                "tmux", "switch-client", "-c", "C", "-t", tmux.session()));
        return command;
    }

    /// True when the task's tmux window is **missing but expected**: on a
    /// suspended task (resume passes the pre-flip status) or with a recorded
    /// Claude context the window was ended and must be recreated — addressing
    /// the bare session would "succeed" on an arbitrary window. An *active*
    /// window-less task without `claude:` is a deliberate session-level config.
    ///
    /// The terminal mirror asks the same question ([com.contextswitcher.Main]'s
    /// preview): there, an arbitrary window means showing another task's
    /// session, so the pane places a placeholder instead.
    public static boolean windowGone(Task task) {
        Task.TmuxConfig tmux = task.tmux();
        return tmux != null && tmux.window() == null
                && (task.claude() != null || task.status() == TaskStatus.SUSPENDED);
    }

    @Override
    public ActionResult run(Task task) {
        Task.TmuxConfig tmux = task.tmux();
        String remote = TmuxHost.of(task);
        if (tmux == null || remote == null) {
            return ActionResult.failure("tmux not configured");
        }
        if (windowGone(task)) {
            return resurrect(task, remote, tmux, task.claude());
        }
        // [impl->dsn~tmux-window-ownership~4]
        // The task's own title as the belt: a window still named after this
        // task is this task's window even when a stray `@cs_session_id` says
        // otherwise — resurrecting on that would duplicate a live session.
        String owner = TmuxWindowOwnership.foreignOwner(
                ssh, remote, tmux, task.claude(), task.title());
        if (owner != null) {
            // Focusing would drop the user into someone else's Claude session,
            // which looks like a working switch — the worst outcome of the
            // three. Resurrecting is the repair: the task gets its own window
            // with its own session resumed, and the listener rewrites the
            // stale `window:` so the collision does not come back.
            Logger.info("Window {} on {} hosts Claude session {}, not the task's {} —"
                            + " resurrecting instead of focusing another task's session",
                    tmux.window(), remote, owner, task.claude().sessionId());
            ActionResult repaired = resurrect(task, remote, tmux, task.claude());
            return repaired.ok()
                    ? ActionResult.success("%s belonged to another session; %s"
                            .formatted(tmux.window(), repaired.detail()))
                    : repaired;
        }
        SshCommandRunner.SshResult result = ssh.run(remote, remoteCommand(tmux));
        if (result.ok()) {
            return ActionResult.success("focused %s on %s".formatted(tmux.session(), remote));
        }
        String window = tmux.window();
        // Only for an immutable window **id**: a `window:` given as a name
        // would resolve to any same-named window on the server, which says
        // nothing about the task's own one.
        String elsewhere = window == null || !window.startsWith("@")
                ? null
                : sessionOf(remote, window);
        if (elsewhere != null) {
            // The window is alive, only its *session* moved — resurrecting
            // here would create a duplicate and overwrite the task file's
            // correct window id with it, while the original keeps running
            // (and holds the Claude session `--resume` would want). The
            // recorded id is right, so it is left alone and the mismatch is
            // reported instead of papered over.
            return ActionResult.failure("window %s is in session %s, not %s — fix the session"
                    .formatted(window, elsewhere, tmux.session()));
        }
        // Focus failed and the window really is gone (or the whole server).
        // Resurrect even without a claude: section: the task gets a plain
        // window back (nothing resumed) instead of a dead chip.
        return resurrect(task, remote, tmux, task.claude());
    }

    /// The session a window id currently belongs to, or null when no window
    /// with that id exists on the server. Window ids are server-global, so
    /// this distinguishes "gone" from "moved" — the distinction that decides
    /// whether resurrecting is a repair or a duplicate.
    ///
    /// It moves without anyone moving it: the terminal mirror is a **grouped**
    /// session ([com.contextswitcher.terminal.TmuxMirrorCommands]), and a
    /// group survives as long as any member does — so when the base session
    /// dies, the mirror keeps the whole window group under `cs-mirror-<name>`
    /// and every `<session>:<window>` target starts failing at once.
    private @Nullable String sessionOf(String remote, String window) {
        SshCommandRunner.SshResult result = ssh.run(remote, List.of(
                "tmux", "display-message", "-p", "-t", "'" + window + "'", "'#{session_name}'"));
        return result.ok() && !result.stdout().isBlank() ? result.stdout().strip() : null;
    }

    /// Window (or whole tmux server, e.g. after a host reboot) is gone —
    /// recreate it, and resume Claude when the task recorded its context.
    // [impl->dsn~tmux-resurrect~7]
    private ActionResult resurrect(Task task, String remote, Task.TmuxConfig tmux,
            Task.@Nullable ClaudeConfig claude) {
        TmuxResurrect.Resurrected resurrected = resurrect.resurrect(remote, tmux, claude, task.title());
        if (resurrected == null) {
            return ActionResult.failure("window gone; resurrect failed (see log)");
        }
        resurrectListener.onResurrected(task, resurrected.windowId());
        ssh.run(remote, remoteCommand(new Task.TmuxConfig(tmux.session(), resurrected.windowId())));
        return ActionResult.success("resurrected as %s%s".formatted(resurrected.windowId(),
                resurrected.resumedClaude() ? " (claude --resume sent)" : ""));
    }
}
