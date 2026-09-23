package com.contextswitcher.tasks;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~auto-pr-category~3]
class AutoPrReconcileTest {

    private static final String PR7 = "https://github.com/o/r/pull/7";
    private static final String PR8 = "https://github.com/o/r/pull/8";
    private static final GroupConfig.AutoPr CONFIG = new GroupConfig.AutoPr("q", 50, 24);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 12, 0);

    private static Task task(String id, TaskStatus status, @Nullable String suspendedAt,
            Task.@Nullable TmuxConfig tmux, String... urls) {
        return task(id, status, suspendedAt, tmux, false, urls);
    }

    /// As above, with the `autoPrClosed` mark — the reconcile's own suspend.
    private static Task task(String id, TaskStatus status, @Nullable String suspendedAt,
            Task.@Nullable TmuxConfig tmux, boolean autoPrClosed, String... urls) {
        return new Task(id, id, status, null, tmux, null, null,
                urls.length == 0 ? null : Task.BrowserConfig.ofUrls(urls), null, null,
                List.of(), List.of(), List.of(), suspendedAt, null, "", false, autoPrClosed);
    }

    @Test
    void addsAMatchNoTaskCarriesYet() {
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(
                List.of(task("review/old", TaskStatus.ACTIVE, null, null, PR7)),
                "review", List.of(PR7, PR8), List.of(PR7, PR8), CONFIG, NOW);
        assertThat(plan.add()).containsExactly(PR8);
        assertThat(plan.suspend()).isEmpty();
        assertThat(plan.delete()).isEmpty();
    }

    @Test
    void aPrTrackedByAnotherCategoryIsNotAddedAgain() {
        // The user already works on that PR somewhere else — a second row for
        // it would compete with the real task.
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(
                List.of(task("jabref/my-work", TaskStatus.ACTIVE, null, null, PR7 + "/")),
                "review", List.of(PR7), List.of(PR7), CONFIG, NOW);
        assertThat(plan.add()).isEmpty();
    }

    @Test
    void suspendsAnActiveTaskWhosePrLeftTheQuery() {
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(
                List.of(task("review/merged", TaskStatus.ACTIVE, null, null, PR7)),
                "review", List.of(PR8), List.of(PR8), CONFIG, NOW);
        assertThat(plan.suspend()).extracting(Task::id).containsExactly("review/merged");
        assertThat(plan.delete()).isEmpty();
    }

    @Test
    void deletesOnlyAfterTheGracePeriod() {
        Task fresh = task("review/fresh", TaskStatus.SUSPENDED, "2026-09-10 11:00", null, PR7);
        Task old = task("review/old", TaskStatus.SUSPENDED, "2026-09-09 11:00", null, PR8);
        AutoPrReconcile.Plan plan =
                AutoPrReconcile.plan(List.of(fresh, old), "review", List.of(), List.of(), CONFIG, NOW);
        assertThat(plan.delete()).extracting(Task::id).containsExactly("review/old");
        assertThat(plan.suspend()).isEmpty();
    }

    @Test
    void neverDeletesWithoutAReadableTimestampOrWithTheGracePeriodOff() {
        Task noStamp = task("review/a", TaskStatus.SUSPENDED, null, null, PR7);
        Task garbage = task("review/b", TaskStatus.SUSPENDED, "yesterday", null, PR8);
        assertThat(AutoPrReconcile.plan(List.of(noStamp, garbage), "review", List.of(), List.of(), CONFIG, NOW)
                .delete()).isEmpty();
        Task old = task("review/c", TaskStatus.SUSPENDED, "2026-09-01 08:00", null, PR7);
        assertThat(AutoPrReconcile.plan(List.of(old), "review", List.of(), List.of(),
                new GroupConfig.AutoPr("q", 0, 0), NOW).delete()).isEmpty();
    }

    @Test
    void leavesTasksTheUserMadeRealOrOwnsAlone() {
        // A started session, a task without a PR, and a task of another
        // category are none of the reconcile's business.
        Task started = task("review/started", TaskStatus.ACTIVE, null,
                new Task.TmuxConfig("0", "@17"), PR7);
        Task handWritten = task("review/notes", TaskStatus.ACTIVE, null, null);
        Task elsewhere = task("other/pr", TaskStatus.ACTIVE, null, null, PR8);
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(
                List.of(started, handWritten, elsewhere), "review", List.of(), List.of(), CONFIG, NOW);
        assertThat(plan.suspend()).isEmpty();
        assertThat(plan.delete()).isEmpty();
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void aTaskStillMatchingIsUntouchedWhicheverOfItsPrsMatches() {
        Task task = task("review/two", TaskStatus.ACTIVE, null, null, PR7, PR8);
        assertThat(AutoPrReconcile.plan(List.of(task), "review", List.of(PR8), List.of(PR8), CONFIG, NOW)
                .isEmpty()).isTrue();
    }

    @Test
    void aStillOpenPrIsKeptEvenWhenItStoppedMatching() {
        // The size filter admits, it does not evict: a PR that grew past
        // maxSloc — or one the user dragged into the category, which the
        // query never matched — is live and stays active.
        Task dragged = task("review/dragged-in", TaskStatus.ACTIVE, null, null, PR7);
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(List.of(dragged), "review",
                List.of(), List.of(PR7), CONFIG, NOW);
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void aDraggedInTaskFollowsTheSameLifecycleOnceItsPrIsDone() {
        // Live no more (merged/closed): suspended like any other, and deleted
        // once the grace period is over.
        Task dragged = task("review/dragged-in", TaskStatus.ACTIVE, null, null, PR7);
        assertThat(AutoPrReconcile.plan(List.of(dragged), "review", List.of(), List.of(),
                CONFIG, NOW).suspend()).containsExactly(dragged);
        Task suspended = task("review/dragged-in", TaskStatus.SUSPENDED, "2026-09-09 11:00",
                null, PR7);
        assertThat(AutoPrReconcile.plan(List.of(suspended), "review", List.of(), List.of(),
                CONFIG, NOW).delete()).containsExactly(suspended);
    }

    @Test
    void aMatchIsAlwaysLiveWhateverTheCallerPassed() {
        Task task = task("review/matched", TaskStatus.ACTIVE, null, null, PR7);
        assertThat(AutoPrReconcile.plan(List.of(task), "review", List.of(PR7), List.of(),
                CONFIG, NOW).isEmpty()).isTrue();
    }

    @Test
    void aReopenedPrBringsItsOwnSuspendBack() {
        Task closed = task("review/reopened", TaskStatus.SUSPENDED, "2026-09-10 09:00", null,
                true, PR7);
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(List.of(closed), "review",
                List.of(PR7), List.of(PR7), CONFIG, NOW);
        assertThat(plan.resume()).containsExactly(closed);
        assertThat(plan.add()).isEmpty();
        assertThat(plan.delete()).isEmpty();
    }

    @Test
    void aRowThePausedUserDismissedStaysPausedAndUndeleted() {
        // No `autoPrClosed` mark: the user paused it. Its PR is open (live),
        // so it is neither resumed nor deleted, however old the pause is.
        Task dismissed = task("review/not-interested", TaskStatus.SUSPENDED, "2026-09-01 08:00",
                null, PR7);
        AutoPrReconcile.Plan plan = AutoPrReconcile.plan(List.of(dismissed), "review",
                List.of(PR7), List.of(PR7), CONFIG, NOW);
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void aDismissedRowIsStillDeletedOnceItsPrIsDone() {
        Task dismissed = task("review/not-interested", TaskStatus.SUSPENDED, "2026-09-01 08:00",
                null, PR7);
        assertThat(AutoPrReconcile.plan(List.of(dismissed), "review", List.of(), List.of(),
                CONFIG, NOW).delete()).containsExactly(dismissed);
    }

    @Test
    void aResumableTaskWithASessionInItIsLeftAlone() {
        Task started = task("review/started", TaskStatus.SUSPENDED, "2026-09-10 09:00",
                new Task.TmuxConfig("0", "@17"), true, PR7);
        assertThat(AutoPrReconcile.plan(List.of(started), "review", List.of(PR7), List.of(PR7),
                CONFIG, NOW).isEmpty()).isTrue();
    }
}
