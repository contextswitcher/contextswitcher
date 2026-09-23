package com.contextswitcher.switching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// [utest->dsn~terminal-owned-session~3]
class LocalDiffTest {

    @Test
    void cleanWorktreeShowsTheLastCommit() {
        assertEquals(List.of("git", "-c", "color.ui=always", "--no-pager", "show"),
                LocalDiff.diffCommand(true));
    }

    @Test
    void changedWorktreeShowsItsChangesAgainstHead() {
        assertEquals(List.of("git", "-c", "color.ui=always", "--no-pager", "diff", "HEAD"),
                LocalDiff.diffCommand(false));
    }

    @Test
    void aDirectoryOutsideAnyRepositorySaysSo(@TempDir Path dir) {
        assertTrue(LocalDiff.render(dir.toString()).startsWith("Not a git repository: "));
    }

    @Test
    void aMissingDirectorySaysSo(@TempDir Path dir) {
        assertTrue(LocalDiff.render(dir.resolve("gone").toString()).startsWith("No such directory: "));
    }
}
