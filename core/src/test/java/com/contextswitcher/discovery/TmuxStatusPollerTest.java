package com.contextswitcher.discovery;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.contextswitcher.queue.ClaudeMode;
import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

// [utest->dsn~task-running-indicator~7]
class TmuxStatusPollerTest {

    @Test
    void statusCommandComputesIdleSecondsOnTheRemote() {
        assertThat(TmuxStatusPoller.statusCommand()).singleElement().asString()
                .startsWith("now=$(date +%s); "
                        + "tmux list-windows -a "
                        + "-F '#{window_id}|#{@cs_status}|#{window_activity}|#{@cs_model}|#{@cs_effort}"
                        + "|#{@cs_session_id}|#{pane_current_command}' "
                        + "| awk -F'|' -v n=$now 'BEGIN{OFS=FS} {print $1,$2,n-$3,$4,$5,$6,$7}'");
    }

    /// The pane grep only visits windows that publish a status, and the command
    /// still exits 0 when the last of them does not match.
    // [utest->dsn~claude-limit-detection~2]
    @Test
    void statusCommandGrepsClaudeWindowPanesForTheLimitMessage() {
        assertThat(TmuxStatusPoller.statusCommand()).singleElement().asString()
                .contains("for w in $(tmux list-windows -a -F '#{?@cs_status,#{window_id},}')")
                .contains("tmux capture-pane -p -J -S -12 -t $w")
                .contains("grep -qE 'limit reached|reached your .{0,40}limit' && echo 'limit|'$w")
                .endsWith("; true");
    }

    /// Java's Windows argv encoding does not escape embedded double quotes, so
    /// ssh.exe would swallow them and the remote shell would see a different
    /// command — the poller command must stay double-quote-free.
    @Test
    void statusCommandContainsNoDoubleQuotes() {
        assertThat(TmuxStatusPoller.statusCommand()).allSatisfy(part ->
                assertThat(part).doesNotContain("\""));
    }

    @Test
    void parseKeepsOnlyWindowsWithANonEmptyStatusKeyedByHost() {
        String output = """
                @3|working|2
                @7|waiting|40
                @9||5
                @11|done|300
                """;

        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseInto("devbox", output, out);

        assertThat(out).containsOnly(
                entry("devbox @3", "working"),
                entry("devbox @7", "waiting"),
                entry("devbox @11", "done"));
    }

    // [utest->dsn~android-finish-notification~1]
    @Test
    void callerChosenIdleThresholdKeepsAQuietWindowWorking() {
        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseInto("h", "@1|working|60\n@2|working|130\n@3|waiting|500\n", out, 120);

        assertThat(out).containsEntry("h @1", "working")
                .containsEntry("h @2", "waiting")
                .containsEntry("h @3", "waiting");
    }

    @Test
    void staleWorkingIsReportedAsWaiting() {
        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseInto("h", "@1|working|120\n@2|working|3\n", out);

        assertThat(out).containsOnly(entry("h @1", "waiting"), entry("h @2", "working"));
    }

    // [utest->dsn~auto-suspend-idle~2]
    @Test
    void parseIdleReportsOnlyStatusPublishingWindows() {
        Map<String, Long> out = new HashMap<>();
        TmuxStatusPoller.parseInto("h", "", new HashMap<>());
        TmuxStatusPoller.parseIdleInto("h", "@1|waiting|3700||\n@2||99999||\n@3|working|12|opus|high|abc\nlimit|@1\n", out);
        assertThat(out).containsOnly(entry("h @1", 3700L), entry("h @3", 12L));
    }

    @Test
    void effectiveStatusOnlyDowngradesStaleWorking() {
        assertThat(TmuxStatusPoller.effectiveStatus("working", 5)).isEqualTo("working");
        assertThat(TmuxStatusPoller.effectiveStatus("working", 90)).isEqualTo("waiting");
        assertThat(TmuxStatusPoller.effectiveStatus("waiting", 500)).isEqualTo("waiting");
        assertThat(TmuxStatusPoller.effectiveStatus("done", 500)).isEqualTo("done");
    }

