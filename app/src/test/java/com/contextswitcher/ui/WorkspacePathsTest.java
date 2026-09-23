package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A task row names its workspace only below the directory its category's
/// header already shows.
// [utest->dsn~category-header-path~1]
class WorkspacePathsTest {

    @Test
    void aPathBelowTheRootKeepsOnlyThePartBelowIt() {
        assertThat(WorkspacePaths.belowRoot("/home/u/ws/jabref/2026-09-16-fix", "/home/u/ws/jabref"))
                .isEqualTo("2026-09-16-fix");
        assertThat(WorkspacePaths.belowRoot("/home/u/ws/jabref/2026-09-16-fix/", "/home/u/ws/jabref/"))
                .isEqualTo("2026-09-16-fix");
        assertThat(WorkspacePaths.belowRoot("C:\\git\\jabref\\task", "C:\\git\\jabref"))
                .isEqualTo("task");
    }

    @Test
    void anythingElseStaysAsItIs() {
        assertThat(WorkspacePaths.belowRoot("/home/u/ws/jabref-old/x", "/home/u/ws/jabref"))
                .as("a sibling sharing the prefix").isEqualTo("/home/u/ws/jabref-old/x");
        assertThat(WorkspacePaths.belowRoot("/home/u/ws/jabref", "/home/u/ws/jabref"))
                .as("the root itself").isEqualTo("/home/u/ws/jabref");
        assertThat(WorkspacePaths.belowRoot("/elsewhere/x", "/home/u/ws/jabref")).isEqualTo("/elsewhere/x");
        assertThat(WorkspacePaths.belowRoot("/home/u/ws/jabref/x", null)).isEqualTo("/home/u/ws/jabref/x");
    }
}
