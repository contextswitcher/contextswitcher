package com.contextswitcher.switching;

/// Lifecycle of one action during a switch, as shown on its status chip.
// [impl->dsn~switch-orchestrator~3]
public enum ActionStatus {
    PENDING,
    RUNNING,
    OK,
    FAILED
}
