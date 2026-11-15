package com.contextswitcher.switching;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.terminal.WindowsTerminalFocus;

/// Focuses the task's local Windows Terminal tab (the local alternative to the
/// remote `tmux` action) by its fixed tab title, via UI Automation; a tab
/// that is gone (after a reboot) is reopened in the task's first `folders` entry.
// [impl->dsn~local-terminal-focus~7]
public class LocalTerminalAction implements SwitchAction {

    private final WindowsTerminalFocus focus;

    public LocalTerminalAction(WindowsTerminalFocus focus) {
        this.focus = focus;
    }

    @Override
    public String name() {
        return "terminal";
    }

    @Override
    public boolean isConfigured(Task task) {
        return task.terminal() != null;
    }

    @Override
    public ActionResult run(Task task) {
        Task.TerminalConfig terminal = task.terminal();
        if (terminal == null) {
            return ActionResult.failure("terminal not configured");
        }
        WindowsTerminalFocus.FocusResult result = focus.focus(terminal.tabTitle(),
                task.folders().isEmpty() ? null : task.folders().getFirst());
        return result.ok() ? ActionResult.success(result.detail()) : ActionResult.failure(result.detail());
    }
}
