package com.contextswitcher.terminal;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;

/// Command lines for the live terminal mirror. The pty attaches to a
/// **grouped session** (`tmux new-session -A -t <session>`): it shares the
/// base session's windows but has its **own current window**, so the mirror
/// can show the task's window without disturbing what the real session (and
/// its human user) has selected. Selecting another task's window later goes
/// through a plain ssh side channel (`select-window` on the mirror session)
/// instead of reconnecting the pty.
// [impl->dsn~terminal-pane~14]
public final class TmuxMirrorCommands {

    private TmuxMirrorCommands() {
    }

    /// Prefix of all mirror sessions — discovery ignores them: a grouped
    /// session shares the base session's windows, so `list-windows -a`
    /// would report every window twice and the import would duplicate
    /// tasks.
    public static final String MIRROR_PREFIX = "cs-mirror-";

    /// tmux format deciding whether a wheel event goes to the **application**
    /// rather than to tmux's own scrollback: true while a pane is in copy-mode
    /// (there `send-keys -M` *is* the scroll), or when the application asked
    /// for mouse reporting **outside** a mirror session. Inside a mirror the
    /// application's request is ignored, so full-screen mouse apps (Claude
    /// Code, vim) cannot swallow the wheel and the scrollback stays reachable.
    ///
    /// Key tables are server-global, so this condition — not the binding's
    /// existence — is what keeps the user's own sessions on tmux's default
    /// behaviour; there the format reduces to tmux's stock
    /// `#{||:#{pane_in_mode},#{mouse_any_flag}}`.
    private static final String WHEEL_TO_APPLICATION =
            "#{||:#{pane_in_mode},#{&&:#{mouse_any_flag},#{==:#{m:" + MIRROR_PREFIX + "*,#{session_name}},0}}}";

