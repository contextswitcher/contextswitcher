package com.contextswitcher.tasks;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/// The decision half of the idle janitor (`dsn~idle-task-removal~3`): a task
/// created with *Remove after 1 day of inactivity* ticked carries
/// `autoRemoveIdle: true`, and once its session has been quiet a day — window
/// idle that long, or the task suspended that long — window, transcript,
/// worktree and file go.
// [impl->dsn~idle-task-removal~3]
public final class IdleRemoval {

    /// The frontmatter flag the Add-task checkbox writes.
    public static final String KEY = "autoRemoveIdle";

    /// "1 day of inactivity".
    // ponytail: fixed at a day, as asked; a per-task duration when one day is wrong.
    public static final int HOURS = 24;

    private IdleRemoval() {
    }

    /// Whether a flagged task is due: an **active** one whose window the
    /// status poll reports idle (`idleSeconds`, null = not reported) for a day
    /// and not `working`, or a **suspended** one stamped (`suspendedAt`) a day
    /// ago. A task without a Claude session, or marked done, never is.
    public static boolean due(Task task, @Nullable String runningStatus, @Nullable Long idleSeconds,
            LocalDateTime now) {
        if (task.claude() == null || task.pinned()) {
            return false;
        }
        return switch (task.status()) {
            case ACTIVE -> !"working".equals(runningStatus)
                    && idleSeconds != null && idleSeconds >= HOURS * 3600L;
            case SUSPENDED -> AutoPrReconcile.expired(task.suspendedAt(), HOURS, now);
            default -> false;
        };
    }
}
