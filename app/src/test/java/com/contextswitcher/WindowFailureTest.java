package com.contextswitcher;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.switching.TmuxResurrect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Field report 2026-10-12: a failed task creation said only "Cannot create a
/// tmux window on koppor@nixos (see log)." — the cause belongs in the main
/// window, not only in the log.
// [utest->dsn~task-create-progress~6]
class WindowFailureTest {

    @Test
    void namesRemoteError() {
        assertThat(Main.windowFailure("me@host", "  can't find session: 0\n"))
                .isEqualTo("Cannot create a tmux window on me@host:\ncan't find session: 0");
    }

    @Test
    void pointsAtLogWithoutRemoteError() {
        assertThat(Main.windowFailure("me@host", " ")).isEqualTo("Cannot create a tmux window on me@host (see log).");
        assertThat(Main.windowFailure("me@host", null)).isEqualTo("Cannot create a tmux window on me@host (see log).");
    }

    @Test
    void createWindowReportsRemoteError() {
        SshCommandRunner refusing = new SshCommandRunner() {
            @Override
            public SshResult run(String host, List<String> remoteCommand) {
                return new SshResult(1, "", "no server running on /tmp/tmux-1000/default\n");
            }

            @Override
            public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
                return run(host, remoteCommand);
            }
        };
        TmuxResurrect resurrect = new TmuxResurrect(refusing);

        assertThat(resurrect.createWindow("me@host", "0", null, "title")).isNull();
        assertThat(resurrect.failure()).isEqualTo("no server running on /tmp/tmux-1000/default");
    }
}
