package com.contextswitcher.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.contextswitcher.discovery.TmuxDiscovery.TmuxSession;
import com.contextswitcher.discovery.TmuxDiscovery.TmuxWindow;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~tmux-task-import~12]
class TmuxTaskImporterTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 12);
    private static final TmuxWindow CLAUDE =
            new TmuxWindow("@3", "0", "/home/o/jabref", "claude", "claude", "", "", "", "abc-123", null);
    private static final TmuxWindow THREADING =
            new TmuxWindow("@5", "2", "", "bash", "threading", "", "", "", null, null);

    // Claude Code publishes its task summary as the pane title — a better
    // task title than the window name when present.
    @Test
    void skeletonPrefersThePaneTitleOverTheWindowName() throws Exception {
        TmuxWindow window = new TmuxWindow("@8", "4", "/h", "claude", "claude",
                "directory-as-library-feature", "", "", null, null);

        Task task = new TaskFileParser().parse("t",
                TmuxTaskImporter.skeleton("devbox", "2", window, DATE, true));

        assertThat(task.title()).isEqualTo("directory-as-library-feature");
    }

    @Test
    void skeletonParsesWithSessionAndImmutableWindowId() throws Exception {
        String skeleton = TmuxTaskImporter.skeleton("devbox", "2", THREADING, DATE, true);

        Task task = new TaskFileParser().parse("devbox-2-2-threading", skeleton);

        assertThat(task.title()).isEqualTo("threading");
        assertThat(task.remote()).isEqualTo("devbox");
        assertThat(task.tmux()).isEqualTo(new Task.TmuxConfig("2", "@5"));
        assertThat(task.tmux().target()).isEqualTo("@5");
        assertThat(task.intellij()).isNull();
        assertThat(task.browser()).isNull();
        assertThat(task.claude()).isNull();
        assertThat(task.notes()).contains("session 2, window 2 (threading)");
    }

    // Every pane has a cwd — that alone must not create a claude: section.
    // [utest->dsn~claude-session-capture~3]
    @Test
    void skeletonWithoutClaudeGetsNoClaudeSection() throws Exception {
        TmuxWindow window = new TmuxWindow("@9", "5", "/home/o", "bash", "bash", "", "", "", null, null);

        Task task = new TaskFileParser().parse("t",
                TmuxTaskImporter.skeleton("devbox", "2", window, DATE, true));

        assertThat(task.claude()).isNull();
    }

    // [utest->dsn~claude-session-capture~3]
    @Test
    void skeletonRecordsClaudeCwdAndSessionId() throws Exception {
        String skeleton = TmuxTaskImporter.skeleton("devbox", "2", CLAUDE, DATE, true);

        Task task = new TaskFileParser().parse("devbox-2-0-claude", skeleton);

        assertThat(task.claude())
                .isEqualTo(new Task.ClaudeConfig("/home/o/jabref", "abc-123", null));
    }

    // [utest->dsn~claude-session-capture~3]
    @Test
    void skeletonWithCwdButNoSessionIdKeepsSectionParseable() throws Exception {
        TmuxWindow window = new TmuxWindow("@4", "1", "/home/o/work", "claude", "claude", "", "", "", null, null);

        Task task = new TaskFileParser().parse("t",
                TmuxTaskImporter.skeleton("devbox", "2", window, DATE, true));

        assertThat(task.claude()).isEqualTo(new Task.ClaudeConfig("/home/o/work", null, null));
    }

    // [utest->dsn~claude-workspace-capture~2]
    @Test
    void skeletonRecordsClaudeWorkspaceWhenReported() throws Exception {
        TmuxWindow window = new TmuxWindow("@6", "3", "/home/o/proj", "claude", "claude", "",
                "/data/koppor/ws/2026-conv", "working", "sess-9", null);

        Task task = new TaskFileParser().parse("t",
                TmuxTaskImporter.skeleton("devbox", "2", window, DATE, true));

        assertThat(task.claude())
                .isEqualTo(new Task.ClaudeConfig("/home/o/proj", "sess-9", "/data/koppor/ws/2026-conv"));
    }

    @Test
    void writeCreatesOneFilePerWindow(@TempDir Path dir) throws Exception {
        List<TmuxSession> sessions = List.of(
                new TmuxSession("1", List.of(new TmuxWindow("@1", "0", "/h", "bash", "bash", "", "", "", null, null))),
                new TmuxSession("2", List.of(CLAUDE, THREADING)));

        TmuxTaskImporter.ImportResult result =
                TmuxTaskImporter.write("devbox", sessions, Set.of(), Set.of(), dir, DATE, true);

        assertThat(result.created()).containsExactly("1:0 (bash)", "2:0 (claude)", "2:2 (threading)");
        assertThat(dir.resolve("devbox-1-0-bash.md")).exists();
        assertThat(dir.resolve("devbox-2-0-claude.md")).exists();
        assertThat(dir.resolve("devbox-2-2-threading.md")).exists();
    }

    @Test
    void sessionLevelTaskCoversAllItsWindows(@TempDir Path dir) throws Exception {
        List<TmuxSession> sessions = List.of(new TmuxSession("2", List.of(CLAUDE, THREADING)));

        TmuxTaskImporter.ImportResult result = TmuxTaskImporter.write("devbox", sessions,
                Set.of(TmuxTaskImporter.sessionKey("devbox", "2")), Set.of(), dir, DATE, true);

        assertThat(result.created()).isEmpty();
        assertThat(result.skipped()).containsExactly("2:0 (claude)", "2:2 (threading)");
    }

    @Test
    void windowLevelTaskMatchesByIdNameOrIndex(@TempDir Path dir) throws Exception {
        List<TmuxSession> sessions = List.of(new TmuxSession("2", List.of(CLAUDE, THREADING)));

        TmuxTaskImporter.ImportResult result = TmuxTaskImporter.write("devbox", sessions,
                Set.of(TmuxTaskImporter.windowKey("devbox", "2", "threading"),
                        TmuxTaskImporter.windowKey("devbox", "2", "@3")), Set.of(), dir, DATE, true);

        assertThat(result.created()).isEmpty();
        assertThat(result.skipped()).containsExactly("2:0 (claude)", "2:2 (threading)");
    }

    // A same-named file is not coverage (it may track a long-dead context):
    // never overwritten, but the window is still imported under a free name.
    @Test
    void existingFilesAreNeverOverwrittenButTheWindowIsStillImported(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("devbox-2-0-claude.md"), "mine");

        TmuxTaskImporter.ImportResult result = TmuxTaskImporter.write("devbox",
                List.of(new TmuxSession("2", List.of(CLAUDE))), Set.of(), Set.of(), dir, DATE, true);

        assertThat(result.created()).containsExactly("2:0 (claude)");
        assertThat(Files.readString(dir.resolve("devbox-2-0-claude.md"))).isEqualTo("mine");
        assertThat(dir.resolve("devbox-2-0-claude-2.md")).exists();
    }

    // The Claude session id is identity: a window whose session some task
    // already tracks is covered regardless of its tmux coordinates.
    @Test
    void knownClaudeSessionIdCoversTheWindow(@TempDir Path dir) throws Exception {
        TmuxTaskImporter.ImportResult result = TmuxTaskImporter.write("devbox",
                List.of(new TmuxSession("9", List.of(CLAUDE))), Set.of(), Set.of("abc-123"), dir, DATE, true);

        assertThat(result.created()).isEmpty();
        assertThat(result.skipped()).containsExactly("9:0 (claude)");
    }

    // A footer-scraped or published PR URL becomes a real browser section.
    // [utest->dsn~claude-pr-capture~2]
    @Test
    void skeletonWithPrUrlGetsARealBrowserSection() throws Exception {
        TmuxWindow window = new TmuxWindow("@7", "2", "/h", "claude", "claude", "", "", "",
                "sess-1", "https://github.com/JabRef/jabref/pull/16246");

        Task task = new TaskFileParser().parse("t",
                TmuxTaskImporter.skeleton("devbox", "0", window, DATE, true));

        assertThat(task.browser().urls())
                .containsExactly("https://github.com/JabRef/jabref/pull/16246");
    }

    // Compact mode (hints off): only real keys, nothing below # Notes.
    // [utest->dsn~skeleton-hints~4]
    @Test
    void compactSkeletonHasNoHintCommentsAndAnEmptyNotesBody() throws Exception {
        String skeleton = TmuxTaskImporter.skeleton("devbox", "2", THREADING, DATE, false);

        assertThat(skeleton)
                .doesNotContain("# Fill in", "# intellij", "# browser", "Imported from tmux")
                .endsWith("# Notes\n\n");
        Task task = new TaskFileParser().parse("t", skeleton);
        assertThat(task.title()).isEqualTo("threading");
        assertThat(task.remote()).isEqualTo("devbox");
        assertThat(task.tmux()).isEqualTo(new Task.TmuxConfig("2", "@5"));
    }

    // Compact strips only the hints — captured claude/PR sections stay real.
    // [utest->dsn~skeleton-hints~4]
    @Test
    void compactSkeletonKeepsRealClaudeAndBrowserSections() throws Exception {
        TmuxWindow window = new TmuxWindow("@7", "2", "/h", "claude", "claude", "", "", "",
                "sess-1", "https://github.com/JabRef/jabref/pull/16246");

        Task task = new TaskFileParser().parse("t",
                TmuxTaskImporter.skeleton("devbox", "0", window, DATE, false));

        assertThat(task.claude().sessionId()).isEqualTo("sess-1");
        assertThat(task.browser().urls())
                .containsExactly("https://github.com/JabRef/jabref/pull/16246");
    }

    @Test
    void fileNameIsSanitized() {
        assertThat(TmuxTaskImporter.fileName("user@host", "my session",
                new TmuxWindow("@4", "1", "/h", "zsh", "a/b", "", "", "", null, null)))
                .isEqualTo("user_host-my_session-1-a_b.md");
    }

    // A window whose name is a pasted multi-sentence task description (no short
    // @cs_title published) must still yield a Windows-safe path: the name
    // component is capped, cut at a word boundary, so Files.writeString cannot
    // fail with "The filename ... syntax is incorrect".
    @Test
    void longWindowNameIsCappedAtAWordBoundary() {
        String hugeName = "I think I would like to assign a Desktop to each category "
                + "and then have a play button at the same location as the task play button";
        String file = TmuxTaskImporter.fileName("h", "0",
                new TmuxWindow("@5", "5", "/h", "bash", hugeName, "", "", "", null, null));
        String namePart = file.substring("h-0-5-".length(), file.length() - ".md".length());
        assertThat(namePart.length()).isLessThanOrEqualTo(60);
        assertThat(namePart).doesNotEndWith("_").doesNotEndWith("-");
        assertThat(namePart).startsWith("I_think_I_would_like");
    }
}
