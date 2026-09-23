package com.contextswitcher.tasks;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [utest->dsn~task-file-parsing~4]
class TaskFileParserTest {

    private final TaskFileParser parser = new TaskFileParser();

    // [utest->dsn~pinned-tasks~2]
    @Test
    void pinnedFlagIsParsedAndWrittenAfterTheTitle() throws Exception {
        String plain = """
                ---
                title: a task
                status: active
                ---
                notes
                """;
        assertThat(parser.parse("t", plain).pinned()).isFalse();

        String pinned = TaskFileParser.withPinned(plain, true);
        assertThat(pinned).contains("title: a task\npinned: true\n");
        assertThat(parser.parse("t", pinned).pinned()).isTrue();
        // Unpinning removes the key again, restoring the original file.
        assertThat(TaskFileParser.withPinned(pinned, false)).isEqualTo(plain);
    }

    /// A bare `intellij:` key (even with all children commented out) enables
    /// the action with every value defaulted; the project path then comes
    /// from the Claude section.
    @Test
    void bareIntellijKeyParsesAsEmptyConfig() throws Exception {
        String content = """
                ---
                title: bare
                remote: devbox
                intellij:
                #   projectPath: /home/user/repos/project
                claude:
                  cwd: /repo
                  workspace: /repo/worktree
                ---
                """;

        Task task = parser.parse("bare", content);

        assertThat(task.intellij()).isEqualTo(new Task.IntellijConfig(null, null, null));
        assertThat(task.intellijProjectPath()).isEqualTo("/repo/worktree");
    }

    @Test
    void intellijWithOnlyRemoteParsesWithoutProjectPath() throws Exception {
        String content = """
                ---
                title: t
                intellij:
                  remote: otherbox
                ---
                """;

        Task task = parser.parse("t", content);

        assertThat(task.intellij()).isEqualTo(new Task.IntellijConfig(null, "otherbox", null));
        assertThat(task.intellijProjectPath()).isNull();
    }

    @Test
    void fullFrontmatterIsParsed() throws Exception {
        String content = """
                ---
                title: "JabRef: fix groups NPE"
                status: suspended
                remote: devbox
                tmux:
                  session: jabref
                  window: claude
                intellij:
                  projectPath: /home/olive/repos/jabref
                  remote: otherbox
                  ide: IU-2025.1
                browser:
                  urls:
                    - https://github.com/JabRef/jabref/pull/12345
                    - https://builds.jabref.org
                ---
                # Notes

                Some notes.
                """;

        Task task = parser.parse("jabref-npe", content);

        assertThat(task.id()).isEqualTo("jabref-npe");
        assertThat(task.title()).isEqualTo("JabRef: fix groups NPE");
        assertThat(task.status()).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(task.remote()).isEqualTo("devbox");
        assertThat(task.tmux()).isEqualTo(new Task.TmuxConfig("jabref", "claude"));
        assertThat(task.intellij())
                .isEqualTo(new Task.IntellijConfig("/home/olive/repos/jabref", "otherbox", "IU-2025.1"));
        assertThat(task.intellijRemote()).isEqualTo("otherbox");
        assertThat(task.browser().urls()).containsExactly(
                "https://github.com/JabRef/jabref/pull/12345",
                "https://builds.jabref.org");
        assertThat(task.notes()).contains("Some notes.");
    }

