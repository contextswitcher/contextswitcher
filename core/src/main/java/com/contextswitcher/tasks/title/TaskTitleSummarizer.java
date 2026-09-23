package com.contextswitcher.tasks.title;

import java.util.Optional;

/// Derives a short task title from a free-form task description.
///
/// Implementations never throw and never block past their own timeout: an
/// empty result means "no title", so a caller can always fall back to another
/// summarizer ([FallbackSummarizer]) or keep the description as it is.
/// A present title is already cleaned up per [TaskTitles#sanitize].
// [impl->dsn~task-create-local-title~1]
@FunctionalInterface
public interface TaskTitleSummarizer {

    Optional<String> summarize(String description);
}
