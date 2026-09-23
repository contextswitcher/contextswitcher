package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~tmux-focus-action~3]
class TmuxFocusActionTest {

    /// The client loop appended to every focus command: switch each client
    /// that is not attached to a mirror session — never bare `switch-client`,
    /// which lets tmux pick the most recently active client (often the app's
    /// own mirror pty, yanking the embedded terminal onto the base session).
    private static List<String> switchNonMirrorClients(String session) {
        return List.of("&&",
                "tmux", "list-clients", "-F", "'#{session_name}=#{client_name}'",
                "|", "grep", "-v", "'^cs-mirror-'",
                "|", "cut", "-d", "=", "-f", "2-",
                "|", "xargs", "-r", "-I", "C",
                "tmux", "switch-client", "-c", "C", "-t", session);
    }

    private static List<String> concat(List<String> head, List<String> tail) {
        List<String> all = new ArrayList<>(head);
        all.addAll(tail);
        return all;
    }

    @Test
    void sessionAndWindowSelectWindowThenSwitchClients() {
        assertThat(TmuxFocusAction.remoteCommand(new Task.TmuxConfig("jabref", "claude")))
                .containsExactlyElementsOf(concat(
                        List.of("tmux", "select-window", "-t", "jabref:claude"),
                        switchNonMirrorClients("jabref")));
    }

    // A bare window id (`@17`) is ambiguous once the window is also linked
    // into a grouped mirror session — the select must pin the base session.
    @Test
    void windowIdIsQualifiedWithTheBaseSession() {
        assertThat(TmuxFocusAction.remoteCommand(new Task.TmuxConfig("2", "@17")))
                .containsExactlyElementsOf(concat(
                        List.of("tmux", "select-window", "-t", "2:@17"),
                        switchNonMirrorClients("2")));
    }

    // `has-session` keeps the failure signal of the old bare `switch-client`:
    // a dead server fails the `&&` chain and triggers the resurrect fallback.
    @Test
    void sessionOnlyChecksSessionThenSwitchesClients() {
        assertThat(TmuxFocusAction.remoteCommand(new Task.TmuxConfig("jabref", null)))
                .containsExactlyElementsOf(concat(
                        List.of("tmux", "has-session", "-t", "jabref"),
                        switchNonMirrorClients("jabref")));
    }

    /// Scripted ssh runner: pops the given results, records every argv.
    private static final class FakeSsh implements SshCommandRunner {
        final List<List<String>> calls = new ArrayList<>();
        final List<SshResult> results;

        FakeSsh(SshResult... scripted) {
            this.results = new ArrayList<>(List.of(scripted));
        }

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            calls.add(remoteCommand);
            return results.removeFirst();
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    }

