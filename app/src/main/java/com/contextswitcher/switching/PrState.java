package com.contextswitcher.switching;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// The state of a task's GitHub pull request, for the row indicator (https://github.com/contextswitcher/contextswitcher-private/issues/49).
/// `DRAFT` is an open PR still marked draft; `gh` reports it as `state=OPEN`
/// with `isDraft=true`.
// [impl->dsn~pr-state-indicator~3]
public enum PrState {
    OPEN,
    DRAFT,
    MERGED,
    CLOSED;

    /// Maps `gh`'s `state` (`OPEN`/`MERGED`/`CLOSED`) and `isDraft` flag to a
    /// state, or null for an unrecognized value. GitLab's `opened` and
    /// `locked` (a closed MR whose discussion is locked) map alike
    /// (`dsn~gitlab-mr-state~1`).
    // [impl->dsn~gitlab-mr-state~1]
    public static @Nullable PrState from(String state, boolean draft) {
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "OPEN", "OPENED" -> draft ? DRAFT : OPEN;
            case "MERGED" -> MERGED;
            case "CLOSED", "LOCKED" -> CLOSED;
            default -> null;
        };
    }
}
