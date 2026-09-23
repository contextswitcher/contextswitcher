package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Matching an activated browser tab against a task's `browser.urls`.
// [utest->dsn~browser-tab-selects-task~5]
class TaskTabUrlMatchTest {

    private static final String PR = "https://github.com/JabRef/jabref/pull/16659";

    private static Task task(String... urls) {
        return new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null,
                Task.BrowserConfig.ofUrls(urls), null, null, "");
    }

    @Test
    void exactUrlIsTheStrongestMatch() {
        assertThat(task("https://builds.example.org", PR).tabUrlMatch(PR)).isEqualTo(2);
    }

    @Test
    void subPageFragmentAndQueryMatchWeakly() {
        assertThat(task(PR).tabUrlMatch(PR + "/files")).isEqualTo(1);
        assertThat(task(PR).tabUrlMatch(PR + "#discussion_r1")).isEqualTo(1);
        assertThat(task(PR).tabUrlMatch(PR + "?w=1")).isEqualTo(1);
    }

    /// The boundary character keeps a longer sibling PR out.
    @Test
    void longerSiblingDoesNotMatch() {
        assertThat(task(PR).tabUrlMatch(PR + "0")).isZero();
        assertThat(task(PR).tabUrlMatch("https://github.com/JabRef/jabref/pull/1")).isZero();
    }

    @Test
    void taskWithoutBrowserSectionNeverMatches() {
        Task task = new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null, null, null, null, "");
        assertThat(task.tabUrlMatch(PR)).isZero();
    }
}
