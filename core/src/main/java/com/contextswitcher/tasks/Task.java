package com.contextswitcher.tasks;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// A task as defined by one Markdown file with YAML frontmatter.
/// Action configs (`tmux`, `intellij`, `browser`) are null when
/// the task file does not configure them; only configured actions run on switch.
/// `remote` is the ssh destination (a `~/.ssh/config` alias or `user@host`)
/// used by all remote actions unless a section overrides it.
/// `folders` are the local directories the switch opens in the file manager
/// (empty when the task configures none).
/// `storedTabs` are the URLs of the Firefox tabs a complete-control suspend
/// stored right before closing their windows; the next resume reopens them
/// and clears the list (`dsn~complete-control-desktop~1`).
/// `chat` is the task's Matrix room (a `matrix.to` permalink or an `element:`
/// URL), opened in the Element desktop app on switch.
/// `pinned` lifts the task above the unpinned ones of its status block
/// (`dsn~pinned-tasks~2`).
/// `autoPrClosed` marks a suspend an auto category did *because the task's
/// pull request ended* — the one suspend it may undo when the pull request
/// comes back (`dsn~auto-pr-category~3`). Every write of a `status:` clears
/// it ([TaskFileParser#withStatus]), so a suspend the user made by hand — the
/// way a pull request is dismissed from an auto category — never carries it
/// and is never resurrected.
// [impl->dsn~task-file-parsing~4]
// [impl->dsn~complete-control-desktop~1]
// [impl->dsn~pinned-tasks~2]
// [impl->dsn~auto-pr-category~3]
public record Task(
        String id,
        String title,
        TaskStatus status,
        @Nullable String remote,
        @Nullable TmuxConfig tmux,
        @Nullable TerminalConfig terminal,
        @Nullable IntellijConfig intellij,
        @Nullable BrowserConfig browser,
        @Nullable ClaudeConfig claude,
        @Nullable String note,
        List<String> folders,
        List<String> tags,
        List<String> storedTabs,
        @Nullable String suspendedAt,
        @Nullable String chat,
        String notes,
        boolean pinned,
        boolean autoPrClosed) {

    /// Convenience constructor without `autoPrClosed` (defaults to a suspend
    /// no auto category made).
    // [impl->dsn~auto-pr-category~3]
    public Task(String id, String title, TaskStatus status, @Nullable String remote,
            @Nullable TmuxConfig tmux, @Nullable TerminalConfig terminal, @Nullable IntellijConfig intellij,
            @Nullable BrowserConfig browser, @Nullable ClaudeConfig claude, @Nullable String note,
            List<String> folders, List<String> tags, List<String> storedTabs,
            @Nullable String suspendedAt, @Nullable String chat, String notes, boolean pinned) {
        this(id, title, status, remote, tmux, terminal, intellij, browser, claude, note,
                folders, tags, storedTabs, suspendedAt, chat, notes, pinned, false);
    }

    /// Defensive copies so a task's tag and folder lists stay immutable.
    // [impl->dsn~task-tag-model~2]
    // [impl->dsn~explorer-folder-focus~3]
    public Task {
        tags = List.copyOf(tags);
        folders = List.copyOf(folders);
        storedTabs = List.copyOf(storedTabs);
    }

    /// Convenience constructor without `pinned` (defaults to unpinned).
    // [impl->dsn~pinned-tasks~2]
    public Task(String id, String title, TaskStatus status, @Nullable String remote,
            @Nullable TmuxConfig tmux, @Nullable TerminalConfig terminal, @Nullable IntellijConfig intellij,
            @Nullable BrowserConfig browser, @Nullable ClaudeConfig claude, @Nullable String note,
            List<String> folders, List<String> tags, List<String> storedTabs,
            @Nullable String suspendedAt, @Nullable String chat, String notes) {
        this(id, title, status, remote, tmux, terminal, intellij, browser, claude, note,
                folders, tags, storedTabs, suspendedAt, chat, notes, false);
    }

    /// Convenience constructor without `suspendedAt` (unknown), so callers
    /// predating the suspend timestamp stay unchanged.
    // [impl->dsn~suspended-timestamp~1]
    public Task(String id, String title, TaskStatus status, @Nullable String remote,
            @Nullable TmuxConfig tmux, @Nullable TerminalConfig terminal, @Nullable IntellijConfig intellij,
            @Nullable BrowserConfig browser, @Nullable ClaudeConfig claude, @Nullable String note,
            List<String> folders, List<String> tags, List<String> storedTabs, String notes) {
        this(id, title, status, remote, tmux, terminal, intellij, browser, claude, note,
                folders, tags, storedTabs, null, null, notes);
    }

    /// Convenience constructor without `storedTabs` (defaults to empty), so
    /// callers predating the complete-control suspend stay unchanged.
    public Task(String id, String title, TaskStatus status, @Nullable String remote,
            @Nullable TmuxConfig tmux, @Nullable TerminalConfig terminal, @Nullable IntellijConfig intellij,
            @Nullable BrowserConfig browser, @Nullable ClaudeConfig claude, @Nullable String note,
            List<String> folders, List<String> tags, String notes) {
        this(id, title, status, remote, tmux, terminal, intellij, browser, claude, note,
                folders, tags, List.of(), notes);
    }

    /// Convenience constructor without `tags` (defaults to empty), so callers
    /// predating tagging stay unchanged.
    // [impl->dsn~task-tag-model~2]
    public Task(String id, String title, TaskStatus status, @Nullable String remote,
            @Nullable TmuxConfig tmux, @Nullable TerminalConfig terminal, @Nullable IntellijConfig intellij,
            @Nullable BrowserConfig browser, @Nullable ClaudeConfig claude, @Nullable String note,
            @Nullable String folder, String notes) {
        this(id, title, status, remote, tmux, terminal, intellij, browser, claude, note,
                folder == null ? List.of() : List.of(folder), List.of(), notes);
    }

    /// Convenience constructor without the `folder` action (defaults to none),
    /// so callers predating the local-folder action stay unchanged.
    public Task(String id, String title, TaskStatus status, @Nullable String remote,
            @Nullable TmuxConfig tmux, @Nullable TerminalConfig terminal, @Nullable IntellijConfig intellij,
            @Nullable BrowserConfig browser, @Nullable ClaudeConfig claude, @Nullable String note,
            String notes) {
        this(id, title, status, remote, tmux, terminal, intellij, browser, claude, note,
                (String) null, notes);
    }

    /// Name of the Firefox tab group the task's tabs are collected in: the
    /// tmux window number (`window: "@339"` → `339`), or `l:<id>` for a task
    /// running no remote tmux window. A `window:` naming the window instead of
    /// numbering it is not unique across sessions and uses the `l:` form too.
    // [impl->dsn~browser-tab-group~2]
    public String tabGroup() {
        String window = tmux == null ? null : tmux.window();
        String number = window == null ? "" : window.replaceFirst("^@", "");
        return number.matches("\\d+") ? number : "l:" + id;
    }

    public record TmuxConfig(String session, @Nullable String window) {

        /// tmux target: a window id (`@17`) targets directly and survives
        /// renumbering; a name/index is scoped to the session; otherwise the
        /// whole session.
        public String target() {
            if (window == null) {
                return session;
            }
            return window.startsWith("@") ? window : session + ":" + window;
        }
    }

    /// A local terminal running the task's session (alternative to remote
    /// `tmux`): the fixed title of the Windows Terminal tab to focus. The user
    /// pins the title via WT "Rename Tab" so it stays stable and unambiguous.
    public record TerminalConfig(String tabTitle) {
    }

    /// `projectPath` is optional: it falls back to the Claude session's
    /// workspace (then cwd), so a task with a `claude:` section only needs a
    /// bare `intellij:` key — an explicit path is the exception (no Claude
    /// involved, or a different project).
    public record IntellijConfig(@Nullable String projectPath, @Nullable String remote, @Nullable String ide) {
    }

    /// One `browser.urls` entry: a URL with an optional short human title
    /// (`"— Probeklausur in moodle"`). The YAML stays terse — a plain
    /// scalar for the common untitled case, a `{url, title}` map only when
    /// a title is set.
    public record UrlEntry(String url, @Nullable String title) {

        /// How the entry reads where there is room for it (the status bar's
        /// hover and "Opening …" lines): the title ahead of the URL, or the
        /// bare URL when there is no title. The title alone would not do —
        /// the point of showing the URL at all is to see *which* address the
        /// icon stands for.
        public String display() {
            return title == null || title.isBlank() ? url : title + " — " + url;
        }
    }

    public record BrowserConfig(List<UrlEntry> entries) {
        public BrowserConfig {
            entries = List.copyOf(entries);
        }

        /// A config of untitled URLs — the common case, and terser for callers
        /// (and tests) that only have addresses.
        public static BrowserConfig ofUrls(String... urls) {
            return new BrowserConfig(java.util.Arrays.stream(urls)
                    .map(url -> new UrlEntry(url, null)).toList());
        }

        /// The URLs only — the switch/close actions and the existence check
        /// care about the addresses, not the titles.
        public List<String> urls() {
            return entries.stream().map(UrlEntry::url).toList();
        }
    }

    /// The Claude session living in the task's tmux window. `cwd` is where
    /// Claude was started (project root — enough to resume after a host reboot
    /// with `cd cwd && claude --resume id`). `workspace` is the working
    /// subdirectory Claude reported for itself via the `@cs_workspace` tmux
    /// user option, when set — it may differ from `cwd`. `commit` is the last
    /// commit the session published via `@cs_commit`: the one thing that
    /// outlives a worktree Claude deletes after committing, so "Show diff" can
    /// still show the task's own change (`dsn~diff-after-worktree-removal~1`).
    public record ClaudeConfig(String cwd, @Nullable String sessionId, @Nullable String workspace,
            @Nullable String commit) {

        /// Back-compat constructor without `commit` (defaults to unset).
        public ClaudeConfig(String cwd, @Nullable String sessionId, @Nullable String workspace) {
            this(cwd, sessionId, workspace, null);
        }
    }

    /// PR URLs of the task (for the PR-state indicator, https://github.com/contextswitcher/contextswitcher-private/issues/49): every
    /// `browser.urls` entry that is a GitHub pull-request URL or a GitLab
    /// merge-request URL on any host (`dsn~gitlab-mr-state~1`). A task can
    /// carry several — one Claude session often opens a code PR and a docs PR.
    // [impl->dsn~pr-state-indicator~3]
    // [impl->dsn~gitlab-mr-state~1]
    // Package-private: `TaskFileParser.addPrUrlsFrom` matches the same shape.
    static final java.util.regex.Pattern PR_URL = java.util.regex.Pattern.compile(
            "https://github\\.com/[\\w.-]+/[\\w.-]+/pull/\\d+/?"
                    + "|https://[^/\\s]+/(?:[\\w.-]+/)+[\\w.-]+/-/merge_requests/\\d+/?");

    /// Any http(s) URL mentioned in free text (a task description, a queued
    /// message) — broader than [#PR_URL], for `TaskFileParser.addUrlsFrom`.
    private static final java.util.regex.Pattern URL =
            java.util.regex.Pattern.compile("https?://\\S+");

    /// URLs found in `text`, trailing sentence punctuation (that wraps
    /// around, not part of, the link) stripped. Order preserved, not
    /// deduplicated — callers dedupe as needed.
    // [impl->dsn~task-url-collect~1]
    public static List<String> urlsIn(String text) {
        return URL.matcher(text).results()
                .map(m -> stripTrailingPunctuation(m.group()))
                .toList();
    }

    private static String stripTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && ".,;:!?)]}'\"".indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }

    /// A URL without its trailing slashes — `…/pull/16247/` and
    /// `…/pull/16247` are the same page, and a task that collected both
    /// showed the PR twice (and the slashed one as a plain link, since
    /// [#PR_URL] does not match it). Used wherever URLs are compared or
    /// stored; the scheme's own `//` is never touched.
    // [impl->dsn~browser-url-dedupe~3]
    public static String normalizeUrl(String url) {
        int end = url.length();
        while (end > 1 && url.charAt(end - 1) == '/' && url.charAt(end - 2) != '/') {
            end--;
        }
        return url.substring(0, end);
    }

    public List<String> prUrls() {
        return prEntries().stream().map(UrlEntry::url).toList();
    }

    /// The PR entries with their titles, for the row icons — each icon shows
    /// its own URL (and title, when set) on hover.
    public List<UrlEntry> prEntries() {
        if (browser == null) {
            return List.of();
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        return browser.entries().stream()
                .filter(entry -> PR_URL.matcher(entry.url()).matches())
                .filter(entry -> seen.add(entry.url()))
                .toList();
    }

    /// The `browser.urls` entries that are *not* pull requests — the task's
    /// repository, an issue, a wiki page, a build. Each gets a plain link icon
    /// on the row left of the PR icons: the PR filter left every other address
    /// of a task invisible, reachable only by switching to it.
    // [impl->dsn~task-link-icons~2]
    public List<UrlEntry> linkEntries() {
        if (browser == null) {
            return List.of();
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        return browser.entries().stream()
                .filter(entry -> !PR_URL.matcher(entry.url()).matches())
                .filter(entry -> seen.add(entry.url()))
                .toList();
    }

    /// How well `tabUrl` — the URL of the browser tab the user just activated
    /// — matches this task's `browser.urls`: 2 for an exact hit, 1 for a page
    /// *under* one of them (a PR's `/files` sub-path, a fragment, a query),
    /// 0 for none. The boundary characters stop a longer sibling from matching
    /// (`.../pull/157850` is not `.../pull/15785`) — the same rule the
    /// extension's `isSameOrSubUrl` applies when focusing a tab.
    // [impl->dsn~browser-tab-selects-task~5]
    public int tabUrlMatch(String tabUrl) {
        if (browser == null) {
            return 0;
        }
        tabUrl = normalizeUrl(tabUrl);
        int best = 0;
        for (String url : browser.urls()) {
            if (tabUrl.equals(url)) {
                return 2;
            }
            if (tabUrl.startsWith(url + "/") || tabUrl.startsWith(url + "#") || tabUrl.startsWith(url + "?")) {
                best = 1;
            }
        }
        return best;
    }

    /// The task's primary PR — the first one — for the single-PR consumers
    /// (Qodo review sync). Null when the task has none.
    public @Nullable String prUrl() {
        return prUrls().stream().findFirst().orElse(null);
    }

    /// The task as it switches inside its category: every action section the
    /// task does not configure itself falls back to the category's default
    /// from its `CONTEXTSWITCHER.md` (`intellij`, `browser`, `folders`).
    /// The fallback is per section, not per key — a task that carries a
    /// section at all wins with it as a whole, so overriding one URL never
    /// means restating the rest of the category's configuration.
    /// (`note:` is not merged here: [com.contextswitcher.switching.NoteFocusAction]
    /// resolves the same fallback itself, to report which of the two it opened.)
    // [impl->dsn~category-action-defaults~1]
    public Task withCategoryDefaults(GroupConfig category) {
        IntellijConfig categoryIntellij = intellij == null ? category.intellij() : intellij;
        BrowserConfig categoryBrowser = browser == null ? category.browser() : browser;
        List<String> categoryFolders = folders.isEmpty() ? category.folders() : folders;
        if (categoryIntellij == intellij && categoryBrowser == browser
                && categoryFolders.equals(folders)) {
            return this;
        }
        return new Task(id, title, status, remote, tmux, terminal, categoryIntellij,
                categoryBrowser, claude, note, categoryFolders, tags, storedTabs, suspendedAt,
                chat, notes, pinned, autoPrClosed);
    }

    /// The task with its tmux window replaced — how the phone points a task
    /// whose synced file is stale at the window it lives in now
    /// ([com.contextswitcher.discovery.TmuxSync#currentWindowId]). Unchanged
    /// without a `tmux:` section.
    // [impl->dsn~android-window-lookup~1]
    public Task withTmuxWindow(String window) {
        if (tmux == null) {
            return this;
        }
        return new Task(id, title, status, remote, new TmuxConfig(tmux.session(), window), terminal, intellij,
                browser, claude, note, folders, tags, storedTabs, suspendedAt, chat, notes, pinned, autoPrClosed);
    }

    /// Remote for the IntelliJ action: section override, falling back to the task remote.
    public @Nullable String intellijRemote() {
        if (intellij != null && intellij.remote() != null) {
            return intellij.remote();
        }
        return remote;
    }

    /// Project path for the IntelliJ action: explicit `intellij.projectPath`,
    /// falling back to `claude.workspace`, then `claude.cwd`.
    public @Nullable String intellijProjectPath() {
        if (intellij != null && intellij.projectPath() != null) {
            return intellij.projectPath();
        }
        if (claude == null) {
            return null;
        }
        return claude.workspace() != null ? claude.workspace() : claude.cwd();
    }

    /// Whether the IntelliJ action would open the Claude session's start `cwd`
    /// (the shared workspaces root) because the session never published a
    /// worktree of its own — e.g. it only read a PR. Nothing to open there, so
    /// the action is disabled. An explicit `intellij.projectPath` wins.
    // [impl->dsn~open-in-intellij~3]
    public boolean lacksWorktree() {
        return (intellij == null || intellij.projectPath() == null)
                && claude != null
                && (claude.workspace() == null || claude.workspace().equals(claude.cwd()));
    }

    /// Whether this task records nothing worth keeping once its tmux window is
    /// gone: no Claude session (nothing to `--resume`), no IntelliJ/terminal
    /// config, no browser URLs, no local folders, no `note:`, no tags, and an empty Notes body —
    /// a bare shell such as a plain `bash` window imported from tmux and then
    /// closed. `title`, `remote`, `tmux`, `status`, and `folder` do not count
    /// as content (an imported shell always has them). Such a task is deleted
    /// rather than suspended when its window disappears (`dsn~tmux-sync~7`).
    public boolean isDisposableShell() {
        return claude == null && intellij == null && terminal == null
                && (browser == null || browser.urls().isEmpty())
                && folders.isEmpty() && note == null && tags.isEmpty()
                && storedTabs.isEmpty() && notesAreEmpty(notes);
    }

    /// A Notes body carrying no user content: blank, or only the bare
    /// `# Notes` heading every created/imported task starts with.
    private static boolean notesAreEmpty(String notes) {
        return notes.lines()
                .map(String::strip)
                .noneMatch(line -> !line.isEmpty() && !line.equalsIgnoreCase("# Notes"));
    }
}
