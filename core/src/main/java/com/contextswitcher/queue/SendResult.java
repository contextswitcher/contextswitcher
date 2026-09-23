package com.contextswitcher.queue;

import org.jspecify.annotations.Nullable;

/// What a send to a task's chat came to: delivered, or failed with a reason
/// short enough for the queue pane's status line.
public sealed interface SendResult {

    record Sent() implements SendResult {
    }

    record Failed(String reason) implements SendResult {
    }

    /// The result of a call that reports "null on success, else the reason" —
    /// the contract [MessageSender#send] still has.
    static SendResult of(@Nullable String error) {
        return error == null ? new Sent() : new Failed(error);
    }
}
