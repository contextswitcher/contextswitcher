---
status: proposed
date: 2026-09-11
decision-makers: Oliver Kopp
---

# ghq Layout for Checkouts, the Workspaces Root Beside It

## Context and Problem Statement

A category created from a repository URL currently lands in `<parent>/<repo>-workspaces`, with the primary clone as a subdirectory of it (`.../jabref-workspaces/jabref`), and `<parent>` is guessed from a sibling category's `workspacesRoot`, falling back to a hardcoded `/data/koppor` (`MainWindow.workspacesParent`).
Two things are wrong with that.
The parent is machine- and remote-specific but is never configured anywhere — it is inferred.
And the clone sits at a path no other tool knows: everything else on the remote finds repositories under the [ghq](https://github.com/x-motemen/ghq) layout `<root>/<host>/<owner>/<repo>`, so a `ghq get` of the same repository later creates a *second* clone somewhere else.

Where does the primary clone go, where does the per-task worktree root go, and who says what the root directory is?

## Decision Drivers

* One clone per repository per machine — a second one under a different path is a trap.
* The root directory differs per remote (`/data/koppor` here, `$HOME/ghq` elsewhere); a guess from a sibling category is not a configuration.
* Existing categories must keep working untouched — no migration.
* Directory names must not collide with a repository that could actually exist.

## Considered Options

* **A** — ghq path is the checkout, workspaces root beside it: `<root>/github.com/JabRef/jabref` plus `<root>/github.com/JabRef/jabref=workspaces`
* **B** — ghq path is the workspaces root, checkout inside it: `<root>/github.com/JabRef/jabref/jabref`
* **C** — keep today's containment, shift it under the ghq root: `<root>/github.com/JabRef/jabref=workspaces/jabref`
* **D** — leave the layout alone, only make the parent configurable

## Decision Outcome

Chosen option: "**A** — ghq path is the checkout, workspaces root beside it", because it is the only one where the primary clone is at the path ghq itself would use, which is the entire point of adopting the convention.
B and C both put the clone one level off the ghq path, so `ghq get`, `ghq look` and any ghq-driven `cd` miss it and clone again.

The sibling directory is named `<repo>=workspaces`, with `=`, not `-`.
A GitHub repository name can only contain `A-Za-z0-9._-`, so `=` is the one separator that can never collide with a real repository checked out beside it — `jabref-workspaces` could plausibly be someone's repository, `jabref=workspaces` cannot be.

The root comes from the remote: a `remotes:` entry in `settings.yaml` may be written `<remote>:<root>` (scp syntax, and still a plain string, so the settings form's multi-line list needs no new type):

```yaml
remotes:
  - devbox:/data/koppor
  - devbox
```

An entry without a root keeps today's behaviour (sibling guess, then `/data/koppor`), so existing settings files mean exactly what they meant before.
A category created from a URL then gets both paths written into its `CONTEXTSWITCHER.md` explicitly:

```yaml
workspacesRoot: /data/koppor/github.com/JabRef/jabref=workspaces
mainCheckout:   /data/koppor/github.com/JabRef/jabref
```

A category created *without* a URL has no ghq path, so it gets `<root>/<name>=workspaces` and no `mainCheckout`.

### Consequences

* Good, because the primary clone is exactly where ghq (and anything built on it) expects it, so there is one clone per repository and `ghq list` sees the app's checkouts.
* Good, because the root is stated per remote instead of inferred from whichever category happened to sort first.
* Good, because owner and host stay in the path: two `jabref` repositories from different forks no longer fight over one directory name.
* Bad, because `mainCheckout` is no longer a subdirectory of `workspacesRoot` — the invariant behind the current template and the `CLAUDE.md` wording ("the primary clone plus per-task worktrees, all in one root") is gone.
  Both are already separate frontmatter keys and nothing in the code derives one from the other, so what this costs is prose: the workspace-root `CLAUDE.md` template and the category-setup prompt must say "clone into the sibling directory", not "into a subdirectory of the current directory".
* Bad, because paths get long and deep, which is mostly a display problem (the category name stays the repository name).
* Neutral, because existing categories are not migrated; they keep their `<name>-workspaces` paths and keep working.

## Pros and Cons of the Options

### B — ghq path is the workspaces root, checkout inside it

* Good, because the current containment invariant survives untouched.
* Bad, because `<root>/github.com/JabRef/jabref` is then a directory full of worktrees rather than a checkout, which is precisely what ghq and every tool reading that layout assume it is not.

### C — `<repo>=workspaces` as the ghq-level directory, checkout inside

* Good, because containment survives and the ghq path stays free.
* Bad, because the checkout is at `.../jabref=workspaces/jabref` and a later `ghq get` of the same repository clones a second copy at `.../jabref`.
* Bad, because it takes the cost of the ghq layout (depth, an owner level) without the benefit (a findable checkout).

### D — configurable parent only

* Good, because it is a few lines.
* Bad, because it leaves the duplicate-clone problem exactly as it is; the parent was never the painful half.

Revisit if ghq stops being how repositories are found on the remotes, or if the split between `mainCheckout` and `workspacesRoot` turns out to confuse Claude sessions in practice.