    /// The mirror session's name for a base session, e.g. `cs-mirror-0`.
    public static String mirrorName(String session) {
        return MIRROR_PREFIX + session.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /// The local argv attaching the mirror: `ssh -t` for a real tty, then
    /// `new-session -A` (attach or create) grouped onto the base session,
    /// `set-option mouse on` so the mouse wheel scrolls the window's tmux
    /// scrollback (enters copy-mode), plus the initial `select-window` when
    /// the task pins a window. `\;` reaches tmux as its command separator
    /// (same trick as the focus action).
    ///
    /// Mouse mode is set on the **mirror** session only (explicit `-t`), so the
    /// user's real session is untouched. Without it, a tmux attach owns the
    /// screen and the wheel has nothing to scroll — scrollback lives in tmux
    /// copy-mode, which mouse mode makes the wheel reach.
    ///
    /// The two `bind-key`s then keep the wheel out of the application in a
    /// mirror (see [#WHEEL_TO_APPLICATION]): stock tmux hands the wheel to
    /// whatever asked for mouse reporting, which made the wheel page through
    /// Claude Code's own message history instead of the window's scrollback.
    /// `WheelDownPane` is unbound in stock tmux (so an application with mouse
    /// reporting on receives it); binding it with no else-branch swallows the
    /// event in a mirror while reproducing the default everywhere else.
    ///
    /// The keepalive options make a **silently dropped network** surface as a
    /// pty exit within ~15 s (`ServerAliveInterval=5` × `ServerAliveCountMax=3`)
    /// instead of a half-open ssh that leaves the mirror frozen — that exit is
    /// what drives the auto-reconnect (`dsn~terminal-reconnect~2`). `ConnectTimeout`
    /// keeps each reconnect attempt from hanging while the link is still down,
    /// so the backoff counter advances toward the give-up cap.
    ///
    /// For [TmuxHost#LOCAL] the `ssh` half falls away and the same words go
    /// through a local `sh -c` instead ([LocalTmuxRunner]), and for a
    /// `wsl:<distro>` host through `wsl.exe` ([WslTmuxRunner]) — one shell on
    /// every route, so the `\;` separators and the quoted formats above mean
    /// the same thing on all three. The keepalives are an ssh concept and are
    /// dropped on both local routes: nothing can go half-open on this machine,
    /// and the pty exits when the shell does.
    // [impl->dsn~terminal-reconnect~2]
    // [impl->dsn~terminal-mouse-scroll~9]
    // [impl->dsn~ssh-command-runner~6]
    // [impl->dsn~terminal-local-mirror~2]
    // [impl->dsn~wsl-sessions~1]
    public static List<String> attachCommand(String host, Task.TmuxConfig tmux) {
        List<String> attach = new ArrayList<>(List.of(
                "tmux", "new-session", "-A", "-s", mirrorName(tmux.session()), "-t", tmux.session(),
                "\\;", "set-option", "-t", mirrorName(tmux.session()), "mouse", "on",
                "\\;", "bind-key", "-T", "root", "WheelUpPane",
                "if-shell", "-F", "'" + WHEEL_TO_APPLICATION + "'", "'send-keys -M'", "'copy-mode -e'",
                "\\;", "bind-key", "-T", "root", "WheelDownPane",
                "if-shell", "-F", "'" + WHEEL_TO_APPLICATION + "'", "'send-keys -M'"));
        if (tmux.window() != null) {
            attach.addAll(List.of("\\;", "select-window", "-t", selectTarget(tmux)));
        }
        // A local mirror runs the very same words through a local shell
        // instead of the remote's — see [LocalTmuxRunner].
        // [impl->dsn~terminal-local-mirror~2]
        if (TmuxHost.isLocal(host)) {
            return List.of(LocalTmuxRunner.SHELL, "-c", LocalTmuxRunner.script(attach));
        }
        // A WSL mirror is the same again, one transport further out: the pty
        // the pane spawns gives `wsl.exe` the terminal `ssh -t` asks for.
        // [impl->dsn~wsl-sessions~1]
        if (TmuxHost.isWsl(host)) {
            return WslTmuxRunner.argv(host, attach);
        }
        List<String> command = new ArrayList<>(List.of(ProcessSshRunner.sshExecutable(), "-t",
                "-o", "ServerAliveInterval=5",
                "-o", "ServerAliveCountMax=3",
                "-o", "ConnectTimeout=10",
                host));
        command.addAll(attach);
        return List.copyOf(command);
    }

    /// The remote argv switching the already-attached mirror to another
    /// window (fast path when the selection changes within one session).
    ///
    /// The chained `display-message` makes the mirror **say which window it
    /// really ended on** — free, in the same round-trip — so the caller
    /// confirms the view rather than the exit code. A `select-window` can
    /// report success and leave the pane elsewhere: a stranded mirror holds an
    /// old generation's windows (see [#repairMirrorCommand]), and then the
    /// pane shows another task's Claude session as if it were this task's.
    /// [#selectedWindow] reads the answer back.
    // [impl->dsn~terminal-pane~14]
    public static List<String> selectWindowCommand(Task.TmuxConfig tmux) {
        return List.of("tmux", "select-window", "-t", selectTarget(tmux),
                "\\;", "display-message", "-p", "-t", mirrorName(tmux.session()), "'#{window_id}'");
    }

    /// The remote argv asking the mirror **which window it is really
    /// showing** — [#selectWindowCommand]'s confirming half on its own, for
    /// the times nothing is being selected and the question is only "whose
    /// terminal is this?" ([TerminalPane] "Select the mirrored task").
    /// Read back with [#selectedWindow], like the select's own answer.
    // [impl->dsn~terminal-mirrored-task~1]
    public static List<String> currentWindowCommand(Task.TmuxConfig tmux) {
        return List.of("tmux", "display-message", "-p", "-t", mirrorName(tmux.session()),
                "'#{window_id}'");
    }

    /// The window id [#selectWindowCommand] printed, trimmed — empty output
    /// (an older tmux, a truncated line) yields null, which the caller reads
    /// as "cannot tell" rather than as a mismatch.
    public static @Nullable String selectedWindow(String stdout) {
        String window = stdout.strip();
        return window.isEmpty() ? null : window;
    }

    /// Run before every full attach: un-sticks a **stranded mirror** and says
    /// whether the task's window exists at all (`ok` / `gone` on stdout).
    ///
    /// A mirror is created grouped onto the base session, but `new-session -A`
    /// *attaches* an existing one by name and silently ignores `-t` — so once
    /// the base session dies and is recreated (a host reboot, "Restart running
    /// tasks…", `dsn~tmux-restart-running~1`), the mirror survives holding the
    /// old generation's windows and is never re-grouped. Every window of the
    /// new session is then `can't find window` and the pane shows the mirror's
    /// own current window instead: another task's Claude session.
    ///
    /// The stranded state is exact — a mirror whose group has no other member
    /// (`session_group_size` below 2) — so a healthy mirror is left alone.
    /// Its leftover windows are **moved into the base session** rather than
    /// killed with it: they are live Claude sessions the old base session was
    /// the last link to, and the sync imports them as tasks again. That base
    /// session is created first when it is gone rather than recreated
    /// (`move-window` into a missing session fails and would leave the
    /// windows stranded), and the mirror is never `kill-session`ed — tmux
    /// destroys a session it has emptied, so a kill could only ever fire
    /// after failed moves and would take the live windows with it.
    // [impl->dsn~terminal-pane~14]
    public static List<String> repairMirrorCommand(Task.TmuxConfig tmux) {
        String mirror = mirrorName(tmux.session());
        String window = tmux.window();
        return List.of("m=" + mirror + "; "
                + "if tmux has-session -t $m 2>/dev/null "
                + "&& [ $(tmux display-message -p -t $m '#{session_group_size}') -lt 2 ]; then "
                // The base session may be gone rather than recreated (the
                // field report of `dsn~stranded-mirror-windows~1`), and
                // `move-window` into a session that does not exist fails,
                // leaving every window where it was. Create it first, so the
                // repair covers that case and not only the reboot one.
                + "tmux has-session -t '" + tmux.session() + "' 2>/dev/null "
                + "|| tmux new-session -d -s '" + tmux.session() + "'; "
                + "for o in $(tmux list-windows -t $m -F '#{window_id}'); do "
                + "tmux move-window -d -s $o -t '" + tmux.session() + ":'; done; "
                // Deliberately no `kill-session -t $m`: tmux destroys a
                // session it has emptied, so the kill could only ever fire
                // after the moves had *failed* — taking with it the live
                // Claude windows the mirror was the last link to.
                + "fi; "
                + (window == null
                        ? "echo ok"
                        : "tmux list-windows -a -F '#{window_id}' | grep -qx '" + window + "'"
                                + " && echo ok || echo gone"));
    }

    /// True when [#repairMirrorCommand] reported the task's window as gone —
    /// attaching anyway would put the mirror's arbitrary current window on
    /// screen as if it were this task's.
    public static boolean windowGone(SshCommandRunner.SshResult repair) {
        return repair.ok() && repair.stdout().strip().equals("gone");
    }

    /// How many `C-u` the clear-input command sends. One kills one line, so
    /// a multi-line draft needs one per line; on an empty box `^U` does
    /// nothing, which makes overshooting free.
    // ponytail: a fixed count, not a "box is empty now" handshake — the
    // channel is write-only, and a draft past 40 lines is not a real one.
    public static final int CLEAR_INPUT_KILLS = 40;

    /// The remote argv discarding the mirrored window's typed-but-unsent
    /// input: `copy-mode -q` first (leave copy-mode, a no-op outside it),
    /// because while the pane is in copy-mode — where the mouse wheel puts
    /// it — a `^U` scrolls instead of reaching the application; then `C-u`
    /// (kill line) [#CLEAR_INPUT_KILLS] times. One tmux invocation, so the
    /// cancel is ordered before the kills. Addressed by session, like
    /// [#cancelCopyModeCommand].
    public static List<String> clearInputCommand(Task.TmuxConfig tmux) {
        List<String> command = new ArrayList<>(List.of("tmux",
                "copy-mode", "-q", "-t", mirrorName(tmux.session()),
                "\\;", "send-keys", "-t", mirrorName(tmux.session())));
        for (int i = 0; i < CLEAR_INPUT_KILLS; i++) {
            command.add("C-u");
        }
        return command;
    }

    /// The remote argv sending Ctrl+C to the mirrored window: `copy-mode -q`
    /// for the reason [#clearInputCommand] gives, then `C-c`. One press per
    /// click — Claude Code reads a single Ctrl+C as "interrupt", two in quick
    /// succession as "exit", so a click never quits the session by itself.
    // [impl->dsn~terminal-interrupt~1]
    public static List<String> interruptCommand(Task.TmuxConfig tmux) {
        return interruptCommand(mirrorName(tmux.session()));
    }

    /// As [#interruptCommand(Task.TmuxConfig)], aimed at `target` itself: the
    /// Android app has no mirror session and names the task's window
    /// (quoted by the caller where it may hold spaces).
    // [impl->dsn~android-terminal-interrupt~1]
    public static List<String> interruptCommand(String target) {
        return keyCommand(target, "C-c");
    }

    /// The remote argv pressing one tmux `key` (`Up`, `Space`, `Enter`, …) in
    /// `target`, after `copy-mode -q` as [#interruptCommand(String)] does: the
    /// phone's stand-in for a keyboard in Claude Code's selection prompts.
    // [impl->dsn~android-terminal-keys~1]
    public static List<String> keyCommand(String target, String key) {
        return List.of("tmux", "copy-mode", "-q", "-t", target, "\\;", "send-keys", "-t", target, key);
    }

    /// The remote argv accepting Claude Code's greyed-out prompt suggestion:
    /// `copy-mode -q` for the reason [#clearInputCommand] gives, `Tab` (fills
    /// the suggestion into the input box), then `Enter` a second later — an
    /// `Enter` in the same burst lands as a newline, the split
    /// `TmuxStatusPoller.continueCommand` uses.
    // [impl->dsn~terminal-accept-suggestion~1]
    public static List<String> acceptSuggestionCommand(Task.TmuxConfig tmux) {
        String target = mirrorName(tmux.session());
        return List.of("tmux", "copy-mode", "-q", "-t", target,
                "\\;", "send-keys", "-t", target, "Tab",
                "\\;", "run-shell", "'sleep 1'",
                "\\;", "send-keys", "-t", target, "Enter");
    }

    /// The remote argv leaving copy-mode in the mirror's current pane — the
    /// "jump to bottom" button. Scrolling the mirror means tmux copy-mode
    /// (see [#attachCommand]), so returning to the live output is `cancel`,
    /// not a scrollbar move. Outside copy-mode tmux just reports an error on
    /// the side channel's stderr; nothing reaches the terminal.
    public static List<String> cancelCopyModeCommand(Task.TmuxConfig tmux) {
        // Addressed by session, not window: the mirror's *current* window is
        // what the pane shows, and `tmux.window()` may be unset.
        return List.of("tmux", "send-keys", "-t", mirrorName(tmux.session()), "-X", "cancel");
    }

    /// Window addressed inside the mirror session — never the base session,
    /// whose current window must stay untouched.
    private static String selectTarget(Task.TmuxConfig tmux) {
        return mirrorName(tmux.session()) + ":" + tmux.window();
    }

    /// The key identifying a mirror connection: same remote and base
    /// session = same pty, only the window selection changes.
    public static String connectionKey(String remote, Task.TmuxConfig tmux) {
        return remote + " " + tmux.session();
    }

    /// Null when the configs address the same window (no select needed).
    public static @Nullable String changedWindow(@Nullable String currentWindow, Task.TmuxConfig tmux) {
        return tmux.window() == null || tmux.window().equals(currentWindow) ? null : tmux.window();
    }
}
