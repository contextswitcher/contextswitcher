package com.contextswitcher;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.discovery.ClaudePrLookup;
import com.contextswitcher.discovery.ClaudeSessionLookup;
import com.contextswitcher.discovery.TmuxDiscovery;
import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Field report 2026-09-12: the periodic reconcile opened one ssh connection
/// per Claude window to scrape the PR footer. With ten windows that is eleven
/// connections a tick through the six global slots of
/// `dsn~ssh-command-runner~6` — invisible at one tick a minute, and eleven
/// minutes of saturation once a woken laptop replayed a backlog of ticks
/// (`dsn~poll-no-wake-backlog~1`). The batch was already there for the footer
/// poll; this pins that the reconcile takes it.
// [utest->dsn~claude-pr-capture~2]
class EnrichClaudeSessionsTest {

    private static final String FOOTER = "… · PR: https://github.com/o/r/pull/%d\n";

    /// Records every remote command it is given and answers a batch capture
    /// with one footer section per window it was asked about.
    private static final class RecordingSsh implements SshCommandRunner {

        private final List<List<String>> runs = new ArrayList<>();

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            runs.add(remoteCommand);
            StringBuilder out = new StringBuilder();
            int pr = 1;
            for (String part : remoteCommand) {
                if (part.startsWith("'@") || part.startsWith("@")) {
                    String window = part.replace("'", "");
                    out.append("===contextswitcher-pane ").append(window).append("===\n")
                            .append(FOOTER.formatted(pr++));
                }
            }
            return new SshResult(0, out.toString(), "");
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    }

    @Test
    void everyWindowsFooterIsScrapedInOneSshCall() {
        RecordingSsh ssh = new RecordingSsh();
        List<TmuxDiscovery.TmuxSession> enriched = Main.enrichClaudeSessions("host",
                List.of(session("a", window("@1"), window("@2")), session("b", window("@3"))),
                new ClaudeSessionLookup(ssh), new ClaudePrLookup(ssh));

        assertThat(ssh.runs).hasSize(1);
        assertThat(ssh.runs.getFirst()).contains("'@1'", "'@2'", "'@3'");
        assertThat(enriched.stream().flatMap(s -> s.windows().stream())
                .map(TmuxDiscovery.TmuxWindow::prUrl))
                .containsExactly("https://github.com/o/r/pull/1",
                        "https://github.com/o/r/pull/2",
                        "https://github.com/o/r/pull/3");
    }

    /// A window that already published `@cs_pr`, and one running something
    /// other than Claude, stay out of the batch — and a run with nothing to
    /// scrape costs no connection at all.
    @Test
    void onlyPrLessClaudeWindowsAreAsked() {
        RecordingSsh ssh = new RecordingSsh();
        TmuxDiscovery.TmuxWindow published = new TmuxDiscovery.TmuxWindow("@1", "0", "", "claude",
                "w", "t", "", "", "sid", "https://github.com/o/r/pull/9");
        TmuxDiscovery.TmuxWindow shell = new TmuxDiscovery.TmuxWindow("@2", "1", "", "bash",
                "w", "t", "", "", "sid", null);
        Main.enrichClaudeSessions("host", List.of(session("a", published, shell)),
                new ClaudeSessionLookup(ssh), new ClaudePrLookup(ssh));

        assertThat(ssh.runs).isEmpty();
    }

    private static TmuxDiscovery.TmuxSession session(String name,
            TmuxDiscovery.TmuxWindow... windows) {
        return new TmuxDiscovery.TmuxSession(name, List.of(windows));
    }

    /// A Claude window with no PR yet — and a session id, so the transcript
    /// heuristic (one ssh call of its own) stays out of the count.
    private static TmuxDiscovery.TmuxWindow window(String id) {
        return new TmuxDiscovery.TmuxWindow(id, "0", "/w", "claude", "name", "title", "", "",
                "session-" + id, null);
    }
}
