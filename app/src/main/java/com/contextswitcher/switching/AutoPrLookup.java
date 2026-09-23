package com.contextswitcher.switching;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.local.LocalCommandRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/// The open pull requests an auto category's query matches, via the locally
/// installed (and authenticated) `gh` CLI — the system-tool approach of
/// MADR 0003, like [PrStateLookup].
///
/// One `gh api graphql` call per category and round: GitHub's own search plus,
/// for every hit, its changed files, so the non-test size filter costs no
/// extra request. The query text travels as a GraphQL variable, so the argv
/// carries no quote of ours (the Windows trap).
// [impl->dsn~auto-pr-lookup~2]
public class AutoPrLookup {

    /// How many search hits one round looks at. A triage query is meant to be
    /// narrow; a wider one is cut off here rather than turned into a hundred
    /// task files.
    static final int SEARCH_LIMIT = 30;

    /// How many of a PR's files are counted. A PR touching more than this is
    /// far past any sensible `maxSloc`, so it is dropped rather than
    /// under-counted from a truncated file list.
    static final int FILES_LIMIT = 100;

    /// Prepended to the configured query: the reconcile deletes the task of a
    /// PR that left the query, so a *closed* PR must never match again — it
    /// would resurrect the task it just deleted, forever.
    static final String FORCED_TERMS = "is:pr is:open ";

    private static final String GRAPHQL = "query($q: String!, $n: Int!) {"
            + " search(query: $q, type: ISSUE, first: $n) { nodes { ... on PullRequest {"
            + " url title files(first: " + FILES_LIMIT + ") { totalCount"
            + " nodes { path additions deletions } } } } } }";

    /// One matched pull request: its URL and title, everything a task file
    /// needs.
    public record Match(String url, String title) {
    }

    /// One search round: the `matches` that passed the size filter — the ones
    /// worth adding — and `open`, the URL of *every* hit, size filter or not.
    /// The two differ on purpose: the filter decides what is worth **adding**,
    /// never what stays. A pull request that grew past the limit after it was
    /// added is still open and still the user's to review.
    public record Search(List<Match> matches, Set<String> open) {
    }

    private final LocalCommandRunner runner;
    private final ObjectMapper mapper = new ObjectMapper();

    public AutoPrLookup(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// The same search on github.com, for the placeholder row of an auto
    /// category (`dsn~auto-category-placeholder-row~2`). Approximate on
    /// purpose: the web search knows `is:pr is:open` and the query, but not
    /// the `maxSloc` filter, which is ours and has no GitHub equivalent.
    // [impl->dsn~auto-category-placeholder-row~2]
    public static String searchUrl(String query) {
        return "https://github.com/search?q="
                + URLEncoder.encode(FORCED_TERMS + query, StandardCharsets.UTF_8)
                + "&type=pullrequests";
    }

    static List<String> command(String query) {
        return List.of("gh", "api", "graphql",
                "-f", "q=" + FORCED_TERMS + query,
                "-F", "n=" + SEARCH_LIMIT,
                "-f", "query=" + GRAPHQL);
    }

    /// The round in `json`: every hit in `open`, and the ones whose non-test
    /// lines are at most `maxSloc` (`0` = no size filter) in `matches`.
    Search parse(String json, int maxSloc) {
        List<Match> matches = new ArrayList<>();
        Set<String> open = new LinkedHashSet<>();
        try {
            JsonNode nodes = mapper.readTree(json).path("data").path("search").path("nodes");
            for (JsonNode node : nodes) {
                String url = node.path("url").asText("");
                if (url.isEmpty()) {
                    continue;
                }
                open.add(url);
                if (fitsSize(node, maxSloc)) {
                    matches.add(new Match(url, node.path("title").asText(url)));
                }
            }
        } catch (java.io.IOException e) {
            Logger.debug("unparseable graphql PR-search response: {}", e.getMessage());
        }
        return new Search(matches, open);
    }

    /// Whether the PR changes at most `maxSloc` lines outside its tests —
    /// added plus deleted, the diff the reviewer actually reads.
    private static boolean fitsSize(JsonNode pr, int maxSloc) {
        if (maxSloc <= 0) {
            return true;
        }
        JsonNode files = pr.path("files");
        if (files.path("totalCount").asInt() > FILES_LIMIT) {
            return false;
        }
        int lines = 0;
        for (JsonNode file : files.path("nodes")) {
            if (!isTestPath(file.path("path").asText(""))) {
                lines += file.path("additions").asInt() + file.path("deletions").asInt();
            }
        }
        return lines <= maxSloc;
    }

    /// Whether a repository path belongs to the tests: `test`/`tests`/`spec`/
    /// `specs` as a whole word anywhere in it — a directory above the file
    /// (`src/test/java/…`, `tests/`) or a word of its name (`FooTest.java`,
    /// `foo_test.go`, `foo.spec.ts`). Words are cut at separators *and* at
    /// camel-case humps, so `latest.json` is not a test file.
    // ponytail: a name heuristic, not a build-system question — a project that
    // keeps its tests somewhere unusual configures a smaller maxSloc instead.
    static boolean isTestPath(String path) {
        String words = path.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").toLowerCase(Locale.ROOT);
        for (String word : words.split("[/._\\- ]")) {
            if (word.equals("test") || word.equals("tests")
                    || word.equals("spec") || word.equals("specs")) {
                return true;
            }
        }
        return false;
    }

    /// The round for `query` — or **null** when the lookup itself failed
    /// (`gh` missing, unauthenticated, offline, a bad query). Null is not
    /// "nothing matches": an empty result would read as "every PR of this
    /// category is gone", so the two must stay distinguishable.
    public @Nullable Search search(String query, int maxSloc) {
        try {
            LocalCommandRunner.LocalResult result = runner.run(command(query));
            if (result.ok()) {
                return parse(result.stdout(), maxSloc);
            }
            Logger.debug("gh PR search failed (exit {}): {}", result.exitCode(), result.stderr());
        } catch (RuntimeException e) {
            Logger.debug("gh unavailable for the PR search: {}", e.getMessage());
        }
        return null;
    }
}
