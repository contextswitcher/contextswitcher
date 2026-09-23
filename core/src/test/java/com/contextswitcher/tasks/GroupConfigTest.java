package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~group-config-apply~2]
class GroupConfigTest {

    private final TaskFileParser parser = new TaskFileParser();

    @Test
    void resolveWorkdirPrefersFixedWorkdirOverWorkspacesRoot() {
        GroupConfig config = new GroupConfig("r", "/ws", "/shared", null);
        assertThat(config.resolveWorkdir()).isEqualTo("/shared");
    }

    @Test
    void resolveWorkdirIsWorkspacesRootVerbatimNoTaskSuffix() {
        // Claude manages the per-task subdir, so no task suffix is appended.
        assertThat(new GroupConfig("r", "/data/koppor/jabref-workspaces", null, null).resolveWorkdir())
                .isEqualTo("/data/koppor/jabref-workspaces");
    }

    @Test
    void resolveWorkdirNullWhenNeitherSet() {
        assertThat(new GroupConfig("r", null, null, null).resolveWorkdir()).isNull();
        assertThat(GroupConfig.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void bootstrapWorktreeOnlyForSharedWorkspacesRoot() {
        // workspacesRoot → Claude creates the worktree; a fixed workdir does not.
        assertThat(new GroupConfig("r", "/ws", null, null).bootstrapWorktree()).isTrue();
        assertThat(new GroupConfig("r", "/ws", "/fixed", null).bootstrapWorktree()).isFalse();
        assertThat(new GroupConfig("r", null, null, null).bootstrapWorktree()).isFalse();
    }

    // [utest->dsn~pinned-categories~1]
    @Test
    void parsesAndWritesThePinnedFlag() {
        assertThat(parser.parseGroupConfig("---\npinned: true\n---\n").pinned()).isTrue();
        assertThat(parser.parseGroupConfig("---\nremote: r\n---\n").pinned()).isFalse();
        assertThat(parser.parseGroupConfig("---\npinned: false\n---\n").pinned()).isFalse();

        String config = "---\n# a comment\nremote: r\n---\n\n# jabref\n";
        String pinned = TaskFileParser.withPinned(config, true);
        assertThat(parser.parseGroupConfig(pinned).pinned()).isTrue();
        assertThat(pinned).contains("# a comment");
        // Pinning off removes the key again, restoring the original file.
        assertThat(TaskFileParser.withPinned(pinned, false)).isEqualTo(config);
        assertThat(TaskFileParser.withPinned(config, false)).isEqualTo(config);
        // An existing line is replaced, not duplicated.
        assertThat(TaskFileParser.withPinned("---\npinned: false\n---\n", true))
                .isEqualTo("---\npinned: true\n---\n");
    }

    // Tax paperwork may get its answer months later: nothing is deleted
    // unattended unless the category asks for it, and a pinned task never.
    // [utest->dsn~auto-delete-opt-in~1]
    @Test
    void autoDeleteIsOptInAndSparesPinnedTasks() throws Exception {
        Task plain = parser.parse("c/t", "---\ntitle: t\n---\n");
        Task pinned = parser.parse("c/t", "---\ntitle: t\npinned: true\n---\n");

        GroupConfig optedOut = parser.parseGroupConfig("---\nremote: r\n---\n");
        assertThat(optedOut.mayAutoDelete(plain)).isFalse();
        assertThat(GroupConfig.EMPTY.mayAutoDelete(plain)).isFalse();

        GroupConfig optedIn = parser.parseGroupConfig("---\nautoDelete: true\n---\n");
        assertThat(optedIn.isEmpty()).isFalse();
        assertThat(optedIn.mayAutoDelete(plain)).isTrue();
        assertThat(optedIn.mayAutoDelete(pinned)).isFalse();
    }

    // [utest->dsn~auto-pr-category~3]
    @Test
    void parsesTheAutoSection() {
        GroupConfig config = parser.parseGroupConfig("""
                ---
                repo: https://github.com/JabRef/jabref
                auto:
                  query: "repo:JabRef/jabref review-requested:@me"
                  maxSloc: 50
                  deleteHours: 12
                ---
                """);
        assertThat(config.autoPr()).isEqualTo(new GroupConfig.AutoPr(
                "repo:JabRef/jabref review-requested:@me", 50, 12));
        assertThat(config.isEmpty()).isFalse();
    }

    // [utest->dsn~auto-pr-category~3]
    @Test
    void autoSectionDefaultsAndLenientNumbers() {
        // The configuration form writes the numbers as strings.
        GroupConfig quoted = parser.parseGroupConfig("""
                ---
                auto:
                  query: is:pr
                  maxSloc: "50"
                ---
                """);
        assertThat(quoted.autoPr())
                .isEqualTo(new GroupConfig.AutoPr("is:pr", 50,
                        GroupConfig.AutoPr.DEFAULT_DELETE_HOURS));
        // Unreadable numbers fall back instead of failing the category.
        assertThat(parser.parseGroupConfig("---\nauto:\n  query: q\n  maxSloc: soon\n---\n")
                .autoPr().maxSloc()).isZero();
    }

    // [utest->dsn~auto-pr-category~3]
    @Test
    void withoutAQueryTheCategoryStaysHandFilled() {
        assertThat(parser.parseGroupConfig("---\nauto:\n  maxSloc: 50\n---\n").autoPr()).isNull();
        assertThat(parser.parseGroupConfig("---\nauto:\n  query: \"  \"\n---\n").autoPr()).isNull();
        assertThat(parser.parseGroupConfig("---\nauto: true\n---\n").autoPr()).isNull();
        assertThat(parser.parseGroupConfig("---\nremote: r\n---\n").autoPr()).isNull();
    }

    @Test
    void parsesConfiguredKeys() {
        String content = """
                ---
                remote: koppor@devbox
                workspacesRoot: /data/koppor/jabref-workspaces
                repo: https://github.com/JabRef/jabref
                ---
                # jabref — group defaults
                """;

        GroupConfig config = parser.parseGroupConfig(content);

        assertThat(config.remote()).isEqualTo("koppor@devbox");
        assertThat(config.workspacesRoot()).isEqualTo("/data/koppor/jabref-workspaces");
        assertThat(config.workdir()).isNull();
        assertThat(config.repo()).isEqualTo("https://github.com/JabRef/jabref");
        assertThat(config.desktop()).isNull();
        assertThat(config.mainCheckout()).isNull();
    }

    // [utest->dsn~diff-after-worktree-removal~1]
    @Test
    void parsesMainCheckout() {
        String content = """
                ---
                remote: r
                workspacesRoot: /data/koppor/jabref-workspaces
                mainCheckout: /data/koppor/jabref-workspaces/jabref
                ---
                """;

        assertThat(parser.parseGroupConfig(content).mainCheckout())
                .isEqualTo("/data/koppor/jabref-workspaces/jabref");
    }

    /// The primary clone is protected even when the category leaves
    /// `mainCheckout:` commented out (the skeleton's default), so the delete
    /// dialog cannot offer it for `rm -rf` after a session stepped back into
    /// it from its removed worktree.
    // [utest->dsn~claude-session-kill~6]
    @Test
    void protectedDirsCoverTheConventionalMainCheckout() {
        String content = """
                ---
                workspacesRoot: /data/koppor/contextswitcher-workspaces
                repo: https://github.com/contextswitcher/contextswitcher
                ---
                """;

        GroupConfig config = parser.parseGroupConfig(content);

        assertThat(config.mainCheckout()).isNull();
        assertThat(config.protectedDirs()).contains(
                "/data/koppor/contextswitcher-workspaces/contextswitcher");
        // A per-task worktree beside it stays removable.
        assertThat(config.protectedDirs())
                .doesNotContain("/data/koppor/contextswitcher-workspaces/2026-09-12-task");
    }

    /// Nothing to derive from: no `repo`, or a `repo` that is no URL.
    // [utest->dsn~claude-session-kill~6]
    @Test
    void protectedDirsWithoutARepoUrlStayAtTheNamedDirectories() {
        assertThat(parser.parseGroupConfig("""
                ---
                workspacesRoot: /data/koppor/ws
                ---
                """).protectedDirs()).containsExactly("/data/koppor/ws");
        assertThat(parser.parseGroupConfig("""
                ---
                workspacesRoot: /data/koppor/ws
                repo: owner/name
                ---
                """).protectedDirs()).containsExactly("/data/koppor/ws");
    }

    // [utest->dsn~refactoring-miner-commands~1]
    @Test
    void baseBranchDefaultsToOriginMainAndParses() {
        assertThat(GroupConfig.EMPTY.resolveBaseBranch()).isEqualTo("origin/main");

        String content = """
                ---
                remote: r
                baseBranch: upstream/develop
                ---
                """;
        GroupConfig config = parser.parseGroupConfig(content);
        assertThat(config.baseBranch()).isEqualTo("upstream/develop");
        assertThat(config.resolveBaseBranch()).isEqualTo("upstream/develop");
    }

    @Test
    void parsesDesktop() {
        String content = """
                ---
                remote: r
                desktop: vs.code
                ---
                """;

        assertThat(parser.parseGroupConfig(content).desktop()).isEqualTo("vs.code");
    }

    // [utest->dsn~complete-control-desktop~1]
    @Test
    void parsesNestedDesktopWithCompleteControl() {
        String content = """
                ---
                remote: r
                desktop:
                  name: JabRef
                  completeControl: true
                ---
                """;

        GroupConfig config = parser.parseGroupConfig(content);
        assertThat(config.desktop()).isEqualTo("JabRef");
        assertThat(config.completeControl()).isTrue();
    }

    // [utest->dsn~complete-control-desktop~1]
    @Test
    void scalarDesktopHasNoCompleteControl() {
        String content = """
                ---
                remote: r
                desktop: JabRef
                ---
                """;

        GroupConfig config = parser.parseGroupConfig(content);
        assertThat(config.desktop()).isEqualTo("JabRef");
        assertThat(config.completeControl()).isFalse();
    }

    // [utest->dsn~complete-control-desktop~1]
    @Test
    void nestedDesktopWithoutFlagOrName() {
        GroupConfig noFlag = parser.parseGroupConfig("---\ndesktop: {name: JabRef}\n---\n");
        assertThat(noFlag.desktop()).isEqualTo("JabRef");
        assertThat(noFlag.completeControl()).isFalse();

        // A half-edited mapping without a name enables nothing to act on.
        GroupConfig noName = parser.parseGroupConfig("---\ndesktop: {completeControl: true}\n---\n");
        assertThat(noName.desktop()).isNull();
        assertThat(noName.completeControl()).isTrue();
    }

    @Test
    void parsesGroupTags() {
        String content = """
                ---
                remote: r
                tags: [jabref, phone]
                ---
                """;

        assertThat(parser.parseGroupConfig(content).tags()).containsExactly("jabref", "phone");
    }

    @Test
    void toleratesMissingOrInvalidFrontmatter() {
        assertThat(parser.parseGroupConfig("no frontmatter here")).isEqualTo(GroupConfig.EMPTY);
        assertThat(parser.parseGroupConfig("---\nremote: r\n")).isEqualTo(GroupConfig.EMPTY); // no closing fence
        assertThat(parser.parseGroupConfig("---\n---\n")).isEqualTo(GroupConfig.EMPTY); // empty mapping
    }

    @Test
    void newTaskContentWithDefaultsWritesRemoteAndClaudeCwd() throws Exception {
        String content = TaskFileParser.newTaskContent(
                "Fix NPE", "koppor@devbox", "/data/koppor/jabref-workspaces/fix-npe");

        Task task = parser.parse("jabref/fix-npe", content);
        assertThat(task.remote()).isEqualTo("koppor@devbox");
        assertThat(task.claude()).isNotNull();
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/jabref-workspaces/fix-npe");
    }

    @Test
    void newTaskContentWithoutDefaultsKeepsSectionsCommented() throws Exception {
        String content = TaskFileParser.newTaskContent("Bare", null, null);

        Task task = parser.parse("bare", content);
        assertThat(task.remote()).isNull();
        assertThat(task.claude()).isNull();
        assertThat(content).contains("# remote:");
        assertThat(content).contains("# claude:");
        assertThat(content).contains("# folders:");
    }

    @Test
    void newTaskContentLocalGroupWritesWorkdirAsFolderNotClaudeCwd() throws Exception {
        // A local group (no remote) is the lightweight, no-tmux category: its
        // workdir is the local folder the switch action opens in Explorer.
        String content = TaskFileParser.newTaskContent(
                "Tidy up", null, "C:\\git-repositories\\koppor-tools");

        Task task = parser.parse("koppor-tools/tidy-up", content);
        assertThat(task.remote()).isNull();
        assertThat(task.folders()).containsExactly("C:\\git-repositories\\koppor-tools");
        assertThat(task.claude()).isNull();
        assertThat(content).contains("# claude:");
    }

    @Test
    void newTaskContentRemoteGroupWritesWorkdirAsClaudeCwdNotFolder() throws Exception {
        String content = TaskFileParser.newTaskContent(
                "Fix NPE", "koppor@devbox", "/data/koppor/jabref-workspaces/fix-npe");

        Task task = parser.parse("jabref/fix-npe", content);
        assertThat(task.claude()).isNotNull();
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/jabref-workspaces/fix-npe");
        assertThat(task.folders()).isEmpty();
        assertThat(content).contains("# folders:");
    }

    // Compact mode (hints off): only the real keys, no commented examples,
    // nothing below # Notes.
    // [utest->dsn~skeleton-hints~4]
    @Test
    void compactNewTaskContentCarriesOnlyRealKeys() throws Exception {
        String content = TaskFileParser.newTaskContent(
                "Fix NPE", "koppor@devbox", "/data/koppor/jabref-workspaces/fix-npe", false);

        assertThat(content.lines()
                .filter(line -> line.stripLeading().startsWith("#"))
                .toList()).containsExactly("# Notes");
        assertThat(content).endsWith("# Notes\n\n");
        Task task = parser.parse("jabref/fix-npe", content);
        assertThat(task.remote()).isEqualTo("koppor@devbox");
        assertThat(task.claude().cwd()).isEqualTo("/data/koppor/jabref-workspaces/fix-npe");
    }

    // [utest->dsn~skeleton-hints~4]
    @Test
    void compactNewTaskContentLocalGroupWritesFolder() throws Exception {
        String content = TaskFileParser.newTaskContent(
                "Tidy up", null, "C:\\git-repositories\\koppor-tools", false);

        assertThat(content).doesNotContain("# claude:", "# remote:", "# browser:");
        Task task = parser.parse("koppor-tools/tidy-up", content);
        assertThat(task.remote()).isNull();
        assertThat(task.folders()).containsExactly("C:\\git-repositories\\koppor-tools");
        assertThat(task.claude()).isNull();
    }

    // The intro group-config skeleton keeps the commented key examples but
    // never writes body prose (the file configures the group, it is not a
    // task).
    // [utest->dsn~skeleton-hints~4]
    @Test
    void introGroupConfigSkeletonCarriesExamplesButNoBodyProse() {
        String content = TaskFileParser.groupConfigSkeleton("jabref", "koppor@devbox", true);

        assertThat(content).contains("remote: koppor@devbox",
                "# workspacesRoot: /data/koppor/jabref-workspaces", "# tags: [needs-review]");
        assertThat(content).endsWith("# jabref — group defaults\n");
        // The machine-suffix example names this machine, ready to uncomment.
        assertThat(content).contains("(this machine: ", "# workspacesRoot-", ": /data/koppor/jabref-workspaces\n");
        GroupConfig config = parser.parseGroupConfig(content);
        assertThat(config.remote()).isEqualTo("koppor@devbox");
        assertThat(config.workspacesRoot()).isNull();
    }

    // Compact mode (hints off): every key stays as a `# key: example` comment
    // (remote real when configured), but the human-readable explanation lines
    // are gone — each frontmatter line is YAML, commented or not.
    // [utest->dsn~skeleton-hints~4]
    @Test
    void compactGroupConfigSkeletonKeepsKeyCommentsButNoProse() {
        String content = TaskFileParser.groupConfigSkeleton("jabref", "koppor@devbox", false);

        assertThat(content).contains("remote: koppor@devbox",
                "# workspacesRoot: /data/koppor/jabref-workspaces",
                "# repo: https://github.com/owner/jabref",
                "# tags: [needs-review]", "# note: onenote:");
        // No explanation lines: every frontmatter comment is a `# key: value`.
        String frontmatter = content.substring(0, content.indexOf("---\n\n"));
        assertThat(frontmatter.lines()
                .filter(line -> line.startsWith("#"))
                .filter(line -> !line.matches("# \\w+: .*"))
                .toList()).isEmpty();
        GroupConfig config = parser.parseGroupConfig(content);
        assertThat(config.remote()).isEqualTo("koppor@devbox");
        assertThat(config.workspacesRoot()).isNull();
    }

    // A skeleton created from the desktop-filter empty state carries a real
    // (uncommented) `desktop:` line with the active desktop, in both flavors.
    // [utest->dsn~desktop-filter-empty-state~2]
    @Test
    void groupConfigSkeletonPreFillsDesktop() {
        for (boolean hints : new boolean[] {true, false}) {
            String content = TaskFileParser.groupConfigSkeleton("jabref", null, "JabRef Dev", hints);

            assertThat(content).contains("\ndesktop: JabRef Dev\n").doesNotContain("# desktop:");
            GroupConfig config = parser.parseGroupConfig(content);
            assertThat(config.desktop()).isEqualTo("JabRef Dev");
        }
    }

    // Removing the `# ` prefixes must yield valid YAML that parses into the
    // example values (one example tag, not the group name).
    // [utest->dsn~skeleton-hints~4]
    @Test
    void compactGroupConfigSkeletonCommentsAreValidYamlWhenUncommented() {
        String content = TaskFileParser.groupConfigSkeleton("tools", null, false);

        int close = content.indexOf("---\n\n");
        String uncommented = content.substring(0, close).lines()
                .map(line -> line.startsWith("# ") ? line.substring(2) : line)
                .reduce("", (a, b) -> a + b + "\n") + "---\n";
        GroupConfig config = parser.parseGroupConfig(uncommented);
        assertThat(config.remote()).isEqualTo("koppor@devbox");
        assertThat(config.workspacesRoot()).isEqualTo("/data/koppor/tools-workspaces");
        // [utest->dsn~auto-pr-category~3]
        assertThat(config.autoPr()).isEqualTo(new GroupConfig.AutoPr(
                "repo:owner/tools review-requested:@me", 50, 24));
        // No `workdir:` example any more — the skeleton stopped advertising it.
        assertThat(config.workdir()).isNull();
        assertThat(config.repo()).isEqualTo("https://github.com/owner/tools");
        assertThat(config.tags()).containsExactly("needs-review");
    }
}
