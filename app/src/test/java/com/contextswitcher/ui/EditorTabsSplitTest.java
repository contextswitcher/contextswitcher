package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The editor lane's two tabs are one file: the configuration tab holds the
/// YAML between the fences, the notes tab the body, and joining them gives
/// the file back.
// [utest->dsn~richtext-markdown-editor~9]
class EditorTabsSplitTest {

    private static final String FILE = """
            ---
            title: Two tabs
            status: active
            ---

            # Notes

            body
            """;

    @Test
    void splitsAtTheFencesAndJoinsBack() {
        String[] parts = MainWindow.splitFrontmatter(FILE);
        assertThat(parts[0]).isEqualTo("title: Two tabs\nstatus: active");
        assertThat(parts[1])
                .as("the blank line after the fence is structure, not the first line of the notes")
                .isEqualTo("# Notes\n\nbody\n");
        assertThat(MainWindow.joinFrontmatter(parts[0], parts[1])).isEqualTo(FILE);
    }

    /// Field report 2026-09-13: "the empty line on top of the notes is
    /// strange". Every task file carries a blank line between the closing
    /// fence and `# Notes`, so the editor opened on an empty first line. It is
    /// hidden on the way in and written back on the way out — a load and a save
    /// must leave the file byte-identical.
    @Test
    void theStructuralBlankLineIsHiddenAndRestored() {
        String file = "---\ntitle: T\n---\n\n# Notes\n\nbody\n";

        String[] parts = MainWindow.splitFrontmatter(file);

        assertThat(parts[1]).startsWith("# Notes");
        assertThat(MainWindow.joinFrontmatter(parts[0], parts[1])).isEqualTo(file);
    }

    /// A file written without that blank line normalises to the template's
    /// shape on the first save — deliberate, and the only shape the app writes.
    @Test
    void aFileWithoutTheBlankLineGainsIt() {
        String[] parts = MainWindow.splitFrontmatter("---\ntitle: T\n---\n# Notes\n");

        assertThat(parts[1]).isEqualTo("# Notes\n");
        assertThat(MainWindow.joinFrontmatter(parts[0], parts[1]))
                .isEqualTo("---\ntitle: T\n---\n\n# Notes\n");
    }

    @Test
    void contentWithoutFrontmatterIsAllNotes() {
        String[] parts = MainWindow.splitFrontmatter("Cannot read x.md: gone");
        assertThat(parts[0]).isEmpty();
        assertThat(parts[1]).isEqualTo("Cannot read x.md: gone");
        assertThat(MainWindow.joinFrontmatter("", "notes")).isEqualTo("notes");
    }

    @Test
    void crlfAndBomAreNormalizedLikeTheParser() {
        String[] parts = MainWindow.splitFrontmatter("\uFEFF---\r\ntitle: T\r\n---\r\nn");
        assertThat(parts[0]).isEqualTo("title: T");
        assertThat(parts[1]).isEqualTo("n");
    }

    @Test
    void emptyFrontmatterAndMissingBodyDoNotThrow() {
        assertThat(MainWindow.splitFrontmatter("---\n---")).containsExactly("", "");
        assertThat(MainWindow.splitFrontmatter("---\ntitle: T\n---")).containsExactly("title: T", "");
    }
}
