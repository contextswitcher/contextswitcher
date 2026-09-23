package com.contextswitcher.tasks.title;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-local-title~1]
class FirstWordsSummarizerTest {

    private final FirstWordsSummarizer summarizer = new FirstWordsSummarizer();

    @Test
    void takesTheFirstSentence() {
        assertThat(summarizer.summarize("Fix the login button on Safari. Also check the console."))
                .contains("Fix the login button on Safari");
    }

    @Test
    void takesTheFirstLine() {
        assertThat(summarizer.summarize("Update README\nwith install steps")).contains("Update README");
    }

    @Test
    void keepsAtMostEightWords() {
        assertThat(summarizer.summarize("one two three four five six seven eight nine ten"))
                .contains("one two three four five six seven eight");
    }

    @Test
    void aDotInsideAWordDoesNotEndTheSentence() {
        assertThat(summarizer.summarize("Review https://x.io/1.5 and approve"))
                .contains("Review https://x.io/1.5 and approve");
    }

    @Test
    void nothingGivesNoTitle() {
        assertThat(summarizer.summarize("  ")).isEmpty();
    }
}
