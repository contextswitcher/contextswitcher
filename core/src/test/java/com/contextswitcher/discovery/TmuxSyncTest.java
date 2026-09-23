package com.contextswitcher.discovery;

import java.util.Arrays;
import java.util.List;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~tmux-sync~7]
class TmuxSyncTest {

    /// A bare shell task (no claude/notes) — disposable when its window dies.
    private static Task task(String id, TaskStatus status, String remote, String window) {
        return new Task(id, id, status, remote,
                window == null ? null : new Task.TmuxConfig("0", window),
                null, null, null, null, null, "");
    }

    /// A shell task carrying user notes — NOT disposable (worth suspending).
    private static Task notedTask(String id, TaskStatus status, String remote, String window) {
        return new Task(id, id, status, remote,
                window == null ? null : new Task.TmuxConfig("0", window),
                null, null, null, null, null, "# Notes\n\nkeep me");
    }

    private static Task claudeTask(String id, String remote, String window,
            Task.ClaudeConfig claude) {
        return new Task(id, id, TaskStatus.ACTIVE, remote, new Task.TmuxConfig("0", window),
                null, null, null, claude, null, List.of(), List.of(), "");
    }

    private static TmuxDiscovery.TmuxWindow window(String id, String sessionId) {
        return new TmuxDiscovery.TmuxWindow(id, "1", "/w", "claude", "w", "", "", "",
                sessionId, null);
    }

    /// A session-level task: `tmux.session` set, no `window` key.
    private static Task sessionTask(String id, TaskStatus status, String remote) {
        return new Task(id, id, status, remote, new Task.TmuxConfig("0", null),
                null, null, null, null, null, "");
    }

    private static List<TmuxDiscovery.TmuxSession> sessions(List<TmuxDiscovery.TmuxWindow> windows) {
        return List.of(new TmuxDiscovery.TmuxSession("0", windows));
    }

    /// Live windows of session `0` by id, none publishing a Claude session id.
    private static List<TmuxDiscovery.TmuxSession> ids(String... ids) {
        return sessions(Arrays.stream(ids).map(id -> window(id, null)).toList());
    }

    // [utest->dsn~android-window-lookup~1]
    @Test
    void currentWindowFollowsTheClaudeSessionIntoARecreatedWindow() {
        Task stale = claudeTask("t", "h", "@857", new Task.ClaudeConfig("/w", "sess-1", null));
        assertThat(TmuxSync.currentWindowId(stale, sessions(List.of(window("@858", "other"), window("@931", "sess-1")))))
                .isEqualTo("@931");
        assertThat(TmuxSync.currentWindowId(stale, sessions(List.of(window("@857", "sess-1"), window("@931", "sess-1")))))
                .as("the recorded window wins while it is live").isEqualTo("@857");
        assertThat(TmuxSync.currentWindowId(stale, sessions(List.of(window("@930", "sess-1"), window("@931", "sess-1")))))
                .as("two claimants: no guess").isNull();
        assertThat(TmuxSync.currentWindowId(stale, sessions(List.of(window("@931", "other"))))).isNull();
        assertThat(TmuxSync.currentWindowId(stale, List.of(new TmuxDiscovery.TmuxSession("elsewhere",
                List.of(window("@931", "sess-1")))))).as("only in the task's own session").isNull();
        assertThat(stale.withTmuxWindow("@931").tmux()).isEqualTo(new Task.TmuxConfig("0", "@931"));
    }

    @Test
    void goneWindowSuspendsAndReappearedWindowReactivates() {
        List<Task> tasks = List.of(
                notedTask("alive", TaskStatus.ACTIVE, "h", "@1"),
                notedTask("gone", TaskStatus.ACTIVE, "h", "@2"),
                notedTask("back", TaskStatus.SUSPENDED, "h", "@3"),
                notedTask("stillgone", TaskStatus.SUSPENDED, "h", "@4"));

        List<TmuxSync.Reconciliation> changes = TmuxSync.reconcile(tasks, "h", ids("@1", "@3"), task -> true);

        assertThat(changes).containsExactlyInAnyOrder(
                new TmuxSync.Reconciliation.SetStatus("gone", TaskStatus.SUSPENDED, "window @2 is gone"),
                new TmuxSync.Reconciliation.SetStatus("back", TaskStatus.ACTIVE, "window @3 is back"));
    }

