---
status: accepted
date: 2026-07-29
decision-makers: Oliver Kopp
---

# Refactoring Insight via the Plain RefactoringMiner JAR, Tunnelled with an ssh Stdin-Watchdog, Diffing Worktree vs Base Branch

## Context and Problem Statement

Issue [#50](https://github.com/contextswitcher/contextswitcher-private/issues/50) wants a per-task view of what Claude *refactored* — a large textual diff with a small semantic change is exactly where the shipped delta pager (`dsn~terminal-diff-window~2`) stops helping.
[RefactoringMiner](https://github.com/tsantalis/RefactoringMiner) (MIT, fits the permissive-only policy) detects refactorings and ships a Monaco AST-diff web UI, but it runs on the task's remote and binds loopback only.
How is it provisioned, what does it diff, and how does its server reach the user's browser — and die reliably?

## Considered Options

* Provisioning: Docker image on the remote vs. the plain release zip (Java 17+)
* Diff basis: committed range only vs. worktree-vs-base-branch directory diff
* Access/teardown: `ssh -tt -L` + `EXIT` trap vs. `ssh -L` + stdin watchdog

## Decision Outcome

Chosen: **plain JAR, worktree-vs-base directory diff, `ssh -L` with a stdin watchdog**, because each is the least machinery that holds — and the watchdog is the only teardown that held at all.

**Plain JAR, not Docker** (pivoted 2026-07-23).
The release zip needs only Java 17+, which the remote already runs; unzip once, point `refactoringMinerHome` at it.
Docker existed in the original plan solely to pin a runtime, and its `--network host` flag solely to reach a *container* loopback bind that a plain JVM gives directly — with it go the image pulls, the docker group, and the pull-time failure modes.

**Worktree vs base branch, in directory mode.**
`diff --src <temp base> --dst <worktree>` needs no git on the view side, so the live worktree shows *uncommitted* work — and untracked new files, which `git diff` cannot show at all.
The base is a `git archive <baseBranch>` extraction into a `mktemp -d`; per-category override via `CONTEXTSWITCHER.md` `baseBranch:` (default `origin/main`).
The automatic badge uses the committed range instead (`-bc <git-common-dir> <merge-base> <HEAD>`): it must be cacheable by HEAD sha, and a JGit-read repository needs the common dir, not a linked worktree path.

**One ssh process is tunnel and server; its stdin is the kill switch.**
`ssh -L port:127.0.0.1:port <remote> sh -c '…'` forwards the port and runs the blocking JVM, so one process embodies the whole view.
The issue's sketch tore down via `ssh -tt` + an `EXIT` trap; tested live (2026-07-29), that **fails** exactly in the app's context — spawned from `ProcessBuilder` without a local tty, killing ssh left the remote JVM running, holding port 6789, so every later view would die on its bind.
The watchdog inverts it: the JVM runs in the background while the script's foreground blocks on `cat`; when the ssh connection ends — `Process.destroy()`, an app crash, a dead network — stdin hits EOF and the script kills the JVM and removes the temp base.
Verified live end to end: serve through the tunnel, kill the local ssh, JVM gone, port free, temp base cleaned.
The one obligation it puts on the app: keep the ssh process's stdin pipe open and never write to it.

### Consequences

* Good, because provisioning is one unzip and one settings key; no new app dependency (JSON via the bundled Jackson), no Docker.
* Good, because teardown is structural — anything that ends the ssh connection ends the remote JVM, orphan-free by construction.
* Good, because uncommitted and untracked work is visible in the view, which neither `git diff` nor the committed-range badge can show.
* Neutral, because one fixed local port means one view at a time — opening another task's view kills the previous, matching the one-pty terminal model.
* Bad, because the view's base is whatever `baseBranch` was last fetched on the remote — a stale `origin/main` shows a stale base until something fetches.
* Bad, because a same-machine app-and-remote setup cannot use the identical local/remote port (the forward occupies it before the JVM binds); irrelevant to the Windows-app → Linux-remote deployment.
