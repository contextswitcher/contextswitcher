package com.contextswitcher.tasks;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-merge-resolution~2]
class TaskMergeTest {

    private static final String BASE = """
            ---
            title: Fix new category not shown in task list
            status: active
            remote: koppor@devbox
            ---
            Notes.
            """;

    @Test
    void keepsBothSidesWhenDifferentKeysChanged() {
        // The screenshot case: one machine added tags, the other suspended the
        // task — adjacent lines, so git conflicts, but different keys.
        String ours = BASE.replace("status: active", "status: suspended");
        String theirs = BASE.replace("status: active\n", "status: active\ntags: [needs-test]\n");

        Optional<String> merged = TaskMerge.merge(BASE, ours, theirs, false);

        // A key only the other side has is appended after the keys this side
        // knows — the value is merged, the position is not preserved.
        assertThat(merged).hasValueSatisfying(text -> assertThat(text).isEqualTo("""
                ---
                title: Fix new category not shown in task list
                status: suspended
                remote: koppor@devbox
                tags: [needs-test]
                ---
                Notes.
                """));
    }

    @Test
    void lastWriterWinsWhenTheSameKeyChangedOnBothSides() {
        String ours = BASE.replace("status: active", "status: suspended");
        String theirs = BASE.replace("status: active", "status: done");

        assertThat(TaskMerge.merge(BASE, ours, theirs, true).orElseThrow()).contains("status: done");
        assertThat(TaskMerge.merge(BASE, ours, theirs, false).orElseThrow()).contains("status: suspended");
    }

    @Test
    void keepsAKeyOnlyOneSideAddedAndOneOnlyOneSideRemoved() {
        String ours = BASE.replace("remote: koppor@devbox\n", "");
        String theirs = BASE.replace("status: active\n", "status: active\ntags: [needs-test]\n");

        String merged = TaskMerge.merge(BASE, ours, theirs, false).orElseThrow();

        assertThat(merged).doesNotContain("remote:").contains("tags: [needs-test]");
    }

    @Test
    void carriesNestedBlocksAndCommentsVerbatim() {
        String base = """
                ---
                title: T
                # the ssh alias, not the host name
                remote: devbox
                tmux:
                  session: '0'
                  window: 3
                ---
                """;
        String ours = base.replace("window: 3", "window: 5");
        String theirs = base.replace("title: T", "title: T2");

        assertThat(TaskMerge.merge(base, ours, theirs, false).orElseThrow()).isEqualTo("""
                ---
                title: T2
                # the ssh alias, not the host name
                remote: devbox
                tmux:
                  session: '0'
                  window: 5
                ---
                """);
    }

    @Test
    void notesChangedOnOneSideWin() {
        String ours = BASE.replace("Notes.", "Notes, extended.");

        assertThat(TaskMerge.merge(BASE, ours, BASE, false).orElseThrow()).contains("Notes, extended.");
    }

    @Test
    void givesUpWhenBothSidesRewroteTheNotes() {
        assertThat(TaskMerge.merge(BASE, BASE.replace("Notes.", "Mine."), BASE.replace("Notes.", "Theirs."), false))
                .isEmpty();
    }

    @Test
    void givesUpOnAFileWithoutFrontmatter() {
        assertThat(TaskMerge.merge("", "plain text\n", "other text\n", false)).isEmpty();
    }

    @Test
    void mergesTwoFilesCreatedIndependentlyUnderTheSameName() {
        String ours = """
                ---
                title: Mine
                status: active
                ---
                """;
        String theirs = """
                ---
                title: Mine
                tags: [x]
                ---
                """;

        assertThat(TaskMerge.merge("", ours, theirs, false).orElseThrow()).isEqualTo("""
                ---
                title: Mine
                status: active
                tags: [x]
                ---
                """);
    }
}
