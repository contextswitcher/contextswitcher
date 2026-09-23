package com.contextswitcher.discovery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import com.contextswitcher.config.EnergySaver;
import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import org.tinylog.Logger;

/// Polls the `@cs_pr` tmux window user option of every window on the task
/// hosts, so a pull request Claude opens *after* import still lands in the
/// task file (https://github.com/contextswitcher/contextswitcher-private/issues/46). A Claude session publishes it with
/// `tmux set -w @cs_pr <url>`; windows that never set it report nothing.
/// One `tmux list-windows` runs per host per tick on a daemon scheduler
/// thread; the merged `host windowId -> prUrl` map is handed to the callback,
/// which marshals to the UI thread and writes the URL into the matching task.
/// The interval is long — a PR is opened once and its URL never changes.
// [impl->dsn~claude-pr-refresh~5]
public class TmuxPrPoller implements AutoCloseable {

    private final SshCommandRunner ssh;
    private final Supplier<Set<String>> hosts;
    private final Consumer<Map<String, String>> onUpdate;
    private final long intervalSeconds;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tmux-pr-poller");
        thread.setDaemon(true);
        return thread;
    });

    public TmuxPrPoller(SshCommandRunner ssh, Supplier<Set<String>> hosts,
            Consumer<Map<String, String>> onUpdate, long intervalSeconds) {
        this.ssh = ssh;
        this.hosts = hosts;
        this.onUpdate = onUpdate;
        this.intervalSeconds = intervalSeconds;
    }

    /// Starts polling. Fixed-*delay* so a slow tmux/ssh round-trip never lets
    /// ticks pile up.
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
        Map<String, String> merged = new HashMap<>();
        for (String host : hosts.get()) {
            try {
                SshCommandRunner.SshResult result = ssh.run(host, prCommand());
                if (result.ok()) {
                    parseInto(host, result.stdout(), merged);
                }
            } catch (Exception e) {
                Logger.debug("PR poll failed for {}: {}", host, e.getMessage());
            }
        }
        onUpdate.accept(Map.copyOf(merged));
    }

    /// `windowId|@cs_pr` per window; the format is single-quoted so the remote
    /// shell leaves `#{…}` and `|` intact, and it carries no double quotes
    /// (see `ProcessSshRunner.buildCommand`).
    static List<String> prCommand() {
        return List.of("tmux", "list-windows", "-a", "-F", "'#{window_id}|#{@cs_pr}'");
    }

    /// Parses `windowId|prUrl` lines, adding `host windowId -> prUrl` for every
    /// window whose `@cs_pr` is non-empty.
    static void parseInto(String host, String stdout, Map<String, String> out) {
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 2);
            if (parts.length < 2) {
                continue;
            }
            String prUrl = parts[1].strip();
            if (!prUrl.isEmpty()) {
                out.put(TmuxStatusPoller.key(host, parts[0]), prUrl);
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
