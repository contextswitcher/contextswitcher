package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-add-link~3]
// [utest->dsn~browser-url-dedupe~3]
// [utest->dsn~task-file-parsing~4]
class TaskFileAddUrlTest {

    private final TaskFileParser parser = new TaskFileParser();

    /// Field report 2026-09-07: two writers racing on the same file each
    /// appended the same PR, so the list showed every URL twice.
    @Test
    void removesDuplicateUrlsOnTheNextWrite() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/11879
                    - https://github.com/o/r/pull/11879
                    - url: https://github.com/o/r/pull/16814
                      title: Second
                    - url: https://github.com/o/r/pull/16814
                      title: Second
                claude:
                  cwd: /h
                ---
                body
                """;

        String updated = TaskFileParser.addBrowserUrl(content, "https://github.com/o/r/pull/11879");

        Task task = parser.parse("t", updated);
        assertThat(task.browser().urls()).containsExactly(
                "https://github.com/o/r/pull/11879", "https://github.com/o/r/pull/16814");
        assertThat(task.browser().entries().getLast().title()).isEqualTo("Second");
        assertThat(task.claude().cwd()).isEqualTo("/h");
    }

    /// Field report 2026-09-08: the same PR filed once with and once without
    /// a trailing slash showed twice, the slashed one as a plain link.
    @Test
    void aTrailingSlashDoesNotMakeADifferentUrl() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/16247
                    - https://github.com/o/r/pull/16247/
                ---
                """;

        String updated = TaskFileParser.addBrowserUrl(content, "https://github.com/o/r/pull/16247/");

