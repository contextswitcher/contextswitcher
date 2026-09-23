package com.contextswitcher.tasks;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// The decision half of the merged-task janitor (`dsn~merged-task-cleanup~2`):
/// a task whose pull requests are all merged has nothing left to do, so its
/// window, transcript, worktree and file can go — unless its last screen shows
/// something the user still has to read.
///
/// "Has to read" is decided on the captured pane text, not on `@cs_status`: a
/// session that ended with a question, a permission prompt, an error or a
/// usage limit is held back; one that ended with the usual progress and
/// summary lines is not. The hold is not forever — a task paused longer than
/// the configured grace period is cleaned up regardless, its last screen
/// appended to the notes first so a `git log` of the task directory can bring
/// it back.
// [impl->dsn~merged-task-cleanup~2]
public final class MergedCleanup {

    /// SGR and charset escapes of a `capture-pane -e` screen; stripped so the
    /// note reads as text and the patterns below match what the user saw.
    private static final Pattern ANSI = Pattern.compile("\\e\\[[0-9;?]*[ -/]*[@-~]|\\e[()][B0]");

    /// What makes a last screen worth a human's eyes. Deliberately a handful
    /// of literal Claude Code shapes rather than a grammar: a question box
    /// (`Do you want …` with its `❯ 1.` choices), a still-running turn, an
    /// error, or the usage limit that no hook announces.
    // ponytail: a regex over the last screen; the upgrade path is a published
    // `@cs_needs_input` option if Claude Code ever fires a hook for it.
    private static final Pattern NEEDS_HUMAN = Pattern.compile(
            "(?i)do you want|❯\\s*\\d+\\.|\\(y/n\\)|esc to interrupt|"
                    + "error|limit reached|reached your .{0,40}limit");

    /// Heading of the appended screen — also the marker that keeps a second
    /// append from stacking on the first.
    static final String HEADING = "## Last screen before the auto-cleanup";

    private MergedCleanup() {
    }

    /// Whether the janitor may judge this task at all — everything about the
    /// task itself that `MainWindow.autoCleanupMerged` checks before it looks
    /// at pull-request states: a worktree session (`claude:`), pull requests
    /// to be merged, not ended by hand, and a window that is neither busy nor
    /// unknown.
    ///
    /// `runningStatus` is the status poll's word on the task's window (null =
    /// the poll has not reported on it). An **active** task with a null status
    /// is skipped: its window is either seconds old — created between two poll
    /// rounds — or gone, and neither is a session that ended. A **suspended**
    /// task has no window left for the poll to see and is judged on its stored
    /// snapshot or the grace period instead.
    public static boolean judgeable(Task task, @Nullable String runningStatus) {
        return task.claude() != null
                && !task.prUrls().isEmpty()
                && task.status() != TaskStatus.DONE
                && !"working".equals(runningStatus)
                && (runningStatus != null || task.status() == TaskStatus.SUSPENDED);
    }

    /// The pane text without its terminal escapes.
    public static String plain(String pane) {
        return ANSI.matcher(pane).replaceAll("");
    }

    /// Whether `pane` ends on something the user has to see. An unknown screen
    /// (no live window, no stored snapshot) counts as "yes": the janitor never
    /// removes a session it could not look at, it only lets the grace period
    /// remove it.
    public static boolean needsHuman(@Nullable String pane) {
        return pane == null || NEEDS_HUMAN.matcher(plain(pane)).find();
    }

    /// Whether the task may be cleaned up now: `graceDays` of `0` turns the
    /// janitor off, an unremarkable last screen clears it right away, and an
    /// interesting one only after the task has been paused `graceDays` long
    /// (`suspendedAt` is the `dsn~suspended-timestamp~1` stamp — an active
    /// task carries none and is therefore never force-cleaned).
    public static boolean cleanUpNow(@Nullable String pane, @Nullable String suspendedAt,
            int graceDays, LocalDateTime now) {
        if (graceDays <= 0) {
            return false;
        }
        return !needsHuman(pane) || AutoPrReconcile.expired(suspendedAt, graceDays * 24, now);
    }

    /// The task file with the last screen appended below its notes, so the
    /// commit that precedes the delete carries what the window showed. A null
    /// or blank screen, or a file that already carries one, is returned
    /// unchanged.
    public static String withLastScreen(String content, @Nullable String pane) {
        if (pane == null || pane.isBlank() || content.contains(HEADING)) {
            return content;
        }
        String screen = plain(pane).stripTrailing();
        return content.stripTrailing() + "\n\n" + HEADING + "\n\n```text\n" + screen + "\n```\n";
    }
}
