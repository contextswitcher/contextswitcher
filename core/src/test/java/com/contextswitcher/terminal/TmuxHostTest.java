package com.contextswitcher.terminal;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-local-mirror~2]
class TmuxHostTest {

    private static Task task(@Nullable String remote, Task.@Nullable TmuxConfig tmux) {
        return new Task("t", "T", TaskStatus.ACTIVE, remote, tmux,
                null, null, null, null, null, "");
    }

    @Test
    void aTaskWithoutTmuxHasNoHost() {
        assertThat(TmuxHost.of(task("devbox", null))).isNull();
    }

    @Test
    void tmuxWithoutARemoteIsThisMachine() {
        assertThat(TmuxHost.of(task(null, new Task.TmuxConfig("0", "@1"))))
                .isEqualTo(TmuxHost.LOCAL);
    }

    @Test
    void tmuxWithARemoteIsThatRemote() {
        assertThat(TmuxHost.of(task("devbox", new Task.TmuxConfig("0", "@1"))))
                .isEqualTo("devbox");
    }

    @Test
    void onlyTheLocalPseudoHostIsLocal() {
        assertThat(TmuxHost.isLocal(TmuxHost.LOCAL)).isTrue();
        assertThat(TmuxHost.isLocal("localhost")).isFalse();
        assertThat(TmuxHost.isLocal("koppor@devbox")).isFalse();
    }
}
