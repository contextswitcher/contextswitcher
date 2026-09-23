package com.contextswitcher.queue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.tinylog.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.contextswitcher.tasks.TextFiles;

/// The qodo agent-prompt texts already placed into a task's message queue,
/// each mapped to the review comment it came from. It lets a re-poll or an app
/// restart avoid re-queuing a prompt the user has sent or deleted, lets a
/// "Qodo sync" tell which queued messages came from qodo (so may be dropped
/// when the suggestion is gone) from the user's own drafts, and gives each such
/// message its open-in-browser link. One YAML `prompt: comment url` map per
/// task, `<tasksDir>/.queues/qodo/<task id, `/`→`__`>.yaml`, stored beside the
/// queues so it rides the task-dir git backup too
/// (`dsn~message-queue-store~2`).
// [impl->dsn~qodo-agent-prompt-queue~11]
public final class QodoImported {

    private QodoImported() {
    }

    public static Path file(Path qodoDir, String taskId) {
        return qodoDir.resolve(taskId.replace("/", "__") + ".yaml");
    }

    /// The stored prompts and their comment URLs, in insertion order; a
    /// missing or corrupt file loads as an empty map (same tolerance as
    /// [QueueFile#load]). A file in the pre-link format (a plain string list)
    /// still loads — those prompts just have no URL yet, until the next sync
    /// re-learns it.
    public static Map<String, String> load(Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions()))
                    .load(TextFiles.read(file));
            Map<String, String> byPrompt = new LinkedHashMap<>();
            if (loaded instanceof Map<?, ?> map) {
                map.forEach((prompt, url) ->
                        byPrompt.put(prompt.toString(), url == null ? "" : url.toString()));
            } else if (loaded instanceof List<?> list) {
                list.forEach(prompt -> byPrompt.put(prompt.toString(), ""));
            }
            return byPrompt;
        } catch (IOException | RuntimeException e) {
            Logger.warn("Cannot read qodo-imported file {}: {}", file, e.getMessage());
            return Map.of();
        }
    }

    /// Writes the map; an empty map deletes the file.
    public static void save(Path file, Map<String, String> byPrompt) throws IOException {
        if (byPrompt.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        TextFiles.write(file, new Yaml().dump(byPrompt));
    }

    /// Follows a task rename, like `QueueFile.rename`, so the qodo-sourced
    /// memory is not lost when a task's id changes. No file = no-op; a failure
    /// only logs.
    public static void rename(Path qodoDir, String fromTaskId, String toTaskId) {
        Path from = file(qodoDir, fromTaskId);
        if (!Files.exists(from)) {
            return;
        }
        try {
            Files.move(from, file(qodoDir, toTaskId));
        } catch (IOException e) {
            Logger.warn("Cannot move qodo-imported file of {} to {}: {}",
                    fromTaskId, toTaskId, e.getMessage());
        }
    }
}
