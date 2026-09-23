package com.contextswitcher.discovery;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.contextswitcher.switching.TmuxWindowOwnership;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.jspecify.annotations.Nullable;

/// Reconciles task statuses against the live tmux windows of a remote: a
/// task whose window is gone is marked suspended (or **deleted** when it is a
/// disposable shell — nothing to keep, `Task.isDisposableShell`), a suspended
/// task whose window is back is reactivated. Pure status reconciliation — no
/// kill or resurrect side effects (the window is already gone, resp. already
/// there). `done` and window-less (session-level) tasks are left alone.
// [impl->dsn~tmux-sync~7]
public final class TmuxSync {

    private TmuxSync() {
    }

    /// What the sync should do to a task after reconciling it against the
    /// live windows: change its status, or delete it (a disposable shell whose
    /// window is gone).
    public sealed interface Reconciliation {
        String taskId();

        /// `reason` says why, for the log and the status bar: a window that
        /// is gone reads the same from the outside as one taken over by
        /// another task's session, and only the latter leaves a live window
        /// behind in the task file.
        record SetStatus(String taskId, TaskStatus status, String reason) implements Reconciliation {
        }

        record Delete(String taskId) implements Reconciliation {
        }
    }

    /// The live window a task's `tmux:` coordinates denote, or null when the
    /// task's window is gone. A tmux window id is unique only for one server
    /// lifetime — after a restart the ids start over at `@0`, so a task
    /// suspended when the old server died carries an id that, days later,
    /// names a stranger's window. Matching on the id alone re-activated such
    /// a task and then synced everything the stranger published onto it
    /// (title, session id, workspace, commit, PR). Hence the window must sit
    /// in the task's recorded session (grouped sessions list a shared window
    /// under every member's name, so a `new-session -t` twin still matches)
    /// and, when both task and window carry a Claude session id, it must be
    /// the same one ([TmuxWindowOwnership#hijacked]) — unless the window is
    /// still **named after the task**, the same belt [TmuxFocusAction] passes:
    /// a window carrying the task's title is that task's window whatever it
    /// publishes, and without the belt here the sync suspended a task the
    /// switch had just reactivated, once per poll (field report 2026-09-18:
    /// `@747`, whose session had changed its id inside the very same window).
    /// `sessions` must be the **raw** discovery output — the enrichment's
    /// guessed id must never make a task's own window look like a stranger's.
    // [impl->dsn~tmux-sync~7]
    // [impl->dsn~tmux-window-ownership~4]
    static TmuxDiscovery.@Nullable TmuxWindow liveWindow(Task task,
            List<TmuxDiscovery.TmuxSession> sessions) {
        TmuxDiscovery.TmuxWindow window = windowAt(task, sessions);
        if (window == null) {
            return null;
        }
        return TmuxWindowOwnership.hijacked(recordedSessionId(task), window.sessionId())
                && !ownWindowName(task, window) ? null : window;
    }

    /// True when the window still carries the task's title as its tmux name.
    private static boolean ownWindowName(Task task, TmuxDiscovery.TmuxWindow window) {
        String name = window.name().strip();
        return !name.isEmpty() && name.equals(task.title().strip());
    }

    /// The id of the window `task` lives in now: its recorded window when that
    /// is still [#liveWindow], else the one window of its recorded session that
    /// publishes the task's Claude session id — a window recreated for the same
    /// conversation (resume, restart) gets a new id, which a copy of the task
    /// file synced before that does not know yet. Null when neither exists, or
    /// when several windows claim the session.
    // [impl->dsn~android-window-lookup~1]
    public static @Nullable String currentWindowId(Task task, List<TmuxDiscovery.TmuxSession> sessions) {
        TmuxDiscovery.TmuxWindow live = liveWindow(task, sessions);
        if (live != null) {
            return live.id();
        }
        String sessionId = recordedSessionId(task);
        if (sessionId == null || task.tmux() == null) {
            return null;
        }
        List<String> claimed = sessions.stream()
                .filter(session -> session.name().equals(task.tmux().session()))
                .flatMap(session -> session.windows().stream())
                .filter(window -> sessionId.equals(window.sessionId()))
                .map(TmuxDiscovery.TmuxWindow::id)
                .distinct()
                .toList();
        return claimed.size() == 1 ? claimed.get(0) : null;
    }

