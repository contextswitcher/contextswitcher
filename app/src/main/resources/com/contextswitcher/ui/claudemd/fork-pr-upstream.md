# <project>-workspaces layout and workflow

This directory is the root for `<project>` work done with Claude Code.

**Location:** lives at `<absolute path>`.

**What `<project>` is:** <one paragraph: what it does, language/build system, anything
surprising about it (e.g. "not a Node project despite the name")>.

## Layout

- `<project>/` — the primary clone. Kept clean and up to date on `<main>`. Used as the source
  for creating per-task worktrees, not for doing task work directly.
- Per-task worktrees — sibling directories, one per task, named `<YYYY-MM-DD>-<short-slug>`.

## Remotes: `origin` (fork, push here) vs `upstream` (PRs go here)

- `origin` — `git@github.com:<you>/<project>.git`, your fork. **All pushes go here**, task
  branches and WIP included.
- `upstream` — `git@github.com:<owner>/<project>.git`, the canonical repo. **Never push here.**

Branch from `upstream/<main>` (the true latest state), push to `origin`, open the PR against
upstream: `gh pr create --repo <owner>/<project> --base <main> --head <you>:<branch>`.

## Workflow: one git worktree per task

1. In `<project>/`: `git fetch upstream --prune && git fetch origin --prune` — never rely on a
   possibly-stale local `<main>`.
2. `git worktree add ../<YYYY-MM-DD>-<slug> -b <slug> upstream/<main>`
3. `cd` into the worktree and do the work there.
4. `git push -u origin <slug>`.

**Resuming an existing task:** a local branch silently falls behind. Fetch both remotes, then
merge/rebase `upstream/<main>` into the branch before continuing.

**Check whether it already landed:** if the tracking branch is gone (`git branch -vv` shows
`[origin/<branch>: gone]`) or the commits are in `upstream/<main>`, the PR merged — remove the
worktree, delete the branch, start fresh. <If upstream squash-merges, say so: the commits won't
appear verbatim, check `gh pr view` instead of `git log`.>

**Cleanup:** once merged, `git worktree remove ../<YYYY-MM-DD>-<slug>` and delete the branch
locally and on `origin`. Not while the PR is still open.

**Why:** concurrent tasks don't collide on branch/index state in one shared checkout.

## Before committing: build, lint & test

<The exact commands CI runs, and how to reproduce them locally — one bullet each. Name the
workflow file so the list can be re-checked. Note anything that needs a display (`xvfb-run`),
a container, or a formatter that must run before commit.>

## Opening PRs

<Does the repo have CONTRIBUTING.md / a PR template? If not, say "write a self-contained
description: what changed, why, how to test". Note maintainer expectations — e.g. keep the diff
minimal in a mature single-file utility.>

**End-of-task summary:** end the chat response with worktree, branch and PR link on one line.

## Code comments

Explain non-obvious *why* (invariants, workarounds, subtle constraints), not *what* the code
already says. No narrating the task that prompted the change — that belongs in the commit
message.
