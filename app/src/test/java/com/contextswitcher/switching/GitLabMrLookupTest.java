package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.contextswitcher.local.LocalCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

// [utest->dsn~gitlab-mr-state~1]
class GitLabMrLookupTest {

    private static final String MR = "https://gitlab.com/stp-team/systemtestportal-webapp/-/merge_requests/629";

    @Test
    void mrUrlMatchesNestedGroupsAnyHostAndATrailingSlash() {
        assertThat(GitLabMrLookup.MR_URL.matcher(MR).matches()).isTrue();
        assertThat(GitLabMrLookup.MR_URL.matcher("https://git.example.org/a/b/c/-/merge_requests/3/").matches()).isTrue();
        assertThat(GitLabMrLookup.MR_URL.matcher("https://github.com/o/r/pull/7").matches()).isFalse();
        assertThat(GitLabMrLookup.MR_URL.matcher("https://gitlab.com/o/r/-/issues/7").matches()).isFalse();
    }

    @Test
    void commandBatchesOneHostIntoOneCallWithoutQuotesInArgv() {
        List<String> argv = GitLabMrLookup.command("gitlab.com",
                List.of(MR, "https://gitlab.com/a/b/c/-/merge_requests/3"));
        assertThat(argv).startsWith("glab", "api", "graphql", "--hostname", "gitlab.com")
                .contains("p0=stp-team/systemtestportal-webapp", "i0=629", "p1=a/b/c", "i1=3")
                .noneMatch(part -> part.contains("\""));
        assertThat(argv.getLast()).startsWith("query=query($p0: ID!, $i0: String!, $p1: ID!, $i1: String!)")
                .contains("m0: project(fullPath: $p0) { mergeRequest(iid: $i0) { state draft title labels(first: 30) { nodes { title color } } } }")
                .contains("m1: project(fullPath: $p1)");
        // The iid is a String! in GitLab's schema: raw (-f), never inferred (-F).
        assertThat(argv.get(argv.indexOf("i0=629") - 1)).isEqualTo("-f");
    }

    @Test
    void parseMapsGitLabStatesAndSkipsMissingMrs() {
        String json = """
                {"data": {
                  "m0": {"mergeRequest": {"state": "opened", "draft": true, "title": "Fix it",
                         "labels": {"nodes": [{"title": "status: review", "color": "#d93f0b"}]}}},
                  "m1": {"mergeRequest": {"state": "merged", "draft": false, "title": "Done", "labels": {"nodes": []}}},
                  "m2": {"mergeRequest": null},
                  "m3": {"mergeRequest": {"state": "locked", "draft": false, "title": "Locked"}}
                }}""";
        Map<String, PrInfo> parsed = new GitLabMrLookup(new LocalCommandRunner())
                .parse(json, List.of("u0", "u1", "u2", "u3"));
        assertThat(parsed).containsOnly(
                entry("u0", new PrInfo(PrState.DRAFT, "Fix it", Map.of("status: review", "#d93f0b"))),
                entry("u1", new PrInfo(PrState.MERGED, "Done", Map.of())),
                entry("u3", new PrInfo(PrState.CLOSED, "Locked", Map.of())));
    }

    @Test
    void statesRunOneGlabCallPerHostAndPrStateLookupMergesThemWithGitHub() {
        List<List<String>> calls = new ArrayList<>();
        LocalCommandRunner runner = new LocalCommandRunner() {
            @Override
            public LocalResult run(List<String> command) {
                calls.add(command);
                String json = command.getFirst().equals("glab")
                        ? "{\"data\": {\"m0\": {\"mergeRequest\": {\"state\": \"opened\", \"draft\": false, \"title\": \"MR\"}}}}"
                        : "{\"data\": {\"p0\": {\"pullRequest\": {\"state\": \"MERGED\", \"isDraft\": false, \"title\": \"PR\"}}}}";
                return new LocalResult(0, json, "");
            }
        };
        String selfHosted = "https://git.example.org/x/y/-/merge_requests/1";
        String pr = "https://github.com/o/r/pull/7";

        Map<String, PrInfo> states = new PrStateLookup(runner).states(List.of(MR, selfHosted, pr));

        assertThat(calls).extracting(List::getFirst).containsExactlyInAnyOrder("glab", "glab", "gh");
        assertThat(states).containsOnlyKeys(MR, selfHosted, pr);
        assertThat(states.get(MR).state()).isEqualTo(PrState.OPEN);
        assertThat(states.get(pr).state()).isEqualTo(PrState.MERGED);
    }

    @Test
    void titleFallsBackToIidAndPathWhenGlabFails() {
        LocalCommandRunner failing = new LocalCommandRunner() {
            @Override
            public LocalResult run(List<String> command) {
                throw new IllegalStateException("Cannot run program \"glab\"");
            }
        };
        assertThat(new PrTitleLookup(failing).title(MR))
                .isEqualTo("MR !629 (stp-team/systemtestportal-webapp)");
        assertThat(new PrTitleLookup(failing).title("https://example.org/x")).isNull();
    }
}
