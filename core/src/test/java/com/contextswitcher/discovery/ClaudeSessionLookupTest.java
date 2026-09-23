package com.contextswitcher.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~claude-session-capture~3]
class ClaudeSessionLookupTest {

    @Test
    void projectDirEncodesSlashesAndDots() {
        assertThat(ClaudeSessionLookup.encodeProjectDir("/home/olive/repos/jabref.dev"))
                .isEqualTo("-home-olive-repos-jabref-dev");
    }

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void projectDirEncodesTheDriveColonAndBackslashesOfAWindowsPath() {
        assertThat(ClaudeSessionLookup.encodeProjectDir(
                "C:\\git-repositories\\github.com\\contextswitcher\\contextswitcher"))
                .isEqualTo("C--git-repositories-github-com-contextswitcher-contextswitcher");
    }

    @Test
    void remoteCommandListsNewestTranscriptFirst() {
        assertThat(ClaudeSessionLookup.remoteCommand("/home/olive/repos/jabref"))
                .containsExactly("ls", "-t",
                        "~/.claude/projects/-home-olive-repos-jabref/*.jsonl",
                        "|", "head", "-1");
    }
}
