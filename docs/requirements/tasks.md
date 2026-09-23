# Task Storage

Design decision: [0002 — one Markdown file with YAML frontmatter per task](../decisions/0002-one-markdown-file-with-yaml-frontmatter-per-task.md).

## Requirements

### Task file format
`req~task-file-format~1`

A task is stored as one Markdown file: YAML frontmatter holds the structured switching configuration (title, status, default `remote` — the ssh destination, an alias from `~/.ssh/config` or `user@host` — and optional `tmux`, `intellij`, `browser` sections), the Markdown body holds free-form notes. Every action section is optional.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Task directory reload
`req~task-directory-reload~1`

Tasks are read from a configurable directory. Changes made to task files outside the application (create, modify, delete) are reflected in the application within about one second, without restart.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Machine-specific values in one file
`req~machine-specific-values~1`

The task directory is synchronised between machines (`req~task-git-backup~4`), but the paths in it are not portable: the same checkout is `C:\git\jabref` on the Windows desktop and `/data/koppor/jabref` on the Linux box.
Any frontmatter key — in a task file and in a category's `CONTEXTSWITCHER.md` — can therefore carry alternative values scoped to a machine, so one synchronised file works everywhere instead of diverging per machine.
The most specific value for the machine reading the file wins; a value scoped to another machine is ignored, and an unscoped value is the fallback.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Robust parse-error handling
`req~task-parse-error-handling~1`

A malformed task file must never crash the application or hide other tasks. The file is shown as an error entry naming the file and the parse problem.
The error entry offers a way to delete the offending file, so a corrupt task the user cannot otherwise act on (it has no task model, hence none of the normal row actions) can be cleared from the list.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Import tasks from running tmux windows
`req~import-tasks-from-tmux~2`

A tmux window (tab) represents one task. The user can point ContextSwitcher at an ssh host; for every running tmux window not yet covered by a task, a task file is generated with the `tmux` section (session + window) filled in and `intellij`/`browser` prepared as commented sections to complete later. A task without a `window` key covers its whole session. Existing files and already-covered windows are never touched.

Tags: windows, linux

Covers:
- feat~remote-development-context~1
- feat~tasks-as-markdown-files~1

Needs: dsn

### Capture the Claude session for resume
`req~claude-session-capture~1`

A task file records the working directory and — when the window runs Claude — the Claude Code session id of its tmux window, so the session can be resumed (`cd <cwd> && claude --resume <sessionId>`) after the remote host rebooted and the tmux window is gone.

Tags: windows, linux

Covers:
- feat~remote-development-context~1
- feat~tasks-as-markdown-files~1

Needs: dsn

### Capture the Claude working subdirectory
`req~claude-workspace-capture~2`

Claude often works in a subdirectory it creates under the directory it was started in (e.g. a per-day workspace), which the pane's cwd does not reveal. A Claude session can publish that path for itself as the `@cs_workspace` tmux user option (`tmux set -w -t "$TMUX_PANE" @cs_workspace "$PWD"` — the explicit pane target for the same reason as the status hooks: a bare `set -w` lands the option on whichever window is focused); ContextSwitcher reads it and records it on the task so the actual working directory is known (distinct from the start `cwd`).
A workspace published *after* the task file exists is picked up too: every tmux sync writes a newly seen or changed `@cs_workspace` into the task (`dsn~claude-workspace-capture~2`). This is the normal case for a task the app created — its session publishes the workspace only once it bootstrapped its worktree, minutes later.

Tags: windows, linux

Covers:
- feat~remote-development-context~1
- feat~tasks-as-markdown-files~1

Needs: dsn

### Capture the task's pull request
`req~claude-pr-capture~2`

