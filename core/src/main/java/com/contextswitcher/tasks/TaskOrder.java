package com.contextswitcher.tasks;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.jspecify.annotations.Nullable;

/// The task list's selectable sort orders. Every mode keeps error rows first
/// (a parse failure must be seen), sorts by [TaskStatus] ordinal (active,
/// suspended, done) next, and lifts the pinned tasks to the top of each of
/// those blocks; the mode decides the order within them, with the
/// case-insensitive title as the final tie-breaker.
// [impl->dsn~task-sort-modes~2]
// [impl->dsn~pinned-tasks~2]
public enum TaskOrder {

    /// By title (the original fixed order).
    ALPHABETICAL("Alphabetical"),
    /// Most recently updated task file first.
    LAST_UPDATE("Last update"),
    /// Tasks needing the user first: attention, then waiting, then working,
    /// then status-less.
    ACTION_NEEDED("Action needed");

    private final String label;

    TaskOrder(String label) {
        this.label = label;
    }

    /// The human label shown in the toolbar's Sort menu.
    public String label() {
        return label;
    }

    /// The comparator for this mode. `runningStatus` resolves a task's live
    /// `@cs_status` (null when unknown), `lastModifiedMillis` an entry's task
    /// file modification time — both passed in so the ordering stays pure and
    /// unit-testable.
    public Comparator<TaskEntry> comparator(Function<Task, @Nullable String> runningStatus,
            ToLongFunction<TaskEntry> lastModifiedMillis) {
        Comparator<TaskEntry> base = Comparator.comparingInt((TaskEntry entry) -> switch (entry) {
            case TaskEntry.Failed failed -> -1;
            case TaskEntry.Loaded loaded -> loaded.task().status().ordinal();
        // A pinned task heads its status block — not the whole list: a pinned
        // done task must not push the active work down.
        // [impl->dsn~pinned-tasks~2]
        }).thenComparingInt(entry -> entry instanceof TaskEntry.Loaded loaded
                && loaded.task().pinned() ? 0 : 1);
        Comparator<TaskEntry> title = Comparator.comparing(entry -> switch (entry) {
            case TaskEntry.Failed failed -> failed.fileName();
            case TaskEntry.Loaded loaded -> loaded.task().title();
        }, String.CASE_INSENSITIVE_ORDER);
        return switch (this) {
            case ALPHABETICAL -> base.thenComparing(title);
            case LAST_UPDATE -> base
                    .thenComparing(Comparator.comparingLong(lastModifiedMillis).reversed())
                    .thenComparing(title);
            case ACTION_NEEDED -> base
                    .thenComparingInt(entry -> entry instanceof TaskEntry.Loaded loaded
                            ? actionRank(runningStatus.apply(loaded.task())) : 0)
                    .thenComparing(title);
        };
    }

    /// Urgency rank of a live `@cs_status`: `limit` (the session is blocked on
    /// its usage limit, `dsn~claude-limit-detection~2`) before `attention`
    /// (Claude asked a question or waits for a permission) before `waiting`
    /// (turn ended) before `working` before no status at all.
    static int actionRank(@Nullable String status) {
        return switch (status) {
            case "limit" -> 0;
            case "attention" -> 1;
            case "waiting" -> 2;
            case "working" -> 3;
            case null, default -> 4;
        };
    }
}
