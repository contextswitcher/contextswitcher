package com.contextswitcher.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Which task (or category) the window in front belongs to — the pure half of
/// the *switch by window* hotkey (`dsn~switch-by-window-title~1`).
///
/// A task or category names its windows with `windowTitle:`, one string or a
/// list of them. Each is matched **case-insensitively as a substring** of the
/// foreground window's title, so `Thesis weekly sync` matches the Teams
/// meeting window `Thesis weekly sync | Microsoft Teams` whatever suffix the
/// app appends. A plain substring rather than a regex: what the user copies
/// out of a title bar is what they type, and no `|` or `(` in a meeting name
/// turns into syntax.
///
/// Several matches are ranked by their **longest** matching entry: the more
/// of the title an entry spells out, the more specific it is — `Thesis weekly
/// sync` beats a category-wide `Thesis`. A task's match always beats a
/// category's, which only counts when no task matches at all.
// [impl->dsn~switch-by-window-title~1]
public final class WindowTitleMatch {

    /// The frontmatter key, in task files and in `CONTEXTSWITCHER.md`.
    public static final String KEY = "windowTitle";

    private WindowTitleMatch() {
    }

    /// A task id or category name with its `windowTitle:` entries.
    public record Candidate(String id, List<String> patterns) {
        public Candidate {
            patterns = List.copyOf(patterns);
        }
    }

    /// What a hotkey press resolves to.
    public enum Kind {
        /// One task matches best: switch to it.
        TASK,
        /// Several tasks match equally well.
        TASKS,
        /// No task, but one category matches best: switch into it.
        CATEGORY,
        /// No task, several categories equally well.
        CATEGORIES,
        /// Nothing matches.
        NONE
    }

    /// The resolution: its kind and the ids (task ids or category names) in
    /// candidate order — one for [Kind#TASK]/[Kind#CATEGORY], none for
    /// [Kind#NONE].
    public record Outcome(Kind kind, List<String> ids) {
        public Outcome {
            ids = List.copyOf(ids);
        }
    }

    /// The length of the longest entry of `patterns` contained in `title`
    /// (case-insensitive), or 0 when none is; blank entries never match.
    public static int score(String title, List<String> patterns) {
        String haystack = title.toLowerCase(Locale.ROOT);
        int best = 0;
        for (String pattern : patterns) {
            String needle = pattern.strip().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && haystack.contains(needle)) {
                best = Math.max(best, needle.length());
            }
        }
        return best;
    }

    /// The ids of the candidates with the highest nonzero [#score], in
    /// candidate order; empty when none matches.
    public static List<String> best(String title, List<Candidate> candidates) {
        int top = 0;
        List<String> ids = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int score = score(title, candidate.patterns());
            if (score > top) {
                top = score;
                ids.clear();
            }
            if (score > 0 && score == top) {
                ids.add(candidate.id());
            }
        }
        return ids;
    }

    /// Resolves the foreground window's `title` against the tasks (callers
    /// pass only the ones not done) and, when no task matches, the categories.
    public static Outcome resolve(String title, List<Candidate> tasks, List<Candidate> categories) {
        if (title.isBlank()) {
            return new Outcome(Kind.NONE, List.of());
        }
        List<String> taskIds = best(title, tasks);
        if (!taskIds.isEmpty()) {
            return new Outcome(taskIds.size() == 1 ? Kind.TASK : Kind.TASKS, taskIds);
        }
        List<String> categoryIds = best(title, categories);
        if (!categoryIds.isEmpty()) {
            return new Outcome(categoryIds.size() == 1 ? Kind.CATEGORY : Kind.CATEGORIES, categoryIds);
        }
        return new Outcome(Kind.NONE, List.of());
    }
}
