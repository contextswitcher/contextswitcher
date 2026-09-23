package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~tmux-resurrect~7]
class TmuxResurrectTest {

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            Fix NPE,        "'Fix NPE'"
            "Don't crash",  "'Don'\\''t crash'"
            """)
    void shellQuoteEscapesEmbeddedSingleQuotes(String value, String expected) {
        assertThat(SshCommandRunner.quote(value)).isEqualTo(expected);
    }

    @Test
    void renameCommandQuotesTargetAndName() {
        assertThat(TmuxResurrect.renameWindowCommand(new Task.TmuxConfig("2", "@17"), "Fix NPE"))
                .containsExactly("tmux", "rename-window", "-t", "'@17'", "'Fix NPE'");
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

    private static final Task.TmuxConfig TMUX = new Task.TmuxConfig("2", "@99");
    private static final Task.ClaudeConfig CLAUDE =
            new Task.ClaudeConfig("/home/o/jabref", "abc-123", null);

    @Test
    void commandsQuoteTargetsAndPrintWindowId() {
        assertThat(ClaudeWindowLauncher.newWindowCommand("2", "/home/o/x", "Fix NPE")).containsExactly(
                "tmux", "new-window", "-t", "'2:'", "-c", "'/home/o/x'", "-n", "'Fix NPE'",
                "-P", "-F", "'#{window_id}'");
        assertThat(ClaudeWindowLauncher.newSessionCommand("2", "/home/o/x", "Fix NPE")).containsExactly(
                "tmux", "new-session", "-d", "-s", "'2'", "-c", "'/home/o/x'", "-n", "'Fix NPE'",
                "-P", "-F", "'#{window_id}'");
        assertThat(TmuxResurrect.resumeCommand("@25", "abc", false)).containsExactly(
                "tmux", "send-keys", "-t", "'@25'",
                "'claude --resume abc || claude'", "Enter");
        // [utest->dsn~claude-auto-permissions~1]
        assertThat(TmuxResurrect.resumeCommand("@25", "abc", true)).containsExactly(
                "tmux", "send-keys", "-t", "'@25'",
                "'claude --resume abc --dangerously-skip-permissions"
                        + " || claude --dangerously-skip-permissions'", "Enter");
    }

    @Test
    void resurrectsIntoExistingSessionAndResumes() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(0, "@25\n", ""),
                new SshCommandRunner.SshResult(0, "", ""));

        TmuxResurrect.Resurrected result = new TmuxResurrect(ssh).resurrect("h", TMUX, CLAUDE, "Fix NPE");

        assertThat(result).isEqualTo(new TmuxResurrect.Resurrected("@25", true));
        assertThat(ssh.calls.get(0).get(1)).isEqualTo("new-window");
        assertThat(ssh.calls.get(1).get(1)).isEqualTo("send-keys");
    }

    @Test
    void fallsBackToNewSessionAfterServerRestart() {
        FakeSsh ssh = new FakeSsh(
                new SshCommandRunner.SshResult(1, "", "can't find session"),
                new SshCommandRunner.SshResult(0, "@1\n", ""),
                new SshCommandRunner.SshResult(0, "", ""));

        TmuxResurrect.Resurrected result = new TmuxResurrect(ssh).resurrect("h", TMUX, CLAUDE, "Fix NPE");

        assertThat(result).isEqualTo(new TmuxResurrect.Resurrected("@1", true));
        assertThat(ssh.calls.get(1).get(1)).isEqualTo("new-session");
    }

    @Test
    void withoutSessionIdOnlyTheWindowIsRecreated() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "@7\n", ""));

        TmuxResurrect.Resurrected result = new TmuxResurrect(ssh)
                .resurrect("h", TMUX, new Task.ClaudeConfig("/home/o", null, null), "Fix NPE");

        assertThat(result).isEqualTo(new TmuxResurrect.Resurrected("@7", false));
        assertThat(ssh.calls).hasSize(1);
    }

    // A suspended task without a claude: section still gets a window back on
    // resume: created plainly, -c omitted, nothing resumed.
    @Test
    void withoutClaudeSectionAPlainWindowIsCreated() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "@8\n", ""));

        TmuxResurrect.Resurrected result = new TmuxResurrect(ssh)
                .resurrect("h", TMUX, null, "Fix NPE");

        assertThat(result).isEqualTo(new TmuxResurrect.Resurrected("@8", false));
        assertThat(ssh.calls.getFirst())
                .containsExactly("tmux", "new-window", "-t", "'2:'", "-n", "'Fix NPE'",
                        "-P", "-F", "'#{window_id}'");
    }

    /// The repair button types the resume line into the window the task
    /// already has — nothing is created.
    // [utest->dsn~start-claude-button~2]
    @Test
    void startClaudeResumesTheRecordedSessionInTheExistingWindow() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0, "", ""));

        assertThat(new TmuxResurrect(ssh).startClaude("h", "@7", "abc")).isTrue();
        assertThat(ssh.calls).singleElement().asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactly("tmux", "send-keys", "-t", "'@7'",
                        "'claude --resume abc || claude'", "Enter");
    }

    /// No recorded session id: a plain `claude`, with the auto flag when set.
    // [utest->dsn~start-claude-button~2]
    @Test
    void startClaudeWithoutASessionIdStartsAFreshOne() {
        assertThat(TmuxResurrect.startCommand("@7", null, true))
                .containsExactly("tmux", "send-keys", "-t", "'@7'",
                        "'claude --dangerously-skip-permissions'", "Enter");
    }
}
