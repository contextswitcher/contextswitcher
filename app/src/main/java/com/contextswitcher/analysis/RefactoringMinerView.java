package com.contextswitcher.analysis;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// The single long-lived tunnel-plus-JVM process behind the refactoring web
/// view ([RefactoringMinerCommands#viewCommand]): starting a view kills the
/// previous one (one fixed local port — the one-view-at-a-time model of the
/// terminal pane), then connect-polls the forwarded `/list` until the server
/// answers 200 and reports the outcome. Generation-guarded like
/// [com.contextswitcher.ui.TerminalPane]: a newer `open` silently invalidates
/// an older one's poll loop.
///
/// The process's stdin pipe is deliberately left open and unwritten — it is
/// the teardown channel: the remote script blocks reading it and cleans up on
/// EOF, so [Process#destroy] (next view, [#close]) and an app crash both end
/// the remote JVM. Nothing here may close or write that stream.
// [impl->dsn~refactoring-web-view~1]
public class RefactoringMinerView implements AutoCloseable {

    /// How long the first `/list` answer may take: `git archive` of the base
    /// plus RM's Jetty startup — generous, a huge worktree takes a while.
    private static final Duration OPEN_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration POLL_EVERY = Duration.ofMillis(500);

    private final ExecutorService executor;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final AtomicLong generation = new AtomicLong();
    private @Nullable Process process;

    public RefactoringMinerView(ExecutorService executor) {
        this.executor = executor;
    }

    /// Starts `command`, waits for `listUrl` to answer 200, and calls
    /// `onOutcome` (on a background thread) with null on success or a failure
    /// story. A previous view's process is destroyed first; a *newer* open
    /// during the wait wins silently (no outcome for the loser beyond its
    /// process ending).
    public void open(List<String> command, String listUrl, Consumer<@Nullable String> onOutcome) {
        long myGeneration = generation.incrementAndGet();
        executor.execute(() -> {
            Process previous;
            synchronized (this) {
                previous = process;
                process = null;
            }
            if (previous != null) {
                previous.destroy();
            }
            Process started;
            try {
                started = new ProcessBuilder(command).redirectErrorStream(true).start();
            } catch (Exception e) {
                onOutcome.accept("cannot start ssh: " + e.getMessage());
                return;
            }
            synchronized (this) {
                if (generation.get() != myGeneration) {
                    started.destroy();
                    return;
                }
                process = started;
            }
            // Drain the combined output while polling (Jetty logs freely; an
            // undrained pipe would stall the JVM); keep a tail for the error
            // story when the process dies instead of serving.
            StringBuilder output = new StringBuilder();
            executor.execute(() -> drain(started.getInputStream(), output));
            long deadline = System.currentTimeMillis() + OPEN_TIMEOUT.toMillis();
            while (System.currentTimeMillis() < deadline) {
                if (generation.get() != myGeneration) {
                    return;
                }
                if (!started.isAlive()) {
                    onOutcome.accept(exitStory(started, output));
                    return;
                }
                if (answers(listUrl)) {
                    onOutcome.accept(null);
                    return;
                }
                try {
                    Thread.sleep(POLL_EVERY.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            started.destroy();
            onOutcome.accept("no answer on %s within %d s".formatted(listUrl,
                    OPEN_TIMEOUT.toSeconds()));
        });
    }

    private boolean answers(String listUrl) {
        try {
            HttpResponse<Void> response = http.send(
                    HttpRequest.newBuilder(URI.create(listUrl))
                            .timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drain(InputStream stream, StringBuilder tail) {
        try (stream) {
            byte[] buffer = new byte[4096];
            for (int read = stream.read(buffer); read >= 0; read = stream.read(buffer)) {
                synchronized (tail) {
                    tail.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    if (tail.length() > 4000) {
                        tail.delete(0, tail.length() - 4000);
                    }
                }
            }
        } catch (Exception e) {
            Logger.debug("Refactoring view output drain ended: {}", e.getMessage());
        }
    }

    private static String exitStory(Process process, StringBuilder output) {
        String tail;
        synchronized (output) {
            tail = output.toString().strip();
        }
        int lastLines = tail.lastIndexOf('\n', Math.max(0, tail.length() - 200));
        return "ssh exited with " + process.exitValue()
                + (tail.isEmpty() ? "" : " — " + tail.substring(lastLines + 1));
    }

    /// Ends the current view (idempotent) — application shutdown.
    @Override
    public void close() {
        generation.incrementAndGet();
        Process current;
        synchronized (this) {
            current = process;
            process = null;
        }
        if (current != null) {
            current.destroy();
        }
        // Otherwise its selector thread outlives the view: one per app start,
        // which the UI tests (one JVM, an app start per class) piled up.
        http.shutdownNow();
    }
}
