package com.contextswitcher.analysis;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~refactoring-analysis-poller~2]
class RefactoringLookupTest {

    // The shape RM 3.1.4's `-bc … -json` actually writes (captured live
    // 2026-07-29): a `commits` array, each with its own `refactorings`.
    @Test
    void countsRefactoringsAcrossAllCommits() {
        String json = """
                {
                "commits": [
                {
                \t"repository": "git@github.com:contextswitcher/contextswitcher.git",
                \t"sha1": "b6f3fb596794a1939b49ed6c34d263152ee23803",
                \t"url": "",
                \t"refactorings": []
                },
                {
                \t"repository": "git@github.com:contextswitcher/contextswitcher.git",
                \t"sha1": "8ad8d03503bf14bb1c5da1cb4288010bec505726",
                \t"url": "",
                \t"refactorings": [{
                \t"type": "Extract Method",
                \t"description": "Extract Method private scrollRowIntoView(...)"
                }, {
                \t"type": "Rename Method",
                \t"description": "Rename Method foo() to bar()"
                }]
                }]
                }
                """;

        assertThat(RefactoringLookup.countRefactorings(json)).isEqualTo(2);
    }

    @Test
    void emptyCommitListCountsZero() {
        assertThat(RefactoringLookup.countRefactorings("{\"commits\": []}")).isZero();
    }

    // Garbage (an RM crash message, a truncated file) must read as "unknown",
    // never as a count — the poller then keeps the previous summary.
    @Test
    void unparseableOutputIsNullNotZero() {
        assertThat(RefactoringLookup.countRefactorings("Exception in thread main ...")).isNull();
        assertThat(RefactoringLookup.countRefactorings("{}")).isNull();
        assertThat(RefactoringLookup.countRefactorings("")).isNull();
    }

    // A run that fails or times out (RM on a huge range) must still pin the
    // HEAD — otherwise every tick starts another full-CPU JVM on the remote.
    @Test
    void failedCountRunPinsTheHeadSoItIsNotRetried() {
        SshCommandRunner ssh = new SshCommandRunner() {
            @Override
            public SshResult run(String host, List<String> remoteCommand) {
                return remoteCommand.contains("-bc")
                        ? new SshResult(124, "", "")
                        : new SshResult(0, "abc\nbase\n", "");
            }

            @Override
            public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
                throw new UnsupportedOperationException();
            }
        };

        RefactoringSummary summary = new RefactoringLookup(ssh)
                .summary("devbox", "/rm", "/wt", "origin/main", null);

        assertThat(summary).isEqualTo(new RefactoringSummary("abc", 0));
    }
}