    // A disposable shell (a closed plain window with nothing recorded) is
    // deleted rather than left as a suspended husk; one that carries notes is
    // suspended, one that still has content stays keepable.
    @Test
    void goneDisposableShellIsDeletedButNotedShellIsSuspended() {
        List<Task> tasks = List.of(
                task("bash", TaskStatus.ACTIVE, "h", "@1"),
                notedTask("noted", TaskStatus.ACTIVE, "h", "@2"),
                claudeTask("claude", "h", "@3", new Task.ClaudeConfig("/w", "sid", null)));

        List<TmuxSync.Reconciliation> changes = TmuxSync.reconcile(tasks, "h", ids(), task -> true);

        assertThat(changes).containsExactlyInAnyOrder(
                new TmuxSync.Reconciliation.Delete("bash"),
                new TmuxSync.Reconciliation.SetStatus("noted", TaskStatus.SUSPENDED, "window @2 is gone"),
                new TmuxSync.Reconciliation.SetStatus("claude", TaskStatus.SUSPENDED, "window @3 is gone"));
    }

    @Test
    void doneAndWindowlessAndOtherRemoteTasksAreLeftAlone() {
        List<Task> tasks = List.of(
                task("done", TaskStatus.DONE, "h", "@9"),
                task("sessionLevel", TaskStatus.ACTIVE, "h", null),
                task("otherHost", TaskStatus.ACTIVE, "other", "@9"));

        assertThat(TmuxSync.reconcile(tasks, "h", ids(), task -> true)).isEmpty();
    }

    // Without the category's consent a disposable shell is kept, suspended.
    // [utest->dsn~auto-delete-opt-in~1]
    @Test
    void goneDisposableShellIsSuspendedWithoutConsent() {
        List<Task> tasks = List.of(task("bash", TaskStatus.ACTIVE, "h", "@1"));

        assertThat(TmuxSync.reconcile(tasks, "h", ids(), task -> false)).containsExactly(
                new TmuxSync.Reconciliation.SetStatus("bash", TaskStatus.SUSPENDED, "window @1 is gone"));
    }

    // A window-level task covers its window in any status (a reappeared window
    // reactivates instead of duplicating); a session-level task covers the
    // whole session only while not suspended.
    @Test
    void coverageKeysCountWindowTasksOfAnyStatusAndActiveSessionLevelTasks() {
        List<Task> tasks = List.of(
                task("active", TaskStatus.ACTIVE, "h", "@1"),
                task("suspendedWithWindow", TaskStatus.SUSPENDED, "h", "@2"),
                sessionTask("sessionLevel", TaskStatus.ACTIVE, "h"),
                task("otherHost", TaskStatus.ACTIVE, "other", "@3"));

        assertThat(TmuxSync.coverageKeys(tasks, "h")).containsExactlyInAnyOrder(
                TmuxTaskImporter.windowKey("h", "0", "@1"),
                TmuxTaskImporter.windowKey("h", "0", "@2"),
                TmuxTaskImporter.sessionKey("h", "0"));
    }

    // Suspend removes the window: line — the leftover window-less task must
    // not masquerade as session-level coverage and block importing new
    // windows of the session.
    @Test
    void coverageKeysIgnoreSuspendedWindowlessTasks() {
        List<Task> tasks = List.of(sessionTask("suspendLeftover", TaskStatus.SUSPENDED, "h"));

        assertThat(TmuxSync.coverageKeys(tasks, "h")).isEmpty();
    }

