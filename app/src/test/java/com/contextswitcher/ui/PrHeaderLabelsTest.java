package com.contextswitcher.ui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~pr-header-line~4]
class PrHeaderLabelsTest {

    @Test
    void keepsOnlyStatusLabels() {
        assertThat(MainWindow.statusLabels(List.of(
                "component: ui", "status: changes-required", "dev: ci-cd", "status: no-bot-comments")))
                .containsExactly("status: changes-required", "status: no-bot-comments");
    }

    @Test
    void isCaseInsensitiveAndKeepsOrder() {
        assertThat(MainWindow.statusLabels(List.of("Status: waiting", "Pinned", "status:merged")))
                .containsExactly("Status: waiting", "status:merged");
    }

    @Test
    void noStatusLabelMeansNoChips() {
        assertThat(MainWindow.statusLabels(List.of("component: ui", "Pinned"))).isEmpty();
    }

    @Test
    void chipNamesPrNumberInsteadOfStatusPrefix() {
        assertThat(MainWindow.statusChipText("https://github.com/JabRef/jabref/pull/17148", "status: ready-for-review"))
                .isEqualTo("17148 · ready-for-review");
        assertThat(MainWindow.statusChipText("https://github.com/o/r/pull/17138/", "Status:to-be-merged"))
                .isEqualTo("17138 · to-be-merged");
    }

    // [utest->dsn~gitlab-mr-state~1]
    @Test
    void chipNamesMergeRequestIidInGitLabSpelling() {
        assertThat(MainWindow.statusChipText("https://gitlab.com/g/sub/p/-/merge_requests/42", "status: review"))
                .isEqualTo("!42 · review");
    }

    @Test
    void chipWithoutPrUrlKeepsStatusOnly() {
        assertThat(MainWindow.statusChipText("https://example.org", "status: waiting")).isEqualTo("waiting");
    }
}
