package com.contextswitcher.provenance;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/// One sent message as recorded at send (`dsn~provenance-record~1`): who sent
/// what to which task's session, and the commit that session had published
/// last. `text` is what the chat received — attachment markers rewritten.
// [impl->dsn~provenance-record~1]
public record ProvenanceRecord(Instant sentAt, String sender, String taskId, String taskTitle,
        @Nullable String repo, String remote, String window, @Nullable String sessionId,
        @Nullable String workspace, @Nullable String commitBefore, String text) {

    /// The task's category: its id's folder, empty for a task at the root.
    public String category() {
        int slash = taskId.lastIndexOf('/');
        return slash < 0 ? "" : taskId.substring(0, slash);
    }
}
