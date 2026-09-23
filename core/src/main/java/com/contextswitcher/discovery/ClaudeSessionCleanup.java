package com.contextswitcher.discovery;

import java.util.Collection;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;

/// Removes a deleted task's remote leftovers (the delete dialog's kill
/// checkboxes): the Claude transcript under `~/.claude/projects/` and the
/// task's working directory. Blocking — callers run it on a background
/// executor. An already-gone target counts as success (`rm -f`/`-rf`), like
/// [com.contextswitcher.switching.TmuxKillAction]; the tmux window kill
/// itself runs through the regular suspend orchestrator, not this helper.
// [impl->dsn~claude-session-kill~6]
public class ClaudeSessionCleanup {

    /// What the user ticked in the delete dialog. `endWindow` and
    /// `closeBrowserTabs` are independent — either can run without the other.
    public record Choices(boolean endWindow, boolean closeBrowserTabs,
            boolean removeTranscript, boolean removeWorkdir) {

        /// Whether anything beyond the window teardown was ticked.
        public boolean remoteCleanup() {
            return removeTranscript || removeWorkdir;
        }
    }

    private final SshCommandRunner ssh;

    public ClaudeSessionCleanup(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// `rm -f` of the session transcript. Claude stores it under the encoded
    /// **start** directory (`claude.cwd`), not the per-task workspace; `~` is
    /// left unquoted for the remote shell to expand.
    static List<String> transcriptCommand(String cwd, String sessionId) {
        return List.of("rm", "-f", "~/.claude/projects/"
                + ClaudeSessionLookup.encodeProjectDir(cwd) + "/" + sessionId + ".jsonl");
    }

    /// Why `workdir` must not be fed to `rm -rf`, or null when it is safe.
    /// Only an absolute path at least two levels deep, without `..` or a
    /// quote-breaking `'`, is accepted, and never one of `protectedDirs` —
    /// the directories the task's category configures (`mainCheckout`,
    /// `workspacesRoot`, `workdir`, [com.contextswitcher.tasks.GroupConfig#protectedDirs]).
    /// Callers pass the per-task `claude.workspace` only — `claude.cwd` may be
    /// the group's **shared** workspacesRoot, whose removal would take every
    /// sibling task with it — but a workspace can *be* a category directory
    /// (a session that ended up in the main checkout), and that is what this
    /// list catches.
    public static @Nullable String rejectWorkdir(String workdir,
            Collection<String> protectedDirs) {
        for (String dir : protectedDirs) {
            if (withoutTrailingSlash(dir).equals(withoutTrailingSlash(workdir))) {
                return "refusing to remove the category's own directory: " + workdir;
            }
        }
        if (!workdir.startsWith("/")) {
            return "not an absolute path: " + workdir;
        }
        if (workdir.contains("..") || workdir.contains("'")) {
            return "unsafe characters in path: " + workdir;
        }
        long depth = workdir.chars().filter(c -> c == '/').count()
                - (workdir.endsWith("/") ? 1 : 0);
        if (depth < 2) {
            return "refusing to remove a top-level directory: " + workdir;
        }
        return null;
    }

    private static String withoutTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /// `rm -rf` of the working directory, single-quoted like the launcher's
    /// `mkdir -p` (no double quotes — the Windows ssh.exe constraint).
    static List<String> workdirCommand(String workdir) {
        return List.of("rm", "-rf", "'" + workdir + "'");
    }

    /// Removes the transcript on `remote`; null on success, else a short
    /// human-readable failure for the status line.
    public @Nullable String removeTranscript(String remote, String cwd, String sessionId) {
        return run(remote, transcriptCommand(cwd, sessionId));
    }

    /// Removes the working directory on `remote` after the safety check;
    /// null on success, else a short human-readable failure.
    public @Nullable String removeWorkdir(String remote, String workdir,
            Collection<String> protectedDirs) {
        String rejected = rejectWorkdir(workdir, protectedDirs);
        if (rejected != null) {
            return rejected;
        }
        return run(remote, workdirCommand(workdir));
    }

    private @Nullable String run(String remote, List<String> command) {
        SshCommandRunner.SshResult result = ssh.run(remote, command);
        if (result.ok()) {
            return null;
        }
        return result.stderr().isBlank() ? "ssh exit " + result.exitCode() : result.stderr().strip();
    }
}
