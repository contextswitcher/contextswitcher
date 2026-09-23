package com.contextswitcher.switching;

import java.util.List;

import com.contextswitcher.local.LocalCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~auto-pr-lookup~2]
class AutoPrLookupTest {

    private final AutoPrLookup lookup = new AutoPrLookup(new LocalCommandRunner());

    /// Two PRs: a small one (6 non-test lines beside 400 test lines) and one
    /// whose production diff is far past any triage size.
    private static final String JSON = """
            {"data": {"search": {"nodes": [
              {"url": "https://github.com/o/r/pull/7", "title": "Fix the label",
               "files": {"totalCount": 3, "nodes": [
                 {"path": "src/main/java/org/jabref/Ui.java", "additions": 4, "deletions": 2},
                 {"path": "src/test/java/org/jabref/UiTest.java", "additions": 300, "deletions": 100},
                 {"path": "CHANGELOG.md", "additions": 1, "deletions": 0}]}},
              {"url": "https://github.com/o/r/pull/8", "title": "Rewrite the parser",
               "files": {"totalCount": 2, "nodes": [
                 {"path": "src/main/java/org/jabref/Parser.java", "additions": 900, "deletions": 40},
                 {"path": "src/test/java/org/jabref/ParserTest.java", "additions": 10, "deletions": 0}]}},
              {}
            ]}}}""";

    // [utest->dsn~auto-category-placeholder-row~2]
    @Test
    void searchUrlCarriesTheForcedTermsEncoded() {
        assertThat(AutoPrLookup.searchUrl("repo:JabRef/jabref review-requested:@me"))
                .isEqualTo("https://github.com/search?q=is%3Apr+is%3Aopen+"
                        + "repo%3AJabRef%2Fjabref+review-requested%3A%40me&type=pullrequests");
    }

    @Test
    void commandForcesOpenPullRequestsAndCarriesNoQuoteOfOurs() {
        List<String> argv = AutoPrLookup.command("repo:JabRef/jabref review-requested:@me");
        assertThat(argv).startsWith("gh", "api", "graphql")
                .contains("q=is:pr is:open repo:JabRef/jabref review-requested:@me",
                        "n=" + AutoPrLookup.SEARCH_LIMIT);
        assertThat(argv.getLast()).startsWith("query=query($q: String!, $n: Int!)")
                .contains("search(query: $q, type: ISSUE, first: $n)")
                .doesNotContain("\"");
        // The count must reach GraphQL as an Int, the query as a raw string.
        assertThat(argv.get(argv.indexOf("n=" + AutoPrLookup.SEARCH_LIMIT) - 1)).isEqualTo("-F");
    }

    @Test
    void parseKeepsOnlyPrsWithinTheNonTestSizeLimit() {
        assertThat(lookup.parse(JSON, 50).matches()).containsExactly(
                new AutoPrLookup.Match("https://github.com/o/r/pull/7", "Fix the label"));
    }

    @Test
    void everyHitIsOpenWhateverTheSizeLimitSays() {
        // `open` is the retention set: the oversized PR is not added, but it
        // is open, so the task of somebody who added it stays.
        assertThat(lookup.parse(JSON, 50).open()).containsExactly(
                "https://github.com/o/r/pull/7", "https://github.com/o/r/pull/8");
    }

    @Test
    void withoutASizeLimitEveryHitCounts() {
        assertThat(lookup.parse(JSON, 0).matches()).extracting(AutoPrLookup.Match::url)
                .containsExactly("https://github.com/o/r/pull/7", "https://github.com/o/r/pull/8");
    }

    @Test
    void aPrWithMoreFilesThanTheQueryReadsIsDropped() {
        String truncated = """
                {"data": {"search": {"nodes": [
                  {"url": "https://github.com/o/r/pull/9", "title": "Huge",
                   "files": {"totalCount": 400, "nodes": [
                     {"path": "a.java", "additions": 1, "deletions": 0}]}}]}}}""";
        assertThat(lookup.parse(truncated, 50).matches()).isEmpty();
        assertThat(lookup.parse(truncated, 50).open()).hasSize(1);
        assertThat(lookup.parse(truncated, 0).matches()).hasSize(1);
    }

    @Test
    void parseReturnsEmptyOnGarbage() {
        assertThat(lookup.parse("not json", 50).matches()).isEmpty();
        assertThat(lookup.parse("not json", 50).open()).isEmpty();
    }

    @Test
    void testPathsAreRecognizedByWholeWordsOnly() {
        assertThat(AutoPrLookup.isTestPath("src/test/java/org/jabref/Ui.java")).isTrue();
        assertThat(AutoPrLookup.isTestPath("core/UiTest.java")).isTrue();
        assertThat(AutoPrLookup.isTestPath("pkg/parser_test.go")).isTrue();
        assertThat(AutoPrLookup.isTestPath("web/app.spec.ts")).isTrue();
        assertThat(AutoPrLookup.isTestPath("tests/conftest.py")).isTrue();
        assertThat(AutoPrLookup.isTestPath("src/main/java/org/jabref/Ui.java")).isFalse();
        assertThat(AutoPrLookup.isTestPath("data/latest.json")).isFalse();
        assertThat(AutoPrLookup.isTestPath("docs/protest-banner.md")).isFalse();
    }
}
