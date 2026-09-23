package com.contextswitcher.ui;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.terminal.PtyTtyConnector;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import javafx.scene.control.Button;
import org.jspecify.annotations.Nullable;

/// What the terminal pane shows live, and everything that depends on which
/// kind it is: a tmux mirror of a window on a host ([TmuxSession]) or a
/// Claude session the app hosts itself ([OwnedSession]). The pane holds at
/// most one and asks it, instead of checking which mode it is in.
// [impl->dsn~terminal-pane~14]
// [impl->dsn~terminal-owned-session~3]
sealed interface TerminalSession permits TmuxSession, OwnedSession {

    /// Where "Copy reply" and the Markdown selection copy read Claude's
    /// transcript: the host it lives on, the recorded session and its id.
    record ReplySource(String host, Task.ClaudeConfig claude, String sessionId) {
    }

    /// The terminal on screen; null while none is up (an attach in flight, a
    /// mirror that gave up reconnecting).
    @Nullable JediTermFxWidget widget();

    /// **Clear input**: discards the draft in the chat's input box.
    void clearInput();

    /// **Accept suggestion**: takes Claude's greyed-out prompt suggestion and sends it.
    void acceptSuggestion();

    /// **Ctrl+C**: interrupts what the session is doing.
    void interrupt();

    /// **Jump to bottom**: back to the live output after scrolling up.
    void jumpToBottom();

    /// **Show diff**: the workspace's changes.
    void showDiff();

    /// **Files**: the files the Claude sessions generated, in a menu above `source`.
    void showFiles(Button source);

    /// Whether Show diff and Files have something to reach.
    boolean offersDiffAndFiles();

    /// The status poll's key for the session's window; null when it publishes none.
    @Nullable String statusKey();

    /// The transcript behind the session; null when none is known.
    @Nullable ReplySource replySource();

    /// The host a clicked path that is not on this machine is fetched from;
    /// null when there is none to fetch from.
    @Nullable String downloadHost();

    /// **Select the mirrored task**.
    void selectShownTask();

    /// A live theme switch: re-colors the terminal where the kind allows it.
    void retheme();

    /// The pane stops showing the session.
    void detach();

    /// What a session may do to the pane showing it.
    interface View {

        /// A status line in place of the terminal — connecting, reconnecting,
        /// given up.
        void showStatus(String text);

        void showTerminal(JediTermFxWidget terminal);

        /// Ends the session with a placeholder saying `text`
        /// ([TerminalPane#showMessage]).
        void end(String text);

        /// The terminal's application title in the pane's band.
        void setTitle(String text);

        /// The "Switching…" cover over the terminal while `window` comes up.
        void cover(@Nullable String window);

        void uncover();

        /// Lifts the blank a click put up ([TerminalPane#blankThen]), when one is up.
        void liftBlank();

        /// Forgets a focus request meant for an earlier attach.
        void clearFocusRequest();

        /// Hands a newly attached terminal the keyboard when it was asked for
        /// or nothing holds it.
        void focusNewTerminal(JediTermFxWidget terminal);

        void focusTerminal();

        /// A started terminal on `connector` with the pane's link filters and
        /// input fixes; `mirror` matches selections against the transcript and
        /// reports the wheel to tmux instead of scrolling the local scrollback.
        JediTermFxWidget liveWidget(PtyTtyConnector connector, boolean mirror);

        void openDiff(String remote, Task.TmuxConfig tmux);

        void openOwnedDiff(String taskId);

        void showRemoteFiles(Button source, String remote);

        void showLocalFiles(Button source);

        /// The Claude session recorded for `tmux`'s window; null when none.
        Task.@Nullable ClaudeConfig claudeOf(String remote, Task.TmuxConfig tmux);

        /// Selects the task that owns `window` on `remote`.
        void selectTaskOfWindow(String remote, String window);
    }
}
