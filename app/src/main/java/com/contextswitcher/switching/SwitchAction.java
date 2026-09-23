package com.contextswitcher.switching;

import com.contextswitcher.tasks.Task;

/// One independent step of a context switch. Actions run only when the task
/// configures them and never depend on each other.
// [impl->dsn~switch-orchestrator~3]
public interface SwitchAction {

    /// Short name shown on the status chip (e.g. "tmux").
    String name();

    boolean isConfigured(Task task);

    /// Runs synchronously; the orchestrator provides the background thread.
    ActionResult run(Task task);
}
