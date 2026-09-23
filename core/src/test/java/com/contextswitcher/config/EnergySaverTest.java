package com.contextswitcher.config;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~energy-saver~1]
class EnergySaverTest {

    @AfterEach
    void reset() {
        EnergySaver.setActive(false);
        EnergySaver.setRefreshers(() -> { }, () -> { });
    }

    @Test
    void theGateIsOpenUntilTheSaverIsSwitchedOn() {
        assertThat(EnergySaver.active()).isFalse();

        EnergySaver.setActive(true);

        assertThat(EnergySaver.active()).isTrue();
    }

    @Test
    void aRefreshRunsItsOwnActionOnly() {
        AtomicInteger taskChange = new AtomicInteger();
        AtomicInteger manual = new AtomicInteger();
        EnergySaver.setRefreshers(taskChange::incrementAndGet, manual::incrementAndGet);
        // The gate never holds a refresh back: it is the way state comes in
        // while the saver runs.
        EnergySaver.setActive(true);

        EnergySaver.refreshTask();
        EnergySaver.refreshAll();
        EnergySaver.refreshAll();

        assertThat(taskChange).hasValue(1);
        assertThat(manual).hasValue(2);
    }
}
