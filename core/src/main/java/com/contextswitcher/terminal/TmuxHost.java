package com.contextswitcher.terminal;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.tasks.Task;

/// Where a task's tmux server lives: its `remote:` host, or **this machine**
/// when the task has a `tmux:` section without one — the shape a local Claude
/// session leaves behind (`dsn~task-create-local~4`).
///
/// The local case needs a name because everything tmux-facing is addressed by
/// host: the mirror ([TmuxMirrorCommands]), the kill on suspend/delete, the
/// pane snapshot, the status poll. They all keep taking a host string and get
/// their commands routed by [HostCommandRunner]; only the transport differs.
// [impl->dsn~terminal-local-mirror~2]
public final class TmuxHost {

    private TmuxHost() {
    }

    /// The pseudo-host of this machine's own tmux server. Parentheses keep it
    /// apart from any real host name or ssh alias.
    public static final String LOCAL = "(local)";

    /// Whether `host` addresses this machine's own tmux server.
    public static boolean isLocal(String host) {
        return LOCAL.equals(host);
    }

    /// The prefix of a **WSL** host: `wsl:` alone is the default
    /// distribution, `wsl:Ubuntu` a named one. A WSL session is a remote in
    /// every way that matters — it has a `remote:` line, a tmux window and a
    /// mirror — so only the transport differs ([WslTmuxRunner]).
    // [impl->dsn~wsl-sessions~1]
    public static final String WSL_PREFIX = "wsl:";

    /// Whether `host` addresses a tmux server inside WSL.
    // [impl->dsn~wsl-sessions~1]
    public static boolean isWsl(String host) {
        return host.startsWith(WSL_PREFIX);
    }

    /// The distribution a `wsl:<distro>` host names; blank for `wsl:`, which
    /// means whichever distribution `wsl.exe` defaults to.
    // [impl->dsn~wsl-sessions~1]
    public static String distro(String host) {
        return host.substring(WSL_PREFIX.length()).strip();
    }

    /// The host string for a distribution (blank: WSL's default).
    // [impl->dsn~wsl-sessions~1]
    public static String wslHost(String distro) {
        return WSL_PREFIX + distro.strip();
    }

    /// The host of the task's tmux window — null when it configures none.
    public static @Nullable String of(Task task) {
        if (task.tmux() == null) {
            return null;
        }
        return task.remote() == null ? LOCAL : task.remote();
    }
}
