package com.contextswitcher.terminal;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The routing that makes a WSL session a remote everywhere but the
/// transport: `HostCommandRunner` picks by host, and nothing else has to
/// know WSL exists.
// [utest->dsn~wsl-sessions~1]
class WslHostRoutingTest {

    /// Records which runner was asked, so the routing itself is what is
    /// asserted rather than any command's output.
    private record Recorder(String name, List<String> seen) implements SshCommandRunner {

        Recorder(String name) {
            this(name, new ArrayList<>());
        }

        @Override
        public SshResult run(String host, List<String> command) {
            seen.add(host);
            return new SshResult(0, name, "");
        }

        @Override
        public SshResult runWithInput(String host, List<String> command, byte[] input) {
            return run(host, command);
        }
    }

    private static final List<String> ANY = List.of("tmux", "-V");

    @Test
    void aWslHostGoesThroughWslAndNothingElseDoes() {
        Recorder ssh = new Recorder("ssh");
        Recorder local = new Recorder("local");
        Recorder wsl = new Recorder("wsl");
        HostCommandRunner runner = new HostCommandRunner(ssh, local, wsl);

        assertThat(runner.run("wsl:Ubuntu", ANY).stdout()).isEqualTo("wsl");
        assertThat(runner.run("wsl:", ANY).stdout()).isEqualTo("wsl");
        assertThat(runner.run(TmuxHost.LOCAL, ANY).stdout()).isEqualTo("local");
        assertThat(runner.run("devbox", ANY).stdout()).isEqualTo("ssh");
        // A real host whose name merely contains the marker is still ssh's:
        // the prefix anchors at the start for exactly this reason.
        assertThat(runner.run("build-wsl:9", ANY).stdout()).isEqualTo("ssh");

        assertThat(wsl.seen()).containsExactly("wsl:Ubuntu", "wsl:");
        assertThat(ssh.seen()).containsExactly("devbox", "build-wsl:9");
    }

    /// The host string is what the distribution name round-trips through, so
    /// the wizard's answer and the config's `remote:` are the same thing.
    @Test
    void theHostStringCarriesTheDistribution() {
        assertThat(TmuxHost.wslHost("Ubuntu-24.04")).isEqualTo("wsl:Ubuntu-24.04");
        assertThat(TmuxHost.distro(TmuxHost.wslHost("Ubuntu-24.04"))).isEqualTo("Ubuntu-24.04");
        assertThat(TmuxHost.distro(TmuxHost.wslHost(""))).isEmpty();
        assertThat(TmuxHost.isWsl("wsl:Ubuntu")).isTrue();
        assertThat(TmuxHost.isWsl(TmuxHost.LOCAL)).isFalse();
        assertThat(TmuxHost.isWsl("devbox")).isFalse();
    }

    /// The mirror is the one tmux command that builds its own argv; a WSL
    /// host has to get the same words, without ssh's keepalives.
    @Test
    void theMirrorAttachesThroughWslWithoutSshOptions() {
        List<String> argv = TmuxMirrorCommands.attachCommand("wsl:Ubuntu",
                new com.contextswitcher.tasks.Task.TmuxConfig("0", "@1"));
        assertThat(argv).startsWith("wsl.exe", "-d", "Ubuntu", "--", "sh", "-lc");
        assertThat(argv.getLast()).contains("tmux new-session -A -s cs-mirror-0 -t 0");
        assertThat(argv).doesNotContain("ServerAliveInterval", "-t");
    }
}