        Task task = parser.parse("t", updated);
        assertThat(task.browser().urls()).containsExactly("https://github.com/o/r/pull/16247");
        assertThat(task.prUrls()).containsExactly("https://github.com/o/r/pull/16247");
    }

    @Test
    void aSlashedPrUrlIsStillAPrUrl() throws Exception {
        Task task = parser.parse("t", """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/16247/
                ---
                """);

        assertThat(task.prUrls()).containsExactly("https://github.com/o/r/pull/16247");
        assertThat(Task.PR_URL.matcher("https://github.com/o/r/pull/16247/").matches()).isTrue();
    }

    // [utest->dsn~gitlab-mr-state~1]
    @Test
    void aGitLabMergeRequestIsAPrUrlOnAnyHost() throws Exception {
        Task task = parser.parse("t", """
                ---
                title: "T"
                browser:
                  urls:
                    - https://gitlab.com/stp-team/systemtestportal-webapp/-/merge_requests/629
                    - https://gitlab.com/stp-team/systemtestportal-webapp/-/issues/12
                    - https://git.example.org/a/b/c/-/merge_requests/3
                ---
                """);

        assertThat(task.prUrls()).containsExactly(
                "https://gitlab.com/stp-team/systemtestportal-webapp/-/merge_requests/629",
                "https://git.example.org/a/b/c/-/merge_requests/3");
        assertThat(task.linkEntries()).extracting(Task.UrlEntry::url)
                .containsExactly("https://gitlab.com/stp-team/systemtestportal-webapp/-/issues/12");
    }

    @Test
    void duplicatesAreDroppedOnLoad() throws Exception {
        Task task = parser.parse("t", """
                ---
                title: "T"
                browser:
                  urls:
                    - https://example.org/a
                    - https://example.org/a
                ---
                """);

        assertThat(task.browser().urls()).containsExactly("https://example.org/a");
    }

    @Test
    void addsToAnExistingBrowserUrlsListInSortedPosition() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/1
                claude:
                  cwd: /h
                ---
                body
                """;

        String updated = TaskFileParser.addBrowserUrl(content, "https://example.org/x");

        Task task = parser.parse("t", updated);
        assertThat(task.browser().urls())
                .containsExactly("https://example.org/x", "https://github.com/o/r/pull/1");
        assertThat(task.claude().cwd()).isEqualTo("/h");
    }

    /// Field report 2026-09-07: a task created from a pasted brain-dump kept
    /// the text in a `title: |-` block, horizontal rule and all. The indented
    /// `---` inside the block read as the closing fence, so the `browser:`
    /// key landed in the middle of the scalar and the file stopped parsing.
    @Test
    void aHorizontalRuleInsideABlockScalarIsNotTheClosingFence() throws Exception {
        String content = """
                ---
                title: |-
                  Prepare a stable branch.

                  ---

                  Maybe an MADR?
                status: active
                ---
                body
                """;

        Task task = parser.parse("t", TaskFileParser.addBrowserUrl(content, "https://example.org/x"));

        assertThat(task.browser().urls()).containsExactly("https://example.org/x");
        assertThat(task.title()).contains("Prepare a stable branch.", "Maybe an MADR?");
        assertThat(task.status()).isEqualTo(TaskStatus.ACTIVE);
    }

    @Test
    void appendsWhenTheNewUrlSortsLast() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/JabRef/jabref/pull/16287
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.addBrowserUrl(content,
                "https://github.com/JabRef/user-documentation/pull/643"));

        assertThat(task.browser().urls()).containsExactly(
                "https://github.com/JabRef/jabref/pull/16287",
                "https://github.com/JabRef/user-documentation/pull/643");
    }

    /// The docs PR arriving first must not pin the code PR behind it.
    @Test
    void placesTheNewUrlBeforeAnEntryThatSortsAfterIt() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/JabRef/user-documentation/pull/643
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.addBrowserUrl(content,
                "https://github.com/JabRef/jabref/pull/16287"));

        assertThat(task.browser().urls()).containsExactly(
                "https://github.com/JabRef/jabref/pull/16287",
                "https://github.com/JabRef/user-documentation/pull/643");
    }

    /// A titled entry spans two lines; the new one goes before its **first**
    /// line, and comparison uses the `url:` value, not the raw `- url: …` text.
    @Test
    void sortsAgainstTheUrlOfATitledEntry() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - url: https://github.com/o/r/pull/2
                      title: Second
                ---
                """;

        String updated = TaskFileParser.addBrowserUrl(content, "https://github.com/o/r/pull/1");
        Task task = parser.parse("t", updated);

        assertThat(task.browser().urls()).containsExactly(
                "https://github.com/o/r/pull/1", "https://github.com/o/r/pull/2");
        assertThat(updated).contains("title: Second");
    }

    /// Only the new entry is placed — an out-of-order list stays as it is.
    @Test
    void doesNotReorderExistingEntries() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://zzz.example.org
                    - https://aaa.example.org
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.addBrowserUrl(content, "https://mmm.example.org"));

        assertThat(task.browser().urls()).containsExactly(
                "https://mmm.example.org", "https://zzz.example.org", "https://aaa.example.org");
    }

    @Test
    void insertsABrowserSectionWhenAbsent() throws Exception {
        String content = """
                ---
                title: "T"
                status: active
                ---
                notes
                """;

        String updated = TaskFileParser.addBrowserUrl(content, "https://example.org/x");

        Task task = parser.parse("t", updated);
        assertThat(task.browser().urls()).containsExactly("https://example.org/x");
        assertThat(updated).contains("notes");
    }

    @Test
    void ignoresACommentedBrowserSectionAndInsertsAReal() throws Exception {
        String content = """
                ---
                title: "T"
                # browser:
                #   urls:
                #     - https://github.com/owner/repo/pull/1
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.addBrowserUrl(content, "https://example.org/x"));

        assertThat(task.browser().urls()).containsExactly("https://example.org/x");
    }

    // [utest->dsn~task-create-ui~15]
    // [utest->dsn~task-url-collect~1]
    @Test
    void addsUrlsMentionedInADescription() throws Exception {
        String content = TaskFileParser.newTaskContent("T");
        String description = "Review https://github.com/JabRef/jabref/pull/16433 and"
                + " https://github.com/JabRef/jabref/pull/16433 plus"
                + " https://github.com/JabRef/jabref/pull/9, see also"
                + " https://github.com/JabRef/jabref/issues/16726.";

        Task task = parser.parse("t", TaskFileParser.addUrlsFrom(content, description));

        assertThat(task.browser().urls()).containsExactlyInAnyOrder(
                "https://github.com/JabRef/jabref/pull/16433",
                "https://github.com/JabRef/jabref/pull/9",
                "https://github.com/JabRef/jabref/issues/16726");
    }

    @Test
    void doesNotAddAUrlTheListAlreadyHas() {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/1
                    - url: https://github.com/o/r/pull/2
                      title: Second
                ---
                """;

        assertThat(TaskFileParser.addBrowserUrl(content, "https://github.com/o/r/pull/1"))
                .isEqualTo(content);
        assertThat(TaskFileParser.addBrowserUrl(content, "https://github.com/o/r/pull/2", "Other"))
                .isEqualTo(content);
    }

    /// A PR number that is a prefix of an existing one is a different PR.
    // [utest->dsn~claude-pr-refresh~5]
    @Test
    void addsTheClaudePrFirstAndOnlyOnce() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/1234
                ---
                """;

        String updated = TaskFileParser.addClaudePrUrl(content, "https://github.com/o/r/pull/123");

        assertThat(parser.parse("t", updated).browser().urls()).containsExactly(
                "https://github.com/o/r/pull/123", "https://github.com/o/r/pull/1234");
        assertThat(TaskFileParser.addClaudePrUrl(updated, "https://github.com/o/r/pull/123"))
                .isEqualTo(updated);
    }

    /// A GitLab MR published as `@cs_pr` takes the same path as a GitHub PR:
    /// first in the list, and a PR entry rather than a plain link.
    // [utest->dsn~claude-pr-refresh~5]
    // [utest->dsn~gitlab-mr-state~1]
    @Test
    void aPublishedGitLabMergeRequestBecomesThePrEntry() throws Exception {
        String mr = "https://gitlab.com/stp-team/systemtestportal-webapp/-/merge_requests/629";
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://gitlab.com/stp-team/systemtestportal-webapp
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.addClaudePrUrl(content, mr));

        assertThat(task.browser().urls()).first().isEqualTo(mr);
        assertThat(task.prUrls()).containsExactly(mr);
    }

    @Test
    void leavesContentAloneWithoutAUrl() {
        String content = TaskFileParser.newTaskContent("T");

        assertThat(TaskFileParser.addUrlsFrom(content, "just a title")).isEqualTo(content);
    }
}
