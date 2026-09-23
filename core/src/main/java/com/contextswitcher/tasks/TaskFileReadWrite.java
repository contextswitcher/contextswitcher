package com.contextswitcher.tasks;

import org.jspecify.annotations.Nullable;

/// Reading and writing one task file by its file name (relative to the tasks
/// directory, e.g. `jabref/2026-09-15-fix-npe.md`) — the part of the app's
/// task-file access that a transform-and-save caller needs, and nothing more.
public interface TaskFileReadWrite {

    /// Reads a task file, returning its real content or `null` when it
    /// cannot be read. Never returns a human-readable error string, so a
    /// failed read is not transformed and written back, corrupting the file.
    @Nullable String read(String fileName);

    /// Returns null on success, otherwise the error text.
    @Nullable String save(String fileName, String content);
}
