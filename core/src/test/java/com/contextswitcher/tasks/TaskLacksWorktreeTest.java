package com.contextswitcher.tasks;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~open-in-intellij~3]
class TaskLacksWorktreeTest {

    private static Task task(Task.@Nullable IntellijConfig intellij,
            Task.@Nullable ClaudeConfig claude) {
        return new Task("t", "T", TaskStatus.ACTIVE, "h", null,
                null, intellij, null, claude, null, List.of(), List.of(), "");
    }

    @Test
    void sessionWithoutPublishedWorkspaceLacksWorktree() {
        assertThat(task(null, new Task.ClaudeConfig("/ws", "sid", null)).lacksWorktree()).isTrue();
        assertThat(task(null, new Task.ClaudeConfig("/ws", "sid", "/ws")).lacksWorktree()).isTrue();
    }

    @Test
    void publishedWorktreeOrExplicitPathOrNoSessionIsFine() {
        assertThat(task(null, new Task.ClaudeConfig("/ws", "sid", "/ws/wt")).lacksWorktree()).isFalse();
        assertThat(task(new Task.IntellijConfig("/ws/jabref", null, null),
                new Task.ClaudeConfig("/ws", "sid", null)).lacksWorktree()).isFalse();
        assertThat(task(null, null).lacksWorktree()).isFalse();
    }
}
