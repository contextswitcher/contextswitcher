package com.contextswitcher.switching;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Opens a throwaway tmux window named `diff` in a task's workspace showing
/// its git diff — the terminal pane's "Show diff" button. Uncommitted
/// (tracked) changes when there are any, else the last commit (`git show` —
/// Claude may have committed its work already). Both page through git's own
/// configured pager, so with e.g. [delta](https://github.com/dandavison/delta)
/// as `core.pager` on the remote the diff arrives syntax-highlighted and
/// navigable; a plain `less` works out of the box. The window lives exactly
/// as long as the command: quitting the pager (and confirming with Enter)
/// closes it, and tmux returns to the previously selected window.
///
/// A worktree Claude already deleted (mainline development: commit, then
/// `git worktree remove`) leaves nothing to open. For that case the category
/// names a permanent `mainCheckout` and the task records the `@cs_commit` the
/// session published — the window then opens in the checkout and shows that
/// commit (`dsn~diff-after-worktree-removal~1`).
// [impl->dsn~terminal-diff-window~2]
public final class TaskDiffWindow {

    /// The shell command the window runs. The `rev-parse` guard turns a
    /// workspace that is no git worktree (e.g. a task whose recorded cwd is
    /// the workspaces root) into one readable line instead of git's full
    /// `--no-index` usage dump; then `--quiet` decides the mode: tracked
    /// changes present → show them; a clean worktree → show the last commit.
    /// The trailing `read` keeps the window open after the pager quit —
    /// git's default `less -FRX` exits by itself when a short diff fits one
    /// screen, and the window must not flash away before it was read.
    /// Deliberately **no quotes of either kind**: double quotes are swallowed
    /// by Windows ssh.exe on the way (the constraint documented in
    /// `dsn~message-queue-send~5` — this command once carried a
    /// `printf "…"` and arrived mangled), and single quotes would need
    /// escaping through the single-quoted transport.
    // ponytail: untracked new files never show (git diff cannot); RefactoringMiner (https://github.com/contextswitcher/contextswitcher-private/issues/50) is the full answer
    static final String DIFF_COMMAND =
            "if ! git rev-parse --git-dir >/dev/null 2>&1; then echo Not a git repository: $PWD; "
                    + "elif git diff --quiet HEAD 2>/dev/null; then git show; "
                    + "else git diff HEAD; fi; "
                    + "echo; echo Enter closes this window...; read -r _";

    /// The command for a task whose worktree may already be gone: `cd` into the
    /// workspace and behave exactly like [#DIFF_COMMAND] while it is there,
    /// else fall back to `git show <commit>` in the window's own directory (the
    /// group's `mainCheckout`, which the caller made the window's cwd). The
    /// `cd` **is** the existence test — no extra ssh round-trip to probe it.
    /// Same no-quotes rule as [#DIFF_COMMAND]; both invariants are pinned by
    /// the unit test.
    // [impl->dsn~diff-after-worktree-removal~1]
    static String diffCommand(String workspace, @Nullable String commit) {
        if (commit == null) {
            return DIFF_COMMAND;
        }
        return "if cd " + workspace + " 2>/dev/null "
                + "&& git rev-parse --git-dir >/dev/null 2>&1; then "
                + "if git diff --quiet HEAD 2>/dev/null; then git show; else git diff HEAD; fi; "
                + "elif git rev-parse --git-dir >/dev/null 2>&1; then "
                + "echo Worktree " + workspace + " is gone - showing commit " + commit + "; echo; "
                + "git show " + commit + "; "
                + "else echo Not a git repository: $PWD; fi; "
                + "echo; echo Enter closes this window...; read -r _";
    }

    private TaskDiffWindow() {
    }

    /// `tmux new-window` with a command instead of a shell, so the window
    /// closes when the pager round ends; otherwise shaped like
    /// [TmuxResurrect#newWindowCommand] (`-P -F` prints the new id).
    static List<String> newWindowCommand(String session, String cwd, String workspace,
            @Nullable String commit) {
        return List.of("tmux", "new-window", "-t", "'" + session + ":'",
                "-c", "'" + cwd + "'",
                "-n", "diff", "-P", "-F", "'#{window_id}'",
                SshCommandRunner.quote(diffCommand(workspace, commit)));
    }

    /// Opens the diff window in `session` for `workspace`. When the task
    /// recorded a `commit` **and** its category a `mainCheckout`, the window is
    /// started in that permanent checkout instead — it always exists, so the
    /// window opens even after Claude deleted the worktree, and the command
    /// then decides which of the two to show. Blocking — run on a background
    /// executor. Returns the new immutable window id, or null with the failure
    /// logged.
    // [impl->dsn~diff-after-worktree-removal~1]
    public static @Nullable String open(SshCommandRunner ssh, String remote, String session,
            String workspace, @Nullable String mainCheckout, @Nullable String commit) {
        boolean fallback = mainCheckout != null && commit != null;
        String cwd = fallback ? mainCheckout : workspace;
        SshCommandRunner.SshResult created =
                ssh.run(remote, newWindowCommand(session, cwd, workspace,
                        fallback ? commit : null));
        if (!created.ok() || created.stdout().isBlank()) {
            Logger.warn("Cannot open a diff window in session {} on {}: {}",
                    session, remote, created.stderr().strip());
            return null;
        }
        return created.stdout().strip();
    }
}
