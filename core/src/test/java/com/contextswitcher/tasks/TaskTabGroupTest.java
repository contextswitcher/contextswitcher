package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The Firefox tab group a task's tabs are collected in.
// [utest->dsn~browser-tab-group~3]
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

    private static Task withUrls(String... urls) {
        return new Task("jabref-pr", "T", TaskStatus.ACTIVE, "devbox", new Task.TmuxConfig("jabref", "@339"),
                null, null, Task.BrowserConfig.ofUrls(urls), null, null, "");
    }

    @Test
    void firstPrOrIssueNumberWinsOverTheWindow() {
        assertThat(withUrls("https://example.org/docs", "https://github.com/JabRef/jabref/pull/17148/files",
                "https://github.com/JabRef/jabref/issues/42").tabGroup()).isEqualTo("#17148");
        assertThat(withUrls("https://github.com/JabRef/jabref/issues/42").tabGroup()).isEqualTo("#42");
        assertThat(withUrls("https://gitlab.example.org/group/sub/proj/-/merge_requests/7").tabGroup())
                .isEqualTo("!7");
    }

    @Test
    void urlsWithoutANumberFallBackToTheWindow() {
        assertThat(withUrls("https://github.com/JabRef/jabref/pulls", "https://example.org/pull/1x").tabGroup())
                .isEqualTo("339");
    }
}