    // A window-less config with a claude section is a suspended task's resume:
    // focusing the bare session would "succeed" on an arbitrary window, so the
    // action resurrects directly and reports the new window id.
    // [utest->dsn~tmux-resurrect~7]
    @Test
    void focusFailureWithoutClaudeResurrectsAPlainWindow() {
        // Stale window id, no claude section: the focus fails, and the task
        // still gets a plain window back (no resume) instead of a dead chip
        // — the resume path of a suspended no-claude task.
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(1, "", "can't find window @99"),
                new SshCommandRunner.SshResult(1, "", "can't find window @99"),
                new SshCommandRunner.SshResult(0, "@30\n", ""),
                new SshCommandRunner.SshResult(0, "", ""));
        List<String> reportedIds = new ArrayList<>();
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> reportedIds.add(newWindowId));
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("2", "@99"), null, null, null, null, null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isTrue();
        assertThat(reportedIds).containsExactly("@30");
    }

    // A window whose *session* moved is not gone: resurrecting would create a
    // duplicate and overwrite the task file's still-correct window id with it,
    // while the original keeps running the Claude session `--resume` wants.
    // [utest->dsn~tmux-resurrect~7]
    @Test
    void focusFailureWithTheWindowAliveElsewhereDoesNotResurrect() {
        FakeSsh ssh = new FakeSsh(
                // The ownership pre-flight: this window publishes no session
                // id, so nothing is claimed and the focus runs as before.
                new SshCommandRunner.SshResult(0, "\n", ""),
                new SshCommandRunner.SshResult(1, "", "can't find window @161"),
                new SshCommandRunner.SshResult(0, "cs-mirror-0\n", ""));
        List<String> reportedIds = new ArrayList<>();
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> reportedIds.add(newWindowId));
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("0", "@161"), null, null, null,
                new Task.ClaudeConfig("/home/o", "abc-123", null), null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo(
                "window @161 is in session cs-mirror-0, not 0 — fix the session");
        assertThat(reportedIds).isEmpty();
    }

    // Resume hands the actions the pre-flip suspended task: window-less and
    // without claude, it still resurrects — only ACTIVE window-less tasks
    // are deliberate session-level configs.
    // [utest->dsn~tmux-resurrect~7]
    @Test
    void windowlessSuspendedTaskWithoutClaudeResurrects() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "@31\n", ""),
                new SshCommandRunner.SshResult(0, "", ""));
        List<String> reportedIds = new ArrayList<>();
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> reportedIds.add(newWindowId));
        Task task = new Task("t", "T", TaskStatus.SUSPENDED, "h",
                new Task.TmuxConfig("2", null), null, null, null, null, null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isTrue();
        assertThat(ssh.calls.get(0).get(1)).isEqualTo("new-window");
        assertThat(reportedIds).containsExactly("@31");
    }

    @Test
    void windowlessActiveTaskWithoutClaudeKeepsSessionFocus() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "", ""));
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> { });
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("jabref", null), null, null, null, null, null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isTrue();
        assertThat(ssh.calls.getFirst()).containsExactlyElementsOf(concat(
                List.of("tmux", "has-session", "-t", "jabref"),
                switchNonMirrorClients("jabref")));
    }

    /// A task whose recorded window publishes **another** task's Claude session
    /// id: focusing it would land the user in that session while every chip
    /// says "focused". Two sessions in one tmux group share their window set,
    /// so `session:window` cannot tell the two tasks apart — only the published
    /// id can.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aWindowHostingAnotherClaudeSessionIsResurrectedInsteadOfFocused() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "26b2441d-85c8-4adc-a972-04d194bc9bdd\n", ""),
                new SshCommandRunner.SshResult(0, "@260\n", ""),
                new SshCommandRunner.SshResult(0, "", ""),
                new SshCommandRunner.SshResult(0, "", ""));
        List<String> reportedIds = new ArrayList<>();
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> reportedIds.add(newWindowId));
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("0", "@53"), null, null, null,
                new Task.ClaudeConfig("/home/o", "3e35d374-6ad4-4411-bfed-5b3995dcac3d", null),
                null, "");

        ActionResult result = action.run(task);

        assertThat(ssh.calls.getFirst()).containsExactly(
                "tmux", "display-message", "-p", "-t", "'@53'", "'#{@cs_session_id}|#{window_name}'");
        // No select-window on the foreign window — straight to a new one.
        assertThat(ssh.calls.get(1).get(1)).isEqualTo("new-window");
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).startsWith("@53 belonged to another session; resurrected as @260");
        assertThat(reportedIds).containsExactly("@260");
    }

    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aWindowPublishingTheTasksOwnSessionIsFocusedNormally() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "abc-123\n", ""),
                new SshCommandRunner.SshResult(0, "", ""));
        List<String> reportedIds = new ArrayList<>();
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> reportedIds.add(newWindowId));
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("0", "@53"), null, null, null,
                new Task.ClaudeConfig("/home/o", "abc-123", null), null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isTrue();
        assertThat(ssh.calls.get(1)).containsExactlyElementsOf(concat(
                List.of("tmux", "select-window", "-t", "0:@53"),
                switchNonMirrorClients("0")));
        assertThat(reportedIds).isEmpty();
    }

    /// The belt against a stray publication: a Claude Code started outside tmux
    /// stamps `@cs_session_id` onto whatever window is current — the mirror's,
    /// i.e. the task on screen (field report 2026-09-09). A window still named
    /// after the task is the task's own whatever it publishes, so it is focused
    /// rather than duplicated by a resurrect.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aWindowStillNamedAfterTheTaskIsFocusedDespiteAForeignId() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "26b2441d-85c8-4adc-a972-04d194bc9bdd|Port PR 629\n", ""),
                new SshCommandRunner.SshResult(0, "", ""));
        List<String> reportedIds = new ArrayList<>();
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> reportedIds.add(newWindowId));
        Task task = new Task("t", "Port PR 629", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("0", "@53"), null, null, null,
                new Task.ClaudeConfig("/home/o", "3e35d374-6ad4-4411-bfed-5b3995dcac3d", null),
                null, "");

        assertThat(action.run(task).ok()).isTrue();
        assertThat(ssh.calls.get(1).get(1)).isEqualTo("select-window");
        assertThat(reportedIds).isEmpty();
    }

    /// The belt is a *name* match, not a blanket amnesty: a window renamed to
    /// another task's title with a foreign id is still a theft.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aWindowNamedAfterAnotherTaskWithAForeignIdIsStillResurrected() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "26b2441d-85c8-4adc-a972-04d194bc9bdd|Other task\n", ""),
                new SshCommandRunner.SshResult(0, "@260\n", ""),
                new SshCommandRunner.SshResult(0, "", ""),
                new SshCommandRunner.SshResult(0, "", ""));
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> { });
        Task task = new Task("t", "Port PR 629", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("0", "@53"), null, null, null,
                new Task.ClaudeConfig("/home/o", "3e35d374-6ad4-4411-bfed-5b3995dcac3d", null),
                null, "");

        assertThat(action.run(task).detail())
                .startsWith("@53 belonged to another session; resurrected as @260");
    }

    /// A failed ownership round-trip is silence, not an accusation: resurrecting
    /// on it would duplicate a live window every time the link hiccups.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void anUnansweredOwnershipCheckStillFocuses() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(255, "", "ssh: connect failed"),
                new SshCommandRunner.SshResult(0, "", ""));
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> { });
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("0", "@53"), null, null, null,
                new Task.ClaudeConfig("/home/o", "abc-123", null), null, "");

        assertThat(action.run(task).ok()).isTrue();
        assertThat(ssh.calls.get(1).get(1)).isEqualTo("select-window");
    }

    /// A `window:` given as a *name* resolves to any same-named window on the
    /// server, so it says nothing about ownership — no round-trip is spent.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aWindowGivenByNameIsNotOwnershipChecked() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "", ""));
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> { });
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                new Task.TmuxConfig("0", "claude"), null, null, null,
                new Task.ClaudeConfig("/home/o", "abc-123", null), null, "");

        assertThat(action.run(task).ok()).isTrue();
        assertThat(ssh.calls).hasSize(1);
        assertThat(ssh.calls.getFirst().get(1)).isEqualTo("select-window");
    }

    // [utest->dsn~tmux-resurrect~7]
    @Test
    void windowlessTaskWithClaudeResurrectsInsteadOfFocusingSession() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "@25\n", ""),
                new SshCommandRunner.SshResult(0, "", ""),
                new SshCommandRunner.SshResult(0, "", ""));
        List<String> reportedIds = new ArrayList<>();
        TmuxFocusAction action = new TmuxFocusAction(ssh, new TmuxResurrect(ssh),
                (task, newWindowId) -> reportedIds.add(newWindowId));
        Task task = new Task("t", "T", TaskStatus.SUSPENDED, "h",
                new Task.TmuxConfig("2", null), null, null, null,
                new Task.ClaudeConfig("/home/o", "abc-123", null), null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isTrue();
        assertThat(ssh.calls.get(0).get(1)).isEqualTo("new-window");
        assertThat(reportedIds).containsExactly("@25");
    }
}
