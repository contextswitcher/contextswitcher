package com.contextswitcher.terminal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import com.contextswitcher.local.RequiredTools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-local-mirror~2]
class LocalTmuxRunnerTest {

    /// The words go through the local shell exactly as the remote shell would
    /// have received them: `\;` stays an escaped separator, single quotes stay
    /// on the tmux formats.
    @Test
    void joinsTheCommandTheWayTheRemoteShellWouldHaveSeenIt() {
        assertThat(LocalTmuxRunner.script(
                List.of("tmux", "send-keys", "-t", "cs-mirror-0", "-X", "cancel")))
                .endsWith("tmux send-keys -t cs-mirror-0 -X cancel");
    }

    /// A GUI-launched app may not have tmux on its `PATH`; the script names
    /// tmux in several places, so its directory is prepended once — and the
    /// command itself is untouched either way.
    @Test
    void prependsTmuxOwnDirectoryToPathWhenItCanBeFound() {
        String script = LocalTmuxRunner.script(List.of("tmux", "select-window"));
        assertThat(script).endsWith("tmux select-window");
        Path tmux = RequiredTools.find("tmux");
        if (tmux == null || tmux.getParent() == null) {
            assertThat(script).isEqualTo("tmux select-window");
        } else {
            assertThat(script)
                    .isEqualTo("PATH=\"" + tmux.getParent() + ":$PATH\"; tmux select-window");
        }
    }

    /// The queue's delivery pipes the message on stdin (`tmux load-buffer -`),
    /// so the local route has to pipe it too — `cat` stands in for tmux here.
    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "runs the command through a POSIX shell")
    void pipesStdinIntoTheCommand() {
        assertThat(new LocalTmuxRunner().runWithInput(TmuxHost.LOCAL, List.of("cat"),
                "hello\nthere".getBytes(StandardCharsets.UTF_8)).stdout())
                .isEqualTo("hello\nthere");
    }

    /// Commands start where a fresh ssh exec channel starts — the home
    /// directory — which is what the relative attachment drop path assumes.
    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "runs the command through a POSIX shell")
    void runsFromTheHomeDirectoryLikeAnSshExecChannel() throws Exception {
        // Compared as real paths: a home directory reached through a symlink
        // (`/home/x` → `/export/home/x`) makes `pwd` and `user.home` two
        // spellings of the same directory.
        Path pwd = Path.of(new LocalTmuxRunner().run(TmuxHost.LOCAL, List.of("pwd")).stdout().strip());
        assertThat(pwd.toRealPath())
                .isEqualTo(Path.of(System.getProperty("user.home")).toRealPath());
    }
}
