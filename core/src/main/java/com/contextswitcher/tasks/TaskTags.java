package com.contextswitcher.tasks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// Pure helpers for task tags: normalizing a frontmatter `tags` value into a
/// clean list, matching a task against an active filter (AND — every selected
/// tag must be present), and deciding which of a task's tags to render while a
/// filter is active (only the selected ones, so a filtered view never reveals
/// a task's other labels). A YAML list item is one tag verbatim (so tags may
/// contain spaces); matching is case-insensitive.
// [impl->dsn~task-tag-model~2]
public final class TaskTags {

    private TaskTags() {
    }

    /// The hues [#autoColor] picks from — GitHub-Primer-like label colors,
    /// distinct from each other and readable with white or dark text.
    private static final String[] AUTO_COLORS = {
            "#2da44e", "#0969da", "#bf3989", "#9a6700", "#8250df",
            "#cf222e", "#1a7f37", "#0550ae", "#d4a72c", "#57606a",
    };

    /// A stable, name-derived chip color (CSS hex) for a tag without a
    /// configured palette color: the lowercased name's hash picks from a
    /// fixed set of hues, so the same tag always renders in the same color.
    // [impl->dsn~tag-auto-color~2]
    public static String autoColor(String name) {
        int index = Math.floorMod(name.strip().toLowerCase(Locale.ROOT).hashCode(),
                AUTO_COLORS.length);
        return AUTO_COLORS[index];
    }

    /// Normalizes a frontmatter `tags` value into an order-preserving,
    /// case-insensitively de-duplicated list. Accepts a YAML list of scalars
    /// (each item one tag verbatim, so tags may contain spaces) or a single
    /// comma-separated scalar; each item is trimmed and blanks are dropped.
    /// Any other type (or null) yields an empty list.
    public static List<String> parse(@Nullable Object value) {
        List<String> raw = new ArrayList<>();
        switch (value) {
            case null -> { }
            case List<?> list -> {
                for (Object item : list) {
                    if (item != null && !item.toString().isBlank()) {
                        raw.add(item.toString().strip());
                    }
                }
            }
            default -> raw.addAll(split(value.toString()));
        }
        // De-duplicate case-insensitively, keeping the first spelling and order.
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String tag : raw) {
            if (seen.add(tag.toLowerCase(Locale.ROOT))) {
                result.add(tag);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> split(String value) {
        List<String> parts = new ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                parts.add(part.strip());
            }
        }
        return parts;
    }

    /// The tags to offer for selection: the union of `configured` (the
    /// settings.yaml palette) and `inUse` (tags already carried by tasks or
    /// groups), de-duplicated case-insensitively with the configured spelling
    /// winning, the whole list sorted alphabetically — so a tag only present
    /// in a task file is selectable without configuring it.
    // [impl->dsn~tag-selection-union~2]
    public static List<String> selectable(List<String> configured, List<String> inUse) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String tag : configured) {
            if (seen.add(tag.toLowerCase(Locale.ROOT))) {
                result.add(tag);
            }
        }
        for (String tag : inUse) {
            if (seen.add(tag.toLowerCase(Locale.ROOT))) {
                result.add(tag);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(result);
    }

    /// How many recently selected tags the filter menu lists above the rest.
    public static final int RECENT_LIMIT = 5;

    /// `recent` with `tag` moved to the front (case-insensitive de-duplication),
    /// capped at [#RECENT_LIMIT] — the most-recently-used list of the tag filter.
    // [impl->dsn~tag-filter-recent~1]
    public static List<String> withRecent(List<String> recent, String tag) {
        List<String> result = new ArrayList<>();
        result.add(tag);
        recent.stream().filter(name -> !name.equalsIgnoreCase(tag)).forEach(result::add);
        return List.copyOf(result.subList(0, Math.min(RECENT_LIMIT, result.size())));
    }

    /// Whether `taskTags` contains **every** tag in `required` (case-insensitive)
    /// — the AND semantics of the multi-select tag filter. An empty `required`
    /// matches every task (no filter active).
    public static boolean matchesAll(List<String> taskTags, Set<String> required) {
        if (required.isEmpty()) {
            return true;
        }
        Set<String> present = new LinkedHashSet<>();
        for (String tag : taskTags) {
            present.add(tag.toLowerCase(Locale.ROOT));
        }
        for (String tag : required) {
            if (!present.contains(tag.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /// The task tags to show on the row: all of them when no filter is active,
    /// otherwise only those in `active` (case-insensitive, keeping the task's
    /// own order) — so a filtered view shows just the selected tags and hides a
    /// task's other labels.
    public static List<String> visible(List<String> taskTags, Set<String> active) {
        if (active.isEmpty()) {
            return taskTags;
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String tag : active) {
            wanted.add(tag.toLowerCase(Locale.ROOT));
        }
        List<String> result = new ArrayList<>();
        for (String tag : taskTags) {
            if (wanted.contains(tag.toLowerCase(Locale.ROOT))) {
                result.add(tag);
            }
        }
        return List.copyOf(result);
    }
}
