package com.contextswitcher.switching;

import java.util.List;
import java.util.Map;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.switching.QodoReviewLookup.QodoComment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// [utest->dsn~qodo-agent-prompt-queue~11]
class QodoReviewLookupTest {

    /// A qodo review-comment body carrying one Agent Prompt, in qodo's real
    /// `<details>` + fenced-block shape.
    private static String bodyWithPrompt(String prompt) {
        return """
                <img src="https://img.shields.io/badge/Action_required" alt="Action required">

                1\\. Some finding <code>🐞 Bug</code>

                <pre>
                A human-readable description of the finding.
                </pre>

                <details>
                <summary><strong>Agent Prompt</strong></summary>

                ```
                %s
                ```

                <code>ⓘ Copy this prompt and use it to remediate the issue with your preferred AI generation tools</code>
                </details>""".formatted(prompt);
    }

    /// The same prompt as qodo repeats it in its "Code Review by Qodo" summary
    /// comment: every line of the finding blockquoted, the `<summary>` without
    /// `<strong>` and with a lowercase "prompt", and a preamble sentence above
    /// the prompt the inline suggestion does not carry.
    private static String summaryBodyWithPrompt(String prompt) {
        String quoted = ("""
                <details>
                <summary>Agent prompt</summary>
                <br/>

                ```
                The issue below was found during a code review. Follow the provided context and guidance below and implement a solution

                %s
                ```
                </details>""".formatted(prompt)).lines()
                .map(line -> line.isEmpty() ? ">" : "> " + line)
                .collect(java.util.stream.Collectors.joining("\n"));
        return "<h3>Code Review by Qodo</h3>\n\n" + quoted;
    }

    private static Map<String, Object> comment(String login, String url, String body) {
        return Map.of("url", url, "body", body,
                "author", Map.of("login", login, "__typename", "User"));
    }

