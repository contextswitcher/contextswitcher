package com.contextswitcher.discovery;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~claude-pr-capture~2]
class ClaudePrLookupTest {

    @Test
    void everyLabeledFooterIsCaptured() {
        String pane = """
                some conversation mentioning https://github.com/other/repo/pull/1
                jabref · Worktree: ../a · Branch: b · PR: https://github.com/JabRef/jabref/pull/16287
                user-documentation · Worktree: ../a-docs · Branch: b-docs · PR: https://github.com/JabRef/user-documentation/pull/643
                """;

        assertThat(ClaudePrLookup.parsePrUrls(pane)).containsExactly(
                "https://github.com/JabRef/jabref/pull/16287",
                "https://github.com/JabRef/user-documentation/pull/643");
    }

    /// A redrawn TUI repeats the same footer many times in the scrollback.
    @Test
    void repeatedFootersAreDeduplicated() {
        String pane = """
                Branch: b · PR: https://github.com/JabRef/jabref/pull/16246
                Branch: b · PR: https://github.com/JabRef/jabref/pull/16246
                """;

        assertThat(ClaudePrLookup.parsePrUrls(pane))
                .containsExactly("https://github.com/JabRef/jabref/pull/16246");
    }

    @Test
    void unlabeledPrLinksDoNotMatch() {
        assertThat(ClaudePrLookup.parsePrUrls("see https://github.com/o/r/pull/9 for context")).isEmpty();
    }

    @Test
    void captureTargetsTheWindowQuoted() {
        assertThat(ClaudePrLookup.remoteCommand("@5"))
                .containsExactly("tmux", "capture-pane", "-p", "-J", "-S", "-100", "-t", "'@5'");
    }

    // [utest->dsn~claude-pr-refresh~5]
    @Test
    void batchCapturesEveryWindowInOneCommandSeparatedByMarkers() {
        assertThat(String.join(" ", ClaudePrLookup.batchCommand(List.of("@5", "@24"))))
                .isEqualTo("echo '===contextswitcher-pane @5===' ; "
                        + "tmux capture-pane -p -J -S -100 -t '@5' ; "
                        + "echo '===contextswitcher-pane @24===' ; "
                        + "tmux capture-pane -p -J -S -100 -t '@24'");
        assertThat(ClaudePrLookup.batchCommand(List.of("@5", "@24")))
                .noneMatch(part -> part.contains("\""));
    }

    /// A gone window leaves an empty section (tmux complains on stderr); the
    /// windows around it still get their URLs.
    // [utest->dsn~claude-pr-refresh~5]
    @Test
    void batchOutputIsSplitPerWindow() {
        String stdout = """
                ===contextswitcher-pane @5===
                Branch: b · PR: https://github.com/JabRef/jabref/pull/16246
                Branch: b · PR: https://github.com/JabRef/jabref/pull/16246
                ===contextswitcher-pane @7===
                ===contextswitcher-pane @24===
                chat about https://github.com/o/r/pull/1
                docs · PR: https://github.com/JabRef/user-documentation/pull/643
                """;

        assertThat(ClaudePrLookup.parseBatch(stdout)).containsExactly(
                Map.entry("@5", List.of("https://github.com/JabRef/jabref/pull/16246")),
                Map.entry("@24", List.of("https://github.com/JabRef/user-documentation/pull/643")));
        assertThat(ClaudePrLookup.parseBatch("")).isEmpty();
    }
}