    // [utest->dsn~claude-mode-report~2]
    @Test
    void parseModesKeepsOnlyWindowsThatReportOne() {
        String output = """
                @3|working|2|Opus|high
                @7|waiting|9||max
                @9|waiting|9|Sonnet|
                @11|waiting|9||
                """;

        Map<String, ClaudeMode> out = new HashMap<>();
        TmuxStatusPoller.parseModesInto("h", output, out);

        assertThat(out).containsOnly(
                entry("h @3", new ClaudeMode("Opus", "high")),
                entry("h @7", new ClaudeMode(null, "max")),
                entry("h @9", new ClaudeMode("Sonnet", null)));
    }

    /// A window whose tmux predates the two options (or a status-only line)
    /// reports no mode rather than a half-parsed one.
    // [utest->dsn~claude-mode-report~2]
    @Test
    void parseModesSkipsLinesWithoutTheModeFields() {
        Map<String, ClaudeMode> out = new HashMap<>();
        TmuxStatusPoller.parseModesInto("h", "@1|working|3\n", out);

        assertThat(out).isEmpty();
    }

    /// The session id rides the same poll so the mirror can check window
    /// ownership without a round-trip. A window that publishes none is absent
    /// rather than empty — a lookup miss is "no evidence", not "no session".
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void parseSessionIdsKeepsOnlyWindowsThatPublishOne() {
        String output = """
                @53|waiting|9|Opus|high|26b2441d-85c8-4adc-a972-04d194bc9bdd
                @54|waiting|9|Opus|high|
                @55|waiting|9|Opus|high
                @56|working|2||
                """;

        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseSessionIdsInto("devbox", output, out);

        assertThat(out).containsOnly(
                entry("devbox @53", "26b2441d-85c8-4adc-a972-04d194bc9bdd"));
    }

    /// The sixth field must not bleed into the fifth: the split limit grew with
    /// the format, so `effort` stays `effort` instead of `effort|<session id>`.
    // [utest->dsn~claude-mode-report~2]
    @Test
    void parseModesIgnoresTheTrailingSessionId() {
        Map<String, ClaudeMode> out = new HashMap<>();
        TmuxStatusPoller.parseModesInto("h", "@3|working|2|Opus|high|26b2441d\n", out);

        assertThat(out).containsOnly(entry("h @3", new ClaudeMode("Opus", "high")));
    }

    /// The marker lines override the polled status — except for a window that
    /// is working again, where the message is stale scrollback.
    // [utest->dsn~claude-limit-detection~2]
    @Test
    void limitMarkersOverrideTheStatusUnlessTheWindowWorks() {
        String output = """
                @3|waiting|20|Opus|high
                @5|working|2|Opus|high
                @7|waiting|20|Opus|high
                limit|@3
                limit|@5
                """;

        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseInto("h", output, out);

        assertThat(out).containsOnly(
                entry("h @3", "limit"),
                entry("h @5", "working"),
                entry("h @7", "waiting"));
    }

    /// The pane's tail is grepped for Claude's API errors, on the same pass
    /// as the limit message.
    // [utest->dsn~api-error-auto-continue~1]
    @Test
    void statusCommandGrepsThePaneTailForApiErrors() {
        assertThat(TmuxStatusPoller.statusCommand()).singleElement().asString()
                .contains("tmux capture-pane -p -J -t $w 2>/dev/null | grep -v '^ *$' | tail -8 "
                        + "| grep -qE 'API Error' && echo 'apierror|'$w");
    }

