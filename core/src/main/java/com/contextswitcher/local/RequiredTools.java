package com.contextswitcher.local;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Which of the command-line tools the app drives are missing from `PATH`.
///
/// Checked once on a fresh install, not on every start: a tool is installed
/// once and the check would otherwise be a startup cost (and a nag) forever.
/// The one that actually bites is `tmux` — without it a local Claude session
/// has nowhere to run, and the failure only shows up at the first click.
// [impl->dsn~startup-tool-check~1]
public final class RequiredTools {

    private RequiredTools() {
    }

    /// The tools a Linux/macOS install needs: `tmux` for local (and remote)
    /// sessions, `ssh` for every remote, `git` for the task-repo sync.
    public static final List<String> EXPECTED = List.of("tmux", "ssh", "git");

    /// Directories searched **besides** `PATH`. A GUI-launched app inherits
    /// the desktop session's environment, which on a Nix/Homebrew/`~/.local`
    /// setup is not the login shell's `PATH` — the tool is installed, the app
    /// just cannot see it. Searched in order, after `PATH`.
    static final List<String> EXTRA_DIRECTORIES = List.of(
            "/usr/bin", "/bin", "/usr/local/bin", "/opt/homebrew/bin",
            "/run/current-system/sw/bin",
            System.getProperty("user.home", "") + "/.nix-profile/bin",
            System.getProperty("user.home", "") + "/.local/bin");

    /// The tool's executable — from `PATH` first, then [#EXTRA_DIRECTORIES] —
    /// or null when no directory holds it.
    public static @org.jspecify.annotations.Nullable Path find(
            @org.jspecify.annotations.Nullable String path, String tool) {
        for (String directory : directories(path)) {
            Path candidate = executable(directory, tool);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /// [#find(String, String)] against this process's own `PATH`.
    public static @org.jspecify.annotations.Nullable Path find(String tool) {
        return find(System.getenv("PATH"), tool);
    }

    /// The tool's absolute path when it can be found, else its bare name — so
    /// a caller can always build an argv and let the OS report the failure.
    public static String resolve(String tool) {
        Path found = find(tool);
        return found == null ? tool : found.toString();
    }

    private static List<String> directories(@org.jspecify.annotations.Nullable String path) {
        List<String> directories = new ArrayList<>(
                List.of((path == null ? "" : path).split(File.pathSeparator, -1)));
        directories.addAll(EXTRA_DIRECTORIES);
        return directories;
    }

    /// Those of `tools` that are in none of the searched directories, in order.
    /// `path` is the raw `PATH` value; entries are separated by the platform's
    /// path separator, and a blank entry means the current directory (which is
    /// not where these tools live, so it is skipped).
    public static List<String> missing(@org.jspecify.annotations.Nullable String path,
            List<String> tools) {
        List<String> missing = new ArrayList<>();
        for (String tool : tools) {
            if (find(path, tool) == null) {
                missing.add(tool);
            }
        }
        return List.copyOf(missing);
    }

    /// The executable file `directory/tool`, or null. A blank directory —
    /// POSIX's "current directory" — is not searched: these tools do not live
    /// there, and running one from it would be a surprise.
    private static @org.jspecify.annotations.Nullable Path executable(String directory, String tool) {
        if (directory.isBlank()) {
            return null;
        }
        try {
            Path candidate = Path.of(directory, tool);
            return Files.isExecutable(candidate) && !Files.isDirectory(candidate) ? candidate : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /// The tools missing from this process's own `PATH`.
    public static List<String> missing() {
        return missing(System.getenv("PATH"), EXPECTED);
    }
}
