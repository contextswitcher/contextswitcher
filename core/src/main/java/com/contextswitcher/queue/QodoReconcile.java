package com.contextswitcher.queue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// Pure reconcile of a task's message queue against qodo's current **active**
/// agent prompts: adds prompts not yet queued and — only when `removeResolved`
/// (an explicit "Qodo sync") — drops queued messages that came from qodo but
/// are no longer suggested, i.e. the "done" ones.
///
/// `qodoSourced` is the set of prompt texts qodo has previously placed into
/// this queue; a queued message outside it is the user's own (hand-typed, or a
/// qodo prompt they edited) and is never removed, so a sync cannot clobber a
/// draft. A suggestion the user already sent or deleted stays in `qodoSourced`
/// but out of the queue, so it is not re-added — the user's choice is honored.
// [impl->dsn~qodo-agent-prompt-queue~11]
public final class QodoReconcile {

    private QodoReconcile() {
    }

    /// The outcome: the new queue, the new qodo-sourced set to persist, and how
    /// many messages were added / removed (for the status line).
    public record Result(List<String> queue, Set<String> qodoSourced, int added, int removed) {
    }

    public static Result apply(List<String> queue, Set<String> qodoSourced,
            List<String> current, boolean removeResolved) {
        Set<String> currentSet = new LinkedHashSet<>(current);
        Set<String> sourced = new LinkedHashSet<>(qodoSourced);
        List<String> result = new ArrayList<>();
        int removed = 0;
        for (String message : queue) {
            if (removeResolved && sourced.contains(message) && !currentSet.contains(message)) {
                sourced.remove(message);
                removed++;
                continue;
            }
            result.add(message);
        }
        int added = 0;
        for (String prompt : current) {
            if (!sourced.contains(prompt) && !result.contains(prompt)) {
                result.add(prompt);
                sourced.add(prompt);
                added++;
            }
        }
        // Forget sourced texts that are neither queued nor still suggested, so
        // the set cannot grow without bound over a PR's lifetime.
        sourced.removeIf(text -> !result.contains(text) && !currentSet.contains(text));
        return new Result(result, sourced, added, removed);
    }
}