    /// `continue` is typed literally and submitted a second later — an Enter
    /// in the same burst is absorbed as a newline.
    // [utest->dsn~api-error-auto-continue~1]
    @Test
    void continueCommandTypesTheWordAndSubmitsItLater() {
        assertThat(TmuxStatusPoller.continueCommand("@7")).containsExactly(
                "tmux", "send-keys", "-l", "-t", "'@7'", "continue", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'@7'", "Enter");
    }

    /// One nudge per error: repeated while the pane still shows it, but not
    /// before the cooldown, not into a working window, and again right away
    /// once the window has worked in between.
    // [utest->dsn~api-error-auto-continue~1]
    @Test
    void autoContinueNudgesAnIdleErroredWindowOnce() {
        List<String> sent = new ArrayList<>();
        SshCommandRunner ssh = new SshCommandRunner() {
            @Override
            public SshResult run(String host, List<String> remoteCommand) {
                sent.add(host + " " + String.join(" ", remoteCommand));
                return new SshResult(0, "", "");
            }

            @Override
            public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
                return run(host, remoteCommand);
            }
        };
        TmuxStatusPoller poller = new TmuxStatusPoller(ssh, Set::of, m -> { }, m -> { }, m -> { },
                m -> { }, m -> { }, false, 5);
        String stdout = "apierror|@3\napierror|@5\n";
        Map<String, String> statuses = new HashMap<>(
                Map.of("h @3", "waiting", "h @5", "working"));

        poller.autoContinue("h", stdout, statuses);
        assertThat(sent).hasSize(1).allSatisfy(command ->
                assertThat(command).startsWith("h tmux send-keys -l -t '@3' continue"));

        // Still erroring, still idle: the cooldown holds the next nudge back.
        poller.autoContinue("h", stdout, statuses);
        assertThat(sent).hasSize(1);

        // The window worked in between — a fresh error is nudged at once.
        poller.autoContinue("h", stdout, new HashMap<>(Map.of("h @3", "working", "h @5", "working")));
        poller.autoContinue("h", stdout, statuses);
        assertThat(sent).hasSize(2);
    }

    /// Only the footer — the bottom non-blank lines — is grepped for Claude's
    /// pending self-update, on the same pass as the limit message: a diff on
    /// screen quoting "Restart to update" must not restart the session.
    // [utest->dsn~claude-update-restart~6]
    @Test
    void statusCommandGrepsTheFooterForAPendingUpdate() {
        assertThat(TmuxStatusPoller.statusCommand()).singleElement().asString()
                .contains("tmux capture-pane -p -J -t $w 2>/dev/null | grep -v '^ *$' | tail -4 "
                        + "| grep -qE 'Restart to update' && echo 'update|'$w");
    }

    /// Only a finished turn is restarted: mid-answer, a question on screen or
    /// a usage limit all keep the session as it is.
    // [utest->dsn~claude-update-restart~6]
    @Test
    void onlyAFinishedTurnIsRestartable() {
        assertThat(TmuxStatusPoller.restartable("waiting")).isTrue();
        assertThat(TmuxStatusPoller.restartable("done")).isTrue();
        assertThat(TmuxStatusPoller.restartable("working")).isFalse();
        assertThat(TmuxStatusPoller.restartable("attention")).isFalse();
        assertThat(TmuxStatusPoller.restartable(TmuxStatusPoller.LIMIT)).isFalse();
        assertThat(TmuxStatusPoller.restartable(null)).isFalse();
    }

    /// Exit and resume are two separate commands: `/exit` cleared of any
    /// draft and submitted a second later, then one more `Enter` for the
    /// "Background work is running" question a session with background
    /// shells asks (its preselected choice is *Exit and stop tasks*); the
    /// resume typed at the shell later, with the auto flag typed back only
    /// when it was set.
    // [utest->dsn~claude-update-restart~6]
    @Test
    void exitAndResumeCommandsAreSeparate() {
        assertThat(TmuxStatusPoller.exitCommand("@7")).containsExactly(
                "tmux", "send-keys", "-t", "'@7'", "Escape", "\\;",
                "send-keys", "-l", "-t", "'@7'", "/exit", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'@7'", "Enter", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'@7'", "Enter");
        assertThat(TmuxStatusPoller.resumeCommand("@7", "s-1", false)).containsExactly(
                "tmux", "send-keys", "-t", "'@7'", "'claude --resume s-1 || claude --continue'", "Enter");
        assertThat(TmuxStatusPoller.resumeCommand("@7", "s-1", true)).contains(
                "'claude --resume s-1 --dangerously-skip-permissions"
                        + " || claude --continue --dangerously-skip-permissions'");
    }

