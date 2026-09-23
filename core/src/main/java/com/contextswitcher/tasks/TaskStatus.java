package com.contextswitcher.tasks;

import java.util.Locale;

// [impl->dsn~task-file-parsing~4]
public enum TaskStatus {
    ACTIVE,
    SUSPENDED,
    DONE;

    public static TaskStatus fromString(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "suspended" -> SUSPENDED;
            case "done" -> DONE;
            default -> throw new IllegalArgumentException(
                    "Unknown status '%s' (expected: active, suspended, done)".formatted(value));
        };
    }

    /// The status a click toggles to: active ↔ suspended. `done` resumes to
    /// active. `done` is reached through other UI, not this toggle.
    public TaskStatus toggled() {
        return switch (this) {
            case ACTIVE -> SUSPENDED;
            case SUSPENDED, DONE -> ACTIVE;
        };
    }

    /// The lower-case spelling used in task-file frontmatter.
    public String yaml() {
        return name().toLowerCase(Locale.ROOT);
    }
}
