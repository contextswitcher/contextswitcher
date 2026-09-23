package com.contextswitcher.tasks;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// What an auto category's next reconcile has to do — the pure decision half
/// of `dsn~auto-pr-category~3`, so the rule that deletes task files is
/// testable without a GitHub account or a JavaFX window.
///
/// The input is the whole task list (a PR already tracked by *any* task is
/// never added a second time), the category being reconciled, the PR URLs its
/// query matched in this round, and the URLs that are still `live` — this
/// round's hits plus every tracked pull request GitHub does not report as
/// merged or closed.
///
/// The two sets are deliberately different: **matched** decides what is added,
/// **live** decides what stays. A pull request that grew past the size limit,
/// lost its review request, or was dragged into the category by hand is not a
/// match and never will be, but it is open and stays until it is closed.
/// A task of the category is managed only while it stays a triage row: it
/// carries a PR URL and no `tmux:` section. The moment a session is started in
/// it, it is the user's task and the reconcile keeps its hands off — no
/// suspend, no delete, no resume.
// [impl->dsn~auto-pr-category~3]
public final class AutoPrReconcile {

    /// The four actions of one reconcile round: `add` are the matched PR URLs
    /// no task carries yet (in query order), `suspend` the category's tasks
    /// whose pull requests are all done, `delete` those suspended long enough
    /// ago, and `resume` those the reconcile itself suspended whose pull
    /// request is open again.
    public record Plan(List<String> add, List<Task> suspend, List<Task> delete, List<Task> resume) {

        /// Back-compat constructor for callers predating the resume list.
        public Plan(List<String> add, List<Task> suspend, List<Task> delete) {
            this(add, suspend, delete, List.of());
        }

        public boolean isEmpty() {
            return add.isEmpty() && suspend.isEmpty() && delete.isEmpty() && resume.isEmpty();
        }
    }

    private AutoPrReconcile() {
    }

    /// The plan for `category` against one round: `matched` are the pull
    /// requests worth adding, `live` those that must not be suspended.
    /// `now` is the clock the delete grace period is measured against.
    public static Plan plan(Collection<Task> allTasks, String category, List<String> matched,
            Collection<String> live, GroupConfig.AutoPr config, LocalDateTime now) {
        Set<String> liveUrls = new HashSet<>();
        live.forEach(url -> liveUrls.add(Task.normalizeUrl(url)));
        Set<String> carried = new HashSet<>();
        for (Task task : allTasks) {
            task.prUrls().forEach(url -> carried.add(Task.normalizeUrl(url)));
        }
        List<String> add = new ArrayList<>();
        for (String url : matched) {
            String normalized = Task.normalizeUrl(url);
            liveUrls.add(normalized);
            if (carried.add(normalized)) {
                add.add(url);
            }
        }

        List<Task> suspend = new ArrayList<>();
        List<Task> delete = new ArrayList<>();
        List<Task> resume = new ArrayList<>();
        String prefix = category + "/";
        for (Task task : allTasks) {
            if (!task.id().startsWith(prefix) || task.tmux() != null || task.prUrls().isEmpty()) {
                continue;
            }
            if (task.prUrls().stream().map(Task::normalizeUrl).anyMatch(liveUrls::contains)) {
                // Live again: undo the reconcile's own suspend — and only
                // that one. A suspend the user made by hand carries no
                // `autoPrClosed`, so pausing a row is how a pull request is
                // dismissed from an auto category: it stays paused, and stays
                // undeleted for as long as its pull request is open.
                if (task.status() == TaskStatus.SUSPENDED && task.autoPrClosed()) {
                    resume.add(task);
                }
            } else if (task.status() == TaskStatus.ACTIVE) {
                suspend.add(task);
            } else if (task.status() == TaskStatus.SUSPENDED
                    && expired(task.suspendedAt(), config.deleteHours(), now)) {
                delete.add(task);
            }
        }
        return new Plan(List.copyOf(add), List.copyOf(suspend), List.copyOf(delete),
                List.copyOf(resume));
    }

    /// Whether a task suspended at `suspendedAt` (the `dsn~suspended-timestamp~1`
    /// spelling) has outlived the grace period. Shared with [MergedCleanup],
    /// whose grace period is the same stamp read in days. `deleteHours` of `0` keeps the
    /// task for good, and so does a missing or unreadable timestamp — a file is
    /// never deleted on a guess.
    static boolean expired(@Nullable String suspendedAt, int deleteHours, LocalDateTime now) {
        if (deleteHours <= 0 || suspendedAt == null) {
            return false;
        }
        try {
            return !LocalDateTime.parse(suspendedAt.strip(), TaskFileParser.SUSPENDED_AT)
                    .plusHours(deleteHours).isAfter(now);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
