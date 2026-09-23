package com.contextswitcher.terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.RequiredTools;
import com.contextswitcher.tasks.Task;

/// Starts a local Claude session in a directory — the local counterpart of
/// `ClaudeWindowLauncher` (which needs ssh + tmux on a remote).
///
/// [#launch] creates a window in the local tmux server, the same place a
/// remote task's Claude lives — no terminal emulator is opened. The window id
/// it prints is recorded in the task file, so the app mirrors the session in
/// its terminal pane (`dsn~terminal-local-mirror~2`); `tmux attach -t 0`
/// reaches it from any other terminal just as well.
///
/// Windows has no tmux, so the app hosts the session itself instead:
/// [#ownedCommand] builds the argv and `TerminalPane.startOwned` runs it in
/// the pane's own ConPTY (`dsn~terminal-owned-session~3`).
// [impl->dsn~task-create-local~4]
public class LocalClaudeLauncher {

    /// The local tmux session local Claude windows are created in — the
    /// same default `usualSession` falls back to for a remote.
    public static final String SESSION = "0";

    /// What a launch produced: the tmux window id the session runs in (null on
    /// failure), and why it failed (null on success). The window id is what
    /// the task file records, so the pane can mirror the session
    /// (`dsn~terminal-local-mirror~2`).
    public record Launch(@Nullable String window, @Nullable String error) {
    }

    private final LocalCommandRunner runner;

