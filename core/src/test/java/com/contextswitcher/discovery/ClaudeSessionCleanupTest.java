package com.contextswitcher.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// [utest->dsn~claude-session-kill~7]
class ClaudeSessionCleanupTest {

    @Test
    void transcriptCommandEncodesTheStartDirectory() {
        assertEquals(
                List.of("rm", "-f",
                        "~/.claude/projects/-data-koppor-contextswitcher-workspaces"
                                + "/0af7f1a2-1111-2222-3333-444455556666.jsonl"),
                ClaudeSessionCleanup.transcriptCommand("/data/koppor/contextswitcher.workspaces",
                        "0af7f1a2-1111-2222-3333-444455556666"));
    }

    @Test
    void localTranscriptIsRemovedFromTheEncodedWindowsStartDirectory(@TempDir Path home)
            throws Exception {
        Path dir = home.resolve(".claude/projects/"
                + "C--git-repositories-github-com-contextswitcher-contextswitcher-workspaces");
        Files.createDirectories(dir);
        Path transcript = Files.writeString(dir.resolve("c43d5db9.jsonl"), "{}");
        Path sibling = Files.writeString(dir.resolve("other.jsonl"), "{}");

        assertNull(ClaudeSessionCleanup.removeLocalTranscript(home,
                "C:\\git-repositories\\github.com\\contextswitcher\\contextswitcher-workspaces",
                "c43d5db9"));

        assertFalse(Files.exists(transcript));
        assertTrue(Files.exists(sibling));
    }

    @Test
    void aMissingLocalTranscriptCountsAsRemoved(@TempDir Path home) {
        assertNull(ClaudeSessionCleanup.removeLocalTranscript(home, "C:\\ws", "gone"));
    }

    @Test
    void workdirCommandSingleQuotesThePath() {
        assertEquals(List.of("rm", "-rf", "'/data/koppor/ws/2026-07-17-task'"),
                ClaudeSessionCleanup.workdirCommand("/data/koppor/ws/2026-07-17-task"));
    }

    @Test
    void safeWorkdirPasses() {
        assertNull(ClaudeSessionCleanup.rejectWorkdir("/data/koppor/ws/2026-07-17-task", List.of()));
        assertNull(ClaudeSessionCleanup.rejectWorkdir("/home/koppor/repos/task/", List.of()));
    }

    @Test
    void aCategoryDirectoryIsNeverRemoved() {
        List<String> owned = List.of("/data/koppor/ws/contextswitcher", "/data/koppor/ws");
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/data/koppor/ws/contextswitcher", owned));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/data/koppor/ws/contextswitcher/", owned));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/data/koppor/ws", owned));
        assertNull(ClaudeSessionCleanup.rejectWorkdir("/data/koppor/ws/2026-09-12-task", owned));
    }

    @Test
    void unsafeWorkdirsAreRejected() {
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("relative/path", List.of()));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/", List.of()));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/home", List.of()));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/home/", List.of()));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/data/koppor/../etc", List.of()));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("/data/it's-a-trap", List.of()));
        assertNotNull(ClaudeSessionCleanup.rejectWorkdir("", List.of()));
    }
}
