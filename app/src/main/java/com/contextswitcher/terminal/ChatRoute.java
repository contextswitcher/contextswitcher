package com.contextswitcher.terminal;

import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;

/// How a task's chat is reached — decided once, here, rather than by every
/// caller asking [TmuxHost#of] and [LocalClaudeLauncher#ownsSession] itself.
/// A tmux window, on a remote or in this machine's own tmux server
/// (`dsn~terminal-local-mirror~2`), is typed into over tmux; on Windows the
/// session the app hosts in the terminal pane is typed into directly
/// (`dsn~terminal-owned-session~3`); any other task has no chat to reach.
// [impl->dsn~terminal-owned-session~3]
public sealed interface ChatRoute {

    /// A tmux window on `host`.
    record Tmux(String host, Task.TmuxConfig tmux) implements ChatRoute {
    }

    /// The session the app hosts itself for the task `taskId`.
    record Owned(String taskId) implements ChatRoute {
    }

    /// No chat to send to.
    record None() implements ChatRoute {
    }

    static ChatRoute of(@Nullable Task task) {
        if (task == null) {
            return new None();
        }
        Task.TmuxConfig tmux = task.tmux();
        String host = TmuxHost.of(task);
        if (tmux != null && host != null) {
            return new Tmux(host, tmux);
        }
        return LocalClaudeLauncher.ownsSession(task) ? new Owned(task.id()) : new None();
    }

    /// Whether a message can be delivered to the chat now.
    default boolean canSend() {
        return !(this instanceof None);
    }

    /// Whether a message can wait for the chat to fall idle. That signal is the
    /// status poll's, read from tmux options — an app-hosted session publishes
    /// none, so it can be sent to but not armed.
    default boolean canDelay() {
        return this instanceof Tmux;
    }
}
