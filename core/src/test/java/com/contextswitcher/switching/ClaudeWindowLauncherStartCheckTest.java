package com.contextswitcher.switching;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.queue.MessageSender;
import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A Claude that quit right after its start gets no prompt: it would land
/// in the shell.
// [utest->dsn~claude-start-check~1]
class ClaudeWindowLauncherStartCheckTest {

    private static final class Fake implements SshCommandRunner {
        final String paneCommand;
        final List<List<String>> inputs = new ArrayList<>();

        Fake(String paneCommand) {
            this.paneCommand = paneCommand;
        }

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            String joined = String.join(" ", remoteCommand);
            if (joined.contains("new-window")) {
                return new SshResult(0, "@7\n", "");
            }
            if (joined.contains("pane_current_command")) {
                return new SshResult(0, paneCommand + "\n", "");
            }
            if (joined.contains("capture-pane")) {
                return new SshResult(0, "Fix the bug\n", "");
            }
            return new SshResult(0, "", "");
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            inputs.add(remoteCommand);
            return new SshResult(0, "", "");
        }
    }

    private static List<List<String>> sent(String paneCommand) {
        Fake ssh = new Fake(paneCommand);
        String window = new ClaudeWindowLauncher(ssh, new MessageSender(ssh, Path.of("unused")), false)
                .launch("me@host", "0", "title", "Fix the bug");
        assertThat(window).isEqualTo("@7");
        return ssh.inputs;
    }

    @Test
    void quitClaudeGetsNoPrompt() {
        assertThat(sent("bash")).isEmpty();
    }

    @Test
    void runningClaudeGetsThePrompt() {
        assertThat(sent("claude")).isNotEmpty();
    }
}