    // Only a covered window with a published id fills a task without one; a
    // task whose window publishes a *different* id is a stranger's window
    // (its name is not the task's title either), so it is left alone —
    // see sessionIdOfTheTasksOwnWindowIsCorrected for the other case.
    // A task without a claude section is
    // filled too — it adopts the session (the window's cwd rides along, since
    // a sessionId without cwd would not parse): that is the plain shell the
    // user started claude in by hand after the import.
    @Test
    void sessionIdBackfillsFillEveryCoveredTaskWithoutAnId() {
        List<Task> tasks = List.of(
                claudeTask("fillMe", "h", "@1", new Task.ClaudeConfig("/w", null, null)),
                claudeTask("hasId", "h", "@2", new Task.ClaudeConfig("/w", "keep-me", null)),
                task("startedByHand", TaskStatus.ACTIVE, "h", "@3"),
                claudeTask("windowSilent", "h", "@4", new Task.ClaudeConfig("/w", null, null)),
                claudeTask("otherHost", "other", "@1", new Task.ClaudeConfig("/w", null, null)));
        List<TmuxDiscovery.TmuxWindow> windows = List.of(
                window("@1", "aaaa-1111"), window("@2", "bbbb-2222"),
                window("@3", "cccc-3333"), window("@4", null));

        assertThat(TmuxSync.sessionIdBackfills(tasks, "h", sessions(windows))).containsExactly(
                new TmuxSync.SessionIdBackfill("fillMe", "aaaa-1111", "/w"),
                new TmuxSync.SessionIdBackfill("startedByHand", "cccc-3333", "/w"));
    }

    /// The window still named after the task, whatever it publishes: the belt
    /// the focus path has always used (field report 2026-09-18: @747's session
    /// changed its id in place, and the sync suspended the task once per poll
    /// while every play reactivated it).
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void sessionIdOfTheTasksOwnWindowIsCorrected() {
        Task mine = claudeTask("mine", "h", "@747", new Task.ClaudeConfig("/w", "old-id", null));
        List<TmuxDiscovery.TmuxSession> live = sessions(List.of(
                new TmuxDiscovery.TmuxWindow("@747", "1", "/w", "claude", "mine", "", "", "",
                        "new-id", null)));

        assertThat(TmuxSync.reconcile(List.of(mine), "h", live, task -> true)).isEmpty();
        assertThat(TmuxSync.sessionIdBackfills(List.of(mine), "h", live))
                .containsExactly(new TmuxSync.SessionIdBackfill("mine", "new-id", "/w"));
    }

    private static TmuxDiscovery.TmuxWindow workspaceWindow(String id, String workspace) {
        return new TmuxDiscovery.TmuxWindow(id, "1", "/w", "claude", "w", "", workspace, "",
                null, null);
    }

    // [utest->dsn~claude-workspace-capture~2]
    @Test
    void workspaceRefreshesFollowThePublishedOptionAndSkipUnchangedOnes() {
        List<Task> tasks = List.of(
                claudeTask("neverHad", "h", "@1", new Task.ClaudeConfig("/root", null, null)),
                claudeTask("movedOn", "h", "@2", new Task.ClaudeConfig("/root", null, "/root/old")),
                claudeTask("unchanged", "h", "@3", new Task.ClaudeConfig("/root", null, "/root/same")),
                claudeTask("windowSilent", "h", "@4", new Task.ClaudeConfig("/root", null, null)),
                task("noClaudeSection", TaskStatus.ACTIVE, "h", "@5"),
                claudeTask("otherHost", "other", "@1", new Task.ClaudeConfig("/root", null, null)));
        List<TmuxDiscovery.TmuxWindow> windows = List.of(
                workspaceWindow("@1", "/root/2026-07-23-a"),
                workspaceWindow("@2", "/root/2026-07-23-b"),
                workspaceWindow("@3", "/root/same"),
                workspaceWindow("@4", ""),
                workspaceWindow("@5", "/root/2026-07-23-c"));

        assertThat(TmuxSync.workspaceRefreshes(tasks, "h", sessions(windows))).containsExactly(
                new TmuxSync.WorkspaceRefresh("neverHad", "/root/2026-07-23-a"),
                new TmuxSync.WorkspaceRefresh("movedOn", "/root/2026-07-23-b"));
    }

