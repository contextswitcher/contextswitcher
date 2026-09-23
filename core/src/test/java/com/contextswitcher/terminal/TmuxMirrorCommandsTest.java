package com.contextswitcher.terminal;

import com.contextswitcher.ssh.ProcessSshRunner;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-pane~14]
// [utest->dsn~terminal-mouse-scroll~9]
// [utest->dsn~terminal-jump-to-bottom~1]
// [utest->dsn~terminal-clear-input~3]
// [utest->dsn~ssh-command-runner~6]
// [utest->dsn~terminal-local-mirror~2]
// [utest->dsn~terminal-mirrored-task~1]
class TmuxMirrorCommandsTest {

    private static final Task.TmuxConfig TMUX = new Task.TmuxConfig("0", "@5");

    /// Wheel goes to the application only in copy-mode, or when mouse
    /// reporting is on **outside** a mirror session — verified against tmux
    /// 3.3a: in a `cs-mirror-*` session it evaluates to 0 even with
    /// `mouse_any_flag` set, elsewhere it matches tmux's stock condition.
    private static final String WHEEL_COND =
            "#{||:#{pane_in_mode},#{&&:#{mouse_any_flag},#{==:#{m:cs-mirror-*,#{session_name}},0}}}";

    /// The base session is created when it is gone, so the moves land — and
    /// the mirror is never killed: tmux destroys the session it empties, so a
    /// kill could only fire after failed moves, taking the live Claude
    /// windows the mirror was the last link to (field report 2026-09-12).
    @Test
    void repairStrandsOnlyALoneMirrorAndReportsWhetherTheWindowExists() {
        String repair = TmuxMirrorCommands.repairMirrorCommand(TMUX).getFirst();
        assertThat(repair).isEqualTo("m=cs-mirror-0; "
                + "if tmux has-session -t $m 2>/dev/null "
                + "&& [ $(tmux display-message -p -t $m '#{session_group_size}') -lt 2 ]; then "
                + "tmux has-session -t '0' 2>/dev/null || tmux new-session -d -s '0'; "
                + "for o in $(tmux list-windows -t $m -F '#{window_id}'); do "
                + "tmux move-window -d -s $o -t '0:'; done; "
                + "fi; "
                + "tmux list-windows -a -F '#{window_id}' | grep -qx '@5' && echo ok || echo gone");
        assertThat(repair).doesNotContain("kill-session");
    }

    /// Without a recorded window there is nothing to look for, so the repair
    /// reports `ok` and the attach proceeds.
    @Test
    void repairReportsOkWhenTheTaskPinsNoWindow() {
        assertThat(TmuxMirrorCommands.repairMirrorCommand(new Task.TmuxConfig("0", null)).getFirst())
                .endsWith("fi; echo ok");
    }

    /// "Whose terminal is this?" is asked of the mirror session, never of the
    /// base session (whose current window is the human user's) and never of a
    /// window id the pane already believes — that belief is what is under
    /// suspicion.
    @Test
    void currentWindowAsksTheMirrorSessionWhatItShows() {
        assertThat(TmuxMirrorCommands.currentWindowCommand(TMUX))
                .containsExactly("tmux", "display-message", "-p", "-t", "cs-mirror-0",
                        "'#{window_id}'");
        assertThat(TmuxMirrorCommands.selectedWindow("@7\n")).isEqualTo("@7");
        // A tmux that printed nothing cannot be quoted as an answer.
        assertThat(TmuxMirrorCommands.selectedWindow("  \n")).isNull();
    }

    @Test
    void windowGoneOnlyForTheGoneAnswer() {
        assertThat(TmuxMirrorCommands.windowGone(new SshCommandRunner.SshResult(0, "gone\n", "")))
                .isTrue();
        assertThat(TmuxMirrorCommands.windowGone(new SshCommandRunner.SshResult(0, "ok\n", "")))
                .isFalse();
        // A failed round-trip is not evidence the window is gone.
        assertThat(TmuxMirrorCommands.windowGone(new SshCommandRunner.SshResult(255, "gone", "")))
                .isFalse();
    }

