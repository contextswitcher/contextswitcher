package com.contextswitcher.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.config.OneNoteBacklinks;
import com.contextswitcher.config.OneNoteBacklinks.Link;
import com.contextswitcher.config.OneNoteBacklinks.Result;

/// Hands the complete backlink list to `ankd onenote-backlinks-sync`
/// whenever the task set may have changed: [#changed()] is cheap and may be
/// called on every list change; the sync itself runs [#DEBOUNCE_SECONDS]
/// after the last call, off the FX thread, and only when the list differs
/// from the one ankd last took.
// [impl->dsn~onenote-backlinks~1]
public final class OneNoteBacklinkSync {

    /// How long a burst of changes (a save, a scan, a group move) settles.
    public static final long DEBOUNCE_SECONDS = 5;

    /// How long ankd may take for one sync (it writes every changed page).
    private static final long TIMEOUT_SECONDS = 120;

    private final Path ankd;
    private final ScheduledExecutorService scheduler;
    private final Executor worker;
    private final Supplier<List<Link>> links;
    private final BooleanSupplier enabled;
    /// Serializes the syncs; not `this`, which [#changed()] takes on the FX
    /// thread and must never wait for a running ankd.
    private final Object syncLock = new Object();
    private @Nullable ScheduledFuture<?> pending;
    private @Nullable List<Link> lastSent;
    private boolean warnedTooOld;

    /// `links` builds the current list (it runs on `worker`, so it reads a
    /// snapshot of the task list, and may read the category configs from
    /// disk), `enabled` reads the setting fresh.
    public OneNoteBacklinkSync(Path ankd, ScheduledExecutorService scheduler, Executor worker,
            Supplier<List<Link>> links, BooleanSupplier enabled) {
        this.ankd = ankd;
        this.scheduler = scheduler;
        this.worker = worker;
        this.links = links;
        this.enabled = enabled;
    }

    /// Restarts the debounce timer.
    public synchronized void changed() {
        ScheduledFuture<?> previous = pending;
        if (previous != null) {
            previous.cancel(false);
        }
        pending = scheduler.schedule(() -> worker.execute(this::syncNow), DEBOUNCE_SECONDS, TimeUnit.SECONDS);
    }

    private void syncNow() {
        synchronized (syncLock) {
            syncLocked();
        }
    }

    private void syncLocked() {
        try {
            if (!enabled.getAsBoolean()) {
                return;
            }
            List<Link> current = links.get();
            if (current.equals(lastSent)) {
                return;
            }
            String output = run(OneNoteBacklinks.request(current));
            if (output == null) {
                return;
            }
            Result result = OneNoteBacklinks.parseResult(output);
            if (result == null) {
                // An ankd before the command prints nothing; asking again for
                // the same list would not help.
                lastSent = current;
                if (!warnedTooOld) {
                    warnedTooOld = true;
                    Logger.info("AnchorHub's ankd does not know {} yet; OneNote backlinks need its latest release",
                            OneNoteBacklinks.COMMAND);
                }
                return;
            }
            Logger.info("OneNote backlinks: {} links, {} pages updated, {} cleaned, {} unchanged, {} failed",
                    current.size(), result.updated(), result.removed(), result.unchanged(), result.failed().size());
            result.failed().forEach(failure ->
                    Logger.warn("OneNote backlink to {} failed: {}", failure.note(), failure.error()));
            // A failed page is tried again with the next change.
            lastSent = result.failed().isEmpty() ? current : null;
        } catch (RuntimeException e) {
            Logger.warn(e, "OneNote backlink sync failed");
        }
    }

    /// ankd's stdout, or null when it failed (logged).
    private @Nullable String run(String request) {
        Process process;
        try {
            process = new ProcessBuilder(ankd.toString(), OneNoteBacklinks.COMMAND).start();
        } catch (IOException e) {
            Logger.warn("Cannot start {}: {}", ankd, e.getMessage());
            return null;
        }
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()), worker);
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()), worker);
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(request.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Logger.debug("Writing the backlink request to ankd failed: {}", e.getMessage());
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Logger.warn("ankd {} took longer than {} s; stopped", OneNoteBacklinks.COMMAND, TIMEOUT_SECONDS);
                return null;
            }
            String output = stdout.get(10, TimeUnit.SECONDS);
            int exit = process.exitValue();
            if (exit != 0) {
                // 1 = invalid request, 2 = unexpected error; either way the
                // reason is on stderr.
                Logger.warn("ankd {} exited with {}{}: {} {}", OneNoteBacklinks.COMMAND, exit,
                        exit == 1 ? " (invalid request)" : "", stderr.get(10, TimeUnit.SECONDS).strip(),
                        output.strip());
                return null;
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            Logger.warn("Cannot read ankd's answer: {}", e.getMessage());
            return null;
        }
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
