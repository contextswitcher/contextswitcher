package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import com.contextswitcher.terminal.TmuxHost;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-suspend~6]
class TmuxKillActionTest {

    /// Scripted ssh runner: pops the given results, records every argv.
    private static final class FakeSsh implements SshCommandRunner {
        final List<List<String>> calls = new ArrayList<>();
        final List<SshResult> results;

        FakeSsh(SshResult... scripted) {
            this.results = new ArrayList<>(List.of(scripted));
        }

        final List<String> hosts = new ArrayList<>();

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            hosts.add(host);
            calls.add(remoteCommand);
            return results.removeFirst();
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    }

    private static Task task(Task.TmuxConfig tmux) {
        return new Task("t", "T", TaskStatus.SUSPENDED, "devbox",
                tmux, null, null, null, null, null, "");
    }

    @Test
    void windowConfigKillsWindowById() {
        assertThat(TmuxKillAction.remoteCommand(new Task.TmuxConfig("jabref", "@17")))
                .containsExactly("tmux", "kill-window", "-t", "@17");
    }

    @Test
    void namedWindowKillsSessionScopedWindow() {
        assertThat(TmuxKillAction.remoteCommand(new Task.TmuxConfig("jabref", "claude")))
                .containsExactly("tmux", "kill-window", "-t", "jabref:claude");
    }

    @Test
    void sessionOnlyConfigKillsSession() {
        assertThat(TmuxKillAction.remoteCommand(new Task.TmuxConfig("jabref", null)))
                .containsExactly("tmux", "kill-session", "-t", "jabref");
    }

    @Test
    void killSucceeds() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "", ""));

        ActionResult result = new TmuxKillAction(ssh).run(task(new Task.TmuxConfig("jabref", "@17")));

        assertThat(result.ok()).isTrue();
        assertThat(ssh.calls.getFirst()).containsExactly("tmux", "kill-window", "-t", "@17");
    }

    /// A local Claude session's task has a `tmux:` section and no `remote:`
    /// — its window is ended in this machine's own tmux server, addressed by
    /// the pseudo-host the runner routes locally. Without this the window of
    /// every completed or suspended local task stayed alive.
    // [utest->dsn~terminal-local-mirror~2]
    @Test
    void aLocalSessionsWindowIsEndedOnThisMachine() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "", ""));
        Task local = new Task("t", "T", TaskStatus.SUSPENDED, null,
                new Task.TmuxConfig("0", "@17"), null, null, null, null, null, "");

        TmuxKillAction action = new TmuxKillAction(ssh);
        assertThat(action.isConfigured(local)).isTrue();
        ActionResult result = action.run(local);

        assertThat(result.ok()).isTrue();
        assertThat(ssh.hosts).containsOnly(TmuxHost.LOCAL);
        assertThat(ssh.calls.getFirst()).containsExactly("tmux", "kill-window", "-t", "@17");
    }

    @Test
    void alreadyGoneWindowCountsAsSuccess() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(1, "", "can't find window: @17\n"));

        ActionResult result = new TmuxKillAction(ssh).run(task(new Task.TmuxConfig("jabref", "@17")));

        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("already gone");
    }

    @Test
    void deadServerCountsAsSuccess() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(1, "",
                "no server running on /tmp/tmux-1000/default\n"));

        ActionResult result = new TmuxKillAction(ssh).run(task(new Task.TmuxConfig("jabref", "@17")));

        assertThat(result.ok()).isTrue();
    }

    private static Task claudeTask(Task.TmuxConfig tmux, String sessionId) {
        return new Task("t", "T", TaskStatus.SUSPENDED, "devbox", tmux, null, null, null,
                new Task.ClaudeConfig("/home/o", sessionId, null), null, "");
    }

    /// The one irreversible outcome on this path: ending a window someone else
    /// is working in. A focus can be taken back, a kill cannot.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aWindowHostingAnotherClaudeSessionIsNotKilled() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "26b2441d-85c8-4adc-a972-04d194bc9bdd\n", ""));

        ActionResult result = new TmuxKillAction(ssh)
                .run(claudeTask(new Task.TmuxConfig("0", "@53"), "3e35d374-6ad4-4411-bfed-5b3995dcac3d"));

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo(
                "@53 now hosts Claude session 26b2441d-85c8-4adc-a972-04d194bc9bdd"
                        + " — not ending another task's window");
        assertThat(ssh.calls).hasSize(1);
        assertThat(ssh.calls.getFirst()).containsExactly(
                "tmux", "display-message", "-p", "-t", "'@53'",
                "'#{@cs_session_id}|#{window_name}'");
    }

    /// The focus path's window-name belt deliberately does **not** apply here:
    /// a task file copied to start a new task carries the old title along with
    /// the old window id, and this is the path whose mistake cannot be undone.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aMatchingWindowNameDoesNotAuthoriseTheKill() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "26b2441d-85c8-4adc-a972-04d194bc9bdd|T\n", ""));

        ActionResult result = new TmuxKillAction(ssh)
                .run(claudeTask(new Task.TmuxConfig("0", "@53"), "3e35d374-6ad4-4411-bfed-5b3995dcac3d"));

        assertThat(result.ok()).isFalse();
        assertThat(ssh.calls).hasSize(1);
    }

    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void aWindowPublishingTheTasksOwnSessionIsKilled() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "abc-123\n", ""),
                new SshCommandRunner.SshResult(0, "", ""));

        ActionResult result = new TmuxKillAction(ssh)
                .run(claudeTask(new Task.TmuxConfig("0", "@53"), "abc-123"));

        assertThat(result.ok()).isTrue();
        assertThat(ssh.calls.get(1)).containsExactly("tmux", "kill-window", "-t", "@53");
    }

    /// An unreachable host is not a theft — refusing on it would leave windows
    /// behind on every hiccup, and the kill itself then reports the real error.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void anUnansweredOwnershipCheckStillKills() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(255, "", "ssh: connect failed"),
                new SshCommandRunner.SshResult(0, "", ""));

        ActionResult result = new TmuxKillAction(ssh)
                .run(claudeTask(new Task.TmuxConfig("0", "@53"), "abc-123"));

        assertThat(result.ok()).isTrue();
        assertThat(ssh.calls.get(1)).containsExactly("tmux", "kill-window", "-t", "@53");
    }

    @Test
    void otherFailuresReportStderr() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(255, "", "ssh: connect refused\n"));

        ActionResult result = new TmuxKillAction(ssh).run(task(new Task.TmuxConfig("jabref", "@17")));

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("connect refused");
    }
}