    @Test
    void attachUsesAGroupedMirrorSessionAndSelectsTheWindowThere() {
        assertThat(TmuxMirrorCommands.attachCommand("koppor@devbox", TMUX)).containsExactly(
                ProcessSshRunner.sshExecutable(), "-t",
                "-o", "ServerAliveInterval=5",
                "-o", "ServerAliveCountMax=3",
                "-o", "ConnectTimeout=10",
                "koppor@devbox",
                "tmux", "new-session", "-A", "-s", "cs-mirror-0", "-t", "0",
                "\\;", "set-option", "-t", "cs-mirror-0", "mouse", "on",
                "\\;", "bind-key", "-T", "root", "WheelUpPane",
                "if-shell", "-F", "'" + WHEEL_COND + "'", "'send-keys -M'", "'copy-mode -e'",
                "\\;", "bind-key", "-T", "root", "WheelDownPane",
                "if-shell", "-F", "'" + WHEEL_COND + "'", "'send-keys -M'",
                "\\;", "select-window", "-t", "cs-mirror-0:@5");
    }

    @Test
    void attachWithoutWindowOmitsTheSelect() {
        assertThat(TmuxMirrorCommands.attachCommand("h", new Task.TmuxConfig("jabref", null)))
                .containsExactly(ProcessSshRunner.sshExecutable(), "-t",
                        "-o", "ServerAliveInterval=5",
                        "-o", "ServerAliveCountMax=3",
                        "-o", "ConnectTimeout=10",
                        "h",
                        "tmux", "new-session", "-A", "-s", "cs-mirror-jabref", "-t", "jabref",
                        "\\;", "set-option", "-t", "cs-mirror-jabref", "mouse", "on",
                        "\\;", "bind-key", "-T", "root", "WheelUpPane",
                        "if-shell", "-F", "'" + WHEEL_COND + "'", "'send-keys -M'", "'copy-mode -e'",
                        "\\;", "bind-key", "-T", "root", "WheelDownPane",
                        "if-shell", "-F", "'" + WHEEL_COND + "'", "'send-keys -M'");
    }

    /// The local mirror is the same tmux command line, handed to a local
    /// shell instead of to the remote's — so `\;` and the quoted formats
    /// mean the same thing on both routes.
    @Test
    void attachOfTheLocalHostGoesThroughAShellInsteadOfSsh() {
        assertThat(TmuxMirrorCommands.attachCommand(TmuxHost.LOCAL, TMUX))
                .hasSize(3)
                .startsWith(LocalTmuxRunner.SHELL, "-c");
        String script = TmuxMirrorCommands.attachCommand(TmuxHost.LOCAL, TMUX)
                .getLast();
        assertThat(script)
                .doesNotContain(ProcessSshRunner.sshExecutable() + " -t")
                .contains("tmux new-session -A -s cs-mirror-0 -t 0")
                .endsWith("\\; select-window -t cs-mirror-0:@5");
    }

    @Test
    void selectWindowTargetsTheMirrorSessionNotTheBase() {
        assertThat(TmuxMirrorCommands.selectWindowCommand(TMUX))
                .containsExactly("tmux", "select-window", "-t", "cs-mirror-0:@5",
                        "\\;", "display-message", "-p", "-t", "cs-mirror-0", "'#{window_id}'");
    }

    /// The select says which window the mirror really ended on, so a select
    /// that "succeeds" onto another task's window is caught; an answerless
    /// tmux reads as "cannot tell", never as a mismatch.
    @Test
    void theSelectReportsTheWindowItLandedOn() {
        assertThat(TmuxMirrorCommands.selectedWindow("@5\n")).isEqualTo("@5");
        assertThat(TmuxMirrorCommands.selectedWindow("  ")).isNull();
    }

