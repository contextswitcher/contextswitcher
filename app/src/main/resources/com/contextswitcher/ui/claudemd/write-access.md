# <project>-workspaces layout and workflow

This directory is the root for `<project>` work done with Claude Code.

**Location:** lives at `<absolute path>`.

**What `<project>` is:** <one paragraph: purpose, language/build system, surprises>.

## Layout

- `<project>/` — the primary clone, tracking `origin/<main>`
  (`git@github.com:<owner>/<project>.git`). Kept clean and up to date. Source for per-task
  worktrees, not for task work directly.
- Per-task worktrees — sibling directories, one per task, named `<YYYY-MM-DD>-<short-slug>`.

## Remotes

- `origin` — `git@github.com:<owner>/<project>.git`, the canonical repo and the only remote.
  We have write access: task branches are pushed here and PRs are opened from them, so the
  team can review. <Add a personal fork as a second remote only if very-WIP pushes need one.>

## Workflow: one git worktree per task

1. In `<project>/`: `git fetch origin --prune` — never rely on a possibly-stale local `<main>`.
2. `git worktree add ../<YYYY-MM-DD>-<slug> -b <slug> origin/<main>` — always branch from
   `origin/<main>`, not local `<main>`.
3. `cd` into the worktree and do the work there.

<Build output (`build/`, `.gradle/`, `bin/`) is gitignored, so each worktree builds
independently — no cross-contamination between concurrent tasks.>

**Resuming an existing task:** fetch first; a local branch can be behind after other commits
landed or after force-pushes from review feedback. Merge `origin/<main>` in before continuing.

**Check whether the upstream branch is gone first:** `[origin/<branch>: gone]` means the PR
merged and the remote branch was deleted — don't resume it, start fresh from `origin/<main>`.

**Cleanup:** once merged, remove the worktree and delete the branch local and remote. Not while
the PR is still open.

**Why:** concurrent tasks don't collide on branch/index state in one shared checkout.

## Before committing: build, lint & test

<The exact commands CI runs and how to reproduce them locally, one bullet each; name the
workflow file. Include the formatter step if the repo has one, and what needs a display.>

## Opening PRs

<PR template / CONTRIBUTING.md, if any — otherwise: self-contained description of what changed,
why, how to test. Note merge style (squash?) and whether screenshots are expected for visible
changes.>

**End-of-task summary:** end the chat response with worktree, branch and PR link on one line.

## Relationship to <sibling project>

<Only if this repo is consumed by, or consumes, another workspace here: which side lands first,
how to test the pair locally, and where the other workspace lives.>

## Code comments

Explain non-obvious *why*, not *what*. No narrating the task that prompted the change.
