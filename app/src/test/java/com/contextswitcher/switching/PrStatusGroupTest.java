package com.contextswitcher.switching;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~pr-status-grouping~1]
class PrStatusGroupTest {

    private static final String JABREF = "https://github.com/JabRef/jabref/pull/1";
    private static final String OTHER = "https://github.com/o/r/pull/2";

    private static PrInfo open(Map<String, String> labels) {
        return new PrInfo(PrState.OPEN, "t", labels);
    }

    @Test
    void labelReposAreThoseWithAnyStatusLabel() {
        assertThat(PrStatusGroup.labelRepos(Map.of(
                JABREF, open(Map.of("status: ready-for-review", "#fff")),
                "https://github.com/JabRef/jabref/pull/3", open(Map.of()),
                OTHER, open(Map.of("bug", "#fff")))))
                .containsExactly("jabref/jabref");
    }

    @Test
    void labelRepoGroupsByStatusLabel() {
        Set<String> repos = Set.of("jabref/jabref");
        assertThat(PrStatusGroup.bucket(JABREF, open(Map.of("bug", "#f", "status: awaits-second-review", "#f")), repos))
                .isEqualTo("status: awaits-second-review");
        assertThat(PrStatusGroup.bucket(JABREF, open(Map.of()), repos)).isEqualTo("no status");
        assertThat(PrStatusGroup.bucket(JABREF, new PrInfo(PrState.DRAFT, "t", Map.of()), repos)).isEqualTo("draft");
    }

    @Test
    void otherRepoGroupsByGitHubState() {
        Set<String> repos = Set.of("jabref/jabref");
        assertThat(PrStatusGroup.bucket(OTHER, new PrInfo(PrState.DRAFT, "t", Map.of()), repos)).isEqualTo("draft");
        assertThat(PrStatusGroup.bucket(OTHER, open(Map.of()), repos)).isEqualTo("ready");
        assertThat(PrStatusGroup.bucket(OTHER, new PrInfo(PrState.OPEN, "t", Map.of(), false, true), repos))
                .isEqualTo("changes requested");
    }

    @Test
    void mergeQueueMergedAndClosedWinOverLabels() {
        Map<String, String> label = Map.of("status: ready-for-review", "#f");
        Set<String> repos = Set.of("jabref/jabref");
        assertThat(PrStatusGroup.bucket(JABREF, new PrInfo(PrState.OPEN, "t", label, true, false), repos))
                .isEqualTo("merge queue");
        assertThat(PrStatusGroup.bucket(JABREF, new PrInfo(PrState.MERGED, "t", label), repos)).isEqualTo("merged");
        assertThat(PrStatusGroup.bucket(OTHER, new PrInfo(PrState.CLOSED, "t", Map.of()), repos)).isEqualTo("closed");
    }

    @Test
    void bucketsSortInWorkflowOrder() {
        List<String> buckets = new java.util.ArrayList<>(List.of("merged", "status: zzz", "ready",
                "merge queue", "draft", "status: changes-required"));
        buckets.sort(PrStatusGroup.BY_WORKFLOW);
        assertThat(buckets).containsExactly("draft", "status: changes-required", "ready",
                "status: zzz", "merge queue", "merged");
    }
}
