package com.contextswitcher.analysis;

import java.util.List;

import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.terminal.TmuxHost;
import com.contextswitcher.terminal.WslTmuxRunner;

/// Command lines for RefactoringMiner on a task's remote (https://github.com/contextswitcher/contextswitcher-private/issues/50): the tunnelled
/// AST-diff web view and the refactoring-count run behind the badge. Pure
/// argv builders, mirroring [com.contextswitcher.terminal.TmuxMirrorCommands].
///
/// Same transport constraint as every remote command here: **no double quotes
/// anywhere** — Java's Windows argv encoding plus ssh.exe's parser swallow
/// them (`dsn~ssh-command-runner~6`) — so shell grouping uses single quotes
/// and the cleanup handler is a named function instead of a quoted `trap`
/// argument.
// [impl->dsn~refactoring-miner-commands~1]
public final class RefactoringMinerCommands {

    private RefactoringMinerCommands() {
    }

    /// The RefactoringMiner release the setup button installs.
    public static final String VERSION = "3.1.4";

    /// Where the setup button installs it: below the app's own remote config
    /// directory, so one `~/.contextswitcher` holds everything the app puts on
    /// a remote. `$HOME` is expanded by the remote shell and the resolved
    /// absolute path is echoed back, because the settings value is used
    /// verbatim in argv positions no shell expands.
    public static final String REMOTE_HOME = "$HOME/.contextswitcher/RefactoringMiner-" + VERSION;

    /// The remote argv installing the release zip once and printing the
    /// installed directory (last stdout line). Re-running is a no-op: an
    /// existing `bin/RefactoringMiner` skips the download. `curl`, else
    /// `wget`; a missing `java` fails here rather than at the first view.
    public static List<String> setupCommand() {
        String url = "https://github.com/tsantalis/RefactoringMiner/releases/download/"
                + VERSION + "/RefactoringMiner-" + VERSION + ".zip";
        String script = "command -v java >/dev/null || { echo no java on the remote >&2; exit 1; }; "
                + "H=" + REMOTE_HOME + "; "
                + "if [ ! -x $H/bin/RefactoringMiner ]; then "
                + "mkdir -p $(dirname $H) || exit 1; "
                + "Z=$H.zip; "
                + "{ curl -fsSL -o $Z " + url + " || wget -q -O $Z " + url + "; } || exit 1; "
                + "unzip -q -o $Z -d $(dirname $H) || exit 1; "
                + "rm -f $Z; fi; "
                + "[ -x $H/bin/RefactoringMiner ] || exit 1; "
                + "echo $H";
        return List.of("sh", "-c", SshCommandRunner.quote(script));
    }

    /// The local argv for the view: one long-lived ssh that forwards the port
    /// **and** runs RefactoringMiner's blocking `diff` server, so ending the
    /// process ends both. The remote script prepares a temp checkout of
    /// `baseBranch` via `git archive` (directory mode needs no git on the
    /// view side, so the live worktree — uncommitted work included — is
    /// `--dst`), starts the JVM in the background, and then blocks reading
    /// stdin: when the ssh connection ends — `Process.destroy()`, an app
    /// crash, a dropped network — stdin hits EOF and the script's cleanup
    /// kills the JVM and removes the temp base.
    ///
    /// That stdin watchdog is the teardown, deliberately not `ssh -tt` + an
    /// `EXIT` trap: without a local tty (the app spawns ssh from
    /// `ProcessBuilder`) the forced pty is not reliable and killing ssh
    /// orphaned the remote JVM in testing, holding the port and making every
    /// later view fail its bind. Consequence for the caller: keep the
    /// process's stdin pipe open and never write to it.
    ///
    /// `ExitOnForwardFailure` makes a busy local port fail the whole command
    /// instead of serving a stale view; the keepalives surface a dead network
    /// as a process exit (same rationale as the terminal mirror's attach).
    public static List<String> viewCommand(String remote, String rmHome, int port,
            String worktree, String baseBranch) {
        String script = "BASE=$(mktemp -d) || exit 1; JPID=; "
                + "cleanup() { kill $JPID 2>/dev/null; rm -rf $BASE; }; "
                + "trap cleanup EXIT HUP INT TERM; "
                + "git -C " + worktree + " archive " + baseBranch + " | tar -x -C $BASE || exit 1; "
                + rmHome + "/bin/RefactoringMiner diff --src $BASE --dst " + worktree + " & "
                + "JPID=$!; cat >/dev/null; exit 0";
        // WSL2 already shares the loopback interface with Windows, so the
        // port the view serves inside the distribution is reachable at
        // 127.0.0.1 without any tunnel — the `-L` forward and the keepalives
        // guarding it are ssh concepts with nothing to guard here.
        // [impl->dsn~wsl-sessions~1]
        if (TmuxHost.isWsl(remote)) {
            return WslTmuxRunner.argv(remote, List.of(script));
        }
        return List.of(ProcessSshRunner.sshExecutable(),
                "-o", "BatchMode=yes",
                "-o", "ExitOnForwardFailure=yes",
                "-o", "ServerAliveInterval=5",
                "-o", "ServerAliveCountMax=3",
                "-L", port + ":127.0.0.1:" + port,
                remote,
                "sh", "-c", SshCommandRunner.quote(script));
    }

    /// The address the served view answers on. `/list`, not `/`: the root
    /// 302-redirects there, so both the connect-poll (expecting 200) and the
    /// browser open must target `/list` (spiked 2026-07-23).
    public static String listUrl(int port) {
        return "http://127.0.0.1:" + port + "/list";
    }

    /// The remote argv printing the worktree's HEAD and its merge-base with
    /// `baseBranch` (two lines) — the cheap per-tick probe that decides
    /// whether the expensive count run is needed at all: same HEAD as the
    /// cached summary → skip, HEAD equal to the merge-base → zero commits to
    /// analyze, count 0 without starting a JVM.
    public static List<String> countProbeCommand(String worktree, String baseBranch) {
        return List.of("git", "-C", worktree, "rev-parse", "HEAD",
                "&&", "git", "-C", worktree, "merge-base", baseBranch, "HEAD");
    }

    /// The remote argv running the refactoring count for the committed range
    /// merge-base(`baseBranch`)..HEAD and printing the result JSON on stdout
    /// (RM's `-json` insists on a file, so a `mktemp` is catted and removed).
    /// `--git-common-dir` because `-bc` reads the repository via JGit, which
    /// cannot resolve a **linked worktree's** `.git` file — the common dir is
    /// the real repository either way. `--path-format=absolute`: from a linked
    /// worktree the common dir would otherwise print relative.
    public static List<String> countCommand(String rmHome, String worktree, String baseBranch) {
        return List.of("cd", worktree,
                "&&", "OUT=$(mktemp)",
                "&&", rmHome + "/bin/RefactoringMiner",
                "-bc", "$(git rev-parse --path-format=absolute --git-common-dir)",
                "$(git merge-base " + baseBranch + " HEAD)",
                "$(git rev-parse HEAD)",
                "-json", "$OUT", ">/dev/null", "2>&1;",
                "S=$?;", "cat", "$OUT;", "rm", "-f", "$OUT;", "exit", "$S");
    }
}