    /// The window's **own** conversation comes back, not whatever ran last in
    /// its directory: tasks share a Claude cwd (a category's workspaces root
    /// with a worktree per task), where `--continue` alone resumed a
    /// neighbouring task's chat. A window that published no id has nothing to
    /// resume by and falls back to `--continue`.
    // [utest->dsn~claude-update-restart~6]
    @Test
    void resumeCommandResumesBySessionIdRatherThanByDirectory() {
        assertThat(String.join(" ", TmuxStatusPoller.resumeCommand("@7", "s-1", false)))
                .contains("claude --resume s-1");
        assertThat(String.join(" ", TmuxStatusPoller.resumeCommand("@7", null, false)))
                .doesNotContain("--resume")
                .endsWith("'claude --continue' Enter");
        assertThat(String.join(" ", TmuxStatusPoller.resumeCommand("@7", "  ", false)))
                .doesNotContain("--resume")
                .endsWith("'claude --continue' Enter");
    }

    private static TmuxStatusPoller pollerRecording(List<String> sent) {
        SshCommandRunner ssh = new SshCommandRunner() {
            @Override
            public SshResult run(String host, List<String> remoteCommand) {
                sent.add(host + " " + String.join(" ", remoteCommand));
                return new SshResult(0, "", "");
            }

            @Override
            public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
                return run(host, remoteCommand);
            }
        };
        return new TmuxStatusPoller(ssh, Set::of, m -> { }, m -> { }, m -> { },
                m -> { }, m -> { }, true, 5);
    }

    /// One `/exit` per pending update: not into a working window, and not
    /// again before the cooldown — then, in the tick that sees the pane at a
    /// shell, the resume on the window's own session.
    // [utest->dsn~claude-update-restart~6]
    @Test
    void autoRestartQuitsAnIdleUpdatedWindowThenResumesOnceItIsAtAShell() {
        List<String> sent = new ArrayList<>();
        TmuxStatusPoller poller = pollerRecording(sent);
        String stdout = "update|@3\nupdate|@5\n";
        Map<String, String> statuses = new HashMap<>(
                Map.of("h @3", "waiting", "h @5", "working"));
        // The same tick's published session ids — @3 comes back on its own.
        Map<String, String> sessionIds = Map.of("h @3", "s-3", "h @5", "s-5");
        Map<String, String> running = Map.of("h @3", "claude", "h @5", "claude");

        poller.autoRestart("h", stdout, statuses, sessionIds, running);
        assertThat(sent).hasSize(1).allSatisfy(command -> assertThat(command)
                .startsWith("h tmux send-keys -t '@3' Escape")
                .contains("/exit")
                .doesNotContain("claude --resume"));

        // Claude is gone from @3: the resume follows, once.
        poller.autoRestart("h", stdout, statuses, sessionIds, Map.of("h @3", "bash", "h @5", "claude"));
        assertThat(sent).hasSize(2);
        assertThat(sent.getLast()).isEqualTo("h tmux send-keys -t '@3'"
                + " 'claude --resume s-3 --dangerously-skip-permissions"
                + " || claude --continue --dangerously-skip-permissions' Enter");

        // The message is still on the footer while the fresh Claude comes up.
        poller.autoRestart("h", stdout, statuses, sessionIds, running);
        assertThat(sent).hasSize(2);
    }

    /// A window whose `/exit` did not take — its pane still runs `claude` a
    /// tick later — gets no resume typed into its input box, and after
    /// `MAX_RESTART_ATTEMPTS` no further `/exit` either (field report
    /// 2026-09-14: a dozen `/exit`s and the resume line submitted as prompts).
    /// A window shown by an attached client — the app's mirror, the user's own
    /// terminal — is not restarted: the user may be typing there. Once no
    /// client shows it, the next tick restarts it.
    // [utest->dsn~claude-update-restart~6]
    @Test
    void autoRestartWaitsUntilNoClientShowsTheWindow() {
        List<String> sent = new ArrayList<>();
        TmuxStatusPoller poller = pollerRecording(sent);
        Map<String, String> statuses = new HashMap<>(Map.of("h @3", "waiting"));
        Map<String, String> sessionIds = Map.of("h @3", "s-3");
        Map<String, String> running = Map.of("h @3", "claude");

        poller.autoRestart("h", "update|@3\nviewed|@3\n", statuses, sessionIds, running);
        assertThat(sent).isEmpty();

        poller.autoRestart("h", "update|@3\nviewed|@9\n", statuses, sessionIds, running);
        assertThat(sent).singleElement().asString().contains("/exit");
    }

    @Test
    void statusCommandListsTheWindowsClientsShow() {
        assertThat(TmuxStatusPoller.statusCommand()).singleElement().asString()
                .contains("tmux list-clients -F 'viewed|#{window_id}'");
    }

    // [utest->dsn~claude-update-restart~6]
    @Test
    void autoRestartNeverResumesIntoARunningClaudeAndGivesUp() throws Exception {
        List<String> sent = new ArrayList<>();
        TmuxStatusPoller poller = pollerRecording(sent);
        String stdout = "update|@3\n";
        Map<String, String> statuses = Map.of("h @3", "done");
        Map<String, String> sessionIds = Map.of("h @3", "s-3");
        Map<String, String> running = Map.of("h @3", "claude");

        poller.autoRestart("h", stdout, statuses, sessionIds, running);
        poller.autoRestart("h", stdout, statuses, sessionIds, running);
        assertThat(sent).hasSize(1);

        // Cooldown over, still running: one more /exit, then none.
        Field restarted = TmuxStatusPoller.class.getDeclaredField("restarted");
        restarted.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Long> lastSent = (Map<String, Long>) restarted.get(poller);
        for (int round = 0; round < 3; round++) {
            lastSent.put("h @3", 0L);
            poller.autoRestart("h", stdout, statuses, sessionIds, running);
            poller.autoRestart("h", stdout, statuses, sessionIds, running);
        }
        assertThat(sent).hasSize(TmuxStatusPoller.MAX_RESTART_ATTEMPTS)
                .allSatisfy(command -> assertThat(command).doesNotContain("claude --resume"));

        // The footer's message gone (a manual restart): the next update starts afresh.
        poller.autoRestart("h", "", statuses, sessionIds, running);
        lastSent.put("h @3", 0L);
        poller.autoRestart("h", stdout, statuses, sessionIds, running);
        assertThat(sent).hasSize(TmuxStatusPoller.MAX_RESTART_ATTEMPTS + 1);
    }

    @Test
    void parseSkipsMalformedLines() {
        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseInto("h", "@1\n\n@2|working|3\n", out);

        assertThat(out).containsExactly(entry("h @2", "working"));
    }

    /// The pane command rides the same line, so the button that repairs a
    /// window without Claude costs no round-trip of its own. A window that
    /// reports none has no entry — absence is "did not answer", not "shell".
    // [utest->dsn~start-claude-button~2]
    @Test
    void parseCommandsKeepsTheReportedPaneCommandPerWindow() {
        String output = """
                @3|working|2|opus|high|abc|claude
                @7|waiting|40|||def|bash
                @9||5||||
                limit|@3
                """;

        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseCommandsInto("devbox", output, out);

        assertThat(out).containsOnly(
                entry("devbox @3", "claude"),
                entry("devbox @7", "bash"));
    }

    /// The session id is the sixth of seven fields now — it must not swallow
    /// the pane command that follows it.
    // [utest->dsn~tmux-window-ownership~4]
    @Test
    void parseSessionIdsStopsBeforeThePaneCommand() {
        Map<String, String> out = new HashMap<>();
        TmuxStatusPoller.parseSessionIdsInto("h", "@3|working|2|opus|high|abc|claude\n", out);

        assertThat(out).containsOnly(entry("h @3", "abc"));
    }
}