    /// One response page with both halves of the query: the review threads and
    /// the PR conversation.
    private static String pageWith(List<Object> threads, List<Object> conversation)
            throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("data", Map.of(
                "viewer", Map.of("login", "pr-author"),
                "repository", Map.of("pullRequest", Map.of(
                        "author", Map.of("login", "pr-author"),
                        "comments", Map.of("nodes", conversation),
                        "reviewThreads", Map.of("nodes", threads))))));
    }

    /// One `gh api graphql` response page carrying the given review threads,
    /// on a PR opened by `prAuthor` (whose own comments are skipped).
    private static String pageBy(String viewer, Object... threads) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("data", Map.of(
                "viewer", Map.of("login", viewer),
                "repository", Map.of("pullRequest", Map.of(
                        "author", Map.of("login", viewer),
                        "reviewThreads", Map.of("nodes", List.of(threads)))))));
    }

    private static String page(Object... threads) throws Exception {
        return pageBy("pr-author", threads);
    }

    /// The same page, on a PR in the given `state` (`OPEN`, `CLOSED`, `MERGED`).
    private static String pageInState(String state, Object... threads) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("data", Map.of(
                "viewer", Map.of("login", "pr-author"),
                "repository", Map.of("pullRequest", Map.of(
                        "state", state,
                        "author", Map.of("login", "pr-author"),
                        "reviewThreads", Map.of("nodes", List.of(threads)))))));
    }

    @Test
    void closedPrContributesNoComments() throws Exception {
        Map<String, Object> open = thread(false, false, QodoReviewLookup.BOT,
                "https://github.com/o/r/pull/1#discussion_r1", bodyWithPrompt("Fix it"));
        QodoReviewLookup lookup = new QodoReviewLookup(new LocalCommandRunner());
        assertThat(lookup.parse(pageInState("OPEN", open))).hasSize(1);
        assertThat(lookup.parse(pageInState("CLOSED", open))).isEmpty();
        assertThat(lookup.parse(pageInState("MERGED", open))).isEmpty();
    }

    /// One review thread with a single comment by `login`.
    private static Map<String, Object> thread(boolean resolved, boolean outdated,
            String login, String url, String body) {
        return thread(resolved, outdated, login, url, body, "", 0, "User");
    }

    private static Map<String, Object> thread(boolean resolved, boolean outdated,
            String login, String url, String body, String path, int line, String typename) {
        return threadOf(resolved, outdated, path, line,
                comment(login, url, body, typename));
    }

    private static Map<String, Object> comment(String login, String url, String body,
            String typename) {
        return Map.of("url", url, "body", body,
                "author", Map.of("login", login, "__typename", typename));
    }

    private static Map<String, Object> threadOf(boolean resolved, boolean outdated,
            String path, int line, Object... comments) {
        return Map.of("isResolved", resolved, "isOutdated", outdated,
                "path", path, "line", line,
                "comments", Map.of("nodes", List.of(comments)));
    }

    @Test
    void commandAsksGraphqlForTheReviewThreads() {
        assertThat(QodoReviewLookup.command("JabRef", "jabref", "16273"))
                .startsWith("gh", "api", "graphql", "--paginate")
                .contains("owner=JabRef", "repo=jabref", "number=16273")
                // The query must reach the thread-level resolution flags, and
                // carry no double quote (the Windows argv trap).
                .anySatisfy(part -> assertThat(part)
                        .contains("isResolved", "isOutdated", "reviewThreads"))
                .noneMatch(part -> part.contains("\""));
    }

    @Test
    void isQodoAcceptsBothLoginSpellings() {
        assertThat(QodoReviewLookup.isQodo(QodoReviewLookup.BOT)).isTrue();
        assertThat(QodoReviewLookup.isQodo(QodoReviewLookup.BOT + "[bot]")).isTrue();
        assertThat(QodoReviewLookup.isQodo("some-human")).isFalse();
    }

    @Test
    void activityCommandAsksOnlyForUpdatedAt() {
        // updatedAt, not headRefOid: a review comment on a branch nobody
        // pushes to leaves the head where it is, and the gate would never open.
        assertThat(QodoReviewLookup.activityCommand("https://github.com/JabRef/jabref/pull/16273"))
                .containsExactly("gh", "pr", "view",
                        "https://github.com/JabRef/jabref/pull/16273", "--json", "updatedAt")
                .noneMatch(part -> part.contains("\""));
    }

    @Test
    void parsesUpdatedAtAndNullWhenAbsent() {
        assertThat(QodoReviewLookup.parseUpdatedAt("{\"updatedAt\":\"2026-09-09T23:18:52Z\"}"))
                .isEqualTo("2026-09-09T23:18:52Z");
        assertThat(QodoReviewLookup.parseUpdatedAt("{\"other\":\"x\"}")).isNull();
    }

    @Test
    void extractsTheFencedPromptFromAnAgentPromptDetails() {
        List<String> prompts = QodoReviewLookup.extractPrompts(
                bodyWithPrompt("## Issue description\nFix the thing.\n\n## Fix Focus Areas\n- File.java[1-2]"));
        assertThat(prompts).containsExactly(
                "## Issue description\nFix the thing.\n\n## Fix Focus Areas\n- File.java[1-2]");
    }

    @Test
    void extractsEveryPromptWhenACommentBundlesSeveral() {
        String body = bodyWithPrompt("First prompt.") + "\n" + bodyWithPrompt("Second prompt.");
        assertThat(QodoReviewLookup.extractPrompts(body))
                .containsExactly("First prompt.", "Second prompt.");
    }

    @Test
    void extractsNothingFromACommentWithoutAnAgentPrompt() {
        assertThat(QodoReviewLookup.extractPrompts("<pre>Just a plain note, no prompt.</pre>"))
                .isEmpty();
    }

    @Test
    void parseReadsUrlActiveAndPromptsOfABotComment() throws Exception {
        String json = page(thread(false, false, QodoReviewLookup.BOT,
                "https://github.com/o/r/pull/7#discussion_r100",
                bodyWithPrompt("Bot prompt.")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .singleElement().satisfies(comment -> {
                    assertThat(comment.url())
                            .isEqualTo("https://github.com/o/r/pull/7#discussion_r100");
                    assertThat(comment.active()).isTrue();
                    assertThat(comment.prompts()).containsExactly("Bot prompt.");
                });
    }

    @Test
    void parseTurnsAHumanReviewersCommentIntoAnAnchoredMessage() throws Exception {
        String json = page(thread(false, false, "some-human",
                "https://github.com/o/r/pull/7#discussion_r200",
                "Use a switch here.", "app/src/Foo.java", 42, "User"));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .singleElement().satisfies(comment -> {
                    assertThat(comment.active()).isTrue();
                    assertThat(comment.prompts()).containsExactly(
                            "Review comment by @some-human on app/src/Foo.java:42"
                                    + " (https://github.com/o/r/pull/7#discussion_r200):\n\n"
                                    + "Use a switch here.");
                });
    }

    @Test
    void parseSkipsAHumanThreadTheUserAlreadyRepliedIn() throws Exception {
        // Seen and answered — re-queuing it would ask for the same work twice.
        String json = pageBy("me",
                threadOf(false, false, "Foo.java", 3,
                        comment("reviewer", "url-answered", "Add a timestamp.", "User"),
                        comment("me", "url-my-reply", "Done in the next push.", "User")),
                threadOf(false, false, "Foo.java", 9,
                        comment("reviewer", "url-open", "Rename this.", "User")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url).containsExactly("url-open");
    }

    @Test
    void parseKeepsAReviewersFollowUpAfterTheUsersReply() throws Exception {
        // The user answered, the reviewer came back — that last comment is new
        // work and must reach the queue even though the thread was replied in.
        String json = pageBy("me",
                threadOf(false, false, "Foo.java", 3,
                        comment("reviewer", "url-first", "Add a timestamp.", "User"),
                        comment("me", "url-my-reply", "Done in the next push.", "User"),
                        comment("reviewer", "url-follow-up", "Still missing on line 9.", "User")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url).containsExactly("url-follow-up");
    }

    @Test
    void parseSkipsAQodoThreadTheUserAnsweredLast() throws Exception {
        // Qodo never resolves its own thread, and a suggestion addressed by
        // *adding* code does not shift the flagged line either — so without the
        // reply rule the prompt stayed active and came back on every sync.
        String json = pageBy("me",
                threadOf(false, false, "Foo.java", 3,
                        comment(QodoReviewLookup.BOT, "url-qodo",
                                bodyWithPrompt("Bot prompt."), "Bot"),
                        comment("me", "url-my-reply", "Fixed in the next push.", "User")),
                threadOf(false, false, "Foo.java", 9,
                        comment(QodoReviewLookup.BOT, "url-open",
                                bodyWithPrompt("Unanswered prompt."), "Bot")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url).containsExactly("url-open");
    }

    @Test
    void parseKeepsAQodoFollowUpAfterTheUsersReply() throws Exception {
        // Qodo re-reviewed after the push and flagged something else: that last
        // comment is new work, exactly like a human reviewer's follow-up.
        String json = pageBy("me",
                threadOf(false, false, "Foo.java", 3,
                        comment(QodoReviewLookup.BOT, "url-qodo",
                                bodyWithPrompt("First prompt."), "Bot"),
                        comment("me", "url-my-reply", "Fixed.", "User"),
                        comment(QodoReviewLookup.BOT, "url-qodo-again",
                                bodyWithPrompt("Second prompt."), "Bot")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url).containsExactly("url-qodo-again");
    }

    @Test
    void parseSkipsTheUsersOwnRepliesAndOtherBots() throws Exception {
        String json = pageBy("me",
                thread(false, false, "me", "url-mine", "My own reply."),
                thread(false, false, "coverage-bot", "url-bot", "Coverage dropped.", "", 0, "Bot"),
                thread(false, false, "reviewer", "url-review", "Please rename this."));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url).containsExactly("url-review");
    }

    @Test
    void reviewMessageOmitsTheAnchorWhenTheThreadHasNoFile() {
        assertThat(QodoReviewLookup.reviewMessage("dev", "", 0, "", "  Looks good.  "))
                .isEqualTo("Review comment by @dev:\n\nLooks good.");
    }

    @Test
    void reviewMessageCarriesTheCommentLink() {
        assertThat(QodoReviewLookup.reviewMessage("dev", "Foo.java", 12, "https://gh/pr/1#discussion_r2", "Rename this."))
                .isEqualTo("Review comment by @dev on Foo.java:12 (https://gh/pr/1#discussion_r2):\n\nRename this.");
    }

    @Test
    void parseMarksResolvedAndOutdatedThreadsInactive() throws Exception {
        // Resolved by hand while the code never changed — the case REST's
        // `position` cannot see, so the prompt kept coming back on every sync.
        String json = page(
                thread(true, false, QodoReviewLookup.BOT, "url-resolved",
                        bodyWithPrompt("Resolved prompt.")),
                thread(false, true, QodoReviewLookup.BOT, "url-outdated",
                        bodyWithPrompt("Outdated prompt.")),
                thread(false, false, QodoReviewLookup.BOT, "url-open",
                        bodyWithPrompt("Open prompt.")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url, QodoComment::active)
                .containsExactly(tuple("url-resolved", false),
                        tuple("url-outdated", false),
                        tuple("url-open", true));
    }

    @Test
    void parseReadsEveryPageOfAPaginatedResponse() throws Exception {
        String json = page(thread(false, false, QodoReviewLookup.BOT, "url-1",
                bodyWithPrompt("First page.")))
                + page(thread(false, false, QodoReviewLookup.BOT + "[bot]", "url-2",
                bodyWithPrompt("Second page.")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url).containsExactly("url-1", "url-2");
    }

    @Test
    void activePromptsFlattensOnlyActiveCommentsWithTheirUrls() {
        List<QodoComment> comments = List.of(
                new QodoComment("url-1", true, List.of("Active A")),
                new QodoComment("url-2", false, List.of("Outdated B")),
                new QodoComment("url-3", true, List.of("Active C")));
        assertThat(QodoReviewLookup.activePrompts(comments))
                .containsExactly(Map.entry("Active A", "url-1"), Map.entry("Active C", "url-3"));
    }

    @Test
    void commentsIsNullForANonPrUrl() {
        // No PR match → returns null (fetch never runs), never an empty list.
        assertThat(new QodoReviewLookup(new LocalCommandRunner()).comments("https://example.com/x"))
                .isNull();
    }

    @Test
    void parseSurvivesEmptyOrUnexpectedOutput() {
        QodoReviewLookup lookup = new QodoReviewLookup(new LocalCommandRunner());
        assertThat(lookup.parse("")).isEmpty();
        assertThat(lookup.parse("[]")).isEmpty();
    }

    @Test
    void parseIsNullWhenTheResponseCannotBeRead() {
        // Not an empty list: "unreadable" must not reach the sync as "the PR
        // carries nothing", which would report +0 added and look like success.
        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse("not json at all")).isNull();
    }

    @Test
    void parseHarvestsQodosSummaryCommentFromThePrConversation() throws Exception {
        String json = pageWith(List.of(),
                List.of(comment(QodoReviewLookup.BOT, "url-summary", summaryBodyWithPrompt(
                        "## Issue description\nThe finding.\n## Recommended Fix\nFix it."))));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .singleElement()
                .extracting(QodoComment::url, QodoComment::active, QodoComment::prompts)
                .containsExactly("url-summary", true,
                        List.of("## Issue description\nThe finding.\n## Recommended Fix\nFix it."));
    }

    @Test
    void theSummaryCopyOfAnAnsweredInlineFindingStaysOut() throws Exception {
        // The reply rule silences the inline suggestion; without this the
        // summary's copy would put it straight back into the queue.
        String prompt = "## Issue description\nThe finding.";
        String json = pageWith(
                List.of(threadOf(false, false, "Some.java", 7,
                        comment(QodoReviewLookup.BOT, "url-inline", bodyWithPrompt(prompt)),
                        comment("pr-author", "url-reply", "Not doing that, because …"))),
                List.of(comment(QodoReviewLookup.BOT, "url-summary",
                        summaryBodyWithPrompt(prompt))));

        assertThat(QodoReviewLookup.activePrompts(
                new QodoReviewLookup(new LocalCommandRunner()).parse(json))).isEmpty();
    }

    @Test
    void parseHarvestsAHumansTopLevelComment() throws Exception {
        String json = pageWith(List.of(),
                List.of(comment("reviewer", "url-talk", "Please document the submodule checkout.")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .singleElement().extracting(QodoComment::prompts)
                .isEqualTo(List.of("Review comment by @reviewer (url-talk):\n\n"
                        + "Please document the submodule checkout."));
    }

    @Test
    void parseSkipsAConversationTheUserCommentedInLast() throws Exception {
        String json = pageWith(List.of(),
                List.of(comment("reviewer", "url-talk", "Please document it."),
                        comment("pr-author", "url-answer", "Done.")));

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json)).isEmpty();
    }

    @Test
    void parseReadsTheConversationOnceAcrossPages() throws Exception {
        // The conversation is not the paged connection, so `gh --paginate`
        // repeats it verbatim on every page.
        List<Object> talk = List.of(comment("reviewer", "url-talk", "Please document it."));
        String json = pageWith(List.of(), talk) + pageWith(List.of(), talk);

        assertThat(new QodoReviewLookup(new LocalCommandRunner()).parse(json))
                .extracting(QodoComment::url).containsExactly("url-talk");
    }

    @Test
    void aFindingQodoPostsInlineAndInItsSummaryIsQueuedOnce() throws Exception {
        String prompt = "## Issue description\nThe finding.\n\n## Recommended Fix\nFix it.";
        String json = pageWith(
                List.of(thread(false, false, QodoReviewLookup.BOT, "url-inline",
                        bodyWithPrompt(prompt))),
                List.of(comment(QodoReviewLookup.BOT, "url-summary",
                        summaryBodyWithPrompt(prompt))));

        List<QodoComment> comments = new QodoReviewLookup(new LocalCommandRunner()).parse(json);
        // The summary comment is still harvested, but contributes nothing: the
        // queue gets the inline copy and its anchored link.
        assertThat(comments).extracting(QodoComment::url, QodoComment::prompts)
                .containsExactly(tuple("url-inline", List.of(prompt)),
                        tuple("url-summary", List.of()));
        assertThat(QodoReviewLookup.activePrompts(comments))
                .containsExactly(Map.entry(prompt, "url-inline"));
    }
}
