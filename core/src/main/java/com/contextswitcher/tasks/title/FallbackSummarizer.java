package com.contextswitcher.tasks.title;

import java.util.Objects;
import java.util.Optional;

/// Asks `primary` first and `fallback` only when it has no title.
// [impl->dsn~task-create-local-title~1]
public final class FallbackSummarizer implements TaskTitleSummarizer {

    private final TaskTitleSummarizer primary;
    private final TaskTitleSummarizer fallback;

    public FallbackSummarizer(TaskTitleSummarizer primary, TaskTitleSummarizer fallback) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public Optional<String> summarize(String description) {
        return primary.summarize(description).or(() -> fallback.summarize(description));
    }
}
