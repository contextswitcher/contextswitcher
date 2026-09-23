package com.contextswitcher.switching;

import java.util.List;
import java.util.Map;

import com.contextswitcher.local.LocalCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

// [utest->dsn~pr-state-indicator~3]
// [utest->dsn~pr-poll-economy~2]
// [utest->dsn~pr-header-line~4]
class PrStateLookupTest {

    private final PrStateLookup lookup = new PrStateLookup(new LocalCommandRunner());

    @Test
    void commandBatchesAllPrsIntoOneCallWithoutQuotesInArgv() {
        List<String> argv = PrStateLookup.command(List.of(
                "https://github.com/o/r/pull/7", "https://github.com/x/y.z/pull/12"));
        assertThat(argv).startsWith("gh", "api", "graphql")
                .contains("o0=o", "r0=r", "n0=7", "o1=x", "r1=y.z", "n1=12")
                .noneMatch(part -> part.contains("\""));
        String query = argv.get(argv.size() - 1);
        assertThat(query).startsWith("query=query(")
                .contains("p0: repository(owner: $o0, name: $r0) { pullRequest(number: $n0) { state isDraft isInMergeQueue reviewDecision title labels(first: 30) { nodes { name color } } } }")
                .contains("p1: repository(owner: $o1, name: $r1)")
                .contains("$n1: Int!");
        // owner/repo travel as raw strings (-f): an all-digit owner login must
        // not be auto-typed into an Int by -F.
        assertThat(argv.get(argv.indexOf("o0=o") - 1)).isEqualTo("-f");
        assertThat(argv.get(argv.indexOf("n0=7") - 1)).isEqualTo("-F");
    }

    @Test
    void parseMapsAliasesBackToUrlsAndSkipsMissingOnes() {
        String json = """
                {"data": {
                  "p0": {"pullRequest": {"state": "OPEN", "isDraft": true, "title": "Fix it",
                         "labels": {"nodes": [{"name": "status: changes-required", "color": "d93f0b"}, {"name": "component: x", "color": "0e8a16"}]}}},
                  "p1": {"pullRequest": {"state": "MERGED", "isDraft": false, "title": "Done"}},
                  "p3": {"pullRequest": {"state": "OPEN", "isDraft": false, "isInMergeQueue": true,
                         "reviewDecision": "CHANGES_REQUESTED", "title": "Queued"}},
                  "p2": null
                }}""";
        Map<String, PrInfo> parsed = lookup.parse(json, List.of("u0", "u1", "u2", "u3"));
        assertThat(parsed).containsOnly(
                entry("u0", new PrInfo(PrState.DRAFT, "Fix it",
                        Map.of("status: changes-required", "#d93f0b", "component: x", "#0e8a16"))),
                entry("u1", new PrInfo(PrState.MERGED, "Done", Map.of())),
                entry("u3", new PrInfo(PrState.OPEN, "Queued", Map.of(), true, true)));
    }

    @Test
    void parseReturnsEmptyOnGarbage() {
        assertThat(lookup.parse("not json", List.of("u0"))).isEmpty();
    }

    @Test
    void fromMapsGhStateAndDraftFlag() {
        assertThat(PrState.from("open", false)).isEqualTo(PrState.OPEN);
        assertThat(PrState.from("open", true)).isEqualTo(PrState.DRAFT);
        assertThat(PrState.from("merged", false)).isEqualTo(PrState.MERGED);
        assertThat(PrState.from("closed", false)).isEqualTo(PrState.CLOSED);
        assertThat(PrState.from("nope", false)).isNull();
    }

    // [utest->dsn~gitlab-mr-state~1]
    @Test
    void fromMapsGitLabStates() {
        assertThat(PrState.from("opened", false)).isEqualTo(PrState.OPEN);
        assertThat(PrState.from("opened", true)).isEqualTo(PrState.DRAFT);
        assertThat(PrState.from("locked", false)).isEqualTo(PrState.CLOSED);
    }
}
