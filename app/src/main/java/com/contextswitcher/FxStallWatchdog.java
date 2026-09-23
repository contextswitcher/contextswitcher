package com.contextswitcher;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.tinylog.Logger;

/// Logs where the JavaFX application thread is stuck whenever it stops
/// servicing events: a daemon thread posts a probe through `Platform.runLater`
/// once a second, and a probe that has not run after [#STALL_THRESHOLD] gets
/// the FX thread's stack trace written to the log, plus a second line once
/// the thread answers again saying how long the stall lasted.
/// Field report 2026-08-27 (Windows): "App startup is slow. App is not
/// responding" — the window drawn, hover and clicks dead — with nothing in
/// the log naming the culprit, because a busy FX thread logs nothing by
/// itself. Like [MemoryLog], a sentinel: the next report carries the answer.
/// [impl->dsn~fx-stall-log~1]
public final class FxStallWatchdog implements Runnable {

    /// How often a probe is posted while the FX thread is responsive.
    static final Duration PROBE_INTERVAL = Duration.ofSeconds(1);

    /// A probe older than this is a stall worth a stack trace. One second is
    /// well above any honest layout or CSS pass and well below what a user
    /// notices as "not responding".
    static final Duration STALL_THRESHOLD = Duration.ofSeconds(1);

    /// Frames kept from the FX thread's stack: the top of the stack names the
    /// blocking call, the bottom is always the Glass event loop.
    static final int MAX_FRAMES = 40;

    private final Thread fxThread;
    private final Consumer<Runnable> runLater;
    private final Duration probeInterval;
    private final Duration stallThreshold;

    FxStallWatchdog(Thread fxThread, Consumer<Runnable> runLater, Duration probeInterval,
            Duration stallThreshold) {
        this.fxThread = fxThread;
        this.runLater = runLater;
        this.probeInterval = probeInterval;
        this.stallThreshold = stallThreshold;
    }

    /// Starts the watchdog as a daemon thread; `fxThread` is the thread
    /// `runLater` dispatches to (the caller of `Application.start`).
    public static Thread start(Thread fxThread, Consumer<Runnable> runLater) {
        Thread thread = new Thread(
                new FxStallWatchdog(fxThread, runLater, PROBE_INTERVAL, STALL_THRESHOLD),
                "cs-fx-watchdog");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                probe();
                Thread.sleep(probeInterval);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IllegalStateException e) {
            // Platform.runLater after the toolkit shut down — the app is
            // exiting, nothing left to watch.
            Logger.debug("FX watchdog stopping: {}", e.getMessage());
        }
    }

    /// One probe: posts a no-op to the FX thread and waits for it. Returns
    /// the stall length in milliseconds — zero when the probe ran within the
    /// threshold, else the full time the thread took to get to it, after
    /// logging the stack trace the moment the threshold passed.
    long probe() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        long posted = System.nanoTime();
        runLater.accept(done::countDown);
        if (done.await(stallThreshold.toMillis(), TimeUnit.MILLISECONDS)) {
            return 0;
        }
        Logger.warn("FX thread unresponsive for {} ms so far; it is at:\n{}",
                elapsedMillis(posted), formatStack(fxThread.getStackTrace()));
        done.await();
        long stalled = elapsedMillis(posted);
        Logger.warn("FX thread responsive again after {} ms", stalled);
        return stalled;
    }

    private static long elapsedMillis(long sinceNanos) {
        return (System.nanoTime() - sinceNanos) / 1_000_000;
    }

    /// The stack as the JVM prints it (`\tat …` per frame), cut to
    /// [#MAX_FRAMES] with a trailing count of what was dropped.
    static String formatStack(StackTraceElement[] stack) {
        String frames = Arrays.stream(stack).limit(MAX_FRAMES)
                .map(frame -> "\tat " + frame)
                .collect(Collectors.joining("\n"));
        if (stack.length > MAX_FRAMES) {
            frames += "\n\t… " + (stack.length - MAX_FRAMES) + " more";
        }
        return frames;
    }
}
