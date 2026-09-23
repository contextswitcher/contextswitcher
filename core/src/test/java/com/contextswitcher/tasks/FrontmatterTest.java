package com.contextswitcher.tasks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The frontmatter key patching behind the configuration form: values are read
/// by key path, writes touch only the edited key's block, and the Markdown body
/// is never involved.
// [utest->dsn~task-field-form~4]
class FrontmatterTest {

    private static final String TASK = """
            ---
            title: Field form task
            status: active
            remote: devbox
            # keep me
            folders-windows: [C:\\git\\p]
            claude:
              cwd: /home/user/p
              workspace: /home/user/p/sub
            ---

            # Notes

            Body text.
            """;

    @Test
    void readsTopLevelAndNestedKeys() {
        Map<String, Object> data = Frontmatter.parse(TASK);

        assertThat(Frontmatter.get(data, "remote")).isEqualTo("devbox");
        assertThat(Frontmatter.get(data, "claude.workspace")).isEqualTo("/home/user/p/sub");
        assertThat(Frontmatter.get(data, "claude.sessionId")).isNull();
        // A scalar parent has no children — no ClassCastException, just unset.
        assertThat(Frontmatter.get(data, "remote.window")).isNull();
    }

    @Test
    void contentWithoutFrontmatterIsLeftAlone() {
        assertThat(Frontmatter.block("# Notes\n")).isNull();
        assertThat(Frontmatter.parse("# Notes\n")).isEmpty();
        assertThat(Frontmatter.set("# Notes\n", "remote", "devbox")).isEqualTo("# Notes\n");
    }

    @Test
    void unparseableFrontmatterReadsAsEmptyRatherThanFailing() {
        assertThat(Frontmatter.parse("---\ntitle: [unclosed\n---\n")).isEmpty();
    }

    @Test
    void setReplacesOneKeyAndKeepsCommentsAndTheBody() {
        String updated = Frontmatter.set(TASK, "remote", "devbox");

        assertThat(updated)
                .contains("remote: devbox")
                .contains("# keep me")
                .contains("title: Field form task")
                .contains("# Notes\n\nBody text.\n");
        assertThat(Frontmatter.parse(updated).get("remote")).isEqualTo("devbox");
    }

    @Test
    void setAppendsAnAbsentKeyIntoTheFrontmatterNotTheBody() {
        String updated = Frontmatter.set(TASK, "chat", "https://matrix.to/#/!r:matrix.org");

        assertThat(Frontmatter.parse(updated).get("chat"))
                .isEqualTo("https://matrix.to/#/!r:matrix.org");
        assertThat(updated).contains("# Notes\n\nBody text.\n");
    }

    @Test
    void aNullValueRemovesTheKey() {
        String updated = Frontmatter.set(TASK, "remote", null);

        assertThat(Frontmatter.parse(updated)).doesNotContainKey("remote");
        assertThat(updated).contains("title: Field form task").contains("# keep me");
    }

    @Test
    void aNestedSectionIsWrittenAsAWholeMap() {
        Map<String, Object> claude = new LinkedHashMap<>();
        claude.put("cwd", "/home/user/p");
        claude.put("sessionId", "abc123");
        String updated = Frontmatter.set(TASK, "claude", claude);

        assertThat(Frontmatter.get(Frontmatter.parse(updated), "claude.sessionId"))
                .isEqualTo("abc123");
        // The keys around the rewritten section are untouched.
        assertThat(updated).contains("remote: devbox").contains("# keep me");
    }

    /// A machine-suffixed key (`folders-windows`) is a key of its own — a patch
    /// of the key above it must not swallow and delete it.
    @Test
    void aMachineSuffixedKeyIsNotSwallowedByTheBlockAboveIt() {
        String updated = Frontmatter.set(TASK, "folders", List.of("/data/p"));

        assertThat(Frontmatter.parse(updated))
                .containsKey("folders-windows")
                .containsEntry("folders", List.of("/data/p"));
        assertThat(Frontmatter.set(updated, "folders", null))
                .contains("folders-windows: [C:\\git\\p]");
    }

    @Test
    void withBlockKeepsTheBodyAfterTheClosingFence() {
        String updated = Frontmatter.withBlock(TASK, "title: Renamed\n");

        assertThat(updated).isEqualTo("---\ntitle: Renamed\n---\n\n# Notes\n\nBody text.\n");
    }

    /// Windows line endings are normalized the way the parser cuts the file, so
    /// a file written by another editor is patched at the right fence.
    @Test
    void crlfContentIsPatchedAtTheSameFences() {
        String updated = Frontmatter.set("---\r\ntitle: T\r\n---\r\n\r\n# Notes\r\n",
                "remote", "devbox");

        assertThat(Frontmatter.parse(updated).get("remote")).isEqualTo("devbox");
        assertThat(updated).doesNotContain("\r");
    }
}
