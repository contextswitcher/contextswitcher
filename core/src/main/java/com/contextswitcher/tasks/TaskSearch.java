package com.contextswitcher.tasks;

import java.util.Locale;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// Free-text matching over task entries, backing the find bar (Ctrl+F).
/// Matching is case-insensitive substring over the fields a user searches by
/// — most importantly the browser URLs, so a task is findable by its PR link
/// (pasting `https://github.com/JabRef/jabref/pull/16245`, or just `16245`).
// [impl->dsn~task-find~9]
public final class TaskSearch {

    private TaskSearch() {
    }

    /// Whether `entry` matches `query`. A blank query matches everything (the
    /// find bar shows the full list); otherwise the trimmed query — with any
    /// trailing `/` dropped, so a URL pasted with a trailing slash still finds
    /// the task storing it without one — is matched case-insensitively as a
    /// substring of the entry's searchable text.
    public static boolean matches(TaskEntry entry, String query) {
        return matches(entry, query, () -> "");
    }

    /// As [#matches(TaskEntry,String)], but falling back to `messages` — the
    /// task's queued and last-sent chat messages — when the entry's own fields
    /// miss. The supplier is consulted only then, so the caller's queue-file
    /// read stays off the path for the tasks that already matched.
    public static boolean matches(TaskEntry entry, String query, Supplier<String> messages) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        while (needle.endsWith("/")) {
            needle = needle.substring(0, needle.length() - 1);
        }
        if (needle.isEmpty()) {
            return true;
        }
        return searchableText(entry).toLowerCase(Locale.ROOT).contains(needle)
                || messages.get().toLowerCase(Locale.ROOT).contains(needle);
    }

    /// The concatenated fields searched for `entry`: for a loaded task its id,
    /// title, remote, note, folder, tags, browser URLs (and their titles), the
    /// Claude cwd / workspace, the remaining configuration (tmux, terminal,
    /// IntelliJ, chat) and the Markdown note body; for a failed entry its file
    /// name and the parse error, so a broken file is findable too.
    static String searchableText(TaskEntry entry) {
        return switch (entry) {
            case TaskEntry.Failed failed -> failed.fileName() + '\n' + failed.message();
            case TaskEntry.Loaded loaded -> searchableText(loaded.task());
        };
    }

    private static String searchableText(Task task) {
        StringBuilder text = new StringBuilder();
        text.append(task.id()).append('\n').append(task.title());
        append(text, task.remote());
        append(text, task.note());
        for (String folder : task.folders()) {
            append(text, folder);
        }
        for (String tag : task.tags()) {
            append(text, tag);
        }
        Task.BrowserConfig browser = task.browser();
        if (browser != null) {
            for (Task.UrlEntry url : browser.entries()) {
                append(text, url.url());
                append(text, url.title());
            }
        }
        Task.ClaudeConfig claude = task.claude();
        if (claude != null) {
            append(text, claude.cwd());
            append(text, claude.workspace());
            append(text, claude.sessionId());
        }
        Task.TmuxConfig tmux = task.tmux();
        if (tmux != null) {
            append(text, tmux.session());
            append(text, tmux.window());
        }
        Task.TerminalConfig terminal = task.terminal();
        if (terminal != null) {
            append(text, terminal.tabTitle());
        }
        Task.IntellijConfig intellij = task.intellij();
        if (intellij != null) {
            append(text, intellij.projectPath());
            append(text, intellij.remote());
            append(text, intellij.ide());
        }
        append(text, task.chat());
        append(text, task.notes());
        return text.toString();
    }

    private static void append(StringBuilder text, @Nullable String value) {
        if (value != null) {
            text.append('\n').append(value);
        }
    }
}
