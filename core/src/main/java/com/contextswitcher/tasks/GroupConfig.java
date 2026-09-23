package com.contextswitcher.tasks;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// Per-group defaults read from a folder's `CONTEXTSWITCHER.md` frontmatter
/// (https://github.com/contextswitcher/contextswitcher-private/issues/45). All keys are optional. `remote` is the ssh destination a new
/// task in the group inherits; the task's working directory is `workspacesRoot`
/// (the shared root where Claude creates and manages each task's own working
/// directory) unless `workdir` pins a fixed directory instead. `repo` is the
/// group's GitHub repository, passed to a live task's bootstrap prompt so
/// Claude can set up the worktree without asking. `desktop` is the name of the
/// virtual desktop this category lives on, focused by the header's play button
/// (nothing else). `mainCheckout` is a permanent checkout of `repo` — the
/// directory "Show diff" falls back to once a task's own worktree is gone.
/// `baseBranch` is what the group's task worktrees branch from — the base of
/// the refactoring view/badge diff (https://github.com/contextswitcher/contextswitcher-private/issues/50); unset means `origin/main`.
/// `tags` are inherited by every task in the group (as if each task carried
/// them), so filtering by a group tag shows the whole group.
/// `intellij`, `browser`, and `folders` are the category's switch-action
/// defaults, used for every task in the group that configures no such section
/// of its own ([Task#withCategoryDefaults]).
/// `completeControl` is the nested `desktop:` form's flag
/// (`desktop: {name: …, completeControl: true}`): suspending a task of this
/// group stores and closes every Firefox window on the named desktop, and
/// resuming reopens the stored tabs (`dsn~complete-control-desktop~1`).
/// `pinned` lifts the category above the unpinned ones in the task list
/// (`dsn~pinned-categories~1`).
/// `autoPr` turns the category into an *auto category*: its tasks are not
/// added by hand but reconciled from a GitHub PR search
/// (`dsn~auto-pr-category~3`).
// [impl->dsn~group-config-apply~2]
// [impl->dsn~task-tag-model~2]
// [impl->dsn~category-desktop-focus~4]
// [impl->dsn~diff-after-worktree-removal~1]
// [impl->dsn~refactoring-miner-commands~1]
// [impl->dsn~category-action-defaults~1]
// [impl->dsn~complete-control-desktop~1]
// [impl->dsn~pinned-categories~1]
// [impl->dsn~auto-pr-category~3]
// [impl->dsn~auto-delete-opt-in~1]
public record GroupConfig(
        @Nullable String remote,
        @Nullable String workspacesRoot,
        @Nullable String workdir,
        @Nullable String repo,
        @Nullable String desktop,
        @Nullable String mainCheckout,
        @Nullable String baseBranch,
        Task.@Nullable IntellijConfig intellij,
        Task.@Nullable BrowserConfig browser,
        List<String> folders,
        List<String> tags,
        boolean completeControl,
        boolean pinned,
        @Nullable AutoPr autoPr,
        boolean autoDelete) {

    /// The `auto:` section of an auto category: the GitHub `search` `query`
    /// whose open pull requests become tasks, the size filter `maxSloc` (the
    /// most non-test lines a PR may change to qualify; `0` = no size filter),
    /// and `deleteHours` — how long after its PR left the query a task is kept
    /// before the file is deleted (`0` = kept for good).
    // [impl->dsn~auto-pr-category~3]
    public record AutoPr(String query, int maxSloc, int deleteHours) {

        /// The grace period between a PR leaving the query and its task file
        /// being deleted, for a category that names none.
        public static final int DEFAULT_DELETE_HOURS = 24;
    }

    /// The `baseBranch` every group without one diffs against.
    public static final String DEFAULT_BASE_BRANCH = "origin/main";

    public static final GroupConfig EMPTY = new GroupConfig(null, null, null, null, null, null,
            null, null, null, List.of(), List.of(), false, false, null, false);

    /// Back-compat constructor without `autoDelete` (defaults to off — nothing
    /// in the category is ever deleted unattended).
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo, @Nullable String desktop,
            @Nullable String mainCheckout, @Nullable String baseBranch,
            Task.@Nullable IntellijConfig intellij, Task.@Nullable BrowserConfig browser,
            List<String> folders, List<String> tags, boolean completeControl, boolean pinned,
            @Nullable AutoPr autoPr) {
        this(remote, workspacesRoot, workdir, repo, desktop, mainCheckout, baseBranch,
                intellij, browser, folders, tags, completeControl, pinned, autoPr, false);
    }

    /// Whether an automatism may delete `task` of this category without asking
    /// (the merged-task janitor, an auto category's expiry, a closed disposable
    /// shell): only when the category opted in with `autoDelete: true`, and
    /// never a pinned task.
    // [impl->dsn~auto-delete-opt-in~1]
    public boolean mayAutoDelete(Task task) {
        return autoDelete && !task.pinned();
    }

    /// Back-compat constructor without `autoPr` (defaults to a plain,
    /// hand-filled category).
    // [impl->dsn~auto-pr-category~3]
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo, @Nullable String desktop,
            @Nullable String mainCheckout, @Nullable String baseBranch,
            Task.@Nullable IntellijConfig intellij, Task.@Nullable BrowserConfig browser,
            List<String> folders, List<String> tags, boolean completeControl, boolean pinned) {
        this(remote, workspacesRoot, workdir, repo, desktop, mainCheckout, baseBranch,
                intellij, browser, folders, tags, completeControl, pinned, null);
    }

    /// Back-compat constructor without `pinned` (defaults to unpinned).
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo, @Nullable String desktop,
            @Nullable String mainCheckout, @Nullable String baseBranch,
            Task.@Nullable IntellijConfig intellij, Task.@Nullable BrowserConfig browser,
            List<String> folders, List<String> tags, boolean completeControl) {
        this(remote, workspacesRoot, workdir, repo, desktop, mainCheckout, baseBranch,
                intellij, browser, folders, tags, completeControl, false);
    }

    /// Back-compat constructor without `completeControl` (defaults to off).
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo, @Nullable String desktop,
            @Nullable String mainCheckout, @Nullable String baseBranch,
            Task.@Nullable IntellijConfig intellij, Task.@Nullable BrowserConfig browser,
            List<String> folders, List<String> tags) {
        this(remote, workspacesRoot, workdir, repo, desktop, mainCheckout, baseBranch,
                intellij, browser, folders, tags, false);
    }

    /// Back-compat constructor without the switch-action defaults.
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo, @Nullable String desktop,
            @Nullable String mainCheckout, @Nullable String baseBranch, List<String> tags) {
        this(remote, workspacesRoot, workdir, repo, desktop, mainCheckout, baseBranch,
                null, null, List.of(), tags);
    }

    /// Back-compat constructor without `baseBranch` (defaults to unset).
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo, @Nullable String desktop,
            @Nullable String mainCheckout, List<String> tags) {
        this(remote, workspacesRoot, workdir, repo, desktop, mainCheckout, null, tags);
    }

    /// Back-compat constructor without `mainCheckout` (defaults to unset).
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo, @Nullable String desktop,
            List<String> tags) {
        this(remote, workspacesRoot, workdir, repo, desktop, null, null, tags);
    }

    /// The branch the group's diffs are based on — `baseBranch`, defaulted.
    public String resolveBaseBranch() {
        return baseBranch != null ? baseBranch : DEFAULT_BASE_BRANCH;
    }

    /// Defensive copies so the group's list values stay immutable.
    public GroupConfig {
        tags = List.copyOf(tags);
        folders = List.copyOf(folders);
    }

    /// Back-compat constructor without `desktop`/`tags` (both default to empty).
    public GroupConfig(@Nullable String remote, @Nullable String workspacesRoot,
            @Nullable String workdir, @Nullable String repo) {
        this(remote, workspacesRoot, workdir, repo, null, List.of());
    }

    public boolean isEmpty() {
        return remote == null && workspacesRoot == null && workdir == null && repo == null
                && desktop == null && mainCheckout == null && baseBranch == null
                && intellij == null && browser == null && folders.isEmpty() && tags.isEmpty()
                && !pinned && autoPr == null && !autoDelete;
    }

    /// The working directory a new task in the group inherits: an explicit
    /// `workdir` wins, else `workspacesRoot` verbatim (Claude manages the
    /// per-task subdirectory under it — no task suffix is appended), else null
    /// (the group sets no working directory).
    public @Nullable String resolveWorkdir() {
        return workdir != null ? workdir : workspacesRoot;
    }

    /// Whether a live task should tell Claude to create a fresh worktree: the
    /// group uses a shared `workspacesRoot` (Claude owns the per-task subdir),
    /// not a fixed `workdir` (an existing checkout to reuse as-is).
    // [impl->dsn~task-create-live~8]
    public boolean bootstrapWorktree() {
        return workdir == null && workspacesRoot != null;
    }

    /// The directories the *category* owns — its permanent `mainCheckout`
    /// (spelled out or [#conventionalMainCheckout]), the shared
    /// `workspacesRoot`, a pinned `workdir`. None of them is a per-task
    /// worktree, so none may ever reach the delete dialog's `rm -rf`
    /// (`dsn~claude-session-kill~6`).
    // [impl->dsn~claude-session-kill~6]
    public List<String> protectedDirs() {
        return java.util.stream.Stream.of(
                        mainCheckout, conventionalMainCheckout(), workspacesRoot, workdir)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /// The primary clone a category has even when it spells no
    /// `mainCheckout:` — `<workspacesRoot>/<repo name>`, the very path
    /// [TaskFileParser#groupConfigForRepo] writes. The hand-edited skeleton
    /// leaves that line commented out, so most categories carry no
    /// `mainCheckout:` at all, and a session that deletes its own worktree and
    /// steps back into the primary clone publishes *that* as its workspace —
    /// which the delete dialog then offered for `rm -rf` (field report
    /// 2026-09-12). Null when the category names no `workspacesRoot`/`repo`,
    /// or the `repo` is no URL.
    // [impl->dsn~claude-session-kill~6]
    private @Nullable String conventionalMainCheckout() {
        if (workspacesRoot == null || repo == null) {
            return null;
        }
        String name = TaskFileParser.repoName(repo);
        if (name == null) {
            return null;
        }
        String root = workspacesRoot.endsWith("/")
                ? workspacesRoot.substring(0, workspacesRoot.length() - 1)
                : workspacesRoot;
        return root + "/" + name;
    }
}
