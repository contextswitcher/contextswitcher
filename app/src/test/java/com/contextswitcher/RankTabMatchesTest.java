package com.contextswitcher;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;

/// Field report 2026-09-09: two tasks named PR 17071 — one implementing it,
/// one checking why a workflow of it fails. Activating that PR's tab switched
/// the app away from the selected task to the other one, terminal mirror
/// included.
// [utest->dsn~browser-tab-selects-task~5]
class RankTabMatchesTest {

    private static final String PR = "https://github.com/JabRef/jabref/pull/17071";

    @Test
    void theSelectedTaskWinsATie() {
        Task other = task("g/other", PR);
        Task selected = task("g/selected", PR);
        assertThat(Main.rankTabMatches(List.of(other, selected), PR, "g/selected"))
                .containsExactly(selected, other);
    }

    @Test
    void aBetterMatchStillBeatsTheSelectedTask() {
        Task exact = task("g/exact", PR);
        Task subPage = task("g/sub", "https://github.com/JabRef/jabref");
        assertThat(Main.rankTabMatches(List.of(subPage, exact), PR, "g/sub"))
                .containsExactly(exact, subPage);
    }

    @Test
    void noSelectionKeepsTheOrderOfEqualMatches() {
        Task first = task("g/first", PR);
        Task second = task("g/second", PR);
        assertThat(Main.rankTabMatches(List.of(first, second), PR, null))
                .containsExactly(first, second);
    }

    private static Task task(String id, String url) {
        return new Task(id, id, TaskStatus.ACTIVE, null, null, null, null,
                Task.BrowserConfig.ofUrls(url), null, null, "");
    }
}
