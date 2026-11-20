package com.contextswitcher.tasks;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// The open lines of a todo.txt file that name a task with a `cs:<id>` tag —
/// the tag koppter writes for "this todo belongs to that ContextSwitcher task" —
/// rendered for the task row: the description with its priority, and its
/// projects, contexts, hashtags and due date as labels.
// [impl->dsn~todo-txt-line~1]
public final class TodoTxt {

    /// One rendered todo.txt line: `projects` are its `+project`s, shown right
    /// before `text`, the description led by `(A)` when the line has a priority;
    /// `labels` are `@context`, `#tag` and `due <date>` in the order of the line.
    public record Line(List<String> projects, String text, List<String> labels) {
        public Line {
            projects = List.copyOf(projects);
            labels = List.copyOf(labels);
        }
    }

    private static final Pattern PRIORITY = Pattern.compile("^\\(([A-Z])\\) ");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    // todo.txt's key:value — a value starting with `/` is a URL (`https://…`).
    private static final Pattern TAG = Pattern.compile("([^\\s:]+):([^\\s/][^\\s]*)");

    private TodoTxt() {
    }

    /// The open lines of `lines` by the task id their `cs:` tag names, in file order.
    public static Map<String, List<Line>> byTask(List<String> lines) {
        Map<String, List<Line>> out = new LinkedHashMap<>();
        for (String raw : lines) {
            if (raw.startsWith("x ")) {
                continue;
            }
            String rest = raw;
            String priority = "";
            var p = PRIORITY.matcher(rest);
            if (p.find()) {
                priority = "(" + p.group(1) + ") ";
                rest = rest.substring(p.end());
            }
            String id = null;
            List<String> words = new ArrayList<>();
            List<String> projects = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            boolean first = true;
            for (String word : rest.trim().split("\\s+")) {
                if (word.isEmpty() || (first && DATE.matcher(word).matches())) {
                    first = false;
                    continue;
                }
                first = false;
                var tag = TAG.matcher(word);
                if (word.length() > 1 && word.startsWith("+")) {
                    projects.add(word);
                } else if (word.length() > 1 && (word.startsWith("@") || word.startsWith("#"))) {
                    labels.add(word);
                } else if (tag.matches()) {
                    switch (tag.group(1)) {
                        case "cs" -> id = decode(tag.group(2));
                        case "due" -> labels.add("due " + tag.group(2));
                        default -> { } // t:, rec:, at: … are planning data, not for the row
                    }
                } else {
                    words.add(word);
                }
            }
            if (id != null) {
                out.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new Line(projects, priority + String.join(" ", words), labels));
            }
        }
        return out;
    }

    // koppter keeps the id as a ContextSwitcher link spells it: each path
    // segment percent-encoded (`My%20Category/task`). A literal `+` stays one.
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    /// A todo.txt file read again only when its modification time changes, so
    /// asking per row repaint costs one `stat`.
    // ponytail: rows pick up an edit of the file on their next repaint, not on
    // their own; add a file watch when that lag shows.
    public static final class File {
        private @Nullable Path path;
        private @Nullable FileTime read;
        private Map<String, List<Line>> byTask = Map.of();

        /// Points at another file; null or blank turns the lines off.
        public synchronized void setPath(@Nullable String file) {
            Path next = file == null || file.isBlank() ? null : Path.of(file.trim());
            if (!Objects.equals(next, path)) {
                path = next;
                read = null;
                byTask = Map.of();
            }
        }

        /// The rendered open lines naming `taskId`; empty when the file is unset or unreadable.
        public synchronized List<Line> linesFor(String taskId) {
            if (path == null) {
                return List.of();
            }
            try {
                FileTime modified = Files.getLastModifiedTime(path);
                if (!modified.equals(read)) {
                    byTask = byTask(Files.readAllLines(path, StandardCharsets.UTF_8));
                    read = modified;
                }
            } catch (IOException e) {
                byTask = Map.of();
                read = null;
            }
            return byTask.getOrDefault(taskId, List.of());
        }
    }
}
