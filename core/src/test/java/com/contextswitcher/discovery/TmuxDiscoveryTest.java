package com.contextswitcher.discovery;

import java.util.List;

import com.contextswitcher.discovery.TmuxDiscovery.TmuxSession;
import com.contextswitcher.discovery.TmuxDiscovery.TmuxWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~tmux-task-import~12]
// [utest->dsn~stranded-mirror-windows~1]
class TmuxDiscoveryTest {

    @Test
    void formatArgumentIsSingleQuotedAgainstRemoteShell() {
        assertThat(TmuxDiscovery.remoteCommand())
                .containsExactly("tmux", "list-windows", "-a", "-F",
                        "'#{session_name}|#{window_id}|#{window_index}|#{pane_current_path}|#{pane_current_command}"
                        + "|#{@cs_workspace}|#{@cs_status}|#{@cs_session_id}|#{@cs_pr}|#{@cs_commit}"
                        + "|#{host}|#{pane_title}|#{window_name}'");
    }

    @Test
    void parseGroupsWindowsBySessionPreservingOrder() {
        String output = """
                2|@3|0|/home/o/jabref|claude||||||devbox|devbox|claude
                2|@7|1|/home/o|vim||||||devbox|devbox|vim
                5|@9|0|/home/o|bash||||||devbox|devbox|bash
                """;

        List<TmuxSession> sessions = TmuxDiscovery.parse(output);

        assertThat(sessions).containsExactly(
                new TmuxSession("2", List.of(
                        new TmuxWindow("@3", "0", "/home/o/jabref", "claude", "claude", "", "", "", null, null),
                        new TmuxWindow("@7", "1", "/home/o", "vim", "vim", "", "", "", null, null))),
                new TmuxSession("5", List.of(
                        new TmuxWindow("@9", "0", "/home/o", "bash", "bash", "", "", "", null, null))));
    }

    @Test
    void parseReadsWorkspaceAndStatusUserOptions() {
        String output = "2|@3|0|/home/o/jabref|claude|/data/koppor/ws/2026-conv|working||||devbox|devbox|claude\n";

        TmuxWindow window = TmuxDiscovery.parse(output).getFirst().windows().getFirst();

        assertThat(window.workspace()).isEqualTo("/data/koppor/ws/2026-conv");
        assertThat(window.status()).isEqualTo("working");
        assertThat(window.commit()).isEmpty();
    }

    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void parseReadsThePublishedCommit() {
        // The commit sits between @cs_pr and #{host}; the window name stays
        // last (it is the field most likely to contain a pipe).
        String output = "2|@3|0|/home/o/jabref|claude|||||abc1234|devbox|devbox|claude\n";

        TmuxWindow window = TmuxDiscovery.parse(output).getFirst().windows().getFirst();

        assertThat(window.commit()).isEqualTo("abc1234");
        assertThat(window.name()).isEqualTo("claude");
    }

    // Claude Code publishes its task summary as the pane title; the tmux
    // default title (the host name) carries no information and becomes "".
    @Test
    void parseKeepsMeaningfulPaneTitlesAndDropsTheHostDefault() {
        String output = """
                2|@3|0|/home/o/jabref|claude||||||devbox|directory-as-library-feature|claude
                2|@7|1|/home/o|vim||||||devbox|devbox|vim
                """;

        List<TmuxWindow> windows = TmuxDiscovery.parse(output).getFirst().windows();

        assertThat(windows.getFirst().paneTitle()).isEqualTo("directory-as-library-feature");
        assertThat(windows.getLast().paneTitle()).isEmpty();
    }

    // The exact id from the SessionStart hook beats any transcript guess.
    // [utest->dsn~claude-session-capture~3]
    @Test
    void parseReadsTheExactSessionIdUserOption() {
        String output = "2|@3|0|/home/o/jabref|claude|||abc-123|||devbox|devbox|claude\n";

        TmuxWindow window = TmuxDiscovery.parse(output).getFirst().windows().getFirst();

        assertThat(window.sessionId()).isEqualTo("abc-123");
    }

    // Grouped mirror sessions share the base session's windows — listing
    // them would duplicate every window (and the import every task).
    @Test
    void parseIgnoresMirrorSessions() {
        String output = """
                0|@3|0|/home/o|claude||||||devbox|devbox|claude
                cs-mirror-0|@3|0|/home/o|claude||||||devbox|devbox|claude
                """;

        assertThat(TmuxDiscovery.parse(output)).hasSize(1);
        assertThat(TmuxDiscovery.parse(output).getFirst().name()).isEqualTo("0");
    }

    /// Field report 2026-09-12: the base session `old-group` died and its
    /// grouped mirror survived holding all eight windows. Dropping every
    /// mirror line made those windows vanish from the listing, and the
    /// reconcile suspended all eight tasks — whose windows the app was
    /// talking to at that very moment. A window only a mirror still holds is
    /// reported under the base session's name.
    // [utest->dsn~stranded-mirror-windows~1]
    @Test
    void parseKeepsTheWindowsOfAStrandedMirror() {
        String output = """
                0|@3|0|/home/o|claude||||||devbox|devbox|claude
                cs-mirror-0|@3|0|/home/o|claude||||||devbox|devbox|claude
                cs-mirror-old-group|@646|0|/data/w|claude||||||devbox|devbox|flicker
                """;

        List<TmuxSession> sessions = TmuxDiscovery.parse(output);

        assertThat(sessions).extracting(TmuxSession::name).containsExactly("0", "old-group");
        assertThat(sessions.getLast().windows()).extracting(TmuxWindow::id).containsExactly("@646");
        // The shared copy is still not duplicated.
        assertThat(sessions.getFirst().windows()).extracting(TmuxWindow::id).containsExactly("@3");
    }

    // [utest->dsn~claude-pr-capture~2]
    @Test
    void parseReadsThePublishedPrUrl() {
        String output = "0|@3|0|/h|claude||||https://github.com/o/r/pull/7||devbox|devbox|claude\n";

        assertThat(TmuxDiscovery.parse(output).getFirst().windows().getFirst().prUrl())
                .isEqualTo("https://github.com/o/r/pull/7");
    }

    @Test
    void parseKeepsPipesInWindowNames() {
        List<TmuxSession> sessions =
                TmuxDiscovery.parse("dev|@1|0|/home/o|zsh||||||devbox|devbox|watch | build\n");

        assertThat(sessions.getFirst().windows().getFirst().name()).isEqualTo("watch | build");
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            "⠂ Debug PdfVerbatimBibtexImporte", devbox, "Debug PdfVerbatimBibtexImporte"
            "✳ directory-as-library-feature",  devbox, "directory-as-library-feature"
            "· thinking",                      devbox, "thinking"
            "plain title",                     devbox, "plain title"
            "devbox",                         devbox, ""
            "",                                devbox, ""
            """)
    void cleanStripsSpinnerFramesAndTheHostDefault(String raw, String host, String expected) {
        assertThat(TmuxDiscovery.cleanPaneTitle(raw, host)).isEqualTo(expected);
    }
}
