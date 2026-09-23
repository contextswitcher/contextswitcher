package com.contextswitcher.terminal;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~wsl-sessions~1]
class WslTmuxRunnerTest {

    /// The words reach the distribution exactly as the remote shell would
    /// have received them: `\;` stays an escaped separator and the single
    /// quotes stay on the tmux formats, so no command builder needs a WSL
    /// dialect.
    @Test
    void joinsTheCommandTheWayTheRemoteShellWouldHaveSeenIt() {
        assertThat(WslTmuxRunner.argv("wsl:Ubuntu",
                List.of("tmux", "select-window", "-t", "0:@1",
                        "\\;", "display-message", "-p", "'#{window_id}'")))
                .containsExactly("wsl.exe", "-d", "Ubuntu", "--", "sh", "-lc",
                        WslTmuxRunner.PATH_PREFIX
                                + "tmux select-window -t 0:@1 \\; display-message -p '#{window_id}'");
    }

    /// `wsl:` with no distribution is the one `wsl.exe` starts by default —
    /// naming none is how that is said, so no `-d` is passed at all.
    @Test
    void aHostWithoutADistributionRunsTheDefaultOne() {
        assertThat(WslTmuxRunner.argv("wsl:", List.of("tmux", "-V")))
                .containsExactly("wsl.exe", "--", "sh", "-lc", WslTmuxRunner.PATH_PREFIX + "tmux -V");
    }

    /// A login shell with `~/.local/bin` on `PATH`: the tools live inside the
    /// distribution and have to be found the way the tmux window finds them.
    @Test
    void usesALoginShellThatSeesTheNativeInstallersDirectory() {
        List<String> argv = WslTmuxRunner.argv("wsl:Debian", List.of("claude", "--version"));
        assertThat(argv).contains("-lc");
        assertThat(argv.getLast()).startsWith("PATH=$PATH:$HOME/.local/bin; ");
    }

    /// Off Windows nothing is spawned: `wsl.exe` is guarded like `powershell`,
    /// so a stray poll reports a reason instead of filling the log with
    /// `Cannot run program`.
    @Test
    void offWindowsTheCommandIsRefusedRatherThanAttempted() {
        SshCommandRunner.SshResult result =
                new WslTmuxRunner().run("wsl:Ubuntu", List.of("tmux", "-V"));
        if (!com.contextswitcher.local.LocalCommandRunner.onWindows()) {
            assertThat(result.ok()).isFalse();
            assertThat(result.stderr()).contains("Windows-only");
        }
    }
}