    /// Targets the mirror *session* — its current window is what the pane
    /// shows, and a task may pin no window at all.
    @Test
    void cancelCopyModeTargetsTheMirrorSessionsCurrentWindow() {
        assertThat(TmuxMirrorCommands.cancelCopyModeCommand(TMUX))
                .containsExactly("tmux", "send-keys", "-t", "cs-mirror-0", "-X", "cancel");
        assertThat(TmuxMirrorCommands.cancelCopyModeCommand(new Task.TmuxConfig("0", null)))
                .containsExactly("tmux", "send-keys", "-t", "cs-mirror-0", "-X", "cancel");
    }

    /// Leaves copy-mode (`-q`: silent no-op outside it) in the **same** tmux
    /// invocation before the kills — a `^U` reaching a copy-mode pane scrolls
    /// instead of clearing. Session-addressed like the cancel command.
    @Test
    void clearInputLeavesCopyModeThenKillsEveryLine() {
        assertThat(TmuxMirrorCommands.clearInputCommand(TMUX))
                .startsWith("tmux", "copy-mode", "-q", "-t", "cs-mirror-0",
                        "\\;", "send-keys", "-t", "cs-mirror-0", "C-u")
                .endsWith("C-u")
                .hasSize(9 + TmuxMirrorCommands.CLEAR_INPUT_KILLS);
    }

    // [utest->dsn~terminal-interrupt~1]
    @Test
    void interruptLeavesCopyModeThenSendsOneCtrlCToTheMirror() {
        assertThat(TmuxMirrorCommands.interruptCommand(TMUX)).containsExactly(
                "tmux", "copy-mode", "-q", "-t", "cs-mirror-0",
                "\\;", "send-keys", "-t", "cs-mirror-0", "C-c");
    }

    // [utest->dsn~android-terminal-interrupt~1]
    @Test
    void interruptCanAimAtAWindowDirectly() {
        assertThat(TmuxMirrorCommands.interruptCommand("'@17'")).containsExactly(
                "tmux", "copy-mode", "-q", "-t", "'@17'", "\\;", "send-keys", "-t", "'@17'", "C-c");
    }

    // [utest->dsn~android-terminal-keys~1]
    @Test
    void keyLeavesCopyModeThenPressesThatKeyInTheWindow() {
        assertThat(TmuxMirrorCommands.keyCommand("'@17'", "Space")).containsExactly(
                "tmux", "copy-mode", "-q", "-t", "'@17'", "\\;", "send-keys", "-t", "'@17'", "Space");
    }

    // [utest->dsn~terminal-accept-suggestion~1]
    @Test
    void acceptSuggestionLeavesCopyModeThenTabsAndSubmitsLater() {
        assertThat(TmuxMirrorCommands.acceptSuggestionCommand(TMUX)).containsExactly(
                "tmux", "copy-mode", "-q", "-t", "cs-mirror-0",
                "\\;", "send-keys", "-t", "cs-mirror-0", "Tab",
                "\\;", "run-shell", "'sleep 1'",
                "\\;", "send-keys", "-t", "cs-mirror-0", "Enter");
    }

    @Test
    void connectionKeyIsRemotePlusBaseSession() {
        assertThat(TmuxMirrorCommands.connectionKey("h", TMUX)).isEqualTo("h 0");
    }

    @Test
    void changedWindowIsNullForSameOrAbsentWindow() {
        assertThat(TmuxMirrorCommands.changedWindow("@5", TMUX)).isNull();
        assertThat(TmuxMirrorCommands.changedWindow("@4", TMUX)).isEqualTo("@5");
        assertThat(TmuxMirrorCommands.changedWindow(null, new Task.TmuxConfig("0", null))).isNull();
    }

    @Test
    void mirrorNameIsSanitized() {
        assertThat(TmuxMirrorCommands.mirrorName("my session")).isEqualTo("cs-mirror-my_session");
    }
}