    /// The window at the task's coordinates, whoever's session runs in it.
    private static TmuxDiscovery.@Nullable TmuxWindow windowAt(Task task,
            List<TmuxDiscovery.TmuxSession> sessions) {
        Task.TmuxConfig tmux = task.tmux();
        if (tmux == null || tmux.window() == null) {
            return null;
        }
        for (TmuxDiscovery.TmuxSession session : sessions) {
            if (!session.name().equals(tmux.session())) {
                continue;
            }
            for (TmuxDiscovery.TmuxWindow window : session.windows()) {
                if (window.id().equals(tmux.window())) {
                    return window;
                }
            }
        }
        return null;
    }

    private static @Nullable String recordedSessionId(Task task) {
        return task.claude() == null ? null : task.claude().sessionId();
    }

    /// Why [#liveWindow] found no window for `task`.
    static String goneReason(Task task, List<TmuxDiscovery.TmuxSession> sessions) {
        TmuxDiscovery.TmuxWindow window = windowAt(task, sessions);
        String id = task.tmux() == null ? "?" : task.tmux().window();
        return window == null
                ? "window " + id + " is gone"
                : "window " + id + " publishes session " + window.sessionId()
                        + ", task records " + recordedSessionId(task) + " (taken over by another session)";
    }

    /// The reconciliations for `remote`'s tasks given its live sessions. Only
    /// tasks of that remote with a window id participate. An active task
    /// whose window vanished (see [#liveWindow]) is deleted when it is a
    /// disposable shell (a closed plain window with nothing recorded), else
    /// suspended so it stays resurrectable. `mayDelete` is the category's
    /// consent (`GroupConfig.mayAutoDelete`): without it a disposable shell is
    /// suspended like any other task.
    // [impl->dsn~auto-delete-opt-in~1]
    public static List<Reconciliation> reconcile(List<Task> tasks, String remote,
            List<TmuxDiscovery.TmuxSession> sessions, Predicate<Task> mayDelete) {
        List<Reconciliation> changes = new ArrayList<>();
        for (Task task : tasks) {
            if (!remote.equals(task.remote()) || task.tmux() == null
                    || task.tmux().window() == null) {
                continue;
            }
            boolean alive = liveWindow(task, sessions) != null;
            if (task.status() == TaskStatus.ACTIVE && !alive) {
                changes.add(task.isDisposableShell() && mayDelete.test(task)
                        ? new Reconciliation.Delete(task.id())
                        : new Reconciliation.SetStatus(task.id(), TaskStatus.SUSPENDED,
                                goneReason(task, sessions)));
            } else if (task.status() == TaskStatus.SUSPENDED && alive) {
                changes.add(new Reconciliation.SetStatus(task.id(), TaskStatus.ACTIVE,
                        "window " + task.tmux().window() + " is back"));
            }
        }
        return changes;
    }

    /// A session id the sync should write into a task's `claude:` section,
    /// with the window's `cwd` for the case where that section has to be
    /// created first.
    public record SessionIdBackfill(String taskId, String sessionId, String cwd) {
    }

    /// The session-id backfills for `remote`'s tasks: a task whose live
    /// window published `@cs_session_id` but whose `claude:` section records
    /// another one (or none) gets the published id (enables resume for tasks created
    /// live, before their session's `SessionStart` hook ran). `sessions` must
    /// be the **raw** discovery output — only the exact published id is
    /// trusted; the newest-transcript enrichment guess must never be written
    /// into an existing task (resume could hijack another task's session).
    ///
    /// A task **without** a `claude:` section is filled too — it *adopts* the
    /// session, section and all (the caller creates it from `cwd`, which a
    /// `sessionId` needs to parse and resume needs anyway). That is the window
    /// imported as a plain shell in which the user later started `claude` by
    /// hand: without adoption such a task never learns about its session, so
    /// suspend warns that nothing can be resumed while a perfectly resumable
    /// session is running in it.
    ///
    /// A **differing** id is corrected too, which only [#liveWindow]'s name
    /// belt can produce: the window is the task's own, but the session in it
    /// took a new id (a `/clear`, a fork, a resume). The recorded id is then
    /// stale for everyone who compares against it — the mirror refuses to show
    /// the pane, the kill guard refuses to end the window, resume would start
    /// the wrong conversation — so the sync writes the published one.
    public static List<SessionIdBackfill> sessionIdBackfills(List<Task> tasks, String remote,
            List<TmuxDiscovery.TmuxSession> sessions) {
        List<SessionIdBackfill> fills = new ArrayList<>();
        for (Task task : tasks) {
            if (!remote.equals(task.remote())) {
                continue;
            }
            TmuxDiscovery.TmuxWindow window = liveWindow(task, sessions);
            if (window != null && window.sessionId() != null
                    && !window.sessionId().equals(recordedSessionId(task))) {
                fills.add(new SessionIdBackfill(task.id(), window.sessionId(), window.cwd()));
            }
        }
        return fills;
    }

