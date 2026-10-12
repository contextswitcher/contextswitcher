package com.contextswitcher.switching;

import java.nio.file.Path;
import java.util.List;

import com.contextswitcher.queue.MessageSender;
import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A failed window creation hands the remote's error to the caller, so the
/// main window can show it instead of "(see log)".
// [utest->dsn~task-create-progress~6]
class ClaudeWindowLauncherFailureTest {

    private static final SshCommandRunner REFUSING = new SshCommandRunner() {
        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            return new SshResult(1, "", "no server running on /tmp/tmux-1000/default\n");
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    };

    @Test
    void launchReportsRemoteError() {
        ClaudeWindowLauncher launcher = new ClaudeWindowLauncher(REFUSING,
                new MessageSender(REFUSING, Path.of("unused")), false);

        assertThat(launcher.launch("me@host", "0", "title", "prompt")).isNull();
        assertThat(launcher.failure()).isEqualTo("no server running on /tmp/tmux-1000/default");
    }

}
