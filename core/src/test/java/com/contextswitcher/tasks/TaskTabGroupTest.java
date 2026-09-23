package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The Firefox tab group a task's tabs are collected in.
// [utest->dsn~browser-tab-group~2]
class TaskTabGroupTest {

    private static Task withWindow(String id, Task.@org.jspecify.annotations.Nullable TmuxConfig tmux) {
        return new Task(id, "T", TaskStatus.ACTIVE, "devbox", tmux, null, null, null, null, null, "");
    }

    @Test
    void windowIdBecomesItsNumber() {
        assertThat(withWindow("jabref-pr", new Task.TmuxConfig("jabref", "@339")).tabGroup()).isEqualTo("339");
    }

    @Test
    void plainWindowIndexIsUsedAsIs() {
        assertThat(withWindow("jabref-pr", new Task.TmuxConfig("jabref", "7")).tabGroup()).isEqualTo("7");
    }

    @Test
    void namedOrMissingWindowFallsBackToTheTaskId() {
        assertThat(withWindow("jabref-pr", new Task.TmuxConfig("jabref", "claude")).tabGroup())
                .isEqualTo("l:jabref-pr");
        assertThat(withWindow("jabref-pr", new Task.TmuxConfig("jabref", null)).tabGroup())
                .isEqualTo("l:jabref-pr");
        assertThat(withWindow("local-notes", null).tabGroup()).isEqualTo("l:local-notes");
    }
}
