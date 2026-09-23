package com.contextswitcher.analysis;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import com.contextswitcher.config.EnergySaver;
import org.jspecify.annotations.Nullable;

/// Polls the refactoring count of every **idle** live task on a fixed
/// interval via [RefactoringLookup], so the task list can show a per-row
/// badge (https://github.com/contextswitcher/contextswitcher-private/issues/50). Shaped like [com.contextswitcher.switching.PrStatePoller]:
/// a daemon scheduler thread, per-task results emitted as they arrive, the
/// merged map handed to a callback that marshals to the UI thread.
///
/// Idle means the window's live `@cs_status` is `waiting` or `done` — while
/// Claude is `working` the range is still moving and the run would be
/// repeated a tick later anyway. The per-task summary cache (keyed by HEAD
/// sha) keeps a tick cheap: one `git rev-parse` round-trip per task, the
/// expensive RefactoringMiner run only when HEAD actually moved.
// [impl->dsn~refactoring-analysis-poller~1]
public class RefactoringMinerPoller implements AutoCloseable {

    /// One pollable task: its id (the badge key), the remote and worktree to
    /// analyze, the group's base branch, and the `host windowId` key its live
    /// status is published under.
    public record Target(String taskId, String remote, String worktree, String baseBranch,
            String statusKey) {
    }

    /// The live statuses under which a task is analyzed.
    private static final Set<String> IDLE_STATUSES = Set.of("waiting", "done");

    private final RefactoringLookup lookup;
    private final Supplier<List<Target>> targets;
    private final Function<String, @Nullable String> statusByKey;
    private final String rmHome;
    private final Consumer<Map<String, RefactoringSummary>> onUpdate;
    private final long intervalSeconds;
    private final Map<String, RefactoringSummary> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "refactoring-miner-poller");
        thread.setDaemon(true);
        return thread;
    });

    public RefactoringMinerPoller(RefactoringLookup lookup, Supplier<List<Target>> targets,
            Function<String, @Nullable String> statusByKey, String rmHome,
            Consumer<Map<String, RefactoringSummary>> onUpdate, long intervalSeconds) {
        this.lookup = lookup;
        this.targets = targets;
        this.statusByKey = statusByKey;
        this.rmHome = rmHome;
        this.onUpdate = onUpdate;
        this.intervalSeconds = intervalSeconds;
    }

    /// Starts polling. Fixed-*delay*, so a slow RefactoringMiner run never
    /// lets ticks pile up on each other.
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /// The periodic tick: skipped whole while the energy saver is on, so the
    /// poller costs nothing until a refresh asks for it.
    // [impl->dsn~energy-saver~1]
    private void tick() {
        if (!EnergySaver.active()) {
            poll();
        }
    }

    /// Runs one poll right now on the poller thread, past the energy-saver
    /// gate — the manual refresh.
    // [impl->dsn~energy-saver~1]
    public CompletableFuture<Void> pollNow() {
        return CompletableFuture.runAsync(this::poll, scheduler);
    }

    private void poll() {
        for (Target target : targets.get()) {
            String status = statusByKey.apply(target.statusKey());
            if (status == null || !IDLE_STATUSES.contains(status)) {
                continue;
            }
            RefactoringSummary cached = cache.get(target.taskId());
            RefactoringSummary summary = lookup.summary(target.remote(), rmHome,
                    target.worktree(), target.baseBranch(), cached);
            // Emit per task as results arrive (a full RM run can take a
            // while); an unchanged summary emits nothing.
            if (summary != null && !summary.equals(cached)) {
                cache.put(target.taskId(), summary);
                onUpdate.accept(Map.of(target.taskId(), summary));
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
