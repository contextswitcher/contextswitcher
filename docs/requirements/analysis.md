# Refactoring insight

Semantic analysis of a task's work via [RefactoringMiner](https://github.com/tsantalis/RefactoringMiner) on the task's remote ([#50](https://github.com/contextswitcher/contextswitcher-private/issues/50), MADR 0022).

## Requirements

### Refactoring web view on demand
`req~refactoring-web-view~1`

From a task's row, the user opens RefactoringMiner's Monaco AST-diff web view comparing the task's live worktree — uncommitted work included — against the category's base branch (default `origin/main`).
The view runs on the task's remote and is reached through a local browser tab; closing the app (or opening another task's view) leaves no server process behind on the remote.

Tags: windows, linux

Covers:
- feat~refactoring-insight~1

Needs: dsn

### Automatic refactoring count badge
`req~refactoring-badge~1`

After the task's AI session goes idle, its row shows how many refactorings the task's committed work contains relative to the category's base branch, without the user asking.
Clicking the count opens the web view.
A task whose count cannot be determined (worktree gone, remote unreachable) keeps its last known count rather than flickering to nothing.

Tags: windows, linux

Covers:
- feat~refactoring-insight~1

Needs: dsn

## Design

### RefactoringMiner command lines
`dsn~refactoring-miner-commands~1`

`RefactoringMinerCommands` builds pure argv, quote-free per `dsn~ssh-command-runner~6` (no double quotes anywhere; shell grouping via single quotes, the cleanup handler a named function instead of a quoted `trap` argument).
The view command is one long-lived ssh that forwards `refactoringMinerPort` (`-L port:127.0.0.1:port`, `ExitOnForwardFailure`) **and** runs the blocking `bin/RefactoringMiner diff --src <temp base> --dst <worktree>` server, the temp base being a `git archive <baseBranch>` extraction; teardown is a **stdin watchdog** — the JVM runs in the background while the script blocks on `cat`, and the connection's end (process destroy, app crash, dead network) EOFs stdin, upon which the script kills the JVM and removes the temp base.
Deliberately no `ssh -tt` + `EXIT` trap: without a local tty the forced pty proved unreliable and orphaned the JVM (verified live 2026-07-29).
The badge runs as two commands: a cheap probe printing HEAD and `merge-base <baseBranch> HEAD`, and — only when needed — the count run `RefactoringMiner -bc <git-common-dir> <merge-base> <HEAD> -json <tmp>` with the JSON catted to stdout; the repository is addressed by its **absolute git common dir** because `-bc` reads it via JGit, which cannot resolve a linked worktree's `.git` file.
`baseBranch` comes from the category's `CONTEXTSWITCHER.md` (`GroupConfig`), defaulting to `origin/main`.

Tags: windows, linux

Covers:
- req~refactoring-web-view~1
- req~refactoring-badge~1

Needs: impl, utest

### Refactoring web view lifecycle
`dsn~refactoring-web-view~1`

`RefactoringMinerView` owns the one long-lived view process: opening a view destroys the previous process (single fixed local port), starts the new one keeping its stdin pipe open (the watchdog channel — never closed, never written), drains its output, and connect-polls `http://127.0.0.1:<port>/list` until HTTP 200 before reporting success; the root `/` 302-redirects, so poll and browser both target `/list`.
Generation-guarded like the terminal pane, closed on application exit.
The per-task hover button in the task list (shown for tasks with a `remote` and a `claude` section) threads to `Main.openRefactoringView`, which resolves settings (`refactoringMinerHome`, `refactoringMinerPort` — feature off while the home is unset), the worktree (`claude.workspace`, else `claude.cwd`) and the category's `baseBranch` fresh per click, then opens the browser on `/list` once the poll succeeds — disabled button and status-bar messages for the round-trip, the standard async single-shot shape.

Tags: windows, linux

Covers:
- req~refactoring-web-view~1

Needs: impl, utest

### RefactoringMiner setup button
`dsn~refactoring-miner-setup~1`

A **Set up** button beside the `refactoringMinerHome` field in the settings dialog provisions RefactoringMiner instead of leaving it a manual unzip per remote: `Main.setupRefactoringMiner` runs the setup command (download the pinned release zip via `curl`, else `wget`, unzip it into `$HOME/.contextswitcher/`, print the resolved absolute directory) on every remote the form lists, skipping remotes that already have it, and fills the field with the printed directory — the value still needs a **Save**.
A remote without `java` and remotes resolving the directory to different absolute paths (one setting names it for all) both fail the whole setup with a status-bar reason rather than a half-working install.

Tags: windows, linux

Covers:
- req~refactoring-web-view~1

Needs: impl, utest

### Refactoring analysis poller
`dsn~refactoring-analysis-poller~1`

`RefactoringMinerPoller` (shaped like `PrStatePoller`: daemon scheduler, fixed delay, per-task emission) analyzes every live task with a remote, a status-publishing tmux window, and a known worktree — but only while the window's live `@cs_status` is `waiting` or `done`, so a working session is never raced.
Per tick and task, `RefactoringLookup` probes HEAD and merge-base first: an unchanged HEAD returns the cached `RefactoringSummary` without an analysis run, HEAD equal to the merge-base is count 0 without a JVM start, and only a moved HEAD runs the count command, whose JSON is parsed into the summary (total refactorings across all commits; unparseable output yields nothing and keeps the previous state).
`MainWindow` merges the summaries per task id — never removing, repainting rows without rebuilding — and the row renders a circled-count badge (hidden at 0) whose click opens the web view.
The poller exists only when `refactoringMinerHome` is configured at startup.

Tags: windows, linux

Covers:
- req~refactoring-badge~1

Needs: impl, utest
