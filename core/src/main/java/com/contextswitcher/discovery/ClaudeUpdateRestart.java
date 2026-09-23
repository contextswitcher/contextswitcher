package com.contextswitcher.discovery;

import java.util.List;
import java.util.function.LongConsumer;

import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;

/// Restarts one Claude session that shows "Restart to update", on request —
/// the phone's *Restart Claude* button. The same steps as the desktop poller's
/// automatic restart (`dsn~claude-update-restart~6`), built from its commands,
/// but in one blocking call instead of spread over poll ticks: only a session
/// whose turn is over is touched ([TmuxStatusPoller#restartable]), `/exit`
/// ([TmuxStatusPoller#exitCommand]), wait until the window no longer runs
/// `claude`, then resume the same conversation ([TmuxStatusPoller#resumeCommand]).
///
/// Unlike the desktop, the phone has no `claudeAuto` setting, so the running
/// Claude's own arguments decide whether the resumed one gets
/// `--dangerously-skip-permissions` back.
// [impl->dsn~android-claude-restart~1]
public final class ClaudeUpdateRestart {

    /// What a restart came to.
    public sealed interface Outcome {
        /// Back on the new version; `sessionId` null when the window published
        /// none and `claude --continue` took over.
        record Restarted(@Nullable String sessionId) implements Outcome {
        }

        /// The turn is not over (`working`, a question on screen, …): nothing sent.
        record NotIdle(@Nullable String status) implements Outcome {
        }

        /// `/exit` was sent but Claude still runs: no resume typed into it.
        record DidNotQuit() implements Outcome {
        }

        record Failed(String message) implements Outcome {
        }
    }

    /// How often, and how many times, the window is asked whether Claude has gone.
    static final long QUIT_POLL_MILLIS = 2_000;
    static final int QUIT_POLLS = 15;

    private ClaudeUpdateRestart() {
    }

    /// `target` is the window as tmux addresses it (`@17`, `session:name`);
    /// `sleep` waits the given milliseconds between the quit checks.
    public static Outcome restart(SshCommandRunner ssh, String host, String target, LongConsumer sleep) {
        SshCommandRunner.SshResult window = ssh.run(host, windowCommand(target));
        if (!window.ok()) {
            return new Outcome.Failed(reason("Cannot read the window", window));
        }
        String[] parts = window.stdout().strip().split("\\|", -1);
        String status = parts.length > 0 && !parts[0].isBlank() ? parts[0] : null;
        String sessionId = parts.length > 1 && !parts[1].isBlank() ? parts[1] : null;
        if (!TmuxStatusPoller.restartable(status)) {
            return new Outcome.NotIdle(status);
        }
        boolean auto = ssh.run(host, argsCommand(target)).stdout().contains("--dangerously-skip-permissions");
        SshCommandRunner.SshResult exit = ssh.run(host, TmuxStatusPoller.exitCommand(target));
        if (!exit.ok()) {
            return new Outcome.Failed(reason("Cannot send /exit", exit));
        }
        for (int poll = 0; poll < QUIT_POLLS; poll++) {
            sleep.accept(QUIT_POLL_MILLIS);
            SshCommandRunner.SshResult command = ssh.run(host, paneCommandCommand(target));
            if (command.ok() && !command.stdout().isBlank() && !"claude".equals(command.stdout().strip())) {
                SshCommandRunner.SshResult resumed = ssh.run(host, TmuxStatusPoller.resumeCommand(target, sessionId, auto));
                return resumed.ok()
                        ? new Outcome.Restarted(sessionId)
                        : new Outcome.Failed(reason("Claude quit but did not resume", resumed));
            }
        }
        return new Outcome.DidNotQuit();
    }

    /// `status|sessionId|command` of the window.
    static List<String> windowCommand(String target) {
        return List.of("tmux", "display-message", "-p", "-t", "'" + target + "'",
                "'#{@cs_status}|#{@cs_session_id}|#{pane_current_command}'");
    }

    /// The command lines of the pane's process and its children — Claude runs
    /// as the pane's process or under the shell that started it. Linux `ps`.
    static List<String> argsCommand(String target) {
        return List.of("P=$(tmux", "display-message", "-p", "-t", "'" + target + "'", "'#{pane_pid}');",
                "ps", "-o", "args=", "-p", "$P", "--ppid", "$P");
    }

    static List<String> paneCommandCommand(String target) {
        return List.of("tmux", "display-message", "-p", "-t", "'" + target + "'", "'#{pane_current_command}'");
    }

    private static String reason(String what, SshCommandRunner.SshResult result) {
        String stderr = result.stderr().strip();
        return what + ": " + (stderr.isEmpty() ? "exit " + result.exitCode() : stderr);
    }
}