    private static TmuxDiscovery.TmuxWindow commitWindow(String id, String commit) {
        return new TmuxDiscovery.TmuxWindow(id, "1", "/w", "claude", "w", "", "", "",
                null, null, commit);
    }

    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void commitRefreshesFollowThePublishedOptionAndSkipUnchangedOnes() {
        List<Task> tasks = List.of(
                claudeTask("neverHad", "h", "@1", new Task.ClaudeConfig("/root", null, null, null)),
                claudeTask("newCommit", "h", "@2",
                        new Task.ClaudeConfig("/root", null, null, "aaa1111")),
                claudeTask("unchanged", "h", "@3",
                        new Task.ClaudeConfig("/root", null, null, "ccc3333")),
                claudeTask("windowSilent", "h", "@4", new Task.ClaudeConfig("/root", null, null)),
                task("noClaudeSection", TaskStatus.ACTIVE, "h", "@5"),
                claudeTask("otherHost", "other", "@1", new Task.ClaudeConfig("/root", null, null)));
        List<TmuxDiscovery.TmuxWindow> windows = List.of(
                commitWindow("@1", "aaa1111"),
                commitWindow("@2", "bbb2222"),
                commitWindow("@3", "ccc3333"),
                commitWindow("@4", ""),
                commitWindow("@5", "ddd4444"));

        assertThat(TmuxSync.commitRefreshes(tasks, "h", sessions(windows))).containsExactly(
                new TmuxSync.CommitRefresh("neverHad", "aaa1111"),
                new TmuxSync.CommitRefresh("newCommit", "bbb2222"));
    }

    /// A tmux window id is unique only per server lifetime: after a restart a
    /// stale task's id names a stranger's window (field report 2026-08-25:
    /// two July tasks at @61 took over the live @61's title and PRs).
    @Test
    void reusedWindowIdIsGoneUnlessSessionAndClaudeSessionMatch() {
        Task.ClaudeConfig oldClaude = new Task.ClaudeConfig("/w", "5c08-old", null);
        Task.ClaudeConfig liveClaude = new Task.ClaudeConfig("/w", "0664-live", null);
        Task.ClaudeConfig noId = new Task.ClaudeConfig("/w", null, null);
        List<Task> tasks = List.of(
                notedTask("otherSession", TaskStatus.ACTIVE, "h", "@61"),
                inSession("otherClaude", TaskStatus.ACTIVE, "old-group", oldClaude),
                inSession("noIdYet", TaskStatus.ACTIVE, "old-group", noId),
                inSession("sameClaude", TaskStatus.SUSPENDED, "old-group", liveClaude));
        List<TmuxDiscovery.TmuxSession> live = List.of(
                new TmuxDiscovery.TmuxSession("old-group", List.of(window("@61", "0664-live"))));

        assertThat(TmuxSync.reconcile(tasks, "h", live, task -> true)).containsExactlyInAnyOrder(
                new TmuxSync.Reconciliation.SetStatus("otherSession", TaskStatus.SUSPENDED, "window @61 is gone"),
                new TmuxSync.Reconciliation.SetStatus("otherClaude", TaskStatus.SUSPENDED,
                        "window @61 publishes session 0664-live, task records 5c08-old"
                        + " (taken over by another session)"),
                new TmuxSync.Reconciliation.SetStatus("sameClaude", TaskStatus.ACTIVE, "window @61 is back"));
    }

    @Test
    void refreshesIgnoreAStrangersWindowThatReusesTheId() {
        List<Task> tasks = List.of(
                claudeTask("stale", "h", "@61", new Task.ClaudeConfig("/w", "5c08-old", null)));
        List<TmuxDiscovery.TmuxSession> live = sessions(List.of(new TmuxDiscovery.TmuxWindow(
                "@61", "1", "/w", "claude", "w", "", "/w/live", "", "0664-live", null)));

        assertThat(TmuxSync.workspaceRefreshes(tasks, "h", live)).isEmpty();
    }

    private static Task inSession(String id, TaskStatus status, String session,
            Task.ClaudeConfig claude) {
        return new Task(id, id, status, "h", new Task.TmuxConfig(session, "@61"),
                null, null, null, claude, null, List.of(), List.of(), "");
    }
}