    public LocalClaudeLauncher(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// Whether this task's Claude session is one the **app itself** hosts: on
    /// Windows, a task with no `remote:` and no tmux window — there is no tmux
    /// there to run it in and no host to open a window on
    /// (`dsn~terminal-owned-session~3`).
    /// Deliberately not gated on a `claude:` section: a task that has not been
    /// started yet is still an owned one, and gating on it left such a task
    /// with the remote flow's placeholder instead.
    // [impl->dsn~terminal-owned-session~3]
    public static boolean ownsSession(Task task) {
        return onWindows() && task.remote() == null && task.tmux() == null;
    }

    /// A queued message as typed input for an app-owned session: one
    /// bracketed paste, so a multi-line message lands in Claude's input box as
    /// one block, with line breaks as `CR` the way `tmux paste-buffer` sends
    /// them. Always bracketed — Claude Code always asks for it, and the widget
    /// keeps whether it was asked private. Not submitted: the caller sends the
    /// `Enter` after a pause.
    // [impl->dsn~terminal-owned-session~3]
    public static String ownedPaste(String text) {
        return ESC + "[200~" + text.replace("\r\n", "\r").replace('\n', '\r') + ESC + "[201~";
    }

    private static final char ESC = 27;

    /// The argv for a Claude session the **app itself** hosts in a ConPTY
    /// (`dsn~terminal-owned-session~3`) — the Windows answer to "type into a
    /// local session", where there is no tmux to mirror.
    ///
    /// `cmd /k` because `claude` is a `.cmd` shim and ConPTY starts the command
    /// line via `CreateProcess`, which does not apply `PATHEXT`; and because
    /// the shell stays open when Claude exits or is missing, so the pane shows
    /// the error instead of going blank. Newlines are folded to spaces — a
    /// command line is one line.
    ///
    /// With a `session` the argv resumes that transcript and passes neither
    /// model nor prompt: both belong to the conversation being continued.
    ///
    /// A `"` becomes `'`: `cmd` knows no escaped quote, so one ended the
    /// quoted prompt and `cmd` parsed the rest — `"<"` became an input
    /// redirect from a missing file, and Claude never started.
    // ponytail: `%NAME%` in the description is still expanded by cmd; deliver
    // the prompt through a file if that ever bites.
    public static List<String> ownedCommand(String description, @Nullable String model,
            @Nullable String session) {
        List<String> command = new ArrayList<>(List.of("cmd", "/k", "claude"));
        if (session != null) {
            command.add("--resume");
            command.add(session);
            return List.copyOf(command);
        }
        addClaudeArguments(command,
                description.replace("\r", " ").replace("\n", " ").replace('"', '\''), model);
        return List.copyOf(command);
    }

    /// The tmux argv for a Claude window in `directory`, named after its last
    /// path segment. `claude` and the prompt are separate argv elements — a
    /// local process, so no shell re-parses them and the description travels
    /// verbatim, quotes, semicolons and all.
    static List<String> tmuxCommand(String directory, String description, @Nullable String model) {
        List<String> command = new ArrayList<>(List.of(tmux(), "new-window",
                "-P", "-F", "#{window_id}", "-t", SESSION + ":",
                "-c", directory, "-n", windowName(directory), "claude"));
        addClaudeArguments(command, description, model);
        return List.copyOf(command);
    }

    /// As [#tmuxCommand], but creating the session — the fallback when the
    /// local tmux server has no session `0` yet (or none at all).
    static List<String> tmuxSessionCommand(String directory, String description,
            @Nullable String model) {
        List<String> command = new ArrayList<>(List.of(tmux(), "new-session", "-d",
                "-P", "-F", "#{window_id}", "-s", SESSION,
                "-c", directory, "-n", windowName(directory), "claude"));
        addClaudeArguments(command, description, model);
        return List.copyOf(command);
    }

    private static void addClaudeArguments(List<String> command, String description,
            @Nullable String model) {
        if (model != null && !model.isBlank()) {
            command.add("--model");
            command.add(model.strip());
        }
        String prompt = description.strip();
        if (!prompt.isEmpty()) {
            command.add(prompt);
        }
    }

    /// `tmux`, by absolute path where it can be found. A GUI-launched app
    /// inherits the desktop session's environment, not the login shell's, so
    /// an installed tmux under `~/.nix-profile/bin` or `/usr/local/bin` may be
    /// off this process's `PATH` — the tool is there, the app just cannot see
    /// it, and the launch failed as if it were missing.
    private static String tmux() {
        return RequiredTools.resolve("tmux");
    }

    /// The tmux window name: the directory's last segment, so the window
    /// reads as the workspace it runs in.
    private static String windowName(String directory) {
        Path path = Path.of(directory);
        Path name = path.getFileName();
        return name == null ? directory : name.toString();
    }

    /// Whether the app-owned ConPTY flow applies (else the tmux one).
    public static boolean onWindows() {
        return LocalCommandRunner.onWindows();
    }

    /// Starts the session in the local tmux server. On success the [Launch]
    /// carries the tmux window id it runs in, else why it failed — the caller puts that in the status bar, because a packaged app
    /// has no console to send the user to and "see log" is no help while the
    /// app is running.
    public Launch launch(String directory, String description, @Nullable String model) {
        try {
            // Like the remote flow's `mkdir -p`: a category's workspaces root
            // may not exist yet, and `tmux -c` refuses it.
            Files.createDirectories(Path.of(directory));
        } catch (IOException | InvalidPathException e) {
            Logger.warn("Cannot create {}: {}", directory, e.getMessage());
            return new Launch(null, "cannot create the directory: " + e.getMessage());
        }
        List<String> tmux = tmuxCommand(directory, description, model);
        Logger.info("Starting a local Claude session: {}", String.join(" ", tmux));
        LocalCommandRunner.LocalResult window = runner.run(tmux);
        if (window.ok()) {
            return new Launch(windowId(window), null);
        }
        // No session `0` yet (or no server at all) — the same fallback the
        // remote flow uses. Its failure is the one worth reporting: the
        // new-window failure above is expected on a fresh tmux.
        Logger.info("No tmux session {} ({}), creating it", SESSION, window.stderr().strip());
        LocalCommandRunner.LocalResult session =
                runner.run(tmuxSessionCommand(directory, description, model));
        return new Launch(session.ok() ? windowId(session) : null, detail(session));
    }

    /// The window id `-P -F '#{window_id}'` printed, or null when tmux
    /// printed nothing recognizable — a mirror without an id is better than a
    /// task file pointing at a window that does not exist.
    private static @Nullable String windowId(LocalCommandRunner.LocalResult result) {
        String printed = result.stdout().strip();
        return printed.startsWith("@") ? printed : null;
    }

    /// The failure text of a launch attempt — stderr where there is one, else
    /// the exit code, so the status bar always names something concrete.
    private static @Nullable String detail(LocalCommandRunner.LocalResult result) {
        if (result.ok()) {
            return null;
        }
        String stderr = result.stderr().strip();
        String message = stderr.isEmpty() ? "exit " + result.exitCode() : stderr;
        Logger.warn("Cannot start a local Claude session: {}", message);
        return message;
    }
}
