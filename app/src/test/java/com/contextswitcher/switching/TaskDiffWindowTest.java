package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-diff-window~2]
class TaskDiffWindowTest {

    /// Scripted ssh runner: pops the given results, records every argv.
    private static final class FakeSsh implements SshCommandRunner {
        final List<List<String>> calls = new ArrayList<>();
        final List<SshResult> results;

        FakeSsh(SshResult... scripted) {
            this.results = new ArrayList<>(List.of(scripted));
        }

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            calls.add(remoteCommand);
            return results.removeFirst();
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    }

    @Test
    void windowCommandTargetsSessionRunsDiffCommandAndPrintsWindowId() {
        String workspace = "/data/koppor/jabref-workspaces/fix-npe";
        assertThat(TaskDiffWindow.newWindowCommand("2", workspace, workspace, null))
                .containsExactly("tmux", "new-window", "-t", "'2:'",
                        "-c", "'/data/koppor/jabref-workspaces/fix-npe'",
                        "-n", "diff", "-P", "-F", "'#{window_id}'",
                        SshCommandRunner.quote(TaskDiffWindow.DIFF_COMMAND));
    }

    @Test
    void diffCommandFallsBackToLastCommitAndHoldsTheWindowOpen() {
        // Non-repo workspace → one readable line; uncommitted tracked changes
        // → git diff HEAD; clean → git show; the trailing read holds the
        // window (git's less -FRX exits by itself on a one-screen diff — the
        // window must not flash away unread).
        assertThat(TaskDiffWindow.DIFF_COMMAND)
                .startsWith("if ! git rev-parse --git-dir")
                .contains("elif git diff --quiet HEAD")
                .contains("then git show; else git diff HEAD; fi")
                .endsWith("read -r _");
    }

    @Test
    void diffCommandCarriesNoQuotesOfEitherKind() {
        // Single quotes would need escaping through the single-quoted
        // transport; double quotes are swallowed by Windows ssh.exe
        // (dsn~message-queue-send~5) — ~1 shipped a printf "\n…" and its
        // quotes never reached the remote.
        assertThat(TaskDiffWindow.DIFF_COMMAND).doesNotContain("'").doesNotContain("\"");
    }

    @Test
    void openReturnsTheNewWindowId() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "@42\n", ""));

        assertThat(TaskDiffWindow.open(ssh, "h", "2", "/w", null, null)).isEqualTo("@42");
        assertThat(ssh.calls.getFirst().get(1)).isEqualTo("new-window");
    }

    @Test
    void openReturnsNullOnFailure() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(1, "", "no such session"));

        assertThat(TaskDiffWindow.open(ssh, "h", "2", "/w", null, null)).isNull();
    }

    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void withoutBothCheckoutAndCommitTheWindowStaysInTheWorkspace() {
        // Half the configuration is no configuration: a category without
        // mainCheckout, or a task whose session never published @cs_commit,
        // must behave exactly as before — window in the workspace, plain
        // DIFF_COMMAND, no fallback branch.
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "@1", ""),
                new SshCommandRunner.SshResult(0, "@2", ""));

        TaskDiffWindow.open(ssh, "h", "2", "/w", "/main", null);
        TaskDiffWindow.open(ssh, "h", "2", "/w", null, "abc1234");

        for (List<String> call : ssh.calls) {
            assertThat(call).contains("'/w'")
                    .contains(SshCommandRunner.quote(TaskDiffWindow.DIFF_COMMAND));
        }
    }

    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void withCheckoutAndCommitTheWindowOpensInTheCheckout() {
        // The worktree may already be deleted, so the window must start in the
        // permanent checkout — a -c into a gone directory fails outright and
        // no window would appear at all.
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "@42", ""));

        TaskDiffWindow.open(ssh, "h", "2", "/w", "/data/koppor/jabref", "abc1234");

        assertThat(ssh.calls.getFirst()).contains("'/data/koppor/jabref'");
    }

    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void fallbackCommandPrefersTheLiveWorktreeAndShowsTheCommitOnlyWhenItIsGone() {
        String command = TaskDiffWindow.diffCommand("/w", "abc1234");

        // The cd is the existence test: while /w is there the command is the
        // old one (uncommitted changes, else the last commit); only a failed
        // cd reaches git show <sha>.
        assertThat(command)
                .startsWith("if cd /w 2>/dev/null && git rev-parse --git-dir")
                .contains("then git show; else git diff HEAD; fi")
                .contains("git show abc1234")
                .contains("Not a git repository: $PWD")
                .endsWith("read -r _");
        assertThat(command.indexOf("git diff HEAD")).isLessThan(command.indexOf("git show abc1234"));
    }

    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void fallbackCommandCarriesNoQuotesOfEitherKind() {
        // Same transport constraint as DIFF_COMMAND — the fallback interpolates
        // a path and a sha, so it is the likelier one to grow a quote.
        assertThat(TaskDiffWindow.diffCommand("/w", "abc1234"))
                .doesNotContain("'").doesNotContain("\"");
    }
}
