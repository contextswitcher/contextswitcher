package com.contextswitcher.switching;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

import org.tinylog.Logger;

/// "Show diff" for an app-owned local session (`dsn~terminal-owned-session~3`).
/// There is no tmux to open a throwaway window in ([TaskDiffWindow]), so git
/// runs on this machine and its output is handed back as text for a read-only
/// terminal view. Same choice of what to show: tracked changes when there are
/// any, else the last commit.
// [impl->dsn~terminal-owned-session~3]
public final class LocalDiff {

    private LocalDiff() {
    }

    private record Output(int exitCode, String text) {
    }

    /// Colors forced: the output goes to a pipe, which git would otherwise
    /// render plain. No pager — there is no terminal for one to page in.
    static List<String> diffCommand(boolean clean) {
        return clean
                ? List.of("git", "-c", "color.ui=always", "--no-pager", "show")
                : List.of("git", "-c", "color.ui=always", "--no-pager", "diff", "HEAD");
    }

    /// The diff of `workspace` as ANSI-colored text, or one line saying why
    /// there is none. Blocking.
    public static String render(String workspace) {
        Path dir;
        try {
            dir = Path.of(workspace);
        } catch (InvalidPathException e) {
            return "Not a usable directory: " + workspace;
        }
        if (!Files.isDirectory(dir)) {
            return "No such directory: " + workspace;
        }
        if (run(dir, List.of("git", "rev-parse", "--git-dir")).exitCode() != 0) {
            return "Not a git repository: " + workspace;
        }
        // Exit 1 is "has changes"; anything else non-zero (no commit yet) falls
        // through to `diff HEAD`, whose own error then says what is wrong.
        boolean clean = run(dir, List.of("git", "diff", "--quiet", "HEAD")).exitCode() == 0;
        Output diff = run(dir, diffCommand(clean));
        if (diff.exitCode() != 0) {
            return "git failed in " + workspace + ":\n" + diff.text();
        }
        return diff.text().isBlank() ? "No changes in " + workspace + "." : diff.text();
    }

    private static Output run(Path dir, List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Output(process.waitFor(), text);
        } catch (IOException e) {
            Logger.warn("Cannot run {} in {}: {}", command, dir, e.getMessage());
            return new Output(-1, e.getMessage() == null ? "" : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Output(-1, "Interrupted.");
        }
    }
}
