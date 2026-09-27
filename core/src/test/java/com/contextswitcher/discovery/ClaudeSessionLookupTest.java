package com.contextswitcher.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~claude-session-capture~3]
class ClaudeSessionLookupTest {

    @Test
    void projectDirEncodesSlashesAndDots() {
        assertThat(ClaudeSessionLookup.encodeProjectDir("/home/olive/repos/jabref.dev"))
                .isEqualTo("-home-olive-repos-jabref-dev");
    }

    // [utest->dsn~terminal-owned-session~4]
    @Test
    void projectDirEncodesTheDriveColonAndBackslashesOfAWindowsPath() {
        assertThat(ClaudeSessionLookup.encodeProjectDir(
                "C:\\git-repositories\\github.com\\contextswitcher\\contextswitcher"))
                .isEqualTo("C--git-repositories-github-com-contextswitcher-contextswitcher");
    }

    // [utest->dsn~terminal-owned-session~4]
    @Test
    void resumesTheRecordedSessionElseTheNewest(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("old.jsonl"), "");
        Files.setLastModifiedTime(dir.resolve("old.jsonl"), FileTime.fromMillis(1_000));
        Files.writeString(dir.resolve("new.jsonl"), "");
        Files.setLastModifiedTime(dir.resolve("new.jsonl"), FileTime.fromMillis(2_000));

        assertThat(ClaudeSessionLookup.sessionToResume(dir, "old")).isEqualTo("old");
        assertThat(ClaudeSessionLookup.sessionToResume(dir, null)).isEqualTo("new");
        assertThat(ClaudeSessionLookup.sessionToResume(dir, "gone")).isEqualTo("new");
        assertThat(ClaudeSessionLookup.sessionToResume(dir.resolve("missing"), "old")).isNull();
    }

    @Test
    void remoteCommandListsNewestTranscriptFirst() {
        assertThat(ClaudeSessionLookup.remoteCommand("/home/olive/repos/jabref"))
                .containsExactly("ls", "-t",
                        "~/.claude/projects/-home-olive-repos-jabref/*.jsonl",
                        "|", "head", "-1");
    }
}
