package com.contextswitcher.tasks;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~merged-task-cleanup~2]
class MergedCleanupTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 12, 0);
    private static final String BORING = """
            > commit and push the fix

            ● Pushed to origin/main. The PR is merged, the worktree is gone.

            ╭──────────────────────────────────────╮
            │ >                                    │
            ╰──────────────────────────────────────╯
            """;

    @Test
    void anUnremarkableLastScreenNeedsNobody() {
        assertThat(MergedCleanup.needsHuman(BORING)).isFalse();
    }

    @Test
    void aQuestionAnErrorAndTheUsageLimitAllNeedAHuman() {
        assertThat(MergedCleanup.needsHuman("Do you want to proceed?\n❯ 1. Yes\n  2. No")).isTrue();
        assertThat(MergedCleanup.needsHuman("API Error: Connection lost mid-response.")).isTrue();
        assertThat(MergedCleanup.needsHuman("Claude usage limit reached — resets at 3pm")).isTrue();
        assertThat(MergedCleanup.needsHuman("● Running tests… (esc to interrupt)")).isTrue();
    }

    @Test
    void aScreenNobodyCouldCaptureCountsAsNeedingAHuman() {
        assertThat(MergedCleanup.needsHuman(null)).isTrue();
    }

    @Test
    void escapeSequencesDoNotHideTheQuestion() {
        assertThat(MergedCleanup.needsHuman("\u001B[1;33mDo you want\u001B[0m to proceed?")).isTrue();
        assertThat(MergedCleanup.plain("\u001B[1;33mdone\u001B[0m")).isEqualTo("done");
    }

    @Test
    void anUnremarkableScreenIsCleanedUpRightAway() {
        assertThat(MergedCleanup.cleanUpNow(BORING, null, 7, NOW)).isTrue();
    }

    @Test
    void aScreenThatWantsAHumanWaitsForTheGracePeriod() {
        String pane = "Do you want to delete the branch?";
        assertThat(MergedCleanup.cleanUpNow(pane, null, 7, NOW)).isFalse();
        assertThat(MergedCleanup.cleanUpNow(pane, "2026-09-06 09:00", 7, NOW)).isFalse();
        assertThat(MergedCleanup.cleanUpNow(pane, "2026-09-03 09:00", 7, NOW)).isTrue();
    }

    @Test
    void zeroDaysTurnsTheJanitorOff() {
        assertThat(MergedCleanup.cleanUpNow(BORING, "2026-01-01 09:00", 0, NOW)).isFalse();
    }

    /// Field report 2026-09-12: a task created from a prompt quoting a
    /// long-merged pull request was archived and its window killed three
    /// seconds after "Add task" — the poll had not yet seen the window, so
    /// Claude was still booting and the screen showed nothing alarming.
    @Test
    void anActiveTaskWhoseWindowThePollHasNotSeenIsNotJudged() {
        assertThat(MergedCleanup.judgeable(task(TaskStatus.ACTIVE), null)).isFalse();
        assertThat(MergedCleanup.judgeable(task(TaskStatus.ACTIVE), "idle")).isTrue();
        // A suspended task has no window for the poll to see; its own route
        // (stored snapshot, then the grace period) still applies.
        assertThat(MergedCleanup.judgeable(task(TaskStatus.SUSPENDED), null)).isTrue();
    }

    @Test
    void aBusyOrEndedOrPrLessTaskIsNotJudged() {
        assertThat(MergedCleanup.judgeable(task(TaskStatus.ACTIVE), "working")).isFalse();
        assertThat(MergedCleanup.judgeable(task(TaskStatus.DONE), "idle")).isFalse();
        assertThat(MergedCleanup.judgeable(new Task("g/no-pr", "No PR", TaskStatus.ACTIVE,
                "host", null, null, null, null, new Task.ClaudeConfig("/w", null, null), null, ""), "idle"))
                .isFalse();
        assertThat(MergedCleanup.judgeable(new Task("g/no-claude", "No session", TaskStatus.ACTIVE,
                "host", null, null, null, Task.BrowserConfig.ofUrls(PR), null, null, ""), "idle"))
                .isFalse();
    }

    private static final String PR = "https://github.com/o/r/pull/1";

    private static Task task(TaskStatus status) {
        return new Task("g/t", "T", status, "host", null, null, null,
                Task.BrowserConfig.ofUrls(PR), new Task.ClaudeConfig("/w", null, null), null, "");
    }

    @Test
    void theLastScreenIsAppendedOnceBelowTheNotes() {
        String content = """
                ---
                title: Fix the parser
                ---

                Notes.
                """;
        String archived = MergedCleanup.withLastScreen(content, "\u001B[32mall done\u001B[0m\n\n");
        assertThat(archived).isEqualTo(content.stripTrailing() + "\n\n"
                + MergedCleanup.HEADING + "\n\n```text\nall done\n```\n");
        assertThat(MergedCleanup.withLastScreen(archived, "later screen")).isEqualTo(archived);
        assertThat(MergedCleanup.withLastScreen(content, null)).isEqualTo(content);
    }
}