    /// A working subdirectory the sync should write into a task's `claude:`
    /// section.
    public record WorkspaceRefresh(String taskId, String workspace) {
    }

    /// The `claude.workspace` refreshes for `remote`'s tasks: a task whose live
    /// window publishes a `@cs_workspace` different from the recorded one gets
    /// the published path. The import snapshots the option once, which is never
    /// enough for a task the **app** created: its window publishes the
    /// workspace only after Claude bootstrapped its worktree, minutes after the
    /// task file was written — and a session that later moves to another
    /// worktree publishes again. Without this the task keeps pointing at the
    /// start `cwd` (the shared workspaces root), which is what "Show diff" and
    /// the IntelliJ action then open. Tasks without a `claude:` section are
    /// skipped: a `workspace` without `cwd` would not parse.
    // [impl->dsn~claude-workspace-capture~2]
    public static List<WorkspaceRefresh> workspaceRefreshes(List<Task> tasks, String remote,
            List<TmuxDiscovery.TmuxSession> sessions) {
        List<WorkspaceRefresh> refreshes = new ArrayList<>();
        for (Task task : tasks) {
            if (!remote.equals(task.remote()) || task.claude() == null) {
                continue;
            }
            TmuxDiscovery.TmuxWindow window = liveWindow(task, sessions);
            if (window == null) {
                continue;
            }
            String published = window.workspace().strip();
            if (!published.isEmpty() && !published.equals(task.claude().workspace())) {
                refreshes.add(new WorkspaceRefresh(task.id(), published));
            }
        }
        return refreshes;
    }

    /// A commit the sync should write into a task's `claude:` section.
    public record CommitRefresh(String taskId, String commit) {
    }

    /// The `claude.commit` refreshes for `remote`'s tasks: a task whose live
    /// window publishes a `@cs_commit` different from the recorded one gets the
    /// published sha. Shaped exactly like [#workspaceRefreshes] — same window
    /// match, same skip of `claude:`-less tasks, same "blank or unchanged
    /// writes nothing".
    ///
    /// This exists because `claude.workspace` is *not* durable: in a
    /// mainline-development flow Claude commits and then deletes its own
    /// worktree, so by the time the user presses "Show diff" the recorded
    /// workspace is a path that no longer exists and the diff has nothing to
    /// open. The published sha outlives the worktree — the commit itself
    /// survives in the repository — and is what the fallback shows.
    // [impl->dsn~diff-after-worktree-removal~1]
    public static List<CommitRefresh> commitRefreshes(List<Task> tasks, String remote,
            List<TmuxDiscovery.TmuxSession> sessions) {
        List<CommitRefresh> refreshes = new ArrayList<>();
        for (Task task : tasks) {
            if (!remote.equals(task.remote()) || task.claude() == null) {
                continue;
            }
            TmuxDiscovery.TmuxWindow window = liveWindow(task, sessions);
            if (window == null) {
                continue;
            }
            String published = window.commit().strip();
            if (!published.isEmpty() && !published.equals(task.claude().commit())) {
                refreshes.add(new CommitRefresh(task.id(), published));
            }
        }
        return refreshes;
    }

    /// The coverage keys of `remote`'s tasks for the import step. A task with
    /// a window covers that window in any status — a reappeared window
    /// reactivates its suspended task via [#reconcile]; importing it too would
    /// duplicate it. A window-less task covers its whole session only while
    /// **not suspended**: suspend removes the `window:` line
    /// (`dsn~task-suspend~6`), and such a leftover must not masquerade as a
    /// session-level task — new windows of the session are new contexts worth
    /// importing.
    public static Set<String> coverageKeys(List<Task> tasks, String remote) {
        Set<String> keys = new HashSet<>();
        for (Task task : tasks) {
            if (!remote.equals(task.remote()) || task.tmux() == null) {
                continue;
            }
            if (task.tmux().window() != null) {
                keys.add(TmuxTaskImporter.windowKey(remote, task.tmux().session(), task.tmux().window()));
            } else if (task.status() != TaskStatus.SUSPENDED) {
                keys.add(TmuxTaskImporter.sessionKey(remote, task.tmux().session()));
            }
        }
        return Set.copyOf(keys);
    }
}
