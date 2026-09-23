package com.contextswitcher.analysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~refactoring-miner-commands~1]
class RefactoringMinerCommandsTest {

    private static final String RM = "/data/koppor/RefactoringMiner-3.1.4";
    private static final String WT = "/data/koppor/ws/2026-07-29-task";

    @Test
    void viewCommandForwardsThePortAndRunsTheQuotedWatchdogScript() {
        List<String> command = RefactoringMinerCommands.viewCommand(
                "devbox", RM, 6789, WT, "origin/main");

        assertThat(command).containsSubsequence("-L", "6789:127.0.0.1:6789", "devbox", "sh", "-c");
        assertThat(command).containsSubsequence("-o", "ExitOnForwardFailure=yes");
        String script = command.getLast();
        assertThat(script).startsWith("'").endsWith("'");
        assertThat(script)
                .contains("git -C " + WT + " archive origin/main")
                .contains(RM + "/bin/RefactoringMiner diff --src $BASE --dst " + WT)
                .contains("trap cleanup EXIT HUP INT TERM");
    }

    // The teardown is the stdin watchdog: the remote script blocks on `cat`
    // and cleans up on EOF. `-n` would EOF stdin instantly (no view), and a
    // forced pty (`-t`) is what the watchdog replaces — without a local tty
    // it left an orphan JVM holding the port (verified live 2026-07-29).
    @Test
    void viewCommandKeepsStdinOpenAndAllocatesNoPty() {
        List<String> command = RefactoringMinerCommands.viewCommand(
                "devbox", RM, 6789, WT, "origin/main");

        assertThat(command).doesNotContain("-n", "-t", "-tt");
        assertThat(command.getLast()).contains("cat >/dev/null");
    }

    // Windows ssh.exe swallows double quotes on the way to the remote shell
    // (`dsn~ssh-command-runner~6`) — no command part may carry one.
    @Test
    void noCommandCarriesADoubleQuote() {
        assertThat(RefactoringMinerCommands.viewCommand("h", RM, 6789, WT, "origin/main"))
                .noneMatch(part -> part.contains("\""));
        assertThat(RefactoringMinerCommands.countProbeCommand(WT, "origin/main"))
                .noneMatch(part -> part.contains("\""));
        assertThat(RefactoringMinerCommands.countCommand(RM, WT, "origin/main"))
                .noneMatch(part -> part.contains("\""));
    }

    // [utest->dsn~refactoring-miner-setup~1]
    @Test
    void setupCommandInstallsThePinnedReleaseBelowTheRemoteConfigDirAndPrintsIt() {
        List<String> command = RefactoringMinerCommands.setupCommand();

        assertThat(command).containsSubsequence("sh", "-c");
        String script = command.getLast();
        assertThat(script).startsWith("'").endsWith("'");
        assertThat(script)
                .contains("command -v java")
                .contains("$HOME/.contextswitcher/RefactoringMiner-"
                        + RefactoringMinerCommands.VERSION)
                .contains("https://github.com/tsantalis/RefactoringMiner/releases/download/"
                        + RefactoringMinerCommands.VERSION + "/RefactoringMiner-"
                        + RefactoringMinerCommands.VERSION + ".zip")
                // Idempotent: an existing install skips the download …
                .contains("if [ ! -x $H/bin/RefactoringMiner ]")
                // … and the resolved directory is the last thing printed.
                .endsWith("echo $H'");
        assertThat(command).noneMatch(part -> part.contains("\""));
    }

    // `/list`, not `/`: the root 302-redirects, so a 200 poll on `/` never
    // succeeds and the browser must land on `/list` directly.
    @Test
    void listUrlTargetsTheListPage() {
        assertThat(RefactoringMinerCommands.listUrl(6789))
                .isEqualTo("http://127.0.0.1:6789/list");
    }

    @Test
    void countProbePrintsHeadThenMergeBase() {
        assertThat(String.join(" ", RefactoringMinerCommands.countProbeCommand(WT, "origin/main")))
                .isEqualTo("git -C " + WT + " rev-parse HEAD"
                        + " && git -C " + WT + " merge-base origin/main HEAD");
    }

    // `-bc` reads the repository via JGit, which cannot resolve a linked
    // worktree's `.git` file — the command must hand it the (absolute)
    // common dir instead of the worktree path.
    @Test
    void countCommandUsesTheAbsoluteGitCommonDir() {
        String joined = String.join(" ", RefactoringMinerCommands.countCommand(
                RM, WT, "origin/main"));

        assertThat(joined)
                .contains("-bc $(git rev-parse --path-format=absolute --git-common-dir)")
                .contains("$(git merge-base origin/main HEAD)")
                .contains(RM + "/bin/RefactoringMiner")
                .contains("cat $OUT");
    }
}
