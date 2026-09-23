package com.contextswitcher.tasks;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~frontmatter-multiline-scalar~3]
class TaskFileMultilineTitleTest {

    /// A plain scalar spanning lines is valid YAML (continuation indented,
    /// folded with spaces) — the shape Oliver's hand-edited task file had.
    private static final String CONTENT = """
            ---
            title: File drag'n'drop for queued messages
              can we also do that for "Queue a message"
            status: active
            ---
            body
            """;

    private final TaskFileParser parser = new TaskFileParser();

    @Test
    void multilineTitleParsesFolded() throws Exception {
        assertThat(parser.parse("t", CONTENT).title())
                .isEqualTo("File drag'n'drop for queued messages can we also do that for \"Queue a message\"");
    }

    @Test
    void withTagsInsertsAfterTheWholeTitle() throws Exception {
        Task task = parser.parse("t", TaskFileParser.withTags(CONTENT, List.of("needs-test")));

        assertThat(task.tags()).containsExactly("needs-test");
        assertThat(task.title()).contains("Queue a message");
    }

    @Test
    void withStatusInsertsAfterTheWholeTitle() throws Exception {
        String withoutStatus = CONTENT.replace("status: active\n", "");

        Task task = parser.parse("t", TaskFileParser.withStatus(withoutStatus, TaskStatus.SUSPENDED));

        assertThat(task.status()).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(task.title()).contains("Queue a message");
    }

    /// A task description pasted from a chat has `---` separators of its own,
    /// indented inside the block-scalar title. Field report 2026-09-12:
    /// "Duplicate frontmatter keys in …: status (line 28)" — the status scan
    /// mistook the first of those for the closing fence, never reached the real
    /// `status:` below it and inserted a second one. YAML keeps the last of
    /// duplicate keys, so the suspend the user asked for silently lost.
    @Test
    void anIndentedFenceInsideTheTitleIsNotTheClosingFence() throws Exception {
        String content = """
                ---
                title: |-
                  Can the welcome tab be restored?

                  ---

                  A created PR goes to the milestone.
                status: active
                remote: host
                ---
                body
                """;

        String suspended = TaskFileParser.withStatus(content, TaskStatus.SUSPENDED);

        assertThat(suspended.lines().filter(line -> line.startsWith("status:")))
                .as("exactly one status key")
                .hasSize(1);
        assertThat(parser.parse("t", suspended).status()).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(parser.parse("t", suspended).title()).contains("milestone");
    }

    @Test
    void withNoteInsertsAfterTheWholeTitle() throws Exception {
        Task task = parser.parse("t", TaskFileParser.withNote(CONTENT, "https://example.org"));

        assertThat(task.note()).isEqualTo("https://example.org");
        assertThat(task.title()).contains("Queue a message");
    }

    @Test
    void withTitleReplacesTheWholeTitle() throws Exception {
        Task task = parser.parse("t", TaskFileParser.withTitle(CONTENT, "Short"));

        assertThat(task.title()).isEqualTo("Short");
        assertThat(task.status()).isEqualTo(TaskStatus.ACTIVE);
    }

    /// The other shape, and the one the app itself writes: a `|-` block scalar
    /// whose value contains a **blank line** — an add-task description with an
    /// `[image: …]` marker below its text. A blank line ends a plain scalar but
    /// is content here, so a mutator that stops at it inserts the new key into
    /// the middle of the title (Oliver, 2026-07-20: adding a tag orphaned the
    /// image marker below the `tags:` line).
    private static final String BLOCK_CONTENT = """
            ---
            title: |-
              I think, /model and /task do not work properly... Timeout?

              [image: C:\\Users\\olive\\.contextswitcher\\attachments\\img.png]
            status: active
            ---
            body
            """;

    @Test
    void blockScalarTitleParsesWithItsBlankLine() throws Exception {
        assertThat(parser.parse("t", BLOCK_CONTENT).title())
                .isEqualTo("""
                        I think, /model and /task do not work properly... Timeout?

                        [image: C:\\Users\\olive\\.contextswitcher\\attachments\\img.png]""");
    }

    @Test
    void withTagsInsertsAfterAWholeBlockScalarTitle() throws Exception {
        Task task = parser.parse("t", TaskFileParser.withTags(BLOCK_CONTENT, List.of("needs-test")));

        assertThat(task.tags()).containsExactly("needs-test");
        assertThat(task.title()).contains("[image:");
    }

    @Test
    void withStatusInsertsAfterAWholeBlockScalarTitle() throws Exception {
        String withoutStatus = BLOCK_CONTENT.replace("status: active\n", "");

        Task task = parser.parse("t", TaskFileParser.withStatus(withoutStatus, TaskStatus.SUSPENDED));

        assertThat(task.status()).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(task.title()).contains("[image:");
    }

    @Test
    void withNoteInsertsAfterAWholeBlockScalarTitle() throws Exception {
        Task task = parser.parse("t", TaskFileParser.withNote(BLOCK_CONTENT, "https://example.org"));

        assertThat(task.note()).isEqualTo("https://example.org");
        assertThat(task.title()).contains("[image:");
    }

    @Test
    void withTitleReplacesAWholeBlockScalarTitle() throws Exception {
        Task task = parser.parse("t", TaskFileParser.withTitle(BLOCK_CONTENT, "Short"));

        assertThat(task.title()).isEqualTo("Short");
        assertThat(task.status()).isEqualTo(TaskStatus.ACTIVE);
    }

    /// The plain-scalar rule must survive the block-scalar one: here the blank
    /// line ends the title, and the key below it is not swallowed.
    @Test
    void aBlankLineStillEndsAPlainScalar() throws Exception {
        String withBlank = """
                ---
                title: File drag'n'drop
                  can we also do that

                status: active
                ---
                """;

        Task task = parser.parse("t", TaskFileParser.withTags(withBlank, List.of("needs-test")));

        assertThat(task.tags()).containsExactly("needs-test");
        assertThat(task.status()).isEqualTo(TaskStatus.ACTIVE);
    }
}
