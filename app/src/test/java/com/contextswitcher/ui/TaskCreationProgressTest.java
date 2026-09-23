package com.contextswitcher.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/// The time-driven creation bar: it must start near empty, grow with the
/// elapsed time, and never reach full — a finished bar on a task whose remote
/// half is still coming up is exactly the lie the bar exists to avoid.
// [utest->dsn~task-create-progress~5]
class TaskCreationProgressTest {

    @Test
    void startsNearEmptyAndGrows() {
        long now = System.nanoTime();
        double justStarted = TaskListCell.Creation.progressAt(now);
        double halfway = TaskListCell.Creation.progressAt(now - TimeUnit.SECONDS.toNanos(10));
        assertTrue(justStarted < 0.05, "just started: " + justStarted);
        assertEquals(0.5, halfway, 0.05);
    }

    @Test
    void staysShortOfFullOnASlowRemote() {
        assertEquals(0.99,
                TaskListCell.Creation.progressAt(System.nanoTime() - TimeUnit.MINUTES.toNanos(5)),
                1e-9);
    }
}
