package com.contextswitcher.switching;

import java.util.Map;

/// What one poll round knows about a GitHub pull request: its [PrState] for
/// the row icon, plus the title and labels (name → GitHub's `#rrggbb` color,
/// in GitHub's order) for the PR header line above the terminal, and whether
/// it sits in the merge queue or has changes requested for the PR-status
/// grouping — all from the same GraphQL alias, so none costs an extra API
/// request.
// [impl->dsn~pr-header-line~4]
// [impl->dsn~pr-status-grouping~1]
public record PrInfo(PrState state, String title, Map<String, String> labels,
        boolean inMergeQueue, boolean changesRequested) {

    public PrInfo(PrState state, String title, Map<String, String> labels) {
        this(state, title, labels, false, false);
    }
}
