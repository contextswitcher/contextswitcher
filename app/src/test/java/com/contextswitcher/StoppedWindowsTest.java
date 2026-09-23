package com.contextswitcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

// [utest->dsn~pr-state-refresh-on-stop~2]
class StoppedWindowsTest {

    @Test
    void windowLeavingWorkingIsStopped() {
        assertThat(Main.stoppedWindows(
                Map.of("h @1", "working", "h @2", "working", "h @3", "waiting"),
                Map.of("h @1", "waiting", "h @2", "working")))
                .containsExactly("h @1");
    }

    @Test
    void vanishedWorkingWindowCountsAsStopped() {
        assertThat(Main.stoppedWindows(Map.of("h @1", "working"), Map.of())).containsExactly("h @1");
    }
}
