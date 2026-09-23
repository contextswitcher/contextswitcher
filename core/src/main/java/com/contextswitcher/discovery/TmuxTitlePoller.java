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

/// Polls the `@cs_title` tmux window user option of every window on the task
/// hosts: the live-creation prompt asks Claude to derive a short task title
/// from the task description and publish it with
/// `tmux set -w @cs_title <title>`. The merged `host windowId -> title` map
/// is handed to the callback, which marshals to the UI thread, replaces the
/// matching task's frontmatter title, and unsets the option — a one-shot
/// mailbox, so a later manual rename is never fought by a stale option.
// [impl->dsn~claude-title-sync~3]
public class TmuxTitlePoller implements AutoCloseable {

    private final SshCommandRunner ssh;
    private final Supplier<Set<String>> hosts;
    private final Consumer<Map<String, String>> onUpdate;
    private final long intervalSeconds;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tmux-title-poller");
        thread.setDaemon(true);
        return thread;
    });

    public TmuxTitlePoller(SshCommandRunner ssh, Supplier<Set<String>> hosts,
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
                SshCommandRunner.SshResult result = ssh.run(host, titleCommand());
                if (result.ok()) {
                    parseInto(host, result.stdout(), merged);
                }
            } catch (Exception e) {
                Logger.debug("Title poll failed for {}: {}", host, e.getMessage());
            }
        }
        if (!merged.isEmpty()) {
            onUpdate.accept(Map.copyOf(merged));
        }
    }

    /// `windowId|@cs_title` per window; the format is single-quoted so the
    /// remote shell leaves `#{…}` and `|` intact, and it carries no double
    /// quotes (see `ProcessSshRunner.buildCommand`).
    static List<String> titleCommand() {
        return List.of("tmux", "list-windows", "-a", "-F", "'#{window_id}|#{@cs_title}'");
    }

    /// Unsets the window's `@cs_title` after it was adopted (or matched the
    /// task already), so the mailbox is empty again.
    public static List<String> unsetCommand(String windowId) {
        return List.of("tmux", "set", "-w", "-u", "-t", "'" + windowId + "'", "@cs_title");
    }

    /// Parses `windowId|title` lines, adding `host windowId -> title` for
    /// every window whose `@cs_title` is non-empty. The title may contain `|`
    /// — only the first pipe separates.
    static void parseInto(String host, String stdout, Map<String, String> out) {
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 2);
            if (parts.length < 2) {
                continue;
            }
            String title = parts[1].strip();
            if (!title.isEmpty()) {
                out.put(TmuxStatusPoller.key(host, parts[0]), title);
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
