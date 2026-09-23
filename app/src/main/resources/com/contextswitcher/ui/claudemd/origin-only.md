# <project>-workspaces layout and workflow

This directory is the root for `<project>` work done with Claude Code.

**Location:** lives at `<absolute path>`.

One-person project: no PRs, push straight to `<main>` on `origin`
(`git@github.com:<you>/<project>.git`). The repo's own `CLAUDE.md` inside the checkout holds
the project conventions — read it at session start.

## Layout

- `<project>/` — the primary clone, tracking `origin/<main>`. Kept clean; source for per-task
  worktrees, not for task work directly.
- Per-task worktrees — sibling directories, one per task, named `<YYYY-MM-DD>-<short-slug>`.

## Workflow: one git worktree per task

1. In `<project>/`: `git fetch origin --prune`.
2. `git worktree add ../<YYYY-MM-DD>-<slug> -b <slug> origin/<main>`
3. `cd` into the worktree and do the work there.
4. When done: merge into `<main>`, push, then `git worktree remove ../<YYYY-MM-DD>-<slug>` and
   delete the branch.

**Resuming an existing task:** fetch and merge `origin/<main>` into the branch first — don't
assume the worktree is current.

**Never `git rebase` — always merge.** <Drop this line if you don't care; keep it if you do,
because "always merge" only works if it's stated once and applied everywhere.>

**Why:** concurrent tasks don't collide on branch/index state in one shared checkout.

## Before committing

<Build/test/lint commands. With no PR and no reviewer, this is the only gate — keep it short
enough that it actually gets run.>

## Code comments

Explain non-obvious *why*, not *what*.
