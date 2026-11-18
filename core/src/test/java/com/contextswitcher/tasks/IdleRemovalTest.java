package com.contextswitcher.tasks;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~idle-task-removal~1]
class IdleRemovalTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);
    private static final long DAY = 24 * 3600L;

    private static Task task(TaskStatus status, @Nullable String suspendedAt, boolean claude) {
        return new Task("g/t", "T", status, "host", null, null, null, null,
                claude ? new Task.ClaudeConfig("/w", null, null) : null, null,
                List.of(), List.of(), List.of(), suspendedAt, null, "", false, false);
    }

    @Test
    void anActiveTaskIsDueOnceItsWindowIdledADay() {
        Task task = task(TaskStatus.ACTIVE, null, true);
        assertThat(IdleRemoval.due(task, "waiting", DAY, NOW)).isTrue();
        assertThat(IdleRemoval.due(task, "waiting", DAY - 1, NOW)).isFalse();
        assertThat(IdleRemoval.due(task, "working", DAY, NOW)).isFalse();
        assertThat(IdleRemoval.due(task, null, null, NOW)).isFalse();
    }

    @Test
    void aSuspendedTaskIsDueADayAfterItsSuspend() {
        assertThat(IdleRemoval.due(task(TaskStatus.SUSPENDED, "2026-11-17 12:00", true), null, null, NOW))
                .isTrue();
        assertThat(IdleRemoval.due(task(TaskStatus.SUSPENDED, "2026-11-17 12:01", true), null, null, NOW))
                .isFalse();
    }

    @Test
    void aDoneOrSessionlessTaskIsNeverDue() {
        assertThat(IdleRemoval.due(task(TaskStatus.DONE, "2026-11-01 12:00", true), null, null, NOW)).isFalse();
        assertThat(IdleRemoval.due(task(TaskStatus.ACTIVE, null, false), "waiting", DAY, NOW)).isFalse();
    }

    @Test
    void theFlagRoundTripsThroughTheFrontmatter() {
        String plain = "---\ntitle: T\n---\n";
        String flagged = TaskFileParser.withFlag(plain, IdleRemoval.KEY, true);
        assertThat(flagged).isEqualTo("---\ntitle: T\nautoRemoveIdle: true\n---\n");
        assertThat(TaskFileParser.frontmatterFlag(flagged, IdleRemoval.KEY)).isTrue();
        assertThat(TaskFileParser.withFlag(plain, IdleRemoval.KEY, false)).isEqualTo(plain);
    }
}
