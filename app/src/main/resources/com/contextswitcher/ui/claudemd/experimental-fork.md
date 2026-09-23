# <project>-experimental-workspaces layout and workflow

Variant of the write-access template for a **semantic fork**: a separate repo that carries
upstream `main` plus a stack of open PRs merged on top, so testers get one build with
everything in review. Copy this only if you run such a repo.

## The two repos

`<owner>/<project>-experimental` is a semantic fork (not a GitHub fork) of `<owner>/<project>`:

- `origin` → `<project>-experimental`, the repo we work in:
  - `origin/main` — a pure mirror of upstream `main`, force-reset by the nightly sync. Never
    commit or merge anything here; it gets discarded.
  - `origin/experimental` — `main` + the PRs listed in the sync workflow + <the one commit that
    never goes upstream: push triggers, rolling prerelease>.
- `upstream` → `<project>`, the real upstream (pushable). No separate clone: `origin/main`
  mirrors `upstream/main` exactly, so a branch can be pushed straight to `upstream` to file the
  PR there.

`.github/workflows/sync-upstream.yml` on `experimental` rebuilds this nightly: force-reset
`main` to upstream, then merge `origin/main` and each PR in its `PRS:` list into `experimental`.

## Branching and PR bases

**Branch from `origin/main` and base the PR on `main`** whenever the change is portable
upstream — the diff then applies to upstream unchanged. Base on another PR's branch only when
stacking. **Never base on `experimental`:** merging a PR's head into its own base makes GitHub
close it as merged and delete the head branch.

```
git -C <project>-experimental fetch origin --prune
git -C <project>-experimental worktree add ../<YYYY-MM-DD>-<slug> -b <slug> origin/main
```

**After opening a PR that should ship in `experimental`**, add its URL to the `PRS:` list in
the sync workflow on `experimental` — otherwise it never lands there. List a stack bottom-up.
Removing a merged or abandoned entry is part of closing the task.

Resuming, `gone`-branch checks and cleanup work as in the write-access template.

## Tooling

<Package manager and the four commands: install, test, lint, build.>
