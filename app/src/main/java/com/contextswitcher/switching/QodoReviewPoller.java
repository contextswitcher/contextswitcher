package com.contextswitcher.switching;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import com.contextswitcher.config.EnergySaver;
/// Polls every task PR for Qodo Merge "Agent Prompt" blocks and queues the new
/// ones as chat messages for that task (#qodo-agent-prompt-queue), on a daemon
/// scheduler with a long interval. To stay a good GitHub citizen each tick
/// makes only a cheap `updatedAt` call ([QodoReviewLookup#lastActivity]) and
/// hits the heavier comments endpoint **only when the PR moved** — a push, the
/// only time qodo has something new to review, or a review comment somebody
/// wrote; on a quiet PR the tick stays quiet. Activity opens a short
/// [#FOLLOWUP_POLLS]-tick window of comment-polling, since a qodo review lands
/// minutes after the push it reviews.
///
/// The PR's currently-active prompts (each with its review-comment URL) are
/// handed to the
/// `onPrompts` callback `(task id, active prompts)`, which marshals to the UI
/// thread; deduping against what is already queued and remembering which
/// prompts came from qodo happens there ([com.contextswitcher.ui.QueuePane]),
/// the single writer of the queue and its qodo-sourced sidecar — this poller
/// keeps no state on disk.
// [impl->dsn~qodo-agent-prompt-queue~11]
public class QodoReviewPoller implements AutoCloseable {

    /// After activity, how many ticks to keep fetching comments before going
    /// quiet — a qodo review lands minutes after the push, so a single fetch
    /// on the tick that saw the push would miss it. At [#intervalSeconds] this
    /// spans roughly that many intervals of comment-polling per push.
    private static final int FOLLOWUP_POLLS = 5;

    private final QodoReviewLookup lookup;
    private final Supplier<Map<String, List<String>>> prUrlsByTask;
    private final BiConsumer<String, Map<String, String>> onPrompts;
    private final long intervalSeconds;

    /// Per-**PR** activity tracking, touched only from the single scheduler
    /// thread (no synchronization needed): the last `updatedAt` seen, and how
    /// many more comment fetches are still owed for it. Keyed by PR URL, not by
    /// task: a task's PRs (code plus docs) move independently, so a shared
    /// window would make one PR's push swallow the other's.
    private final Map<String, String> lastActivity = new HashMap<>();
    private final Map<String, Integer> followupsLeft = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "qodo-review-poller");
        thread.setDaemon(true);
        return thread;
    });

    public QodoReviewPoller(QodoReviewLookup lookup,
            Supplier<Map<String, List<String>>> prUrlsByTask,
            BiConsumer<String, Map<String, String>> onPrompts, long intervalSeconds) {
        this.lookup = lookup;
        this.prUrlsByTask = prUrlsByTask;
        this.onPrompts = onPrompts;
        this.intervalSeconds = intervalSeconds;
    }

    /// Starts polling. Fixed-*delay*, so a slow batch of `gh` calls never lets
    /// ticks pile up on each other.
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
        Map<String, List<String>> current = prUrlsByTask.get();
        Set<String> liveUrls = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : current.entrySet()) {
            for (String url : entry.getValue()) {
                liveUrls.add(url);
                harvest(entry.getKey(), url);
            }
        }
        // Drop bookkeeping for PRs no task carries any more, so the maps can't
        // grow without bound over a long-running session.
        lastActivity.keySet().retainAll(liveUrls);
        followupsLeft.keySet().retainAll(liveUrls);
    }

    private void harvest(String taskId, String url) {
        // Cheap gate first: one light `gh pr view` for the PR's updatedAt.
        // Nothing new can be there unless the PR moved, so on a quiet PR skip
        // the heavier paginated comments call entirely. Activity opens a short
        // window ([#FOLLOWUP_POLLS] ticks) of comment-polling, since a qodo
        // review lands a few minutes after the push. On startup lastActivity is
        // empty, so the first tick for each PR opens a window too (catches
        // anything posted while the app was off).
        String activity = lookup.lastActivity(url);
        if (activity == null) {
            return;   // gh down / not a PR: retry next tick, don't hammer comments
        }
        if (!activity.equals(lastActivity.get(url))) {
            lastActivity.put(url, activity);
            followupsLeft.put(url, FOLLOWUP_POLLS);
        }
        int left = followupsLeft.getOrDefault(url, 0);
        if (left <= 0) {
            return;   // nothing happened since the last window — stay quiet
        }
        followupsLeft.put(url, left - 1);

        Map<String, String> active = lookup.promptsFor(url);
        if (active != null && !active.isEmpty()) {
            onPrompts.accept(taskId, active);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
