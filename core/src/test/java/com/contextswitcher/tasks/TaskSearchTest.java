package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-find~9]
class TaskSearchTest {

    private static TaskEntry.Loaded task(String id, String title,
            Task.@org.jspecify.annotations.Nullable BrowserConfig browser) {
        return new TaskEntry.Loaded(new Task(id, title, TaskStatus.ACTIVE, "devbox",
                null, null, null, browser, null, null, null, ""));
    }

    @Test
    void blankQueryMatchesEverything() {
        assertThat(TaskSearch.matches(task("jabref/fix", "Fix NPE", null), "")).isTrue();
        assertThat(TaskSearch.matches(task("jabref/fix", "Fix NPE", null), "   ")).isTrue();
    }

    @Test
    void matchesByTitleCaseInsensitively() {
        assertThat(TaskSearch.matches(task("jabref/fix", "Fix NPE", null), "npe")).isTrue();
    }

    @Test
    void matchesByIdAndGroup() {
        assertThat(TaskSearch.matches(task("jabref/fix-npe", "T", null), "jabref")).isTrue();
    }

    @Test
    void matchesByRemote() {
        assertThat(TaskSearch.matches(task("t", "T", null), "devbox")).isTrue();
    }

    @Test
    void findsTaskByFullPrUrl() {
        TaskEntry.Loaded entry = task("jabref/pr", "Review",
                Task.BrowserConfig.ofUrls("https://github.com/JabRef/jabref/pull/16245"));
        assertThat(TaskSearch.matches(entry, "https://github.com/JabRef/jabref/pull/16245")).isTrue();
    }

    @Test
    void findsTaskByPrUrlWithTrailingSlash() {
        TaskEntry.Loaded entry = task("jabref/pr", "Review",
                Task.BrowserConfig.ofUrls("https://github.com/JabRef/jabref/pull/16245"));
        assertThat(TaskSearch.matches(entry, "https://github.com/JabRef/jabref/pull/16245/")).isTrue();
    }

    @Test
    void findsTaskByBarePrNumber() {
        TaskEntry.Loaded entry = task("jabref/pr", "Review",
                Task.BrowserConfig.ofUrls("https://github.com/JabRef/jabref/pull/16245"));
        assertThat(TaskSearch.matches(entry, "16245")).isTrue();
    }

    @Test
    void matchesByBrowserUrlTitle() {
        Task.BrowserConfig browser = new Task.BrowserConfig(
                java.util.List.of(new Task.UrlEntry("https://x/1", "Probeklausur in moodle")));
        assertThat(TaskSearch.matches(task("t", "T", browser), "moodle")).isTrue();
    }

    @Test
    void matchesByTag() {
        TaskEntry.Loaded entry = new TaskEntry.Loaded(new Task("t", "T", TaskStatus.ACTIVE, "devbox",
                null, null, null, null, null, null, java.util.List.of(), java.util.List.of("phone"), ""));
        assertThat(TaskSearch.matches(entry, "phone")).isTrue();
    }

    @Test
    void matchesByNoteBody() {
        TaskEntry.Loaded entry = new TaskEntry.Loaded(new Task("t", "T", TaskStatus.ACTIVE, "devbox",
                null, null, null, null, null, null, java.util.List.of(), java.util.List.of(),
                "Ask the tutor about exercise 36"));
        assertThat(TaskSearch.matches(entry, "exercise 36")).isTrue();
    }

    @Test
    void matchesByConfiguration() {
        TaskEntry.Loaded entry = new TaskEntry.Loaded(new Task("t", "T", TaskStatus.ACTIVE, "devbox",
                new Task.TmuxConfig("jabref", "claude"), new Task.TerminalConfig("build log"),
                new Task.IntellijConfig("/home/o/jabref", null, null), null, null, null, ""));
        assertThat(TaskSearch.matches(entry, "claude")).isTrue();
        assertThat(TaskSearch.matches(entry, "build log")).isTrue();
        assertThat(TaskSearch.matches(entry, "/home/o/jabref")).isTrue();
    }

    @Test
    void nonMatchingQueryFails() {
        assertThat(TaskSearch.matches(task("jabref/fix", "Fix NPE", null), "zzz")).isFalse();
    }

    @Test
    void matchesQueuedAndSentMessageTextAsFallback() {
        TaskEntry.Loaded entry = task("t", "T", null);
        assertThat(TaskSearch.matches(entry, "flaky", () -> "please retry the flaky test")).isTrue();
        assertThat(TaskSearch.matches(entry, "zzz", () -> "please retry the flaky test")).isFalse();
    }

    @Test
    void doesNotReadMessagesWhenTheEntryItselfMatches() {
        assertThat(TaskSearch.matches(task("t", "Fix NPE", null), "npe", () -> {
            throw new AssertionError("messages must not be read for a matching entry");
        })).isTrue();
    }

    @Test
    void matchesFailedEntryByFileNameAndError() {
        TaskEntry.Failed failed = new TaskEntry.Failed("bad", "bad.md", "mapping values not allowed");
        assertThat(TaskSearch.matches(failed, "bad.md")).isTrue();
        assertThat(TaskSearch.matches(failed, "mapping values")).isTrue();
    }
}