    @Test
    void missingOptionalKeysYieldNullConfigsAndDefaults() throws Exception {
        String content = """
                ---
                remote: devbox
                ---
                """;

        Task task = parser.parse("minimal", content);

        assertThat(task.title()).isEqualTo("minimal");
        assertThat(task.status()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(task.tmux()).isNull();
        assertThat(task.intellij()).isNull();
        assertThat(task.browser()).isNull();
        assertThat(task.intellijRemote()).isEqualTo("devbox");
    }

    // [utest->dsn~local-terminal-focus~5]
    @Test
    void terminalSectionParsesTabTitle() throws Exception {
        String content = """
                ---
                terminal:
                  tabTitle: jabref-npe
                ---
                """;

        Task task = parser.parse("t", content);

        assertThat(task.terminal()).isEqualTo(new Task.TerminalConfig("jabref-npe"));
        assertThat(task.tmux()).isNull();
    }

    @Test
    void terminalSectionRequiresTabTitle() {
        String content = """
                ---
                terminal:
                  foo: bar
                ---
                """;

        assertThatThrownBy(() -> parser.parse("t", content))
                .isInstanceOf(TaskParseException.class)
                .hasMessageContaining("terminal.tabTitle");
    }

    @Test
    void numericTmuxWindowBecomesString() throws Exception {
        String content = """
                ---
                tmux:
                  session: work
                  window: 2
                ---
                """;

        Task task = parser.parse("numeric", content);

        assertThat(task.tmux().window()).isEqualTo("2");
    }

    @Test
    void brokenYamlIsReported() {
        String content = """
                ---
                title: "unclosed
                  bad: [
                ---
                """;

        assertThatThrownBy(() -> parser.parse("broken", content))
                .isInstanceOf(TaskParseException.class)
                .hasMessageContaining("YAML");
    }

    @Test
    void missingFrontmatterIsReported() {
        assertThatThrownBy(() -> parser.parse("nofm", "# Just a heading\n"))
                .isInstanceOf(TaskParseException.class)
                .hasMessageContaining("frontmatter fence");
    }

    @Test
    void missingClosingFenceIsReported() {
        assertThatThrownBy(() -> parser.parse("noclose", "---\ntitle: x\n"))
                .isInstanceOf(TaskParseException.class)
                .hasMessageContaining("Closing");
    }

    @Test
    void unknownStatusIsReported() {
        String content = """
                ---
                status: someday
                ---
                """;

        assertThatThrownBy(() -> parser.parse("status", content))
                .isInstanceOf(TaskParseException.class)
                .hasMessageContaining("someday");
    }

    // [utest->dsn~task-rename-title~2]
    @Test
    void withTitleReplacesOnlyTheTitleLine() throws Exception {
        String content = """
                ---
                title: "Old"
                remote: devbox   # keep me
                ---
                notes
                """;

        String renamed = TaskFileParser.withTitle(content, "New \"quoted\" name");

        assertThat(renamed).contains("remote: devbox   # keep me");
        assertThat(parser.parse("t", renamed).title()).isEqualTo("New \"quoted\" name");
    }

    // [utest->dsn~task-rename-title~2]
    @Test
    void withTitleInsertsWhenMissing() throws Exception {
        String renamed = TaskFileParser.withTitle("---\nremote: devbox\n---\n", "Fresh");

        assertThat(parser.parse("t", renamed).title()).isEqualTo("Fresh");
        assertThat(renamed).contains("remote: devbox");
    }

    // [utest->dsn~group-note-open~5]
    @Test
    void groupNoteReadsTheNoteUrl() {
        String config = """
                ---
                # some comment
                note: onenote:https://d.docs.live.net/x/Projects.one#Entry%20Preview&end
                ---
                body
                """;

        assertThat(TaskFileParser.groupNote(config))
                .isEqualTo("onenote:https://d.docs.live.net/x/Projects.one#Entry%20Preview&end");
    }

    // [utest->dsn~group-note-open~5]
    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            "---\\nremote: devbox\\n---\\n"
            "no frontmatter at all"
            "---\\nnote: [broken\\n---\\n"
            "---\\nnote: ''\\n---\\n"
            """)
    void groupNoteIsNullWithoutUsableNote(String content) {
        assertThat(TaskFileParser.groupNote(content.replace("\\n", "\n"))).isNull();
    }

    /// SnakeYAML picks quoting/escaping for scalars inserted into templates.
    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', textBlock = """
            threading,   threading
            @17,         "'@17'"
            "a: b # c",  "'a: b # c'"
            """)
    void yamlScalarQuotesOnlyWhenNeeded(String value, String expected) {
        assertThat(TaskFileParser.yamlScalar(value)).isEqualTo(expected);
    }

    // [utest->dsn~task-create-ui~15]
    @Test
    void newTaskFileNameSlugsTitleAndKeepsGroupFolder() {
        assertThat(TaskFileParser.newTaskFileName("Fix NPE in preview")).isEqualTo("fix-npe-in-preview");
        assertThat(TaskFileParser.newTaskFileName("JabRef/Fix NPE")).isEqualTo("jabref/fix-npe");
        assertThat(TaskFileParser.newTaskFileName("weird  //  name?!")).isEqualTo("weird/name");
        assertThat(TaskFileParser.newTaskFileName("???")).isEqualTo("task");
    }

    // [utest->dsn~task-create-ui~15]
    @Test
    void newTaskFileNameCapsLongDescriptionAtWordBoundary() {
        String description = "I think I would like to assign a desktop to each category "
                + "and then have a play button at the same location as the task play button";
        assertThat(TaskFileParser.newTaskFileName("contextswitcher/" + description))
                .isEqualTo("contextswitcher/i-think-i-would-like-to-assign-a-desktop-to-each-category");
        assertThat(TaskFileParser.slug("x".repeat(200))).hasSize(60);
        assertThat(TaskFileParser.slug("a".repeat(59) + ".b c")).isEqualTo("a".repeat(59));
    }

    // [utest->dsn~claude-title-sync~3]
    @Test
    void adoptedFileNameKeepsGroupAndDateAndSlugsTitle() {
        assertThat(TaskFileParser.adoptedFileName(
                "contextswitcher/2026-07-17-i-think-i-would-like-to-assign",
                "Desktop per category", java.time.LocalDate.of(2026, 7, 18)))
                .isEqualTo("contextswitcher/2026-07-17-desktop-per-category");
        assertThat(TaskFileParser.adoptedFileName("old-undated-task", "New title",
                java.time.LocalDate.of(2026, 7, 18)))
                .isEqualTo("2026-07-18-new-title");
        assertThat(TaskFileParser.adoptedFileName("2026-07-17-same-title", "Same title",
                java.time.LocalDate.of(2026, 7, 18)))
                .isNull();
    }

    // [utest->dsn~task-create-ui~15]
    @Test
    void newTaskContentParsesWithGivenTitle() throws Exception {
        Task task = parser.parse("t", TaskFileParser.newTaskContent("My \"new\" task"));

        assertThat(task.title()).isEqualTo("My \"new\" task");
        assertThat(task.status()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(task.tmux()).isNull();
        assertThat(task.intellij()).isNull();
        assertThat(task.browser()).isNull();
    }

    // [utest->dsn~task-status-cycle~2]
    @Test
    void withStatusReplacesExistingStatusLineOnly() throws Exception {
        String content = """
                ---
                title: "T"
                status: active
                host: devbox   # keep me
                ---
                notes
                """;

        String suspended = TaskFileParser.withStatus(content, TaskStatus.SUSPENDED);

        assertThat(suspended).contains("status: suspended");
        assertThat(suspended).contains("host: devbox   # keep me");
        assertThat(parser.parse("t", suspended).status()).isEqualTo(TaskStatus.SUSPENDED);
    }

    // [utest->dsn~suspended-timestamp~1]
    @Test
    void withStatusStampsSuspendedAndClearsItOnResume() throws Exception {
        String content = """
                ---
                title: "T"
                status: active
                host: devbox   # keep me
                ---
                notes
                """;
        java.time.LocalDateTime when = java.time.LocalDateTime.of(2026, 9, 5, 14, 32, 7);

        String suspended = TaskFileParser.withStatus(content, TaskStatus.SUSPENDED, when);

        assertThat(suspended).contains("status: suspended\nsuspended: \"2026-09-05 14:32\"\nhost: devbox   # keep me");
        assertThat(parser.parse("t", suspended).suspendedAt()).isEqualTo("2026-09-05 14:32");

        String again = TaskFileParser.withStatus(suspended, TaskStatus.SUSPENDED, when.plusDays(1));
        assertThat(again).containsOnlyOnce("suspended:").contains("suspended: \"2026-09-06 14:32\"");

        String resumed = TaskFileParser.withStatus(suspended, TaskStatus.ACTIVE);
        assertThat(resumed).doesNotContain("suspended:").contains("status: active");
        assertThat(parser.parse("t", resumed).suspendedAt()).isNull();
        assertThat(parser.parse("t", content).suspendedAt()).isNull();
    }

    // [utest->dsn~task-status-cycle~2]
    @Test
    void withStatusInsertsAfterTitleWhenMissing() throws Exception {
        String content = """
                ---
                title: "T"
                host: devbox
                ---
                """;

        String done = TaskFileParser.withStatus(content, TaskStatus.DONE);

        assertThat(parser.parse("t", done).status()).isEqualTo(TaskStatus.DONE);
        assertThat(done).contains("title: \"T\"");
        assertThat(done).contains("host: devbox");
    }

    // [utest->dsn~task-status-cycle~2]
    @Test
    void withStatusInsertsAtTopWhenNoTitle() throws Exception {
        String suspended = TaskFileParser.withStatus("---\nhost: devbox\n---\n", TaskStatus.SUSPENDED);

        assertThat(parser.parse("t", suspended).status()).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(suspended).contains("host: devbox");
    }

    // [utest->dsn~open-in-intellij~3]
    @Test
    void withIntellijInsertsBareSectionWhenAbsent() throws Exception {
        String content = """
                ---
                title: "T"
                status: active
                # intellij:                 # commented example, ignored
                ---
                notes
                """;

        String updated = TaskFileParser.withIntellij(content);

        assertThat(parser.parse("t", updated).intellij()).isNotNull();
        assertThat(updated).contains("notes");
    }

    // [utest->dsn~open-in-intellij~3]
    @Test
    void withIntellijProjectPathCreatesSectionWhenAbsent() throws Exception {
        String content = """
                ---
                title: "T"
                remote: devbox
                ---
                """;

        String updated = TaskFileParser.withIntellijProjectPath(content, "/home/me/repo");

        Task.IntellijConfig intellij = parser.parse("t", updated).intellij();
        assertThat(intellij).isNotNull();
        assertThat(intellij.projectPath()).isEqualTo("/home/me/repo");
    }

    // [utest->dsn~open-in-intellij~3]
    @Test
    void withIntellijProjectPathAddsChildToBareSection() throws Exception {
        String content = """
                ---
                title: "T"
                intellij:
                browser:
                  urls:
                    - https://example.com
                ---
                """;

        String updated = TaskFileParser.withIntellijProjectPath(content, "/home/me/repo");

        Task parsed = parser.parse("t", updated);
        assertThat(parsed.intellijProjectPath()).isEqualTo("/home/me/repo");
        // The sibling section is preserved, not clobbered by the child insert.
        assertThat(parsed.browser()).isNotNull();
    }

    /// The intellij edits must be purely additive: no other section (least of
    /// all a `claude:` session that enables `--resume`) may be lost. Guards the
    /// data-loss regression that "Open in IntelliJ" nearly implied.
    // [utest->dsn~open-in-intellij~3]
    @Test
    void intellijEditsPreserveEveryOtherSection() throws Exception {
        String content = """
                ---
                title: "T"
                status: active
                remote: koppor@devbox
                tmux:
                  session: '0'
                  window: '@24'
                claude:
                  cwd: /data/koppor/repo
                  sessionId: abc-123
                  workspace: /data/koppor/repo/sub
                browser:
                  urls:
                    - https://github.com/owner/repo/pull/1
                ---

                # Notes

                keep me verbatim
                """;

        for (String updated : List.of(
                TaskFileParser.withIntellij(content),
                TaskFileParser.withIntellijProjectPath(content, "/data/koppor/repo"))) {
            Task task = parser.parse("t", updated);
            assertThat(task.remote()).isEqualTo("koppor@devbox");
            assertThat(task.tmux()).isNotNull();
            assertThat(task.tmux().window()).isEqualTo("@24");
            assertThat(task.claude()).isNotNull();
            assertThat(task.claude().cwd()).isEqualTo("/data/koppor/repo");
            assertThat(task.claude().sessionId()).isEqualTo("abc-123");
            assertThat(task.claude().workspace()).isEqualTo("/data/koppor/repo/sub");
            assertThat(task.browser()).isNotNull();
            assertThat(task.browser().urls()).containsExactly("https://github.com/owner/repo/pull/1");
            assertThat(task.notes()).contains("keep me verbatim");
            assertThat(task.intellij()).isNotNull();
        }
    }

    // The sync's session-id backfill: fills only an existing claude section
    // (comments and siblings preserved); a task without one stays unchanged —
    // a sessionId without cwd would not parse.
    // [utest->dsn~tmux-sync~7]
    @Test
    void withClaudeSessionIdFillsExistingClaudeSection() throws Exception {
        String content = """
                ---
                title: "T"
                remote: devbox
                tmux:
                  session: "0"
                  window: "@7"
                claude:
                  cwd: /data/koppor/repo   # keep me
                ---

                notes stay verbatim
                """;

        String updated = TaskFileParser.withClaudeSessionId(content, "abc-123");

        Task task = parser.parse("t", updated);
        assertThat(task.claude()).isNotNull();
        assertThat(task.claude().sessionId()).isEqualTo("abc-123");
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/repo");
        assertThat(updated).contains("# keep me").contains("notes stay verbatim");
    }

    // [utest->dsn~tmux-sync~7]
    @Test
    void withClaudeSessionIdReplacesExistingIdAndSkipsSectionlessFiles() {
        String withId = """
                ---
                claude:
                  cwd: /repo
                  sessionId: old-1
                ---
                """;
        assertThat(TaskFileParser.withClaudeSessionId(withId, "new-2"))
                .contains("sessionId: new-2").doesNotContain("old-1");

        String noSection = """
                ---
                title: "T"
                ---
                """;
        assertThat(TaskFileParser.withClaudeSessionId(noSection, "abc-123"))
                .isEqualTo(noSection);
    }

    // The sync's workspace refresh: inserts the working subdirectory next to
    // the start cwd, and replaces a stale one (the session moved worktrees).
    // [utest->dsn~claude-workspace-capture~2]
    @Test
    void withClaudeWorkspaceInsertsAndReplacesLeavingCwdAlone() throws Exception {
        String content = """
                ---
                title: "T"
                claude:
                  cwd: /data/koppor/repo-workspaces   # keep me
                  sessionId: abc-123
                ---
                """;

        String inserted = TaskFileParser.withClaudeWorkspace(content, "/data/koppor/repo-workspaces/2026-07-23-x");
        Task task = parser.parse("t", inserted);
        assertThat(task.claude()).isNotNull();
        assertThat(task.claude().workspace()).isEqualTo("/data/koppor/repo-workspaces/2026-07-23-x");
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/repo-workspaces");
        assertThat(task.claude().sessionId()).isEqualTo("abc-123");
        assertThat(inserted).contains("# keep me");

        assertThat(TaskFileParser.withClaudeWorkspace(inserted, "/data/koppor/repo-workspaces/2026-07-24-y"))
                .contains("workspace: /data/koppor/repo-workspaces/2026-07-24-y")
                .doesNotContain("2026-07-23-x");
    }

    // The commit outlives the worktree Claude deletes after committing, so it
    // is written the same way — inserted, then replaced by the next one, with
    // cwd and workspace left alone.
    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void withClaudeCommitInsertsAndReplacesLeavingTheOtherChildrenAlone() throws Exception {
        String content = """
                ---
                title: "T"
                claude:
                  cwd: /data/koppor/repo-workspaces   # keep me
                  workspace: /data/koppor/repo-workspaces/2026-07-24-x
                ---
                """;

        String inserted = TaskFileParser.withClaudeCommit(content, "aaa1111");
        Task task = parser.parse("t", inserted);
        assertThat(task.claude()).isNotNull();
        assertThat(task.claude().commit()).isEqualTo("aaa1111");
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/repo-workspaces");
        assertThat(task.claude().workspace())
                .isEqualTo("/data/koppor/repo-workspaces/2026-07-24-x");
        assertThat(inserted).contains("# keep me");

        assertThat(TaskFileParser.withClaudeCommit(inserted, "bbb2222"))
                .contains("commit: bbb2222")
                .doesNotContain("aaa1111");
    }

    // [utest->dsn~open-in-intellij~3]
    @Test
    void withIntellijLeavesExistingSectionUntouched() throws Exception {
        String content = """
                ---
                title: "T"
                intellij:
                  projectPath: /repo   # keep me
                ---
                """;

        assertThat(TaskFileParser.withIntellij(content)).isEqualTo(content);
    }

    // [utest->dsn~task-status-cycle~2]
    @Test
    void toggledSwitchesActiveAndSuspendedAndResumesDone() {
        assertThat(TaskStatus.ACTIVE.toggled()).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(TaskStatus.SUSPENDED.toggled()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(TaskStatus.DONE.toggled()).isEqualTo(TaskStatus.ACTIVE);
    }

    // [utest->dsn~tmux-resurrect~7]
    @Test
    void withTmuxWindowReplacesOnlyTheWindowLine() throws Exception {
        String content = """
                ---
                title: "T"
                tmux:
                  session: "2"
                  window: "@99"   # old id
                claude:
                  cwd: "/home/o"
                ---
                """;

        String updated = TaskFileParser.withTmuxWindow(content, "@25", "resurrected 2026-07-12");

        Task task = parser.parse("t", updated);
        assertThat(task.tmux().window()).isEqualTo("@25");
        assertThat(updated).contains("# resurrected 2026-07-12");
        assertThat(task.claude().cwd()).isEqualTo("/home/o");
    }

    // [utest->dsn~tmux-resurrect~7]
    @Test
    void withTmuxWindowInsertsAfterSessionWhenLineIsMissing() throws Exception {
        String suspended = """
                ---
                title: "T"
                tmux:
                  session: "2"
                claude:
                  cwd: "/home/o"
                ---
                """;

        String updated = TaskFileParser.withTmuxWindow(suspended, "@25", "resurrected 2026-07-13");

        Task task = parser.parse("t", updated);
        assertThat(task.tmux().window()).isEqualTo("@25");
        assertThat(updated).contains("  session: \"2\"\n  window: \"@25\"   # resurrected 2026-07-13");
    }

    // [utest->dsn~remote-window-choice~6]
    @Test
    void withTmuxSectionAddsBothKeysToARemoteOnlyTask() throws Exception {
        String content = """
                ---
                title: "T"
                remote: koppor@devbox
                # tmux:
                #   session: "0"
                #   window: "@17"
                ---
                """;

        String updated = TaskFileParser.withTmuxSection(content, "0", "@42", "created 2026-07-17");

        Task task = parser.parse("t", updated);
        assertThat(task.tmux().session()).isEqualTo("0");
        assertThat(task.tmux().window()).isEqualTo("@42");
        assertThat(task.remote()).isEqualTo("koppor@devbox");
        // The commented template block is left untouched.
        assertThat(updated).contains("# tmux:");
    }

    // [utest->dsn~remote-window-choice~6]
    @Test
    void withTmuxSectionSkipsContentWithoutFrontmatter() {
        String content = "no frontmatter here\n";

        assertThat(TaskFileParser.withTmuxSection(content, "0", "@42", "n")).isEqualTo(content);
    }

    // [utest->dsn~remote-window-choice~6]
    @Test
    void withClaudeSectionAddsCwdWhenAbsent() throws Exception {
        String content = """
                ---
                title: "T"
                remote: koppor@devbox
                tmux:
                  session: "0"
                  window: "@42"
                ---
                """;

        String updated = TaskFileParser.withClaudeSection(content, "/data/koppor/jabref-workspaces");

        Task task = parser.parse("t", updated);
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/jabref-workspaces");
    }

    /// The adoption the sync and the teardown re-sync write for a window that
    /// was imported as a plain shell and later had `claude` started in it by
    /// hand: section created from the window's cwd, then the published facts
    /// as its children — and the result must parse back as a full session.
    // [utest->dsn~teardown-claude-resync~1]
    @Test
    void adoptedClaudeSectionCarriesTheWindowsSessionWorkspaceAndCommit() throws Exception {
        String content = """
                ---
                title: "icu"
                remote: koppor@devbox
                tmux:
                  session: old-group
                  window: "@212"
                ---

                # Notes
                """;

        String updated = TaskFileParser.withClaudeCommit(
                TaskFileParser.withClaudeWorkspace(
                        TaskFileParser.withClaudeSessionId(
                                TaskFileParser.withClaudeSection(content, "/data/koppor/icu"),
                                "aaaa-1111"),
                        "/data/koppor/icu/worktree"),
                "0f83998");

        Task task = parser.parse("t", updated);
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/icu");
        assertThat(task.claude().sessionId()).isEqualTo("aaaa-1111");
        assertThat(task.claude().workspace()).isEqualTo("/data/koppor/icu/worktree");
        assertThat(task.claude().commit()).isEqualTo("0f83998");
        assertThat(updated).contains("# Notes");
    }

    // [utest->dsn~remote-window-choice~6]
    @Test
    void withClaudeSectionLeavesAnExistingSectionUntouched() {
        String content = """
                ---
                title: "T"
                claude:
                  cwd: /home/o
                  sessionId: abc-123
                ---
                """;

        assertThat(TaskFileParser.withClaudeSection(content, "/data/other")).isEqualTo(content);
    }

    // [utest->dsn~remote-window-choice~6]
    @Test
    void frontmatterStringReadsATopLevelScalar() {
        String content = """
                ---
                title: "T"
                remote: koppor@devbox
                workspacesRoot: /data/koppor/jabref-workspaces
                ---

                # Notes
                """;

        assertThat(parser.frontmatterString(content, "workspacesRoot"))
                .isEqualTo("/data/koppor/jabref-workspaces");
        assertThat(parser.frontmatterString(content, "workdir")).isNull();
        assertThat(parser.frontmatterString("no frontmatter", "workspacesRoot")).isNull();
    }

    // [utest->dsn~task-suspend~6]
    @Test
    void withoutTmuxWindowRemovesTheWindowLineOnly() throws Exception {
        String content = """
                ---
                title: "T"
                tmux:
                  session: "2"
                  window: "@99"   # resurrected 2026-07-12
                claude:
                  cwd: "/home/o"
                ---
                """;

        String suspended = TaskFileParser.withoutTmuxWindow(content);

        Task task = parser.parse("t", suspended);
        assertThat(task.tmux().window()).isNull();
        assertThat(task.tmux().session()).isEqualTo("2");
        assertThat(task.claude().cwd()).isEqualTo("/home/o");
        assertThat(suspended).doesNotContain("@99");
    }

    // [utest->dsn~task-suspend~6]
    @Test
    void withoutTmuxWindowLeavesWindowlessContentUntouched() {
        String content = """
                ---
                title: "T"
                tmux:
                  session: "2"
                ---
                """;

        assertThat(TaskFileParser.withoutTmuxWindow(content)).isEqualTo(content);
    }

    @Test
    void missingTmuxSessionIsReported() {
        String content = """
                ---
                tmux:
                  window: claude
                ---
                """;

        assertThatThrownBy(() -> parser.parse("notmuxsession", content))
                .isInstanceOf(TaskParseException.class)
                .hasMessageContaining("tmux.session");
    }

    /// The line is the one in the **file**, so it can be found by eye — the
    /// YAML block starts on the line after the opening fence.
    // [utest->dsn~frontmatter-duplicate-keys~1]
    @Test
    void duplicateKeysAreReportedWithTheirFileLine() {
        String content = """
                ---
                remote: koppor@devbox
                desktop: jabref
                tags: [jabref]
                desktop: cloudref
                ---

                # cloudref
                """;

        assertThat(parser.duplicateFrontmatterKeys(content))
                .containsExactly("desktop (line 5)");
    }

    // [utest->dsn~frontmatter-duplicate-keys~1]
    @Test
    void duplicateKeysInsideASectionAreFoundToo() {
        String content = """
                ---
                title: t
                tmux:
                  session: "0"
                  window: "@53"
                  session: "old-group"
                ---
                """;

        assertThat(parser.duplicateFrontmatterKeys(content))
                .containsExactly("session (line 6)");
    }

    // [utest->dsn~frontmatter-duplicate-keys~1]
    @Test
    void everyRepeatIsReportedNotOnlyTheFirst() {
        String content = """
                ---
                a: 1
                a: 2
                a: 3
                b: 1
                b: 2
                ---
                """;

        assertThat(parser.duplicateFrontmatterKeys(content))
                .containsExactly("a (line 3)", "a (line 4)", "b (line 6)");
    }

    /// Nothing to report is the common case and must stay silent — including
    /// for the files that have no frontmatter to look at, and the broken ones
    /// whose own parse failure is the report.
    // [utest->dsn~frontmatter-duplicate-keys~1]
    @Test
    void wellFormedOrUnreadableFrontmatterReportsNothing() {
        assertThat(parser.duplicateFrontmatterKeys("---\ntitle: t\nstatus: active\n---\n")).isEmpty();
        assertThat(parser.duplicateFrontmatterKeys("no frontmatter at all\n")).isEmpty();
        assertThat(parser.duplicateFrontmatterKeys("---\ntitle: t\n")).isEmpty();
        assertThat(parser.duplicateFrontmatterKeys("---\n---\n")).isEmpty();
        assertThat(parser.duplicateFrontmatterKeys("---\n\tbad: [unclosed\n---\n")).isEmpty();
    }

    /// The duplicate wins the way YAML says it does — the check reports it, it
    /// does not change what the file means.
    // [utest->dsn~frontmatter-duplicate-keys~1]
    @Test
    void theLastDuplicateStillWinsWhenParsing() {
        GroupConfig config = parser.parseGroupConfig("""
                ---
                desktop: jabref
                desktop: cloudref
                ---
                """);

        assertThat(config.desktop()).isEqualTo("cloudref");
    }

    // [utest->dsn~complete-control-desktop~1]
    @Test
    void parsesStoredTabs() throws Exception {
        Task task = parser.parse("t", """
                ---
                title: T
                storedTabs:
                  - https://a.example/
                  - https://b.example/
                ---
                # Notes
                """);

        assertThat(task.storedTabs()).containsExactly("https://a.example/", "https://b.example/");
        assertThat(parser.parse("t", "---\ntitle: T\n---\n").storedTabs()).isEmpty();
    }

    // [utest->dsn~complete-control-desktop~1]
    @Test
    void withStoredTabsInsertsReplacesAndRemoves() throws Exception {
        String content = """
                ---
                title: T
                # a comment worth keeping
                status: active
                ---
                # Notes
                """;

        String stored = TaskFileParser.withStoredTabs(content,
                java.util.List.of("https://a.example/", "https://b.example/"));
        assertThat(parser.parse("t", stored).storedTabs())
                .containsExactly("https://a.example/", "https://b.example/");
        assertThat(stored).contains("# a comment worth keeping");

        // A second store replaces, never duplicates the key.
        String replaced = TaskFileParser.withStoredTabs(stored, java.util.List.of("https://c.example/"));
        assertThat(parser.parse("t", replaced).storedTabs()).containsExactly("https://c.example/");
        assertThat(parser.duplicateFrontmatterKeys(replaced)).isEmpty();

        // Clearing restores the original byte for byte.
        assertThat(TaskFileParser.withoutStoredTabs(stored)).isEqualTo(content);
        assertThat(TaskFileParser.withoutStoredTabs(content)).isEqualTo(content);
    }

    /// The PR-task skeleton both PR flows write must parse back into exactly
    /// the task they meant to create — a shape nobody notices being wrong
    /// until an auto category has filled the list with error rows.
    // [utest->dsn~auto-pr-category~3]
    // [utest->dsn~task-from-pr~6]
    @Test
    void prTaskContentParsesBackIntoTheTask() throws TaskParseException {
        String url = "https://github.com/o/r/pull/7";
        Task task = parser.parse("review/fix",
                TaskFileParser.prTaskContent("Fix: the label", "koppor@devbox", url,
                        "Added automatically from " + url + " on 2026-09-10."));
        assertThat(task.title()).isEqualTo("Fix: the label");
        assertThat(task.status()).isEqualTo(TaskStatus.ACTIVE);
        assertThat(task.remote()).isEqualTo("koppor@devbox");
        assertThat(task.prUrls()).containsExactly(url);
        assertThat(task.tmux()).isNull();
        assertThat(task.notes()).contains("Added automatically from " + url);

        // Compact mode (no provenance) and a category without a remote.
        Task compact = parser.parse("review/fix",
                TaskFileParser.prTaskContent("Plain", null, url, null));
        assertThat(compact.remote()).isNull();
        assertThat(compact.prUrls()).containsExactly(url);
        assertThat(compact.notes().strip()).isEqualTo("# Notes");
    }

    /// The mark that separates the reconcile's suspend from the user's must
    /// survive a write and vanish on the next status change — otherwise a
    /// dismissed row would be resurrected by the next round.
    // [utest->dsn~auto-pr-category~3]
    @Test
    void theAutoPrClosedMarkIsClearedByEveryStatusWrite() throws TaskParseException {
        String content = "---\ntitle: t\nstatus: active\n# a comment\nremote: r\n---\nnotes\n";
        String suspended = Frontmatter.set(
                TaskFileParser.withStatus(content, TaskStatus.SUSPENDED),
                TaskFileParser.AUTO_PR_CLOSED, true);
        Task task = parser.parse("t", suspended);
        assertThat(task.status()).isEqualTo(TaskStatus.SUSPENDED);
        assertThat(task.autoPrClosed()).isTrue();
        assertThat(task.suspendedAt()).isNotNull();
        assertThat(suspended).contains("# a comment");

        // Back to active: timestamp and mark both gone.
        String resumed = TaskFileParser.withStatus(suspended, TaskStatus.ACTIVE);
        assertThat(resumed).doesNotContain(TaskFileParser.AUTO_PR_CLOSED, "suspended:");
        assertThat(parser.parse("t", resumed).autoPrClosed()).isFalse();

        // A hand-made suspend of a once-marked task carries no mark — that is
        // what makes pausing a row a dismissal.
        String paused = TaskFileParser.withStatus(suspended, TaskStatus.SUSPENDED);
        assertThat(parser.parse("t", paused).autoPrClosed()).isFalse();
        assertThat(parser.parse("t", paused).status()).isEqualTo(TaskStatus.SUSPENDED);
    }

    /// The shape of the field report 2026-09-13: a OneNote "Copy Link" pasted
    /// where the commented `# note:` example had been — two keyless lines, so
    /// SnakeYAML gives up at the next key (`repo:`, line 5).
    private static final String PASTED_ONENOTE_CONFIG = """
            ---
            https://onedrive.live.com/view.aspx?resid=D7087%21509417&id=documents&wd=target%28x%29&end
            onenote:https://d.docs.live.net/d7087/Documents/SLR Angelika Kaplan.one#section-id={AAA-111}&end
            # remote: koppor@devbox
            repo: https://github.com/koppor/paper-slr-kaplan
            ---

            # paper-slr-kaplan
            """;

    // [utest->dsn~group-config-parse-error~1]
    @Test
    void aConfigThatDoesNotLoadNamesTheProblemAndItsFileLine() {
        assertThat(parser.groupConfigError(PASTED_ONENOTE_CONFIG))
                .isNotNull()
                .endsWith("(line 5)");
        assertThat(parser.parseGroupConfig(PASTED_ONENOTE_CONFIG)).isEqualTo(GroupConfig.EMPTY);
    }

    // [utest->dsn~group-config-parse-error~1]
    @Test
    void aLoadableEmptyOrAbsentConfigHasNoError() {
        assertThat(parser.groupConfigError("---\nremote: koppor@devbox\n---\n")).isNull();
        assertThat(parser.groupConfigError("---\n# only comments\n---\n")).isNull();
        assertThat(parser.groupConfigError("no frontmatter at all\n")).isNull();
        assertThat(parser.groupConfigError("---\n- a\n- b\n---\n"))
                .isEqualTo("Frontmatter must be a YAML mapping");
    }

    /// Unquoted `onenote:` links are legal YAML — `#` opens a comment only
    /// after whitespace, `{` is special only at the start of a value. Pinned so
    /// nobody "fixes" the template's `# note:` example by quoting it on a hunch.
    /// A space before the `#` does truncate silently; no warning for it — a
    /// OneNote link never has one there, and `url # comment` is legal YAML.
    // [utest->dsn~onenote-paste-repair~1]
    @Test
    void unquotedOnenoteLinksSurviveWhole() {
        String template = "onenote:https://d.docs.live.net/…/Projects.one#Page&section-id={…}&page-id={…}&end";
        String real = "onenote:https://d.docs.live.net/d7087/Documents/SLR Angelika Kaplan.one#section-id={AAA-111}&end";
        for (String link : List.of(template, real)) {
            assertThat(TaskFileParser.groupNote("---\nnote: " + link + "\n---\n")).isEqualTo(link);
        }
        assertThat(TaskFileParser.groupNote("---\nnote: onenote:https://x/y.one #Page&end\n---\n"))
                .isEqualTo("onenote:https://x/y.one");
    }

    /// The category note resolves per machine like every other key, so the
    /// repair's `note-linux:` web link is what a Linux desktop opens.
    // [utest->dsn~machine-key-variants~1]
    @Test
    void groupNoteTakesTheOsVariant() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String expected = os.contains("win") ? "onenote:w"
                : os.contains("mac") || os.contains("darwin") ? "onenote:m" : "https://l";
        String config = """
                ---
                note: onenote:w
                note-mac: onenote:m
                note-linux: https://l
                ---
                """;

        assertThat(TaskFileParser.groupNote(config)).isEqualTo(expected);
    }

    // [utest->dsn~onenote-paste-repair~1]
    @Test
    void aPastedOnenoteClipboardBecomesTheNoteKey() {
        String repaired = parser.repairPastedOneNoteLink(PASTED_ONENOTE_CONFIG);

        assertThat(repaired).startsWith("""
                ---
                note-linux: https://onedrive.live.com/view.aspx?resid=D7087%21509417&id=documents&wd=target%28x%29&end
                note: onenote:https://d.docs.live.net/d7087/Documents/SLR Angelika Kaplan.one#section-id={AAA-111}&end
                # remote: koppor@devbox
                """);
        assertThat(parser.parseGroupConfig(repaired).repo())
                .isEqualTo("https://github.com/koppor/paper-slr-kaplan");
    }

    /// The clipboard pasted behind an existing `note: ` puts the web URL on
    /// the key line; it moves to `note-linux:` and the desktop link takes `note:`.
    // [utest->dsn~onenote-paste-repair~1]
    @Test
    void aClipboardPastedBehindNoteKeepsTheOnenoteLink() {
        String content = """
                ---
                note: https://onedrive.live.com/view.aspx?resid=X&end
                onenote:https://d.docs.live.net/x/Doc.one#Sec&section-id={A}&end
                ---
                """;

        String repaired = parser.repairPastedOneNoteLink(content);

        assertThat(parser.groupConfigError(repaired)).isNull();
        assertThat(repaired).isEqualTo("""
                ---
                note-linux: https://onedrive.live.com/view.aspx?resid=X&end
                note: onenote:https://d.docs.live.net/x/Doc.one#Sec&section-id={A}&end
                ---
                """);
    }

    /// Loadable files are never touched, and a broken one the repair cannot
    /// make load comes back as it was — no half-repaired write.
    // [utest->dsn~onenote-paste-repair~1]
    @Test
    void theRepairLeavesLoadableAndUnrepairableFilesAlone() {
        String fine = "---\nnote: onenote:https://x/y.one#S&end\n---\n";
        String otherwiseBroken = "---\nonenote:https://x/y.one#S&end\n\tbad: [unclosed\n---\n";

        assertThat(parser.repairPastedOneNoteLink(fine)).isSameAs(fine);
        assertThat(parser.repairPastedOneNoteLink(otherwiseBroken)).isSameAs(otherwiseBroken);
    }
}
