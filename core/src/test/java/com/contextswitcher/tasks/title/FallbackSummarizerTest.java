package com.contextswitcher.tasks.title;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

// [utest->dsn~task-create-local-title~1]
class FallbackSummarizerTest {

    private static final TaskTitleSummarizer NEVER = description -> {
        throw new AssertionError("fallback asked although the primary answered");
    };

    @Test
    void thePrimaryTitleWins() {
        var summarizer = new FallbackSummarizer(description -> Optional.of("Primary"), NEVER);
        assertThat(summarizer.summarize("text")).contains("Primary");
    }

    @Test
    void theFallbackAnswersWhenThePrimaryHasNoTitle() {
        var summarizer = new FallbackSummarizer(description -> Optional.empty(),
                description -> Optional.of("Fallback for " + description));
        assertThat(summarizer.summarize("text")).contains("Fallback for text");
    }

    @Test
    void bothAreRequired() {
        assertThatNullPointerException().isThrownBy(() -> new FallbackSummarizer(null, NEVER));
        assertThatNullPointerException().isThrownBy(() -> new FallbackSummarizer(NEVER, null));
    }
}
