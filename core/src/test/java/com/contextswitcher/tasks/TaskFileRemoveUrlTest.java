package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-remove-link~2]
class TaskFileRemoveUrlTest {

    private final TaskFileParser parser = new TaskFileParser();

    @Test
    void removesTheEntryAndItsTitleLineOnly() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/11879
                    - url: https://github.com/o/r/pull/16814
                      title: Second
                    - https://example.org/issue
                claude:
                  cwd: /h
                ---
                body
                """;

        String updated = TaskFileParser.removeBrowserUrl(
                content, "https://github.com/o/r/pull/16814");

        Task task = parser.parse("t", updated);
        assertThat(task.browser().urls()).containsExactly(
                "https://github.com/o/r/pull/11879", "https://example.org/issue");
        assertThat(updated).doesNotContain("Second");
        assertThat(task.claude().cwd()).isEqualTo("/h");
        assertThat(updated).contains("body");
    }

    /// A trailing slash is the same page (`Task.normalizeUrl`), so the trash
    /// icon works on an entry filed in either spelling.
    @Test
    void aTrailingSlashStillMatches() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://github.com/o/r/pull/16247/
                    - https://example.org/issue
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.removeBrowserUrl(
                content, "https://github.com/o/r/pull/16247"));

        assertThat(task.browser().urls()).containsExactly("https://example.org/issue");
    }

    @Test
    void aUrlTheListDoesNotHaveIsANoOp() {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://example.org/issue
                ---
                """;

        assertThat(TaskFileParser.removeBrowserUrl(content, "https://example.org/other"))
                .isEqualTo(content);
    }

    /// Removing the last entry takes the whole block: a bare `urls:` would
    /// make the next `addBrowserUrl` write a second `urls:` key.
    @Test
    void removingTheLastEntryDropsTheBrowserBlock() throws Exception {
        String content = """
                ---
                title: "T"
                browser:
                  urls:
                    - https://example.org/issue
                claude:
                  cwd: /h
                ---
                """;

        String empty = TaskFileParser.removeBrowserUrl(content, "https://example.org/issue");
        assertThat(empty).doesNotContain("browser:").doesNotContain("urls:");

        Task task = parser.parse("t", TaskFileParser.addBrowserUrl(empty, "https://example.org/new"));
        assertThat(task.browser().urls()).containsExactly("https://example.org/new");
        assertThat(task.claude().cwd()).isEqualTo("/h");
    }
}
