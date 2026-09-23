package com.contextswitcher.switching;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;

/// Decides whether a task's recorded tmux window still hosts *that task's*
/// Claude session, from the `@cs_session_id` window option Claude Code's
/// `SessionStart` hook publishes.
///
/// A recorded `window:` can end up pointing at a window another task owns: the
/// id is written once and never re-verified, while `resurrect` can create a
/// second window for a session whose first one is still alive, and a task file
/// copied as the start of a new task carries the old id along. Nothing in the
/// tmux target itself catches this — window ids are server-global, and a
/// `session:window` target does not narrow them down: sessions in a **group**
/// (a mirror session, or a second session created with `new-session -t`) share
/// their whole window set, so `0:@53` and `old-group:@53` are the same window
/// and both targets succeed. Focusing then silently lands in another task's
/// Claude session — the exact confusion the terminal mirror's placeholder
/// already guards against for a *missing* window.
///
/// The published id is the only evidence that settles it, so a window that
/// publishes nothing (hook never ran, plain shell) is never called stolen.
// [impl->dsn~tmux-window-ownership~4]
public final class TmuxWindowOwnership {

    /// The tmux window user option Claude Code's `SessionStart` hook sets to
    /// the session's own id — the same one [com.contextswitcher.discovery.TmuxDiscovery] reads.
    public static final String OPTION = "@cs_session_id";

    private TmuxWindowOwnership() {
    }

    /// The remote argv printing one window's published session id and its
    /// name, separated by a `|` — empty before the separator for a window
    /// that never published an id. Quoted like every other tmux target here,
    /// since the remote shell sees the argv as a line.
    ///
    /// Both in one round-trip: the name is only a tie-breaker
    /// ([#foreignOwner]), never worth a second ssh slot on a path that runs
    /// on every switch. A window name may itself contain `|` (task titles are
    /// free text), the session id never can, so the **first** separator
    /// splits.
    public static List<String> command(String windowId) {
        return List.of("tmux", "display-message", "-p", "-t", "'" + windowId + "'",
                "'#{" + OPTION + "}|#{window_name}'");
    }

    /// True when `published` is a session id and it is **not** the task's.
    /// Both nulls and a blank publication mean "no evidence": the caller then
    /// behaves exactly as before this check existed, rather than treating an
    /// unhooked window as stolen.
    public static boolean hijacked(@Nullable String expected, @Nullable String published) {
        if (expected == null || published == null) {
            return false;
        }
        String owner = published.strip();
        return !owner.isEmpty() && !owner.equals(expected.strip());
    }

    /// Asks `remote` who owns the task's recorded window, returning that other
    /// Claude session's id when it is not the task's — else null, which is also
    /// the answer for every case that carries no evidence:
    ///
    /// - no window recorded, or one given as a *name*: a name resolves to any
    ///   same-named window on the server and says nothing about ownership;
    /// - no `claude.sessionId` on the task: nothing to compare against;
    /// - a failed round-trip: an unreachable host is not a theft, and treating
    ///   it as one would let every hiccup change what the caller does.
    ///
    /// `ownWindowName` is the belt against a **stray publication**: a Claude
    /// Code started outside tmux stamps `@cs_session_id` onto whichever window
    /// is current (an unguarded hook, see the README's remote setup), which is
    /// the mirror's — i.e. the task on screen. The id then looks foreign while
    /// the window is still the task's own, and the caller resurrects a second
    /// window for a session already running in the first (field report
    /// 2026-09-09). A window still carrying the task's title as its tmux name
    /// is that task's window whatever it publishes, so the theft is called off.
    ///
    /// Only the callers whose false-positive costs something pass a name.
    /// `null` keeps the id the sole evidence — the right choice wherever the
    /// mistake is unrecoverable ([com.contextswitcher.switching.TmuxKillAction]),
    /// since a task file copied to start a new one carries the old title along
    /// with the old window id and would then be allowed to kill the original's
    /// window.
    public static @Nullable String foreignOwner(SshCommandRunner ssh, String remote,
            Task.TmuxConfig tmux, Task.@Nullable ClaudeConfig claude,
            @Nullable String ownWindowName) {
        String window = tmux.window();
        if (window == null || !window.startsWith("@")
                || claude == null || claude.sessionId() == null) {
            return null;
        }
        SshCommandRunner.SshResult result = ssh.run(remote, command(window));
        if (!result.ok()) {
            return null;
        }
        String out = result.stdout();
        int separator = out.indexOf('|');
        String published = (separator < 0 ? out : out.substring(0, separator)).strip();
        String name = separator < 0 ? "" : out.substring(separator + 1).strip();
        if (ownWindowName != null && !name.isEmpty() && name.equals(ownWindowName.strip())) {
            return null;
        }
        return hijacked(claude.sessionId(), published) ? published : null;
    }
}
