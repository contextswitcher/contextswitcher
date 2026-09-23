package com.contextswitcher.provenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.tasks.TextFiles;

/// Record files in the provenance repository's copy: one Markdown file per sent
/// message at `<category or _>/<task name>/<sentAt>-<sender>.md`, frontmatter
/// for the fields and the text under `# Sent`. A file per message, so two
/// machines recording at once never touch the same file.
// [impl->dsn~provenance-record~1]
public final class ProvenanceStore {

    static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);

    private ProvenanceStore() {
    }

    /// Writes `record` under `dir` and returns the file; never overwrites (a
    /// taken name gets `-2`, `-3`…).
    public static Path write(Path dir, ProvenanceRecord record) throws IOException {
        String category = record.category().isEmpty() ? "_" : record.category();
        String task = record.taskId().substring(record.taskId().lastIndexOf('/') + 1);
        Path folder = dir.resolve(category).resolve(task);
        Files.createDirectories(folder);
        String base = STAMP.format(record.sentAt()) + "-" + record.sender();
        Path file = folder.resolve(base + ".md");
        for (int i = 2; Files.exists(file); i++) {
            file = folder.resolve(base + "-" + i + ".md");
        }
        TextFiles.write(file, content(record));
        return file;
    }

    static String content(ProvenanceRecord record) {
        StringBuilder text = new StringBuilder("---\n");
        field(text, "sentAt", record.sentAt().toString());
        field(text, "sender", record.sender());
        field(text, "task", record.taskId());
        field(text, "title", record.taskTitle());
        field(text, "repo", record.repo());
        field(text, "remote", record.remote());
        field(text, "window", record.window());
        field(text, "sessionId", record.sessionId());
        field(text, "workspace", record.workspace());
        field(text, "commitBefore", record.commitBefore());
        return text.append("---\n\n# Sent\n\n").append(record.text().strip()).append('\n').toString();
    }

    private static void field(StringBuilder text, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            text.append(key).append(": ").append(TaskFileParser.yamlScalar(value)).append('\n');
        }
    }
}
