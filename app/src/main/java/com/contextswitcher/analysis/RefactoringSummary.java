package com.contextswitcher.analysis;

/// The badge's cached result for one task: how many refactorings
/// RefactoringMiner found in the committed range base..`head`, keyed by the
/// HEAD sha so an unchanged worktree never re-runs the analysis.
// [impl->dsn~refactoring-analysis-poller~1]
public record RefactoringSummary(String head, int count) {
}
