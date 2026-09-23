package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~pr-state-indicator~3]
class TaskPrUrlTest {

    private static Task task(Task.@org.jspecify.annotations.Nullable BrowserConfig browser) {
        return new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null, browser, null, null, "");
    }

    @Test
    void picksTheFirstGitHubPullRequestUrl() {
        Task task = task(Task.BrowserConfig.ofUrls(
                "https://builds.example.org",
                "https://github.com/JabRef/jabref/pull/16246",
                "https://github.com/JabRef/jabref/pull/16247"));
        assertThat(task.prUrl()).isEqualTo("https://github.com/JabRef/jabref/pull/16246");
    }

    @Test
    void keepsEveryGitHubPullRequestUrl() {
        Task task = task(Task.BrowserConfig.ofUrls(
                "https://builds.example.org",
                "https://github.com/JabRef/jabref/pull/16287",
                "https://github.com/JabRef/user-documentation/pull/643"));
        assertThat(task.prUrls()).containsExactly(
                "https://github.com/JabRef/jabref/pull/16287",
                "https://github.com/JabRef/user-documentation/pull/643");
    }

    /// The status bar's hover and "Opening …" lines show the title ahead of
    /// the URL; an untitled entry stays the bare URL.
    @Test
    void displayPutsTheTitleAheadOfTheUrl() {
        assertThat(new Task.UrlEntry("https://github.com/o/r/pull/1", "Docs PR").display())
                .isEqualTo("Docs PR — https://github.com/o/r/pull/1");
    }

    @Test
    void displayOfAnUntitledEntryIsTheBareUrl() {
        assertThat(new Task.UrlEntry("https://github.com/o/r/pull/1", null).display())
                .isEqualTo("https://github.com/o/r/pull/1");
    }

    @Test
    void displayOfABlankTitleIsTheBareUrl() {
        assertThat(new Task.UrlEntry("https://github.com/o/r/pull/1", "  ").display())
                .isEqualTo("https://github.com/o/r/pull/1");
    }

    @Test
    void prEntriesKeepTheTitlesOfThePrUrls() {
        Task task = task(new Task.BrowserConfig(java.util.List.of(
                new Task.UrlEntry("https://example.org", "Not a PR"),
                new Task.UrlEntry("https://github.com/o/r/pull/1", "Code PR"))));

        assertThat(task.prEntries())
                .containsExactly(new Task.UrlEntry("https://github.com/o/r/pull/1", "Code PR"));
    }

    /// The row's link icons: everything that is not a PR, titles kept.
    // [utest->dsn~task-link-icons~2]
    @Test
    void linkEntriesAreTheNonPrUrls() {
        Task task = task(new Task.BrowserConfig(java.util.List.of(
                new Task.UrlEntry("https://github.com/JabRef/jabref/pull/16287", null),
                new Task.UrlEntry("https://github.com/koppor/reference-shortener", "Shortener"),
                new Task.UrlEntry("https://github.com/o/r/issues/1", null),
                new Task.UrlEntry("https://github.com/o/r/issues/1", null))));

        assertThat(task.linkEntries()).containsExactly(
                new Task.UrlEntry("https://github.com/koppor/reference-shortener", "Shortener"),
                new Task.UrlEntry("https://github.com/o/r/issues/1", null));
    }

    // [utest->dsn~task-link-icons~2]
    @Test
    void linkEntriesAreEmptyWithoutBrowserOrWithPrsOnly() {
        assertThat(task(null).linkEntries()).isEmpty();
        assertThat(task(Task.BrowserConfig.ofUrls("https://github.com/o/r/pull/1")).linkEntries()).isEmpty();
    }

    @Test
    void emptyWhenNoBrowserOrNoPrUrl() {
        assertThat(task(null).prUrls()).isEmpty();
        assertThat(task(Task.BrowserConfig.ofUrls("https://github.com/o/r/issues/1")).prUrls()).isEmpty();
    }

    @Test
    void nullWhenNoBrowserOrNoPrUrl() {
        assertThat(task(null).prUrl()).isNull();
        assertThat(task(Task.BrowserConfig.ofUrls("https://github.com/o/r/issues/1")).prUrl()).isNull();
        assertThat(task(Task.BrowserConfig.ofUrls("https://example.org")).prUrl()).isNull();
    }
}
