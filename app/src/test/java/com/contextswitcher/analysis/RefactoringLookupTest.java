package com.contextswitcher.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~refactoring-analysis-poller~1]
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
}
