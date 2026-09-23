---
status: accepted
date: 2026-07-16
decision-makers: Oliver Kopp
---

# Task Directory Backup via the System `git` CLI

## Context and Problem Statement

Tasks are Markdown files in `~/.contextswitcher/tasks/`.
Moving between machines, or simply wanting a safety net during development, calls for the task directory to be backed up somewhere durable and synced — ideally versioned, so history is not lost and edits from either machine can be reconciled.
How should the app back the directory up, and with what git access?

## Considered Options

* System `git` CLI (shell out `add` / `commit` / `push` after each change)
* JGit (`org.eclipse.jgit`, the pure-Java git library; "EGit" is its Eclipse IDE front-end)
* App-level file sync without git (copy to a second location / cloud folder)

## Decision Outcome

Chosen option: **system `git` CLI**, because it matches the project's standing preference for system tools over embedded clients (MADR 0003, "system `ssh` only") and — decisively — reuses the user's **existing git credentials**: they already push repositories daily, so `git push` authenticates with the configured SSH keys / credential manager with zero extra wiring. JGit would need its own SSH and credential configuration, which is precisely the hard part, for no benefit here. Versioning (over plain file copy) gives free history and a path to later reconciliation.

Recovery on a fresh machine is the symmetric case: when the task directory is missing or empty at startup, the app offers to `git clone` the repository into it (a small dialog with the clone URL and a "Skip git sync"), so moving machines is a URL, not a manual clone. `git clone` is used only for the empty/missing case (it refuses a non-empty target anyway); an existing local directory is never touched.

The app does **not** otherwise manage the repository or remote: backup acts only when the task directory is already a git repo (`.git` present). The user runs the one-time `git init`, `git remote add`, and initial push; the app then stages, commits, merges the remote in (`pull --no-rebase --no-edit`, to integrate another machine's commits so concurrent backups do not reject each other — a merge, not a rebase, so the history keeps a merge commit recording what each machine knew), and pushes after each change (debounced, off the UI thread, best-effort — a missing remote or no network only logs). This keeps the app free of remote/credential configuration and makes the feature strictly opt-in: no repo, no backup.

The `.git` directory is excluded from the task scanner/watcher so git's internal writes do not churn the file watcher or appear as a project group.

### Consequences

* Good, because push authentication is inherited from the user's git setup — nothing to configure in the app.
* Good, because no new dependency, and the mechanism is a few `git` invocations mirroring the existing `ssh`/`powershell` shell-out pattern.
* Good, because opt-in by repo presence: users who do not want it simply never `git init` the directory.
* Bad, because it depends on `git` being on `PATH` (a given on the dev machines this targets; a silent no-op otherwise).
* Neutral, because a `pull` (merge) before each push absorbs the common case (the machines touched different files) and keeps a merge commit that records each machine's state at reconciliation (merge, not rebase, chosen for that visible history); a genuine conflict on the same file aborts the merge and leaves the local commit unpushed, logged, for manual resolution — the app does not attempt automatic conflict resolution.
* Neutral, because commit cadence is a debounced "after each change", not a curated history — commits are machine-generated snapshots.

## Pros and Cons of the Options

### JGit

* Good, because pure Java — no dependency on an installed `git`.
* Good, because programmatic access to status/commit/push without parsing CLI output.
* Bad, because remote push needs JGit's own SSH/credential stack configured separately from the user's working git setup — the main cost, and the main thing we want to avoid.
* Bad, because it is a new dependency (EPL — acceptable per policy, but still weight) for something the CLI does in three commands.

### App-level file sync without git

* Good, because trivial to copy files elsewhere.
* Bad, because no history and no built-in remote/merge story — reinvents the useful part of git badly.
