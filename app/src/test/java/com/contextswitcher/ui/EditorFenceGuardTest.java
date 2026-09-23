package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The editor auto-save fence guard ([MainWindow#droppedFrontmatterFence]):
/// it must catch exactly the case where a valid task file (opening `---`
/// fence) would be written back without that fence — the corruption that
/// turns a task into an unparseable "must start with a '---' frontmatter
/// fence" row — while leaving every legitimate edit alone.
// [utest->dsn~richtext-markdown-editor~9]
class EditorFenceGuardTest {

    private static final String VALID = """
            ---
            title: F1 help for task fields
            status: active
            remote: koppor@devbox
            ---

            # Notes

            body
            """;

    @Test
    void flagsDroppedOpeningFence() {
        // The exact corruption: the leading `---` line vanished, the rest intact.
        String corrupt = VALID.substring(VALID.indexOf('\n') + 1);
        assertThat(corrupt).startsWith("title:");
        assertThat(MainWindow.droppedFrontmatterFence(VALID, corrupt)).isTrue();
    }

    @Test
    void allowsAnOrdinaryEditThatKeepsTheFence() {
        String edited = VALID.replace("body", "a longer body\nwith more notes");
        assertThat(MainWindow.droppedFrontmatterFence(VALID, edited)).isFalse();
    }

    @Test
    void allowsEditingTheFrontmatterItself() {
        String edited = VALID.replace("status: active", "status: done");
        assertThat(MainWindow.droppedFrontmatterFence(VALID, edited)).isFalse();
    }

    @Test
    void allowsWritingAFileThatNeverHadAFence() {
        // A group config or scratch file the user is authoring from nothing:
        // the loaded content had no fence, so removing/keeping one is the
        // user's business, not corruption.
        assertThat(MainWindow.droppedFrontmatterFence("free text", "other text")).isFalse();
    }

    @Test
    void toleratesCrlfLineEndings() {
        String crlf = VALID.replace("\n", "\r\n");
        String corrupt = crlf.substring(crlf.indexOf('\n') + 1);
        assertThat(MainWindow.droppedFrontmatterFence(crlf, corrupt)).isTrue();
    }

    @Test
    void toleratesALeadingByteOrderMark() {
        String bom = "﻿" + VALID;
        String corrupt = "﻿" + VALID.substring(VALID.indexOf('\n') + 1);
        assertThat(MainWindow.droppedFrontmatterFence(bom, corrupt)).isTrue();
    }
}
