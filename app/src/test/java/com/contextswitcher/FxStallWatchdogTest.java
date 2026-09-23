package com.contextswitcher;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~fx-stall-log~1]
class FxStallWatchdogTest {

    private static final Duration THRESHOLD = Duration.ofMillis(50);

    @Test
    void responsiveThreadProducesNoStall() throws Exception {
        FxStallWatchdog watchdog = new FxStallWatchdog(Thread.currentThread(), Runnable::run,
                Duration.ofMillis(1), THRESHOLD);

        assertThat(watchdog.probe()).isZero();
    }

    /// The probe only runs once the "FX thread" gets round to it — here a
    /// worker that sleeps past the threshold first. The reported stall is the
    /// full wait, not the threshold.
    @Test
    void lateProbeReportsTheWholeStall() throws Exception {
        FxStallWatchdog watchdog = new FxStallWatchdog(Thread.currentThread(),
                runnable -> Thread.ofVirtual().start(() -> {
                    try {
                        Thread.sleep(THRESHOLD.multipliedBy(3));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    runnable.run();
                }),
                Duration.ofMillis(1), THRESHOLD);

        assertThat(watchdog.probe()).isGreaterThanOrEqualTo(THRESHOLD.multipliedBy(3).toMillis() - 5);
    }

    @Test
    void stackIsCutToTheTopFrames() {
        StackTraceElement[] deep = IntStream.range(0, FxStallWatchdog.MAX_FRAMES + 7)
                .mapToObj(i -> new StackTraceElement("com.example.C" + i, "m", "C.java", i))
                .toArray(StackTraceElement[]::new);

        String formatted = FxStallWatchdog.formatStack(deep);

        assertThat(formatted.lines()).hasSize(FxStallWatchdog.MAX_FRAMES + 1);
        assertThat(formatted).startsWith("\tat com.example.C0.m(C.java:0)")
                .endsWith("\t… 7 more");
        assertThat(FxStallWatchdog.formatStack(new StackTraceElement[] {deep[0]}))
                .isEqualTo("\tat com.example.C0.m(C.java:0)");
    }
}
