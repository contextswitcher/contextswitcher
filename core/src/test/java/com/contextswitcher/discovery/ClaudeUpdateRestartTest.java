package com.contextswitcher.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

// [utest->dsn~android-claude-restart~1]
class ClaudeUpdateRestartTest {

    private static final String HOST = "koppor@devbox";

    /// Answers by what is asked: the window line, the process arguments, and a
    /// scripted sequence of pane commands; records everything sent.
    private static final class Remote implements SshCommandRunner {
        final List<List<String>> sent = new ArrayList<>();
        final String window;
        final String args;
        final Deque<String> paneCommands;

        Remote(String window, String args, String... paneCommands) {
            this.window = window;
            this.args = args;
            this.paneCommands = new ArrayDeque<>(List.of(paneCommands));
        }

        @Override
        public SshResult run(String host, List<String> command) {
            sent.add(command);
            String joined = String.join(" ", command);
            if (joined.contains("#{@cs_status}")) {
                return new SshResult(0, window + "\n", "");
            }
            if (joined.contains("ps -o args=")) {
                return new SshResult(0, args, "");
            }
            if (joined.contains("'#{pane_current_command}'")) {
                return new SshResult(0, (paneCommands.size() > 1 ? paneCommands.poll() : paneCommands.peek()) + "\n", "");
            }
            return new SshResult(0, "", "");
        }

        @Override
        public SshResult runWithInput(String host, List<String> command, byte[] input) {
            return run(host, command);
        }
    }

    @Test
    void anIdleSessionQuitsAndResumesItsOwnConversationWithItsFlags() {
        Remote remote = new Remote("waiting|abc-123|claude", "bash\nclaude --dangerously-skip-permissions\n",
                "claude", "claude", "bash");

        var outcome = ClaudeUpdateRestart.restart(remote, HOST, "@17", millis -> { });

        assertThat(outcome).isEqualTo(new ClaudeUpdateRestart.Outcome.Restarted("abc-123"));
        assertThat(remote.sent).contains(TmuxStatusPoller.exitCommand("@17"));
        assertThat(remote.sent.getLast()).isEqualTo(TmuxStatusPoller.resumeCommand("@17", "abc-123", true));
    }

    @Test
    void aWorkingSessionIsNotTouched() {
        Remote remote = new Remote("working|abc-123|claude", "claude", "claude");

        var outcome = ClaudeUpdateRestart.restart(remote, HOST, "@17", millis -> { });

        assertThat(outcome).isEqualTo(new ClaudeUpdateRestart.Outcome.NotIdle("working"));
        assertThat(remote.sent).doesNotContain(TmuxStatusPoller.exitCommand("@17"));
    }

    @Test
    void aClaudeThatDoesNotQuitGetsNoResumeTypedIntoIt() {
        Remote remote = new Remote("done||claude", "claude", "claude");

        var outcome = ClaudeUpdateRestart.restart(remote, HOST, "@17", millis -> { });

        assertThat(outcome).isEqualTo(new ClaudeUpdateRestart.Outcome.DidNotQuit());
        assertThat(remote.sent).noneMatch(command -> String.join(" ", command).contains("--resume")
                || String.join(" ", command).contains("--continue"));
    }

    @Test
    void withoutASessionIdAndWithoutTheFlagItContinues() {
        Remote remote = new Remote("waiting||claude", "claude\n", "bash");

        var outcome = ClaudeUpdateRestart.restart(remote, HOST, "jabref:claude", millis -> { });

        assertThat(outcome).isEqualTo(new ClaudeUpdateRestart.Outcome.Restarted(null));
        assertThat(remote.sent.getLast()).isEqualTo(TmuxStatusPoller.resumeCommand("jabref:claude", null, false));
    }

    // [utest->dsn~android-claude-restart~1]
    @Test
    void theFooterRuleLooksAtTheBottomLinesOnly() {
        String footer = "\u001B[2m  Update installed \u00B7 Restart to update\u001B[0m";
        assertThat(TmuxStatusPoller.updatePendingIn("answer\n\n> \n" + footer + "\n\n\n")).isTrue();
        assertThat(TmuxStatusPoller.updatePendingIn("a diff quoting Restart to update\n1\n2\n3\n4\n5\n")).isFalse();
        assertThat(TmuxStatusPoller.updatePendingIn("")).isFalse();
    }
}