A Claude session usually works towards one pull request; its URL belongs to the task (`browser.urls`), so switching focuses the PR alongside the session.
The import records it: either the session publishes it as the `@cs_pr` tmux window user option, or ContextSwitcher scrapes the footer Claude prints (`… · PR: https://github.com/owner/repo/pull/16246`).
A PR opened *after* import is also picked up: ContextSwitcher polls `@cs_pr` for live tasks and writes a newly seen URL into the task file (`dsn~claude-pr-refresh~5`).
A session that works on a PR it did not open — a review, a fix-up, an edit of the PR body — publishes nothing at all; the task's PR is then found from the branch its workspace has checked out, so such a task carries its link too.
See also issue [#46](https://github.com/contextswitcher/contextswitcher-private/issues/46).

Tags: windows, linux

Covers:
- feat~remote-development-context~1
- feat~tasks-as-markdown-files~1

Needs: dsn

### An auto category fills itself from a pull-request search
`req~auto-pr-category~3`

A category can be configured with a GitHub pull-request search instead of being filled by hand.
Every open pull request the search matches becomes a task in that category, and the user can narrow the search further by size — the most lines a pull request may change outside its tests to be worth the list at all (configurable, off by default).
The search decides what is *added*, the pull request's own state what *stays*: a task is suspended once its pull request is merged or closed, and after a configurable grace period (24 hours by default) the task file is deleted, so a triage list empties itself.
A pull request that stops matching while staying open — it grew past the size limit, its review request went elsewhere, or the user dragged a pull request of their own into the category — keeps its task until it is closed; the size limit is an admission filter, never an eviction rule.
A pull request that is **reopened** brings its task back: the suspend was the application's, and the reason for it is gone.
The user's own suspend is the opposite and must survive: pausing a row is how a pull request is *dismissed* from an auto category, so such a task is neither resurrected while its pull request is open nor deleted out from under the dismissal.
A pull request the user already tracks in another task is never added a second time, and a task the user has started working in (a session runs in it) is never suspended, resumed or deleted by this.

Tags: windows, linux

Covers:
- feat~auto-pr-tasks~1
- feat~browser-context~1

Needs: dsn

### Task tags
`req~task-tags~3`

A task can carry a set of tags in its frontmatter (`tags:` — each list item is one tag verbatim, so names may contain spaces).
The available tags and the color each is shown in are configured centrally (a `tags:` palette in `settings.yaml`, each entry a name and an optional color), so tags stay consistent across tasks and a task file references them by name.
The tags offered for selection — in the toolbar filter picker and the task/group tag menus — are the union of the configured palette and the tags already carried by tasks or groups, so a tag written into a task file by hand is selectable without first configuring it.
A tag without a configured color is shown in a stable, automatically derived color, so every tag is distinguishable without hand-picking colors.
A project group can declare tags in its `CONTEXTSWITCHER.md`; those tags are inherited by every task in the group (as if each task carried them explicitly), and creating a group's config makes the group's own tag available in the palette automatically.

Tags: windows, linux

Covers:
- feat~task-tagging~1

Needs: dsn

### Back up the task directory to git
`req~task-git-backup~4`

When the task directory is a git repository, the application syncs it: on startup it pulls what other machines pushed while it was closed, while it runs it commits, pulls and pushes every few minutes, and on close it commits everything the run changed and pushes it — so the tasks can be carried to another machine and recovered, and a phone following the repo sees a change within minutes, without a commit per edit in between.
A rejected close-time push (another machine pushed meanwhile) is retried once after pulling and reconciling.
On a fresh machine (the task directory missing or empty), the application offers on startup to clone the user's task repository into it, so the recovery is a URL away.
The application does not create or configure the repository or its remote otherwise; sync only acts when a repository is already present, and a failure to sync or clone (no remote, no network) never disrupts the app.
A merge conflict between two machines is resolved by the application itself: a file deleted on either side stays deleted (deletion wins), task files are reconciled per part of the file (so "one machine set the status, the other added a tag" never needs the user), and anything left is resolved by a headless Claude run — only when all of that fails is the conflict left to the user.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Share tasks with a sync group
`req~task-sync-groups~1`

A task can be shared with other people through a *sync group*: a git repository (e.g. on GitHub) that every member's application mirrors.
A task belongs to one or more sync groups; only its content is shared — title, tags, links, chat, note and the Markdown notes — while its status, sessions, paths and category stay with each member, who sorts shared tasks into categories of their own.
Deleting a shared task deletes it for the group; a member who wants to keep it for the others leaves the group first, after which the local copy can be deleted freely.
A shared task a member does not take part in is offered in a sync-groups window, reached from a toolbar button whose badge counts the waiting tasks; from there it is moved into a category (joining, or re-joining, the group) or ignored until it changes again.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Clean up a task's remote leftovers on delete
`req~claude-session-cleanup~1`

Deleting a task can also clean up what the task left on its remote — the tmux window, the Claude transcript under `~/.claude/projects/`, and the task's working directory (worktree) — each individually selectable, so deleted tasks do not accumulate orphan sessions and worktrees on the remote.
Alternatively the user can have the task's Claude session tidy up after itself first: the application sends a canned wrap-up prompt (merge/push what is worth keeping, remove the worktree and branch) into the task's window, without the user typing it.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### A merged task removes itself
`req~merged-task-cleanup~1`

A task whose pull requests are all merged has nothing left to do, and the session, worktree and row it still occupies are only in the way.
The application therefore cleans such a task up on its own: it looks at the last screen of the task's Claude window and — when that screen shows nothing a human still has to answer or read — ends the window, removes the Claude transcript and the task's working directory, and deletes the task file.
A last screen that *does* want a human (a question, a permission prompt, an error, the usage limit) holds the cleanup back, but not forever: once the task has been paused longer than the configured grace period (default a week) it is removed as well.
Nothing is lost by that: the last screen is appended to the task's notes and the task directory is committed before the file is deleted, so the removed task can be read back out of git.
The grace period is configurable and can be set to zero, which turns the whole automatism off.
It only acts in a category that allows unattended deletion (`req~auto-delete-opt-in~1`).

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Unattended deletion is opted into per category
`req~auto-delete-opt-in~1`

No automatism deletes a task unless its category says it may: a merged task, an auto category's expired pull request and a closed plain shell are otherwise kept (suspended where they would have been deleted).
A category may hold work whose answer arrives months later — tax paperwork, say — and a task deleted on a schedule would lose it.
A pinned task is never deleted automatically, whatever its category allows.
Deleting a task by hand is unaffected.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

## Design

### Kill dialog with per-target cleanup
`dsn~claude-session-kill~6`

The delete dialog (`dsn~task-delete~6`) carries one checkbox per cleanup target, each shown only when applicable and executed off the FX thread by `Main.killSession` after **Delete**:
"End the tmux window" and "Close browser tabs" are independent ticks — either can run without the other — each mapping to the matching `SwitchOrchestrator` action name (`"tmux"`, `"browser"`) via its `only` overload (`dsn~switch-orchestrator~3`), so the regular suspend teardown (`dsn~task-suspend~6`, per-action chips) runs just the ticked ones; transcript and working-directory removal run over ssh in the orchestrator's completion callback — after the window ended, so Claude's exit cannot recreate the transcript — via `ClaudeSessionCleanup` (`rm -f` of `~/.claude/projects/<encoded claude.cwd>/<sessionId>.jsonl`, `rm -rf` of the single-quoted workdir; already-gone counts as success, results land in the status bar).
Only the per-task `claude.workspace` is ever offered for removal — `claude.cwd` may be the group's **shared** workspacesRoot — and `rejectWorkdir` additionally refuses non-absolute, shallow (fewer than two levels), `..`- or `'`-containing paths.
It also refuses any directory the task's category configures for itself (`GroupConfig.protectedDirs`: `mainCheckout`, `workspacesRoot`, `workdir`), compared ignoring a trailing slash (`~4`→`~5`).
The skeleton `CONTEXTSWITCHER.md` leaves `mainCheckout:` commented out, so most categories name none and the list would have missed the very directory the report was about; `protectedDirs` therefore also derives the *conventional* primary clone — `<workspacesRoot>/<repo name>`, the path `TaskFileParser.groupConfigForRepo` writes for a category created from a URL — whenever `mainCheckout:` is absent (`~5`→`~6`).
A `claude.workspace` is normally a per-task worktree, but it is whatever `@cs_workspace` last reported, and a session that finished by removing its worktree and stepping back into the main checkout reports *that* — so the dialog offered to `rm -rf` the category's permanent clone, taking every sibling worktree's metadata with it (field report 2026-09-12).
The guard sits in `rejectWorkdir` rather than in the dialog because the merged-task janitor (`dsn~merged-task-cleanup~2`) ticks the workdir target without asking anyone; the dialog runs the same check to *label* its box, showing the refusal reason in place of the path and disabling the tick, so it never offers what the cleanup would refuse.
Window/browser/transcript ticks persist across runs (Java Preferences, like the import destination); the workdir tick deliberately starts unticked every time (`rm -rf` is irreversible).
"Close browser tabs" names what it would close, and the number is what is **open right now**, not what the task lists: `Main.countOpenTabs` asks the extension for one `list-tabs` and counts the task's `browser.urls` that a tab exactly matches — the same exact match `close-url` uses (`dsn~browser-close-action~2`), so the tick promises to close what it counted rather than a sub-page tab the close would leave behind.
The count is a round-trip with a 5 s timeout, so the tick is not allowed to block the dialog: it opens disabled and unticked, saying `Close browser tabs — checking…`, and the answer (marshalled back to the FX thread) sets the final label and enables it.
Enabled means a positive number and nothing else — a task with no URLs of its own, none of them open (`Close browser tabs — none open`), or no extension to ask (`Close browser tabs — no browser extension connected`) all leave it off, the last two being different answers that must not be collapsed into each other: tabs may well be open behind a browser that cannot be reached, and offering to close them would promise what cannot happen.
The `KILL_CLOSE_BROWSER_TABS` preference is written back only from an enabled tick, for the reason the foreign-window guard does the same (`dsn~tmux-window-ownership~4`): a "no" the app forced is not the user's and would otherwise stop closing tabs on every later delete.
The count is deliberately scoped to the task's `browser.urls`, never a category- or desktop-wide tally, so the tick never promises closing tabs that belong to another task.
The label always says *browser tabs* and never breaks the count down into PRs: "Close 1 PR" reads as GitHub's close-the-pull-request rather than as closing a tab (field report 2026-09-09).
The dialog's third button, **Ask Claude to tidy up first** (shown for a live window), sends `Main.CLEANUP_PROMPT` through the message-queue delivery (`dsn~message-queue-send~5`) and deletes nothing; the prompt is generic on purpose — the session knows its own worktree and branch.
Deferred from the full Feature B plan: per-group orphan-session discovery and listing (B1/B2) — this design covers the delete-task flow only, and `rm -rf` leaves the primary clone's worktree metadata for a later `git worktree prune` (the graceful path has Claude run `git worktree remove` properly).

Tags: windows, linux

Covers:
- req~claude-session-cleanup~1

Needs: impl, utest

### A merged task removes itself
`dsn~merged-task-cleanup~2`

`MainWindow.autoCleanupMerged` runs right after each `updatePrStates` round (`dsn~pr-state-indicator~3`) and nominates **one** task per round: it carries a `claude:` section (a worktree session — an auto-category triage row belongs to `dsn~auto-pr-category~3`), it is not the selected row and not marked done (a task the user ended by hand is none of the janitor's business), its window is not `working`, every one of its `prUrls()` is known `MERGED`, and at least one of them was part of *this* round.
That last condition is what paces the janitor: a pull request already known merged is re-polled only every `PrStatePoller.MERGED_POLL_SECONDS` (`dsn~pr-poll-economy~2`), so the ssh capture behind each decision runs at that rhythm rather than on every tick.

An **active** task whose window the status poll has not reported on is skipped whatever its pull requests say (`~1`→`~2`).
The janitor judges a session by its screen, and a window the poll has never seen is one of two things: seconds old, created between two rounds, or gone — neither is a session that ended.
Field report 2026-09-12: a task created from a prompt that merely *quoted* a long-merged pull request was archived, its window killed and its file deleted **three seconds after "Add task"** — the quoted URL was seeded as the task's own PR, and Claude was still booting in the new window, so its screen had nothing alarming on it yet.
The selected-row condition should have caught that one and did not: a row that has only just appeared is selected through `pendingSelectionId`, and a list rebuild clears the `ListView` selection in between (`dsn~browser-tab-selects-task~5`), so the guard reads "nothing selected" exactly during the seconds a fresh task is most vulnerable.
It is kept — it is the right rule for an established row — but it is no longer the only thing standing between a new task and the janitor.
A **suspended** task has no window left for the poll to see and keeps its own route: an unremarkable stored snapshot, or the grace period below.

`Main.cleanupMerged` then acts off the FX thread: `PaneSnapshots.captureNow` reads the window's current screen (falling back to the snapshot a suspend stored, `dsn~terminal-suspend-snapshot~3`), and `MergedCleanup` decides on it.
`MergedCleanup.needsHuman` strips the terminal escapes and matches a handful of literal Claude Code shapes — `Do you want`, a `❯ 1.` choice list, `(y/n)`, `esc to interrupt`, `error`, the usage-limit messages; a screen that could not be captured at all counts as needing a human, so a session the janitor could not look at is never removed on the strength of a guess.
`MergedCleanup.cleanUpNow` clears an unremarkable screen immediately and an interesting one only once `AutoPrReconcile.expired` says the task has been paused `mergedCleanupDays` long (the `dsn~suspended-timestamp~1` stamp — an active task carries none and is therefore never force-cleaned); `0` days turns the janitor off entirely.

Before anything is removed, `MergedCleanup.withLastScreen` appends the screen as a fenced block below the task's notes (once — the heading is its own marker) and `TaskGitBackup.commitNow` commits the task directory without pushing, so the archived version exists as a commit even though the next push will only see the deletion.
The removal itself reuses the delete dialog's machinery (`dsn~claude-session-kill~6`): `Main.killSession` with the window, transcript and workdir targets ticked — browser tabs deliberately not, closing pages is not part of finishing a merge — followed by the plain file delete.

Tags: windows, linux

Covers:
- req~merged-task-cleanup~1

Needs: impl, utest

### Unattended deletion is opted into per category
`dsn~auto-delete-opt-in~1`

A category's `CONTEXTSWITCHER.md` carries `autoDelete: true` (`GroupConfig.autoDelete`, parsed leniently — anything but `true` is off, and so is a root-level task or a category without a config file); the category form offers it as a checkbox.
`GroupConfig.mayAutoDelete(task)` is the one gate: `autoDelete && !task.pinned()`.
Each unattended delete asks it for the task's own category:
`MainWindow.autoCleanupMerged` (`dsn~merged-task-cleanup~2`) does not nominate a task it refuses;
`MainWindow.applyAutoPrs` (`dsn~auto-pr-category~3`) drops such tasks from the plan's deletes, so an expired triage row stays suspended;
`TmuxSync.reconcile` (`dsn~tmux-sync~7`) takes the gate as a predicate — `Main` reads the category config fresh off the FX thread — and suspends a refused disposable shell instead of deleting it.
Existing auto categories that relied on `auto.deleteHours` need `autoDelete: true` to keep deleting.

Tags: windows, linux

Covers:
- req~auto-delete-opt-in~1

Needs: impl, utest

### Task file parsing
`dsn~task-file-parsing~4`

`TaskFileParser` splits the file on `---` fences, parses the frontmatter with SnakeYAML into an immutable `Task` model (records; nested configs for `tmux`, `intellij`, `browser`; the ssh destination key is `remote`, per-section overridable for `intellij`), and keeps the body verbatim. All `intellij` keys are optional — a bare `intellij:` key already enables the action, with `projectPath` resolved from `claude.workspace`, then `claude.cwd` (`Task.intellijProjectPath()`); an explicit path is only needed when no Claude session is involved. Parse failures produce a diagnostic carrying file name and cause instead of an exception escaping to the UI. Scalars inserted into generated frontmatter (skeletons, rename, new task) are serialized by SnakeYAML (`yamlScalar`), not hand-escaped.

The textual writers (`withTitle`, `withStatus`, `addBrowserUrl`, …) find the frontmatter's bounds line by line, and a fence line is `---` at **column 0** (`isFence`), never a stripped one.
An indented `---` is content: a task created from a pasted brain-dump keeps the text in a `title: |-` block, horizontal rules included, and reading such a line as the closing fence made every writer insert its key into the middle of the block scalar — the file then failed to parse (field report 2026-09-07, a `browser:` key added by the URL button).

Tags: windows, linux

Covers:
- req~task-file-format~1
- req~task-parse-error-handling~1

Needs: impl, utest

### Duplicate frontmatter keys are reported
`dsn~frontmatter-duplicate-keys~1`

YAML leaves duplicate keys undefined; SnakeYAML keeps the last one and warns — through `java.util.logging`, a channel this app bridges nowhere, so the warning reaches neither `~/.contextswitcher/logs/` nor the user, and it names no file either, since `loadYaml` only ever sees the YAML text (field report 2026-07-30: `WARNING: duplicate keys found : desktop`, with nothing to say *which* file).
A category with two `desktop:` lines therefore resolved silently to whichever came last.

`TaskFileParser.duplicateFrontmatterKeys(content)` reports them as `key (line N)`, with `N` counted in the **file** (the block starts on the line after the opening fence) so the offending line can be found by eye.
It `compose`s the node tree rather than loading it — a loaded `Map` has already dropped the duplicates — and walks nested mappings and sequences, so a repeat inside `tmux:` or `intellij:` is found as well.
Frontmatter that is absent, unterminated, or does not compose at all reports nothing: that is somebody else's report (a task file fails to parse with its own message, a category config stays `GroupConfig.EMPTY` on purpose).
Nothing about parsing changes — the last duplicate still wins; this only makes the loss visible.

It is surfaced three ways, none of them modal (a duplicate key is a nuisance, not a failure):

- **The log.** `TaskRepository.logDuplicateKeys` warns with the path and every repeat, through tinylog so it lands in the log file. It runs where a file is *read from disk* — the initial scan, the watcher's task-file upsert, the watcher's `CONTEXTSWITCHER.md` change — never on a render, so a broken config cannot repeat itself into the log a few times a second.
- **The editor lane.** `MainWindow.setEditorFileStatus` appends the warning to the file label — which already doubled as the lane's status line ("— saved", "— reverted to disk") — in `-color-warning-fg`, on open, save and revert. The warning is appended *after* the action's note rather than replaced by it: the note is about the last action, the warning about the file.
- **The category header.** `TaskListCell.GroupHeader` gains a `@Nullable String warning`, rendered as a `⚠` with the detail in its tooltip; `MainWindow.groupConfigWarning` fills it from the same check. This is the only place a `CONTEXTSWITCHER.md` problem can be seen from the list at all — a category is not a task, so `TaskRepository.isTaskFile` excludes it and it can never become a `TaskEntry.Failed` error row.

SnakeYAML's own warning is silenced (`Main.SNAKEYAML_LOGGER`, level `OFF`): it cannot name a file, so leaving it on means the only message reaching the console is the one that does not say where to look.

Tags: windows, linux

Covers:
- req~task-parse-error-handling~1

Needs: impl, utest

### A category config that does not load is reported, never hidden
`dsn~group-config-parse-error~1`

`parseGroupConfig` answers `GroupConfig.EMPTY` for a `CONTEXTSWITCHER.md` whose frontmatter does not load — deliberately, a half-edited config must not break anything — but that also dropped its `desktop:`, and the active-desktop filter then hid the category: it disappeared without a word (field report 2026-09-13, a OneNote link pasted without its `note:` key).
A task file in that state is a `TaskEntry.Failed` row; a category cannot be one, so it gets the same three signals another way.

`TaskFileParser.groupConfigError(content)` returns SnakeYAML's own problem text with the line counted in the **file**, like `dsn~frontmatter-duplicate-keys~1` (`expected '<document start>', but found '<block mapping start>' (line 5)`), or null when the frontmatter loads, is empty, or is absent.

- **The log.** `TaskRepository.checkGroupConfig` warns `Cannot parse category config <path>: <problem>` where the file is read from disk (scan and watcher), next to the duplicate-key line.
- **The category header.** `MainWindow.groupConfigWarning` puts the problem into the header's `⚠` ahead of any duplicate keys; clicking the `⚠` opens the config in the editor, since the cog that does the same shows only on hover. It is re-read on every rebuild, so fixing the file clears it without a restart.
- **The filter.** `MainWindow.onActiveDesktop` keeps a category whose config does not load, whatever the desktop filter and its sub-option say: its missing `desktop:` is an artefact of the breakage, and hiding it would hide the warning.

Unquoted `onenote:` links are legal YAML — `#` opens a comment only after whitespace, `{` is special only at the start of a value — so the template's `# note:` example stays unquoted; a unit test pins that the template's example and a real link survive whole.
Only a space before `#` truncates silently; that gets no warning, since a OneNote link never has one and `url # comment` is legal YAML.

Tags: windows, linux

Covers:
- req~task-parse-error-handling~1

Needs: impl, utest

### A pasted OneNote clipboard is repaired
`dsn~onenote-paste-repair~1`

OneNote's "Copy Link to Page" puts two lines on the clipboard — a `https://onedrive.live.com/…` web URL and an `onenote:` desktop URL — and pasted as-is into a `CONTEXTSWITCHER.md` they are two keyless lines, so the file no longer loads (`dsn~group-config-parse-error~1`).
The shape is unambiguous, so `TaskFileParser.repairPastedOneNoteLink` repairs it in place: the `onenote:` line becomes `note:`, the adjacent web URL (bare, or pasted behind an existing `note: `) becomes `note-linux:` — Linux has no OneNote app to take the desktop link, and the machine-scoped suffix (`dsn~machine-key-variants~1`) opens the web page there instead.
It touches only a frontmatter that does not load, and returns the repair only when the result loads; anything else comes back unchanged, so no guess is ever written.
`TaskRepository.checkGroupConfig` writes the repair back where it reads the file (scan and watcher) and logs it; the write re-triggers the watcher, which finds nothing left to repair.
`MainWindow.groupConfigChanged` reloads a clean editor showing that config, whose next auto-save would otherwise write the paste back.
`TaskFileParser.groupNote` now folds machine suffixes like every other reader, which `dsn~machine-key-variants~1` already claimed.

Tags: windows, linux

Covers:
- req~group-note-link~1

Needs: impl, utest

### Machine-scoped key suffixes
`dsn~machine-key-variants~1`

A key may be suffixed with `-<token>`, where the token is this machine's short host name (`workdir-devbox`) or its OS (`folders-windows`, `folders-linux`, `folders-mac`).
`TaskFileParser.applyMachineVariants` folds those keys into their plain form right inside `loadYaml`, so every reader — task parse, `parseGroupConfig`, `frontmatterString`, `groupNote` — sees a map that already speaks for this machine, and no parse rule has to know the scheme.
Tokens are applied least specific first (OS, then host), so the host name wins; a suffix naming another machine matches nothing and stays in the map as an unknown key, which every parse rule ignores anyway.
Nested mappings are folded too (`intellij.projectPath-devbox`); list *items* are values, not keys, so lists are not descended into.
Matching is case-insensitive (Windows reports `COMPUTERNAME` upper-cased) and needs a non-empty base name, so a key that is only the suffix (`-linux`) stays a plain key.
The host name is read from `COMPUTERNAME`, then `HOSTNAME`, then `InetAddress.getLocalHost()` (domain stripped, lower-cased) — the env vars first because the DNS-backed lookup can block; an unresolvable host yields an empty token that matches nothing, leaving the OS token to work.
See MADR 0023 for why the suffix beats a per-OS block or a nested per-key map.

Tags: windows, linux

Covers:
- req~machine-specific-values~1

Needs: impl, utest

### Textual edits skip multiline scalars
`dsn~frontmatter-multiline-scalar~3`

A YAML scalar may span physical lines, and a `title:` legitimately does — hand-edited, or written by the app itself from a multi-line add-task description.
The textual frontmatter mutators anchored on `title:` (`withTitle` replacing it; `withStatus`/`withNote`/`withTags` inserting after it) therefore treat the key's whole value span as the anchor (`TaskFileParser.scalarEnd`) — replacing only the key's first line, or inserting a new key right after it, lands inside the scalar and corrupts the frontmatter.

Two shapes span lines, and they differ in what a blank line means.
A **plain** scalar carries no block symbol (continuation lines merely indented, folded with spaces on parse) and ends at the first blank line.
A **block** scalar (`title: |-`, what `yamlScalar` emits for any multi-line value) keeps blank lines as *content* and runs until a line back at the key's own indent — so `scalarEnd` skips blank lines only for that shape, detected by a `|`/`>` indicator after the key's colon (`isBlockScalarHeader`, any chomping/indent suffix).
Trailing blank lines of a block scalar are excluded, so an inserted key follows the value's content; only `|+`/`>+` would read those as value and nothing here writes them.

A block-scalar value also **contains the fence characters**: a task description pasted from a chat is full of `---` separator lines, indented inside the title.
Every scan that walks the frontmatter to its closing fence must therefore test the line **as written** (`isFence`: `"---"` after `stripTrailing`), never `strip()`ped: stripping the leading space too makes such a line look like the closing fence.
`withStatus` did, so the scan stopped at the first separator inside the title, never reached the real `status:` below it, and *inserted* a second one — and since YAML keeps the last of duplicate keys, the status the write was making is the one that lost.
Field report 2026-09-12: "Duplicate frontmatter keys in …-do-we-have-noted-down-our-decision-drivers-for.md: status (line 28)", on a task whose suspend had silently not taken (`~2`→`~3`).
The duplicate-key warning of `dsn~frontmatter-duplicate-keys~1` is what surfaced it; it is a symptom worth reading as "a writer inserted where it should have replaced".

Both bugs this prevents were real: `~1` came from toggling a tag on a task with a two-line plain title, which left the title's second line dangling below the new `tags:` line; `~2` from tagging a task whose `|-` title held an `[image: …]` marker under a blank line — the `tags:` key landed inside the title and orphaned the marker (2026-07-20).

Tags: windows, linux

Covers:
- req~task-file-format~1
- req~task-file-editing~1

Needs: impl, utest

### Optional per-URL title in browser.urls
`dsn~browser-url-title~1`

A `browser.urls` entry is either a plain scalar URL (the common, terse case) or a `{url, title}` map when the user wants a short human label (e.g. `Probeklausur in moodle`).
`TaskFileParser.parseBrowser` accepts both forms into `Task.UrlEntry(url, title)`; `BrowserConfig.urls()` exposes the addresses only, so the switch/close actions and the existence check are unaffected by titles.
`TaskFileParser.addBrowserUrl(content, url, title)` appends the scalar form when no title is given and the `{url, title}` map form otherwise (both textual, comment-preserving); the row subtitle prefers the first entry's title over its URL.

Tags: windows, linux

Covers:
- req~task-file-format~1
- req~task-file-editing~1

Needs: impl, utest

### No duplicate browser URLs
`dsn~browser-url-dedupe~3`

A `browser.urls` list must never show the same URL twice, and field files did (2026-09-07: two identical PR entries each, from writers racing on the file — the `@cs_pr` and footer-scrape PR writers, an add-link over a stale editor buffer).
`TaskFileParser.addBrowserUrl` therefore runs `dedupeBrowserUrls` on the content first — a textual pass over the `browser:` block that keeps the first occurrence of each URL (with its title and the map form's `title:` continuation line) and drops the later ones — so every write path cleans up what raced in, including the "already there" no-op return.
`parseBrowser` drops duplicates on load as well (first entry wins), so a file not yet rewritten still shows each URL once.

Comparison — and storage — runs on `Task.normalizeUrl`, which strips a URL's **trailing slashes**: `…/pull/16247/` and `…/pull/16247` are the same page, but they landed as two entries, and the slashed one did not even match `Task.PR_URL`, so it showed as a plain link instead of the pull request (2026-09-08).
`parseBrowser` stores the normalized form, so an old file's slashed entry gets its PR icon back without an edit; `tabUrlMatch` and the "task for this PR already exists" check normalize their incoming URL for the same reason.
The two PR patterns (`Task.PR_URL`, `PrTitleLookup.PR_URL`) accept a trailing slash as well, so a `…/pull/123/` pasted into the Add-task dialog is recognized as a pull request — the URL is normalized on the way into the file, but it must be *understood* before that.

Tags: windows, linux

Covers:
- req~task-file-format~1
- req~task-file-editing~1

Needs: impl, utest

### Task repository with directory watching
`dsn~task-repository-watching~6`

`TaskRepository` scans the task directory and its immediate subdirectories (one level deep; a subfolder is a task group/project) on startup and registers a JDK `WatchService` key per directory (root plus each subfolder) — a subfolder created later is picked up and watched automatically. File events re-parse the affected file and update an `ObservableList` on the JavaFX application thread.
All file IO (existence check, read, parse) runs on the calling thread — the watcher's daemon thread, or the background thread `Main` runs the startup scan on (`dsn~startup-background~1`) — and only the list mutation hops to the FX executor: parsing every task file inside `Platform.runLater` blocked the FX thread for the whole scan on a slow drive. A task's id is its file path relative to the task directory with the `.md` extension stripped, forward-slash separated regardless of platform (e.g. `jabref/fix-npe`); `TaskEntry.group()` derives the owning subfolder name from it, empty for a root-level task.
An event on a group's `CONTEXTSWITCHER.md` never touches the entry list (it is not a task) but fires a group-config-change callback on the same thread, which `MainWindow` wires to a row rebuild — so a header renders freshly edited group defaults (remote glyph, tags) without a restart.
Every wake-up of the watcher thread is applied as **one** list mutation: it drains the events of all signalled keys, keeps collecting for a 50 ms settle window, and hands the collected updates to the FX executor as a single batch.
One logical change is several events — a save fires several MODIFYs, a rename a DELETE plus a CREATE, a group removal a burst — and one mutation per event means one full task-list rebuild per event: a renamed task visibly blinked out of the list and back, and a busy write stalled the FX thread.
A re-parse that yields an entry equal to the one already listed is **not** republished (`Task` and the `TaskEntry` variants are records, so equality is by value):
the watcher fires several MODIFY events per write — and for pure mtime touches — and every list `set()` costs a full task-list rebuild in the UI, which transiently clears the selection and re-fires the preview.

Tags: windows, linux

Covers:
- req~task-directory-reload~1
- req~task-folder-grouping~1

Needs: impl, utest

### Template task file
`dsn~task-template-file~1`

When the task directory contains no Markdown file at startup, the repository seeds a commented `TEMPLATE.md` showing the full frontmatter schema. `TEMPLATE.md` is never listed as a task; the empty-state UI points the user at it.

Tags: windows, linux

Covers:
- req~task-file-format~1

Needs: impl, utest

### Sync tasks with running tmux windows
`req~tmux-sync~1`

One action reconciles the task list with the live tmux windows of the configured remotes: a task whose window is gone on the remote is marked suspended, a suspended task whose window is back is reactivated (status only — no kill or resurrect), and windows not yet covered by a task are imported.
The remotes are configured once (a `remotes:` list in `settings.yaml`), so the action needs no per-run host prompt; with none configured it falls back to the distinct remotes of existing tasks.

Tags: windows, linux

Covers:
- feat~remote-development-context~1
- feat~task-context-switching~1

Needs: dsn

### tmux window import
`dsn~tmux-task-import~12`

`TmuxDiscovery` runs `tmux list-windows -a` (single-quoted format string; the fields include `#{pane_title}` plus `#{host}` purely to recognize the default title) on the host via the ssh command runner.
Sessions named `cs-mirror-*` — the terminal pane's grouped mirror sessions (`dsn~terminal-pane~14`) — are ignored: they share the base session's windows, so listing them would duplicate every window.
`TmuxTaskImporter` writes one skeleton per unmatched window (`<host>-<session>-<index>-<name>.md`, sanitized): `tmux.session` and `tmux.window` set to the **immutable window id** (`@17`), which — unlike the index — survives renumbering when other windows close.
The `<name>` component is length-capped (60 chars, cut at a `_`/`-` word boundary): a window whose name is a pasted multi-sentence task description (Claude never published a short `@cs_title`) would otherwise exceed the Windows path-component limit and the skeleton write would fail with "The filename, directory name, or volume label syntax is incorrect".
The **title prefers the pane title** — Claude Code publishes its task summary there (e.g. `directory-as-library-feature`); a title equal to the host name is tmux's default and is ignored, and leading spinner glyphs (braille animation frames like `⠂`, asterisks, bullets) that Claude Code animates while working are stripped (`TmuxDiscovery.cleanPaneTitle`) — falling back to the plain window name; the `remote`/target context is derived from the frontmatter and shown by the task list as subtitle, not baked into the title; session/index/name land in the notes, `intellij`/`browser` as commented YAML. Windows are matched against existing tasks **first by Claude session id** — the strongest identity, covering a window no matter how its tmux coordinates shifted (suspended tasks included: importing a second task for a resumable session would fork the context) — then by session+window (id, name, or index) in **any** status (a reappeared window reactivates its suspended task, `dsn~tmux-sync~7`, rather than spawning a duplicate) or by a session-level task without `window`; a **suspended window-less** task does not count as session-level coverage — suspend removed its `window:` line (`dsn~task-suspend~6`) and ended its live context, so other windows of the session are new contexts worth importing. A same-named existing file is **not** coverage — it may track a long-dead context (renamed task, reused window index): it is never overwritten, the new skeleton gets a `-2`/`-3`… suffixed file name instead of being silently skipped.
The toolbar's import dialog asks for the host, pre-filled with the last used destination (kept across app restarts in Java Preferences — UI state, not configuration) or, lacking one, the first task's `remote`; new files appear through the directory watcher.

Tags: windows, linux

Covers:
- req~import-tasks-from-tmux~2

Needs: impl, utest

### tmux sync: status reconciliation + import
`dsn~tmux-sync~7`

`Main.syncTmuxSessions` runs across the remotes (settings `remotes`, else the tasks' distinct remotes) on the executor.
While it runs, the toolbar's sync menu item is disabled; a completion callback (invoked on the FX thread on any outcome, errors included) restores it — no second sync can start in parallel.
Per remote it lists the live windows and calls `TmuxSync.reconcile(tasks, remote, sessions)` — a pure function returning per-task `Reconciliation`s (a task with a window id, `done` and window-less tasks untouched; no `TmuxKillAction`/resurrect side effects, the window is already gone resp. present): `SetStatus` for gone → suspended and back → active (written with `TaskFileParser.withStatus`), and `Delete` for an active task whose window vanished **and** which is a *disposable shell* (`Task.isDisposableShell`: no `claude:`/`intellij:`/`terminal:` section, no browser URLs, no `note:`, no tags, empty Notes body — a plain window such as `bash` closed with nothing recorded). A disposable shell is removed (`TaskFileAccess.delete`, which also drops its queue file) rather than left as a suspended husk that would focus a dead/reused window on the next switch; anything worth resuming (a Claude session) or remembering (notes/PR) is suspended, not deleted.
**A live window is a task's window only in the task's recorded session, and — when both carry one — with the same Claude session id** (`TmuxSync.liveWindow`, the one match shared by the reconcile, the session-id backfill and the workspace/commit refreshes; fed the **raw** discovery output, never the enrichment's guessed ids).
A tmux window id is unique only for one server lifetime: after a server restart the ids start over at `@0`, so a task suspended when the old server died still carries an id that, days later, names a stranger's window on the new server.
Matching on the id alone re-activated such a task and then synced everything the stranger published onto it — title, session id, workspace, commit, PR URLs (field report 2026-08-25: two stale July tasks at `@61` took over the title and PR links of the live `@61`).
The `@cs_title`/`@cs_pr` pollers still key by window id alone (their round-trip carries no session), so `adoptPublishedTitles`/`applyDiscoveredPrs` skip suspended tasks — a stale task is suspended long before its id can be reused.
Every `SetStatus` carries a `reason`, logged at INFO with the task id and, for a suspend, shown in the status bar with the hint to press play: "window @710 is gone" versus "window @710 publishes session X, task records Y (taken over by another session)" — the second leaves a window that is demonstrably alive in the task file, and without the reason it reads as a bug.
Ceiling: a stale task **without** a Claude session id in a same-named session cannot be told apart from its own window and still reactivates.
On the **manual** sync it then imports the uncovered windows (`dsn~tmux-task-import~12`) with the keys of `TmuxSync.coverageKeys`: window-level tasks cover their window in **any** status (a reappeared window reactivates its suspended task rather than spawning a duplicate), but a **suspended window-less** task — the state suspend leaves behind after removing the `window:` line (`dsn~task-suspend~6`) — contributes no session-level key, so new windows of its session are still imported. The import step is gated on the `importNew` flag: the automatic passes (post-create backfill, periodic reconcile) reconcile and backfill but never auto-spawn a task for every tmux window.
A covered window that published `@cs_session_id` backfills the id into a task whose `claude:` section still lacks a `sessionId` (`TmuxSync.sessionIdBackfills` → `TaskFileParser.withClaudeSessionId`, comment-preserving) — the state a live-created task is left in when its window was typed into before the `SessionStart` hook ran; only the exact published id is written, never the newest-transcript enrichment guess (a wrong id would let resume hijack another task's session).
A covered task **without** a `claude:` section adopts the session instead of being skipped: the section is created from the window's own `cwd` (`TaskFileParser.withClaudeSection`, a no-op when one exists) and the id written into it.
That is the window imported as a plain shell in which the user later started `claude` by hand — the file has no `cwd` to hang a `sessionId` on, and without adoption it never gains one, so the task stays a "plain window" forever: suspend warns that nothing can be resumed, "Show diff" has no workspace, and a closed window would even delete the task as a disposable shell (`Task.isDisposableShell`) although a resumable session ran in it.
The window's `@cs_workspace`/`@cs_commit` follow on the next pass, once the section they need exists.
Every file the sync rewrites (a status change or a session-id backfill) that is **currently open in the editor lane** is reloaded on the FX thread (`MainWindow.reloadEditorIfShowing`, discarding unsaved edits to it): a just-created, still-selected task otherwise both hides its freshly backfilled `sessionId` and overwrites it on the editor's next auto-save.
**Auto-sync after a live task is created:** `createLiveTask` starts a retrying silent backfill on a daemon `ScheduledExecutorService` — a silent sync every `POST_CREATE_SYNC_SECONDS` (30 s), up to `POST_CREATE_SYNC_ATTEMPTS` times, stopping as soon as the task carries a `claude.sessionId` — so the id Claude publishes on startup is recorded without the user pressing the sync button. The first delay lets Claude Code boot and its `SessionStart` hook publish `@cs_session_id`; the retries also cover a slow boot and re-write the id if a user's editor save dropped it back out between attempts. The silent path (`announce=false`) only logs; the manual button path (`announce=true`) shows the summary alert.
**Periodic auto-reconcile:** on startup a recurring silent sync runs every `AUTO_RECONCILE_SECONDS` (60 s) on the same daemon scheduler, so a closed window is suspended-or-deleted on its own without pressing the sync button. Like the post-create pass it sets `importNew=false` (reconcile + backfill only, no import). When there are no remotes to sync the automatic passes return silently — only the manual sync shows the "no remotes" hint.
A summary alert (manual sync only) reports imported/reactivated/suspended/deleted/backfilled counts and per-remote errors.

Tags: windows, linux

Covers:
- req~tmux-sync~1

Needs: impl, utest

### Claude session capture
`dsn~claude-session-capture~3`

The import format additionally carries `#{pane_current_path}`, `#{pane_current_command}`, and `#{@cs_session_id}`.
The **exact** session id comes from the `@cs_session_id` window user option, published by Claude Code's `SessionStart` hook (README snippet; needs `jq` on the remote).
Only windows without it fall back to `ClaudeSessionLookup`, which guesses: it fetches the newest transcript under `~/.claude/projects/<encoded-cwd>/*.jsonl` on the host (cwd encoded with `/` and `.` as `-`) — wrong when two sessions started in the same directory, so a guessed id that would be assigned twice (or collides with an exact one) is dropped with a warning: no id beats a wrong one, which would let resume hijack another task's session.
The skeleton gains a `claude:` section only for windows actually hosting Claude (the pane runs `claude`, an id is known, or `@cs_workspace` is published) — every pane has a cwd, that alone proves nothing.

Tags: windows, linux

Covers:
- req~claude-session-capture~1

Needs: impl, utest

### Claude PR capture via option or footer scrape
`dsn~claude-pr-capture~2`

The list-windows format additionally reads `#{@cs_pr}` — a published option is the authoritative source.
For Claude windows without it, `ClaudePrLookup` captures the pane (`capture-pane -p -J -S -100`) and scrapes the **labeled** footer `PR: https://github.com/<owner>/<repo>/pull/<n>` — the label is required so PR links merely mentioned in the conversation do not match.
Every distinct match is returned in order of appearance: one session can open several PRs at once (a code PR plus its documentation PR) and then prints one `PR:` footer line per repository; the deduplication absorbs the repeats a redrawn TUI leaves in the scrollback.
At import the first URL makes the skeleton's `browser:` section real (single `urls` entry) and `dsn~claude-pr-refresh~5` appends the rest on its next tick; without any the commented example stays.

Both callers scrape through `ClaudePrLookup.batchCommand` — **one** ssh round-trip for every window that needs one, never one per window.
The periodic footer poll of `dsn~claude-pr-refresh~5` was batched from the start; `Main.enrichClaudeSessions`, which runs for every window of every session on each import *and each periodic reconcile* (`dsn~tmux-sync~7`), was not, so a host with ten Claude windows cost eleven connections per reconcile through the six global slots of `dsn~ssh-command-runner~6`.
Invisible at one reconcile a minute, and eleven minutes of saturation once a woken laptop replayed a backlog of them (`dsn~poll-no-wake-backlog~1`, field report 2026-09-12) — with the terminal mirror's attach queued behind.
The window ids to ask about are collected first, in one pass over the sessions: a window that already published `@cs_pr`, and one not running `claude`, stay out of the batch, and a round with nothing to scrape opens no connection at all.
The transcript-id heuristic of `dsn~claude-session-capture~3` in the same method keeps its own call per window — it is already bounded to windows that published no `@cs_session_id`, which the hook makes the exception.

Tags: windows, linux

Covers:
- req~claude-pr-capture~2

Needs: impl, utest

### Refresh @cs_pr for live tasks
`dsn~claude-pr-refresh~5`

A pull request Claude opens after import is captured without re-importing. `TmuxPrPoller` polls `#{@cs_pr}` per task host on a short fixed interval (`tmux list-windows -a -F '#{window_id}|#{@cs_pr}'`, single-quoted and double-quote-free — cheap, so ~15 s) and hands a `host windowId -> prUrl` map to a callback marshalled to the FX thread; `Main.applyDiscoveredPrs` matches each entry to a live task by remote + tmux window and records the URL.
As a fallback for sessions that print the PR only in their **footer** (`PR: …`) and never publish `@cs_pr`, the same tick runs `Main.scrapeFooterPrs`: the live Claude tasks (`claude:` section, not suspended) are grouped by host, and **one** ssh call per host (`ClaudePrLookup.batchCommand`: `echo '<marker @id>'; tmux capture-pane … -t '@id'; …`, `;`-joined so a gone window skips only itself) captures every window's pane off the FX thread; the output is split at the markers and every URL found is recorded for the tasks of that window.
One call per task instead — some forty simultaneous `ssh.exe` processes every tick — was refused by sshd's `MaxStartups` for the most part and starved the FX thread on the local machine (the 2026-08-27 startup freeze).
The scrape runs even for tasks that already have a PR — a session that opened a code PR often opens its documentation PR minutes later, and stopping at the first one would never pick that up; the write path's dedup keeps the repeat scrapes free of effect.
A session may also work on a PR it never opened — reviewing it, fixing it up, editing its body: it publishes no `@cs_pr` and prints no footer, so neither path above ever sees the link.
For those, every eighth tick runs `Main.lookupWorkspacePrs`: for the live Claude tasks whose workspace has not answered yet, `WorkspacePrLookup` asks `gh` on the remote which PR the workspace's branch belongs to (`cd <workspace> && gh pr view --json url`, `gh` has no `-C`) and records it — again one ssh call per host (`batchCommand`: each distinct workspace in its own `( … )` subshell behind a marker line, so several tasks sharing the main checkout cost one `gh` call, a failed `cd` leaks into no other workspace, and a branch without a PR ends nothing), run through a runner whose timeout (90 s) is sized for the sequential `gh` calls of a whole batch rather than one.
`gh` runs locally when the workspace path exists on this machine (with the workspace as the process's working directory — `gh` has no `-C`), one call per such workspace, and only the others go into the host's ssh batch: local first, because ssh is the part that breaks and a dropped connection made every lookup fail although the checkout was right there.
The branch — not the pane — is the source here on purpose: a pane routinely shows PR links from a changelog, an issue, or another repository's review, and scraping bare URLs would file all of them under the task.
Twice bounded because each lookup costs a GitHub API call: a workspace drops out for good once its PR was found, and the lookup runs only every eighth (~2 min) tick.
The bound is the *workspace*, not "the task has some PR URL": a task whose `browser.urls` came from its prompt — the PR it references, an issue's PR — would otherwise never get the PR of its own branch (the 2026-09-10 report of a freshly opened PR missing from the task file).
In both batches only a failure that leaves no output at all (the connection itself) discards the result — the exit code is the last command's.
Both writers go through `Main.writePrToTask`, which re-reads the file and adds the URL via `TaskFileParser.addClaudePrUrl` (textual, comment-preserving), skipping the save when that returns the content unchanged — so the two FX-thread writers never double-append.
The PR Claude is working on goes **first** in `browser.urls`, not into its sorted position (`dsn~task-add-link~3`): it is the link to open, and `BrowserFocusAction` focuses the first URL.
The dedup compares list **entries**, not the file text — a substring check called `…/pull/123` present because the file had `…/pull/1234`. The host set is the status poller's (suspended tasks excluded); the watcher re-parses the file and the PR-state indicator then shows it; an editor lane showing the file is reloaded (`MainWindow.reloadEditorIfShowing`) so its clean buffer neither hides the URL nor overwrites it on its next auto-save.

Tags: windows, linux

Covers:
- req~claude-pr-capture~2

Needs: impl, utest

### Claude workspace capture via tmux user option
`dsn~claude-workspace-capture~2`

`TmuxDiscovery`'s list-windows format additionally reads the `@cs_workspace` (and `@cs_status`) window user options via `#{@cs_workspace}`/`#{@cs_status}` — empty when unset, so nothing is required of windows that do not opt in. On import, a non-empty `@cs_workspace` is written to the task's `claude.workspace` field (`TaskFileParser` parses it into `Task.ClaudeConfig.workspace`); it is the working subdirectory Claude reported, which may differ from the start `cwd`.

Import alone only ever captures what was published *before* the task file existed, which for an app-created live task is nothing — its session publishes the workspace once it bootstrapped its worktree, long after `Main` wrote the file, so the task kept pointing at the start `cwd` (the shared workspaces root) and "Show diff" / "Open in IntelliJ" opened that root instead of the worktree.
Every tmux sync pass therefore refreshes it: `TmuxSync.workspaceRefreshes` matches `remote`'s tasks to the raw discovery windows by window id and reports each task whose window publishes a non-blank `@cs_workspace` different from the recorded one; `Main` writes it with `TaskFileParser.withClaudeWorkspace` (textual, comment-preserving, inserting the child when absent and leaving `cwd` alone — it shares its body with `withClaudeSessionId`) and counts it in the sync summary.
Tasks without a `claude:` section are skipped — a `workspace` without `cwd` would not parse. A session that moves to another worktree and republishes is followed the same way; an unchanged option writes nothing, so repeated passes are free of effect.

Tags: windows, linux

Covers:
- req~claude-workspace-capture~2

Needs: impl, utest

### Application settings
`dsn~app-settings~5`

`AppSettings` loads `%USERPROFILE%\.contextswitcher\settings.yaml`, creating it on first run with defaults: `tasksDir` (`%USERPROFILE%\.contextswitcher\tasks`), `wsPort` (17872), and a randomly generated `wsToken`.
An optional `tags:` list configures the tag palette — each entry a `name` and an optional `color` (CSS hex) parsed into `AppSettings.TagDef` (a null color renders as a muted gray chip); entries missing a name are skipped rather than failing the load, and the list round-trips through `store` (a null color is omitted).
An optional `hints:` boolean (default `true`, also on an absent or non-boolean value) selects between the intro and compact flavor of generated files (`dsn~skeleton-hints~4`); it round-trips through `store`, so a freshly created file shows the key.
An optional `claudeAuto:` boolean (default `false`; only an explicit boolean `true` counts — auto mode is opt-in) starts every launched/resumed Claude session with `--dangerously-skip-permissions` (`dsn~claude-auto-permissions~1`); it round-trips through `store` too.

Tags: windows, linux

Covers:
- req~task-directory-reload~1
- req~extension-token-auth~1
- req~task-tags~3
- req~skeleton-hints~2
- req~claude-auto-permissions~1

Needs: impl, utest

### Generated task files: intro vs compact
`req~skeleton-hints~2`

Files the app generates — task files from tmux import, `Add task…` (plain and remote Claude), and `Add from PR`, and a group's `CONTEXTSWITCHER.md` config skeleton — come in two flavors.
**Intro** (the default) embeds onboarding hints: commented example sections in the frontmatter and a provenance note under `# Notes`.
**Compact** drops the human-readable hint text — for experienced users whom the boilerplate disturbs: a task file carries only the real frontmatter keys; a group config keeps each key as a `# key: example` comment line (valid YAML once the `# ` is removed, with one example tag) but nothing prose.
A `hints` setting in `settings.yaml` selects the flavor once, app-wide.
Neither flavor writes explanatory body prose into a group config: the file configures the group and is never listed as a task.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Skeleton hints switch
`dsn~skeleton-hints~4`

`AppSettings.hints` is threaded into every generator of task-file content: `TmuxTaskImporter.skeleton`/`write`, `TaskFileParser.newTaskContent`, and `Main`'s PR-task and live-task builders.
With hints off (compact mode), nothing is written below the `# Notes` heading (no `Imported from tmux …`/`Created …` provenance line), and the commented hint lines disappear: the `# Fill in when ready`/`# intellij:` block of the import skeleton, the commented example sections of `newTaskContent`, and the commented-example fallbacks of `dsn~group-config-apply~2` and `dsn~claude-pr-capture~2` (an absent key or PR then produces nothing instead of the commented example — this item deliberately overrides those two without bumping them).
Real sections — a captured `claude:` section, a known PR's `browser:` entry, the group-default `remote`/`claude.cwd`/`folder` keys — are unaffected.
The group-config skeleton (`TaskFileParser.groupConfigSkeleton`, written by `ensureGroupConfig` — the "Add CS config…"/"Add category…"/group-tag-toggle path) follows the same switch: both flavors carry every key (`note`, `remote` — real when one is configured, else the commented example —, `workspacesRoot`, `repo`, `tags` with one example tag, and the switch-action defaults `intellij`/`browser`/`folders` of `dsn~category-action-defaults~1`) as `# key: example` comment lines that are valid YAML once the `# ` is removed — the two nested sections in flow style (`# intellij: {projectPath: …}`), so the one-line-per-key shape holds; intro adds the human-readable explanation lines under each key plus a machine-suffix example (`dsn~machine-key-variants~1`) that names **this** machine's own host token (`# workspacesRoot-<host>: …`, the placeholder `mylaptop` only when the host is unknown), so the F1 field reference for a category config both shows the host name and offers it ready to copy; compact is the bare key lines.
Neither flavor writes body prose below the `# <group> — group defaults` heading (this file configures the group; it is never listed as a task, `dsn~group-config-create~9`).
The user-editable `TEMPLATE.md` (`dsn~task-template-file~1`) is not touched: it is seeded once and owned by the user.

Tags: windows, linux

Covers:
- req~skeleton-hints~2

Needs: impl, utest

### Task tag model
`dsn~task-tag-model~2`

`Task` carries an immutable `List<String> tags` (defensively copied; a back-compat constructor defaults it empty so pre-tagging callers are unchanged).
`TaskFileParser` reads the frontmatter `tags` via `TaskTags.parse`, which normalizes a YAML list (each item one tag verbatim, so tags may contain spaces) or a single comma-separated scalar into an order-preserving, case-insensitively de-duplicated list.
`TaskFileParser.withTags(content, tags)` textually inserts/replaces the top-level `tags:` line as a flow list (scalars via `yamlScalar`, comment-preserving like `withNote`); an empty list removes the line.
`TaskTags` also provides the pure filter predicates: `matchesAll(taskTags, required)` (every required tag present, case-insensitive; empty required matches all) and `visible(taskTags, active)` (all tags when no filter, else only the active ones in task order).
A project group's `CONTEXTSWITCHER.md` may declare `tags:` too (`GroupConfig.tags`, parsed the same way); these are inherited by every task in the group.
The palette (name → optional color) lives in `AppSettings.TagDef` (`dsn~app-settings~5`).

Tags: windows, linux

Covers:
- req~task-tags~3

Needs: impl, utest

### Task directory git sync at startup and close
`dsn~task-git-backup~5`

`TaskGitBackup` syncs the task directory with its git remote at the application's lifecycle edges and in a round every few minutes — no per-change commits (the earlier debounced auto-backup produced a commit per edit burst, which read as noise; a round commits at most once per interval); all entry points are no-ops unless a `.git` directory is present, so the feature stays opt-in by repository presence.
**Periodic round:** `Main` calls `syncWhileRunning` every `TASK_BACKUP_MINUTES` (5, first after 5, skipped under the energy saver): on the same serialized executor, commit a dirty tree, `git pull --no-rebase --no-edit` with the automatic conflict resolution, `git push` — the sync-group round (`TaskGitBackup.syncNow`). It exists for the phone (`dsn~android-window-lookup~1`): the desktop recreating a task's window, queueing a message or the phone adding a task or deleting a queued message otherwise only crossed over at a desktop start or close. The pulled files land under the running app like the startup pull's, through the directory watcher; a queue the desktop holds in memory is not re-read (ceiling).
**Startup sync:** `pullOnStartup` runs one `git pull --no-rebase --no-edit` off the UI thread on the class's single serialized daemon executor, pulling in what another machine pushed while this one was closed; the already-started directory watcher reflects whatever lands. A conflict goes through the automatic resolution (`dsn~task-merge-resolution~2`); when that gives up too, the merge is aborted (`git merge --abort` — never strand the repo mid-merge) and only logs, offline only logs.
**Close sync:** `Main.stop` calls `syncOnClose` after the watcher stopped: `git status --porcelain`, on a dirty tree `git add -A` + `git commit -m "ContextSwitcher auto-backup <timestamp>"`, then `git push` — even on a clean tree, since a previous offline close may have left a commit unpushed. A rejected push (another machine pushed while this one ran) is retried once: `git pull --no-rebase --no-edit`, the automatic conflict resolution, `git push` again. The pull is a **merge** (never rebase) so the history keeps a merge commit recording what each machine knew at reconciliation; `--no-edit` keeps it non-interactive and the explicit `--no-rebase` dodges git's divergent-branches prompt. `syncOnClose` blocks the closing app — that is the point, the push must happen before the JVM exits — but at most `CLOSE_TIMEOUT_MINUTES` (10) as a last-resort cap over the per-command timeouts.
git runs via `LocalCommandRunner` (system git, MADR 0011) with a network-tolerant timeout; a commit failure is logged at **warn** (a persistent one silently stops all backup, so it must be visible), other failures (no remote, offline, no `git`) at info.
**Stale lock recovery:** a git process killed mid-write (app force-closed, timeout) leaves a `.git/index.lock` that blocks every future `add`/`commit`, silently killing the sync. Before each sync `clearStaleLock` removes an `index.lock` older than `STALE_LOCK_MS` (30 s) — the sync serializes its own git on one thread and `add`/`commit` are fast, so a lock that old is a leftover, while a fresh one (a real in-flight git) is left alone.
`TaskRepository` excludes `.git` from scanning and watching (`isProjectGroup`), so git's internal writes neither churn the watcher nor surface as a project group.

Tags: windows, linux

Covers:
- req~task-git-backup~4

Needs: impl, utest

### Automatic conflict resolution: deletion wins, semantic task merge, Claude
`dsn~task-merge-resolution~2`

git merges task files line by line, so two machines that changed *different* frontmatter keys still conflict whenever the keys happen to sit near each other — the routine "one machine set `status`, the other added `tags`" ends in conflict markers in a file the app then cannot even parse.
`TaskGitBackup.resolveConflicts` therefore retries a failed merge itself before aborting it: `git diff -z --name-only --diff-filter=U` lists the conflicted files, and each is resolved by the first applicable rule:

* **Deletion wins:** a file missing its "ours" or "theirs" merge stage was deleted on that side while the other modified it — it is resolved as deleted (`git rm -f`), no questions asked.
* **Semantic task merge:** a `.md` file is merged by `TaskMerge` **per top-level frontmatter key** instead of per line. The three merge stages (`git show :1:` / `:2:` / `:3:` — base, ours, theirs) are split into frontmatter and notes, and the frontmatter into one verbatim text block per top-level key: the key's line, its indented continuation lines (`tmux:` and its nested keys), and the comment lines directly above it. Blocks are carried over as text, so comments, quoting, and key order survive — no YAML round-trip that would reformat the untouched parts of the file. Per key the ordinary three-way rule applies: changed on one side only → **that change is kept**, whichever side it came from (this is the case that motivated the feature — *both* machines' changes end up in the file); equal on both sides → no decision needed; added by the other machine only → appended after the keys this machine knows (the value is merged, its position is not preserved). Genuinely divergent — the same key changed to two different values — is decided **last writer wins**, read off git history: the commit timestamps of the last commit touching that file on `HEAD` and on `MERGE_HEAD`. The Markdown notes are merged as one unit by the same rule, except that a divergence hands the file to the next rule instead of guessing at prose.
* **Claude:** whatever remains — a non-task file, or a task file whose notes both machines rewrote — goes to a headless `claude -p --dangerously-skip-permissions` run in the task directory (`TaskGitBackup.claudeResolve`, `LocalCommandRunner` with a 5-minute timeout): the working tree holds the file with git's conflict markers, and the prompt asks Claude to edit it in place, preferring the more recent change and deleting the file when that is the resolution. The result is accepted only when Claude exits cleanly and the file afterwards is gone (staged as a deletion) or free of conflict markers (staged as resolved).

When every conflicted file resolved, the merge is completed with `git commit --no-edit`; a single give-up leaves the merge in progress for the caller to abort, which also discards the files already written.

Tags: windows, linux

Covers:
- req~task-git-backup~4

Needs: impl, utest

### Empty categories tracked with `.gitkeep`
`dsn~empty-category-gitkeep~1`

git cannot represent an empty directory, so a category folder with no task files would never commit or sync to another machine. `MainWindow.TaskFileAccess` maintains the invariant *an empty category folder holds exactly one `.gitkeep`, a populated one holds none* (the tasks root is never touched — only subfolders):
`createFolder` writes a `.gitkeep` into the freshly created (empty) category; `save` and the target of a `move` drop it once a real file lands; `delete` and the source of a `move` re-add it when their folder goes empty (deleting a category's last task keeps the category). `isFolderEmpty` treats a lone `.gitkeep` as empty. The placeholder is not a `.md` file, so `TaskRepository` never parses it as a task, and a folder holding only a `.gitkeep` still counts as a project group (`isProjectGroup`), so the empty category still shows in the list with its "add first task" affordance. The `.gitkeep` files live in the task directory, so the close-time git sync commits them with everything else.

Tags: windows, linux

Covers:
- req~task-git-backup~4

Needs: impl, utest

### Task files are written atomically
`dsn~task-file-atomic-write~1`

`Files.writeString` truncates the target before it writes it, so a process that dies between the two leaves a **0-byte** task file — the content is gone, and the file no longer parses ("must start with a '---' frontmatter fence").
That is not hypothetical: on 2026-09-07 a background frontmatter save (the Claude commit/workspace re-sync) was in flight while `Main.stop` was tearing the executors down, and `jabcon-board/2026-09-07-gource-highlights-video.md` came back empty on the next start — recoverable only because the close-time backup had committed it moments earlier.
`Main.writeAtomically` therefore writes the content to a sibling `<name>.tmp` and replaces the target with one `Files.move(…, ATOMIC_MOVE)`; on a filesystem without atomic moves it falls back to `REPLACE_EXISTING`. The target is either the old content or the new one, never nothing.
Every task-file write goes through the single `MainWindow.TaskFileAccess.save`, so the one guard covers the editor lane, the frontmatter mutators, task creation and the tmux/Claude re-syncs alike. The temp file is not a `.md`, so the directory watcher ignores it (`TaskRepository`).

Tags: windows, linux

Covers:
- req~task-file-format~1

Needs: impl, utest

### A failed frontmatter write is reported, never swallowed
`dsn~frontmatter-write-failure~1`

`MainWindow.rewriteFrontmatter` — the load-transform-save path behind the pin toggle, the status writes, the tmux/Claude re-syncs and the link writers — dropped `TaskFileAccess.save`'s error return on the floor.
A write that fails (the file locked by a sync client, a read-only directory, a full disk) then looked exactly like a successful one: the row kept showing the value the in-memory task carries until the next reload, and the change was simply gone after the next restart, with nothing said at the time it was lost.
The error is now logged and put on the status bar (naming the file), and the editor reload is skipped — the file on disk did not change, so re-opening it would only replace the user's buffer with the old content.
The unreadable-file branch above it already logged and returned; this is its write-side counterpart.

Tags: windows, linux

Covers:
- req~task-file-format~1

Needs: impl

### Clone the task repository on a fresh machine
`dsn~task-git-clone-setup~2`

The clone offer is a page of the first-start wizard (`dsn~setup-wizard~8`), shown by `Main.runSetupWizard` during `start()` (on the FX thread), before the repository is scanned or seeded: when the task directory is missing or empty (`isEmptyOrMissing`) and not already a git repo.
A non-blank clone URL runs `git clone <url> <tasksDir>` via `LocalCommandRunner` (`Main.cloneTaskRepo`: system git, MADR 0011; a network-tolerant timeout); a failure is logged and shown, and startup continues with an empty directory. An empty field (or a cancelled wizard) starts fresh — the normal empty-directory path then seeds a `TEMPLATE.md`.
The offer is deliberately limited to the missing/empty case: that is where a `git clone` is safe (it refuses a non-empty target) and where the fresh-machine recovery applies; an existing local-only task directory is left untouched (and is never re-prompted, since it is no longer empty).

Tags: windows, linux

Covers:
- req~task-git-backup~4

Needs: impl

### The auto category's pull-request search
`dsn~auto-pr-lookup~2`

`AutoPrPoller` lists the task subfolders carrying a `CONTEXTSWITCHER.md`, reads each one's `auto:` section (`GroupConfig.AutoPr`: `query`, `maxSloc`, `deleteHours`) from disk on its own daemon thread — two small files, and no FX-thread-only collection is touched — and runs one `AutoPrLookup` round per configured category every `Main.AUTO_PR_POLL_SECONDS` (600), skipped whole while the energy saver is on and re-runnable through the toolbar refresh.
`AutoPrLookup` asks the local `gh` (MADR 0003) for one `gh api graphql` call per category: GitHub's `search` plus, for each hit, `files(first: 100)`, so the size filter costs no second request. The round comes back as an `AutoPrLookup.Search` — the size-filtered `matches` to add, and `open`, the URL of *every* hit. The configured query travels as a GraphQL variable (no quote of ours in the argv — the Windows trap) with `is:pr is:open` prepended: the reconcile deletes the task of a PR that left the query, so a closed PR matching again would resurrect the task it just deleted, forever.
`maxSloc` counts additions plus deletions of the files whose path carries no `test`/`tests`/`spec`/`specs` word — split at separators *and* camel-case humps, so `src/test/java/…`, `FooTest.java`, `foo_test.go` and `app.spec.ts` are tests while `latest.json` is not; a PR touching more files than the query reads is dropped rather than under-counted. `maxSloc: 0` (the default) filters nothing.
What keeps a task is not that round's hits but the state of its pull request: the poller subtracts the hits from the category's own task PR URLs (`Main.categoryPrUrls`, the whole category — never narrowed to the rows on screen) and resolves the remainder through one batched `PrStateLookup` call, adding everything **not** answered `MERGED`/`CLOSED` — an unreadable answer included — to the round's `live` set. A category nobody dropped anything into has no remainder and makes no such call.
A failed round returns **null**, not an empty list, and reports nothing: an empty result means "every pull request of this category is done", which an offline `gh` must never be able to say.
Nothing runs before `Main.tasksScanned` reports the initial task-directory scan finished, for the same reason: reconciling against a half-loaded list would add a second task for every pull request whose task has not been parsed yet.

Tags: windows, linux

Covers:
- req~auto-pr-category~3

Needs: impl, utest

### Reconciling an auto category's tasks
`dsn~auto-pr-category~3`

`AutoPrReconcile.plan` is the pure decision — the whole task list, the category, the round's matched and live URLs, the category's `AutoPr` and a clock in; four lists out — so the rule that deletes task files is tested without a GitHub account or a window (`AutoPrReconcileTest`).
**Matched** decides what is added, **live** what stays, and the two are deliberately different sets (`dsn~auto-pr-lookup~2`).
**Add:** a matched URL no task carries (in *any* category, compared through `Task.normalizeUrl`) — the user already working on that pull request elsewhere must not get a competing row.
**Suspend:** a category task whose pull requests are all *out of the live set* — merged or closed, not merely unmatched — while it is `active`: `TaskFileParser.withStatus(SUSPENDED)`, which stamps the `suspended:` timestamp (`dsn~suspended-timestamp~1`) the grace period is then measured from, followed by `Frontmatter.set(…, TaskFileParser.AUTO_PR_CLOSED, true)`. This is what lets a pull request dragged into the category by hand stay, whatever its size and whoever it was requested from, and what stops a PR that outgrew `maxSloc` from being evicted for growing.
**Resume:** a suspended task carrying that `autoPrClosed: true` mark whose pull request is live again — `withStatus(ACTIVE)`, which drops the timestamp *and* the mark.
The mark is what separates the application's suspend from the user's: `withStatus` clears it on **every** status write, so only the reconcile's own suspend carries it, and a row the user paused — the dismissal gesture for an auto category (`Task.autoPrClosed`) — is never resurrected. Nor is it deleted, since its pull request being open keeps it in the live set and out of the delete branch.
**Delete:** such a task once `suspended:` is more than `deleteHours` old; `0`, a missing or an unreadable timestamp keeps the file — a file is never deleted on a guess.
A task carrying a `tmux:` section is exempt from all three: the moment a session runs in it, it is the user's task, not a triage row. So is a task with no pull-request URL at all (hand-written notes living in the category).
`MainWindow.applyAutoPrs` executes the plan on the FX thread: each added pull request becomes a task file with its title, `status: active`, the pull request as the single `browser.urls` entry and the category's default `remote` (no `tmux:` — nothing is started, the user switches to it when it is their turn), and the deletes are plain file deletes — no confirmation (an unattended list is the point) and no session kill (by the exemption above there is no session). The round's outcome is counted onto the status bar and into the log.

Tags: windows, linux

Covers:
- req~auto-pr-category~3

Needs: impl, utest

### The auto category's synced-from row
`dsn~auto-category-placeholder-row~2`

An auto category (its `CONTEXTSWITCHER.md` carries an `auto:` section) is filled by its search, never by hand, so it gets neither the header's plus nor the `Add task…` row (`dsn~task-create-ui~15`): its `AddTaskRow` renders as a task-shaped placeholder instead — a sync icon, "Synced from a GitHub pull-request search", and the query as the muted second line.
`MainWindow` emits that row as the **last** row of an auto category whether or not it has tasks (where a hand-filled category's last row is its `Add task…` row), so where the rows come from stays visible.
A click anywhere on the row opens `AutoPrLookup.searchUrl` — the same query with the forced `is:pr is:open`, URL-encoded into `https://github.com/search?…&type=pullrequests` — in the browser. The web search is approximate on purpose: `maxSloc` is this application's filter and has no GitHub equivalent.

Tags: windows, linux

Covers:
- req~auto-pr-category~3

Needs: impl, utest

### Sync-group rounds
`dsn~task-sync-groups~1`

`TaskSync` keeps one clone per group under `<configDir>/sync/<group>` — outside the task directory, which is a git repository of its own — and reads the group list (`name`, `url`) and the ignored shared tasks from the task directory's `.sync/groups.yaml` and `.sync/ignored.yaml`, so both ride the personal backup (`dsn~task-git-backup~5`) to the user's other machines.
A local task takes part through `sync:` (its group names) and `syncId:` (its file name in the groups).
A group's repository is flat: `<syncId>.md` holding the task file reduced to `title`, `tags`, `browser`, `chat`, `note` and the notes body; every other key stays local.
A round per group: clone on first use; write each local task's shared part into the clone; `TaskGitBackup.syncNow` commits, pulls (merge, never rebase, with the automatic conflict resolution of `dsn~task-merge-resolution~2`) and pushes with the user's own git credentials (MADR 0011); then each shared file that changed is merged back into its local file per key (`TaskMerge`, with what was written out as the base), leaving the local-only keys untouched.
The `syncId`s the last round had in sync are remembered per machine in the clone's `.git/contextswitcher-synced`, which tells the cases apart: a remembered task gone locally was deleted (its shared file is removed for everyone) or, when a local file still carries its `syncId`, left (the shared file is recorded as ignored); a remembered task whose shared file is gone was deleted by another member (the local file leaves the group but is kept — it holds the user's own sessions); a task not remembered yet whose shared file exists is joining and adopts the group's version instead of overwriting it.
No deletion is propagated in a round that found a task file with unparseable frontmatter, or no task file at all, since a hidden `syncId` would read as a deletion.
An ignored shared task is recorded with the SHA-256 of its content, so a later change offers it again.

Tags: windows, linux

Covers:
- req~task-sync-groups~1

Needs: impl, utest

### Sync-groups button, window and task menu
`dsn~task-sync-groups-ui~1`

The main toolbar's sync-groups button (`ACCOUNT_SYNC`) carries the update button's badge style with the count of shared tasks not ignored and waiting to be sorted in; a click opens `SyncGroupsWindow`, a non-modal window owned by the main stage.
The window lists the configured groups (add with name and repository URL, remove after confirmation — local tasks keep their `sync:` and simply stop syncing) and the waiting shared tasks, ignored ones on "Show ignored"; a task's right-click menu offers "Move to category" (the root plus every category) and "Ignore until it changes".
Moving creates `<category>/<syncId>.md` from the shared content with `status: suspended` and the group joined, or — when a local file still carries the `syncId` after leaving — joins that file again and moves it there.
A task row's right-click menu gains a "Sync groups" submenu, one check item per configured group, joining or leaving; the delete confirmation warns that a task in a configured group is deleted for every member.
`Main` runs a round on the single `task-sync` thread every 5 minutes (skipped under the energy saver, `dsn~energy-saver~1`, and first after 5 minutes so the startup backup pull has landed), on "Refresh now", after every join, leave, sort-in and "Sync now", and once on close ahead of the backup's close sync (at most 2 minutes); the badge is filled from the clones at startup without network.

Tags: windows, linux

Covers:
- req~task-sync-groups~1

Needs: impl, utest
