package com.contextswitcher.queue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.contextswitcher.tasks.TextFiles;

/// Persists one task's message queue as a YAML string list in
/// `<tasksDir>/.queues/<sanitized task id>.yaml` — YAML because messages are
/// multi-line Markdown that may itself contain `---` or any other ad-hoc
/// delimiter, and SnakeYAML is already on board for task files and settings.
/// Index 0 is the oldest message — the next one to send.
///
/// Inside the tasks directory but under a dot-prefixed `.queues/` the
/// repository scanner ignores (like `.git`), so a queue file is never parsed
/// as a broken task, yet the queue rides the task-dir git backup and syncs
/// across machines with the tasks.
// [impl->dsn~message-queue-store~2]
public final class QueueFile {

    private QueueFile() {
    }

    /// The queue file for a task id; `/` (group separator in ids) maps to
    /// `__` so grouped tasks stay one flat file per task.
    public static Path file(Path queuesDir, String taskId) {
        return queuesDir.resolve(taskId.replace("/", "__") + ".yaml");
    }

    /// The stored messages; a missing or unreadable file is an empty queue
    /// (the file appears with the first message, and a corrupt hand-edited
    /// file must not take the pane down).
    public static List<String> load(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object loaded = yaml.load(TextFiles.read(file));
            if (loaded instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
            return List.of();
        } catch (IOException | RuntimeException e) {
            org.tinylog.Logger.warn("Cannot read queue file {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    /// The last-sent file for a task id: the one message most recently
    /// delivered to the task's chat, plain text (a single message needs no
    /// list format). Beside the queue in `.queues/`, so it syncs too.
    // [impl->dsn~last-sent-message~5]
    public static Path sentFile(Path queuesDir, String taskId) {
        return queuesDir.resolve(taskId.replace("/", "__") + ".sent.txt");
    }

    /// The task's last sent message, or null when none was ever recorded
    /// (or the file is unreadable — nothing to show either way).
    // [impl->dsn~last-sent-message~5]
    public static @Nullable String loadSent(Path file) {
        try {
            return Files.exists(file) ? TextFiles.read(file) : null;
        } catch (IOException e) {
            org.tinylog.Logger.warn("Cannot read last-sent file {}: {}", file, e.getMessage());
            return null;
        }
    }

    /// The sent-history file for a task id: every message delivered to the
    /// task's chat, a YAML string list oldest-first like the queue itself
    /// (so [#load] and [#save] serve it too). Beside the queue in `.queues/`,
    /// so it syncs as well.
    // [impl->dsn~last-sent-message~5]
    public static Path sentHistoryFile(Path queuesDir, String taskId) {
        return queuesDir.resolve(taskId.replace("/", "__") + ".sent-history.yaml");
    }

    /// How many sent messages a task keeps: the box above the last sent one
    /// is for recalling what was asked recently, not an archive, and the file
    /// rides the task-dir git backup.
    private static final int HISTORY_LIMIT = 50;

    /// Records `text` as the task's last sent message, replacing the previous
    /// one, and appends it to the task's sent history (capped at the newest
    /// [#HISTORY_LIMIT] messages).
    /// Best effort — a failure only logs, the send itself already succeeded.
    // [impl->dsn~last-sent-message~5]
    public static void saveSent(Path queuesDir, String taskId, String text) {
        Path file = sentFile(queuesDir, taskId);
        try {
            Files.createDirectories(file.getParent());
            TextFiles.write(file, text);
        } catch (IOException e) {
            org.tinylog.Logger.warn("Cannot record the last sent message in {}: {}",
                    file, e.getMessage());
        }
        Path historyFile = sentHistoryFile(queuesDir, taskId);
        List<String> history = new java.util.ArrayList<>(load(historyFile));
        history.add(text);
        try {
            save(historyFile, history.subList(Math.max(0, history.size() - HISTORY_LIMIT),
                    history.size()));
        } catch (IOException e) {
            org.tinylog.Logger.warn("Cannot append to the sent history {}: {}",
                    historyFile, e.getMessage());
        }
    }

    /// The quick-message file: the short messages the queue pane offers as
    /// one-click buttons below the last-sent box ("Go" and whatever else one
    /// keeps retyping), a YAML string list like the queue itself (so [#load]
    /// and [#save] serve it too). One list for all tasks — a quick reply is
    /// not task-specific — and inside `.queues/`, so it rides the git backup
    /// and syncs across machines with everything else.
    // [impl->dsn~quick-message-buttons~7]
    public static Path quickFile(Path queuesDir) {
        return queuesDir.resolve("quick-messages.yaml");
    }

    /// The read-marks file for a task id: the queued messages the user ticked
    /// as **read**, a YAML string list like the queue itself (so [#load] and
    /// [#save] serve both). Beside the queue in `.queues/`, so it syncs too.
    // [impl->dsn~message-queue-read-mark~1]
    public static Path readFile(Path queuesDir, String taskId) {
        return queuesDir.resolve(taskId.replace("/", "__") + ".read.yaml");
    }

    /// Moves a task's queue file, last-sent file, sent history and read marks when its task file is
    /// renamed or moved — both are keyed by task id, so they must follow or
    /// the pending messages orphan. No file = no-op; a failure is only logged
    /// (the task rename already happened, the queue must not block it).
    // [impl->dsn~task-move-dnd~6]
    // [impl->dsn~last-sent-message~5]
    public static void rename(Path queuesDir, String fromTaskId, String toTaskId) {
        moveIfPresent(file(queuesDir, fromTaskId), file(queuesDir, toTaskId));
        moveIfPresent(sentFile(queuesDir, fromTaskId), sentFile(queuesDir, toTaskId));
        moveIfPresent(sentHistoryFile(queuesDir, fromTaskId), sentHistoryFile(queuesDir, toTaskId));
        moveIfPresent(readFile(queuesDir, fromTaskId), readFile(queuesDir, toTaskId));
    }

    private static void moveIfPresent(Path from, Path to) {
        if (!Files.exists(from)) {
            return;
        }
        try {
            Files.move(from, to);
        } catch (IOException e) {
            org.tinylog.Logger.warn("Cannot move {} to {}: {}", from, to, e.getMessage());
        }
    }

    /// The messages armed for a delayed send, per task id in send order — kept
    /// so an app restart does not silently disarm them. Local, **not** beside
    /// the synced queues: two machines sharing the tasks directory would
    /// otherwise both deliver the same message. Missing or unreadable = none.
    // [impl->dsn~message-queue-delayed-send~4]
    public static Map<String, List<String>> loadArmed(Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, List<String>> armed = new LinkedHashMap<>();
            if (yaml.load(TextFiles.read(file)) instanceof Map<?, ?> map) {
                map.forEach((id, texts) -> {
                    if (texts instanceof List<?> list && !list.isEmpty()) {
                        armed.put(id.toString(), list.stream().map(Object::toString).toList());
                    }
                });
            }
            return armed;
        } catch (IOException | RuntimeException e) {
            org.tinylog.Logger.warn("Cannot read armed messages {}: {}", file, e.getMessage());
            return Map.of();
        }
    }

    /// Writes the armed messages; nothing armed deletes the file.
    // [impl->dsn~message-queue-delayed-send~4]
    public static void saveArmed(Path file, Map<String, List<String>> armed) throws IOException {
        if (armed.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        TextFiles.write(file, new Yaml().dump(armed));
    }

    /// Writes the queue; an empty queue deletes the file so `queues/` holds
    /// only tasks that actually have pending messages.
    public static void save(Path file, List<String> messages) throws IOException {
        if (messages.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        TextFiles.write(file, new Yaml().dump(messages));
    }
}
