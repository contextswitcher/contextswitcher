package com.contextswitcher.tasks;

/// One entry in the task list: a successfully parsed task or a parse failure
/// (shown as an error row, so a broken file never hides or crashes anything).
// [impl->dsn~task-repository-watching~6]
public sealed interface TaskEntry {

    /// The task file's path relative to the task directory, without the
    /// `.md` extension (forward-slash separated); identifies the entry
    /// across reloads.
    String id();

    /// The task's group: its containing subfolder name, or `""` for a
    /// root-level task.
    // [impl->dsn~task-repository-watching~6]
    default String group() {
        int slash = id().lastIndexOf('/');
        return slash < 0 ? "" : id().substring(0, slash);
    }

    record Loaded(Task task) implements TaskEntry {
        @Override
        public String id() {
            return task.id();
        }
    }

    record Failed(String id, String fileName, String message) implements TaskEntry {
    }
}
