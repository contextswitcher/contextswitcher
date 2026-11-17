package com.contextswitcher.terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.tinylog.Logger;

/// The working/waiting status of a Claude session the app hosts itself
/// (`dsn~terminal-owned-session~5`): no tmux holds such a session, so the
/// `@cs_status` hooks have nowhere to write. Each start instead passes Claude
/// `--settings` with hooks of its own that write the status into a file next
/// to it, and the status poll reads those files like a tmux report.
///
/// The hook is `echo <status>>"<file>"`, which bash and `cmd` both run — the
/// shell Claude Code picks for hooks on Windows is not ours to choose — and
/// the read strips the line break either one writes.
// [impl->dsn~terminal-owned-session~5]
public final class OwnedStatus {

    /// The host part of an owned session's key in the status map — not a host
    /// name ssh would accept, so it cannot collide with a polled one.
    private static final String HOST = "<owned>";

    private OwnedStatus() {
    }

    /// The status map key for the owned session of `taskId`.
    public static String key(String taskId) {
        return HOST + " " + fileName(taskId);
    }

    /// Writes the `--settings` file for the session of `taskId` into `dir` and
    /// drops a status left from an earlier run; returns the settings file.
    public static Path prepare(Path dir, String taskId) throws IOException {
        Files.createDirectories(dir);
        Path status = dir.resolve(fileName(taskId) + ".status");
        Files.deleteIfExists(status);
        Path settings = dir.resolve(fileName(taskId) + ".settings.json");
        Files.writeString(settings, settingsJson(status));
        return settings;
    }

    /// Claude Code settings whose hooks write the session's status to `status`.
    static String settingsJson(Path status) {
        String file = status.toAbsolutePath().toString().replace('\\', '/');
        return """
                {"hooks": {
                  "UserPromptSubmit": [%s],
                  "Stop": [%s],
                  "Notification": [%s],
                  "SessionStart": [%s]
                }}
                """.formatted(hook("working", file), hook("waiting", file),
                hook("attention", file), hook("waiting", file));
    }

    private static String hook(String value, String file) {
        String command = "echo " + value + ">\"" + file + "\"";
        return "{\"hooks\": [{\"type\": \"command\", \"command\": \""
                + command.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}]}";
    }

    /// Every status in `dir`, keyed like [#key]; an empty map when there is
    /// no directory yet.
    public static Map<String, String> read(Path dir) {
        Map<String, String> statuses = new HashMap<>();
        if (!Files.isDirectory(dir)) {
            return statuses;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(file -> file.getFileName().toString().endsWith(".status")).forEach(file -> {
                String name = file.getFileName().toString();
                try {
                    String status = Files.readString(file).strip();
                    if (!status.isEmpty()) {
                        statuses.put(HOST + " " + name.substring(0, name.length() - ".status".length()),
                                status);
                    }
                } catch (IOException e) {
                    Logger.debug("Cannot read {}: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            Logger.debug("Cannot list {}: {}", dir, e.getMessage());
        }
        return statuses;
    }

    /// A task id (`group/slug`) as a file name.
    private static String fileName(String taskId) {
        return taskId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
