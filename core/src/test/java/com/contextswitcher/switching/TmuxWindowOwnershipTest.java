package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~tmux-window-ownership~4]
class TmuxWindowOwnershipTest {

    @Test
    void commandPrintsTheWindowsPublishedSessionId() {
        assertThat(TmuxWindowOwnership.command("@53")).containsExactly(
                "tmux", "display-message", "-p", "-t", "'@53'",
                "'#{@cs_session_id}|#{window_name}'");
    }

    @Test
    void aDifferentPublishedIdIsAHijack() {
        assertThat(TmuxWindowOwnership.hijacked("3e35d374", "26b2441d")).isTrue();
    }

    @Test
    void theTasksOwnIdIsNotAHijackWhitespaceIncluded() {
        assertThat(TmuxWindowOwnership.hijacked("abc-123", "abc-123\n")).isFalse();
    }

    /// The three shapes of "no evidence" — a window whose `SessionStart` hook
    /// never ran, a task without a recorded session, an unanswered read. Each
    /// must leave the caller behaving exactly as it did before the check.
    @Test
    void missingEvidenceIsNeverAHijack() {
        assertThat(TmuxWindowOwnership.hijacked("abc-123", "  ")).isFalse();
        assertThat(TmuxWindowOwnership.hijacked("abc-123", null)).isFalse();
        assertThat(TmuxWindowOwnership.hijacked(null, "26b2441d")).isFalse();
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

    private static Task.ClaudeConfig claude(String sessionId) {
        return new Task.ClaudeConfig("/home/o", sessionId, null);
    }

    @Test
    void foreignOwnerNamesTheOtherSession() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "26b2441d|Other task\n", ""));

        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "@53"), claude("3e35d374"), "Port PR 629"))
                .isEqualTo("26b2441d");
    }

    /// The belt: a window still carrying the task's title is the task's own,
    /// whatever id a stray publication left on it (field report 2026-09-09).
    @Test
    void aWindowStillNamedAfterTheTaskIsNotForeign() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "26b2441d|Port PR 629\n", ""));

        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "@53"), claude("3e35d374"), "Port PR 629"))
                .isNull();
    }

    /// A title may contain the separator, a session id may not — so the
    /// **first** `|` splits and the whole rest is the name.
    @Test
    void aTitleContainingTheSeparatorStillMatches() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "26b2441d|Port a|b\n", ""));

        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "@53"), claude("3e35d374"), "Port a|b"))
                .isNull();
    }

    /// A null name switches the belt off — what the kill path passes, where a
    /// copied task file's stale title must not authorise ending the original's
    /// window.
    @Test
    void withoutANameTheIdRemainsTheSoleEvidence() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "26b2441d|Port PR 629\n", ""));

        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "@53"), claude("3e35d374"), null))
                .isEqualTo("26b2441d");
    }

    /// A window that never published a name (no `|` in the output at all, an
    /// older remote) leaves the id as the only evidence rather than matching an
    /// empty name against an empty title.
    @Test
    void aWindowWithoutAPublishedNameIsJudgedByItsIdAlone() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "26b2441d\n", ""));

        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "@53"), claude("3e35d374"), ""))
                .isEqualTo("26b2441d");
    }

    /// The cases that must not cost a round-trip at all — the check runs on
    /// every switch and every suspend.
    @Test
    void nothingToCompareAsksNothing() {
        FakeSsh ssh = new FakeSsh();

        // A window given as a name resolves to any same-named window.
        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "claude"), claude("abc"), "T")).isNull();
        // No window at all.
        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", null), claude("abc"), "T")).isNull();
        // No claude section, and a claude section without a session id.
        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "@53"), null, "T")).isNull();
        assertThat(TmuxWindowOwnership.foreignOwner(
                ssh, "h", new Task.TmuxConfig("0", "@53"), claude(null), "T")).isNull();
        assertThat(ssh.calls).isEmpty();
    }
}
