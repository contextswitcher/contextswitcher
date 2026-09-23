package com.contextswitcher.tasks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-sort-modes~2]
class TaskOrderTest {

    private static TaskEntry task(String title, TaskStatus status) {
        return new TaskEntry.Loaded(new Task(title.toLowerCase(java.util.Locale.ROOT), title,
                status, null, null, null, null, null, null, null, List.of(), List.of(), ""));
    }

    private static TaskEntry pinned(String title, TaskStatus status) {
        TaskEntry.Loaded loaded = (TaskEntry.Loaded) task(title, status);
        Task raw = loaded.task();
        return new TaskEntry.Loaded(new Task(raw.id(), raw.title(), raw.status(), null, null, null,
                null, null, null, null, List.of(), List.of(), List.of(), null, null, "", true));
    }

    private static List<String> sortedIds(Comparator<TaskEntry> order, TaskEntry... entries) {
        List<TaskEntry> list = new ArrayList<>(List.of(entries));
        list.sort(order);
        return list.stream().map(TaskEntry::id).toList();
    }

    private static Comparator<TaskEntry> comparator(TaskOrder mode,
            Map<String, String> statusById, Map<String, Long> modifiedById) {
        return mode.comparator(task -> statusById.get(task.id()),
                entry -> modifiedById.getOrDefault(entry.id(), 0L));
    }

    @Test
    void alphabeticalSortsByStatusThenTitleCaseInsensitively() {
        assertThat(sortedIds(comparator(TaskOrder.ALPHABETICAL, Map.of(), Map.of()),
                task("beta", TaskStatus.ACTIVE),
                task("Alpha", TaskStatus.SUSPENDED),
                task("celeste", TaskStatus.ACTIVE),
                task("Anton", TaskStatus.ACTIVE)))
                .containsExactly("anton", "beta", "celeste", "alpha");
    }

    // [utest->dsn~pinned-tasks~2]
    @Test
    void pinnedTasksHeadTheirStatusBlockInEveryMode() {
        TaskEntry pinnedActive = pinned("zulu", TaskStatus.ACTIVE);
        TaskEntry pinnedDone = pinned("zebra", TaskStatus.DONE);
        for (TaskOrder mode : TaskOrder.values()) {
            // The pin beats the mode's own ordering (title, file time, status)
            // …
            assertThat(sortedIds(comparator(mode, Map.of(), Map.of("zulu", 1L, "alpha", 9L)),
                    task("alpha", TaskStatus.ACTIVE), pinnedActive))
                    .containsExactly("zulu", "alpha");
            // … but never lifts a task out of its status block.
            assertThat(sortedIds(comparator(mode, Map.of(), Map.of()),
                    task("alpha", TaskStatus.ACTIVE), pinnedDone))
                    .containsExactly("alpha", "zebra");
        }
    }

    @Test
    void errorRowsSortFirstInEveryMode() {
        TaskEntry failed = new TaskEntry.Failed("broken", "broken.md", "boom");
        for (TaskOrder mode : TaskOrder.values()) {
            assertThat(sortedIds(comparator(mode, Map.of(), Map.of()),
                    task("aaa", TaskStatus.ACTIVE), failed))
                    .containsExactly("broken", "aaa");
        }
    }

    @Test
    void lastUpdateSortsNewestFirstWithinStatus() {
        Map<String, Long> modified = Map.of("old", 1_000L, "new", 3_000L, "mid", 2_000L,
                "suspended-new", 9_000L);
        assertThat(sortedIds(comparator(TaskOrder.LAST_UPDATE, Map.of(), modified),
                task("old", TaskStatus.ACTIVE),
                task("suspended-new", TaskStatus.SUSPENDED),
                task("new", TaskStatus.ACTIVE),
                task("mid", TaskStatus.ACTIVE)))
                .containsExactly("new", "mid", "old", "suspended-new");
    }

    @Test
    void lastUpdateBreaksTiesByTitle() {
        assertThat(sortedIds(comparator(TaskOrder.LAST_UPDATE, Map.of(), Map.of()),
                task("beta", TaskStatus.ACTIVE), task("alpha", TaskStatus.ACTIVE)))
                .containsExactly("alpha", "beta");
    }

    @Test
    void actionNeededRanksAttentionThenWaitingThenWorkingThenNone() {
        Map<String, String> statuses = Map.of(
                "asks", "attention", "ready", "waiting", "busy", "working");
        assertThat(sortedIds(comparator(TaskOrder.ACTION_NEEDED, statuses, Map.of()),
                task("busy", TaskStatus.ACTIVE),
                task("silent", TaskStatus.ACTIVE),
                task("ready", TaskStatus.ACTIVE),
                task("asks", TaskStatus.ACTIVE)))
                .containsExactly("asks", "ready", "busy", "silent");
    }

    @Test
    void actionNeededKeepsSuspendedAfterActiveRegardlessOfStatus() {
        Map<String, String> statuses = Map.of("paused", "attention");
        assertThat(sortedIds(comparator(TaskOrder.ACTION_NEEDED, statuses, Map.of()),
                task("paused", TaskStatus.SUSPENDED),
                task("silent", TaskStatus.ACTIVE)))
                .containsExactly("silent", "paused");
    }

    @Test
    void actionRankOrdersTheKnownStatuses() {
        assertThat(TaskOrder.actionRank("limit")).isLessThan(TaskOrder.actionRank("attention"));
        assertThat(TaskOrder.actionRank("attention")).isLessThan(TaskOrder.actionRank("waiting"));
        assertThat(TaskOrder.actionRank("waiting")).isLessThan(TaskOrder.actionRank("working"));
        assertThat(TaskOrder.actionRank("working")).isLessThan(TaskOrder.actionRank(null));
        assertThat(TaskOrder.actionRank("gibberish")).isEqualTo(TaskOrder.actionRank(null));
    }
}
