package com.contextswitcher.switching;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import com.contextswitcher.config.EnergySaver;
import org.jspecify.annotations.Nullable;

/// Polls the state of the *visible* task PR URLs on a fixed interval via
/// [PrStateLookup] (one batched `gh` call per tick), so the task list can show
/// a per-row PR-state indicator (https://github.com/contextswitcher/contextswitcher-private/issues/49). The merged `url -> state` map is handed
/// to the callback, which marshals to the UI thread.
///
/// GitHub-API economy (`dsn~pr-poll-economy~2`): a PR last seen `MERGED` is
/// effectively final, so it is re-read only on the first tick (startup) and
/// then every [#MERGED_POLL_SECONDS]; everything the callers hand in is
/// already narrowed to the rows on screen, and [#refreshMissing] resolves a
/// PR the moment it first becomes visible.
// [impl->dsn~pr-state-indicator~3]
// [impl->dsn~pr-poll-economy~2]
public class PrStatePoller implements AutoCloseable {

    /// Seconds between re-reads of a PR already seen as merged — merged never
    /// becomes un-merged, the slow re-read only heals a mis-parse.
    static final long MERGED_POLL_SECONDS = 600;

    /// Seconds the burst of row changes is collected before the missing URLs
    /// are fetched, and seconds between retries while some stay unresolved.
    static final long MISSING_COALESCE_SECONDS = 2;
    static final long MISSING_RETRY_SECONDS = 10;
    static final int MISSING_RETRIES = 5;

    private final PrStateLookup lookup;
    private final Supplier<Set<String>> urls;
    private final Consumer<Map<String, PrInfo>> onUpdate;
    private final long intervalSeconds;
    private final long mergedEveryNthTick;

    /// Last state seen per URL; touched only on the scheduler thread.
    private final Map<String, PrInfo> known = new HashMap<>();
    private long tick;

    /// The coalesced missing-URL round; replaced, not stacked.
    private @Nullable ScheduledFuture<?> pendingMissing;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pr-state-poller");
        thread.setDaemon(true);
        return thread;
    });

    public PrStatePoller(PrStateLookup lookup, Supplier<Set<String>> urls,
            Consumer<Map<String, PrInfo>> onUpdate, long intervalSeconds) {
        this.lookup = lookup;
        this.urls = urls;
        this.onUpdate = onUpdate;
        this.intervalSeconds = intervalSeconds;
        this.mergedEveryNthTick = Math.max(1, MERGED_POLL_SECONDS / intervalSeconds);
    }

    /// Starts polling. Fixed-*delay*, so a slow `gh` call never lets ticks
    /// pile up on each other.
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tickPoll, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /// Re-reads the given URLs now, on the poller thread, ahead of the next
    /// tick — for a task whose Claude session just stopped and may have
    /// changed its PR (draft, ready, merged) meanwhile.
    // [impl->dsn~pr-state-refresh-on-stop~2]
    public void refresh(Set<String> urls) {
        if (!urls.isEmpty()) {
            scheduler.execute(() -> poll(urls));
        }
    }

    /// Re-reads only the URLs never resolved in this run — for rows that just
    /// became visible: a URL already in [#known] keeps its cached icon (the
    /// window remembers it) until its next regular tick, so scrolling and
    /// filtering never cost API requests.
    ///
    /// Row changes come in bursts — the task list streams in row by row at
    /// startup, and every filter toggle rebuilds it — so the round is
    /// coalesced into one call [#MISSING_COALESCE_SECONDS] after the last
    /// change instead of one per change (that burst is what tripped GitHub's
    /// secondary rate limit at startup, leaving the whole list without icons
    /// until the first regular tick two minutes later), and retried every
    /// [#MISSING_RETRY_SECONDS] while URLs stay unresolved.
    public void refreshMissing() {
        scheduleMissing(MISSING_COALESCE_SECONDS, MISSING_RETRIES);
    }

    private synchronized void scheduleMissing(long delaySeconds, int retriesLeft) {
        if (pendingMissing != null) {
            pendingMissing.cancel(false);
        }
        pendingMissing = scheduler.schedule(
                () -> pollMissing(retriesLeft), delaySeconds, TimeUnit.SECONDS);
    }

    private void pollMissing(int retriesLeft) {
        Set<String> unknown = new HashSet<>(urls.get());
        unknown.removeAll(known.keySet());
        if (unknown.isEmpty()) {
            return;
        }
        poll(unknown);
        unknown.removeAll(known.keySet());
        if (!unknown.isEmpty() && retriesLeft > 0) {
            scheduleMissing(MISSING_RETRY_SECONDS, retriesLeft - 1);
        }
    }

    /// Re-reads every polled URL right now, past the energy-saver gate —
    /// the manual refresh.
    // [impl->dsn~energy-saver~1]
    public CompletableFuture<Void> pollNow() {
        return CompletableFuture.runAsync(() -> poll(new HashSet<>(urls.get())), scheduler);
    }

    // [impl->dsn~energy-saver~1]
    private void tickPoll() {
        if (EnergySaver.active()) {
            return;
        }
        boolean mergedDue = tick++ % mergedEveryNthTick == 0;
        Set<String> due = new HashSet<>();
        for (String url : urls.get()) {
            PrInfo info = known.get(url);
            if (mergedDue || info == null || info.state() != PrState.MERGED) {
                due.add(url);
            }
        }
        poll(due);
    }

    private void poll(Set<String> urls) {
        if (urls.isEmpty()) {
            return;
        }
        Map<String, PrInfo> states = lookup.states(urls);
        if (!states.isEmpty()) {
            known.putAll(states);
            onUpdate.accept(states);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
