---
status: accepted
date: 2026-10-09
decision-makers: Oliver Kopp
---

# ghq Layout for Checkouts, gwq for Per-Task Worktrees

## Context and Problem Statement

A category created from a repository URL currently lands in `<parent>/<repo>-workspaces`, with the primary clone as a subdirectory of it (`.../jabref-workspaces/jabref`), and `<parent>` is guessed from a sibling category's `workspacesRoot`, falling back to a hardcoded `/data/koppor` (`MainWindow.workspacesParent`).
The setup wizard proposes the same shape under the session host's home.
Two things are wrong with that.
The parent is machine- and remote-specific but is never configured anywhere — it is inferred.
And the clone sits at a path no other tool knows: everything else on the remote — and on the desktop itself — finds repositories under the [ghq](https://github.com/x-motemen/ghq) layout `<root>/<host>/<owner>/<repo>`, so a `ghq get` of the same repository later creates a *second* clone somewhere else.
Per-task worktrees have the same problem one level down: where [gwq](https://github.com/d-kuro/gwq) manages them, it already has its own place for them (its `worktree.basedir` and `naming.template`), and a worktree the app's session creates elsewhere is one `gwq list` does not see.

Where does the primary clone go, where do the per-task worktrees go, and who says what the root directory is — on a remote, in WSL, and on this machine alike?

The first version of this record (2026-09-11, proposed) answered for remotes only, took the root from a `<remote>:<root>` entry in `settings.yaml`, and kept the worktrees in an app-owned `<repo>=workspaces` directory.
Local sessions next to an existing ghq/gwq setup (2026-10-09: ghq root `c:\git-repositories`, gwq `naming.template` `{{.Host}}/{{.Owner}}/{{.Repository}}={{.Branch}}`) showed that the worktree half needs the same treatment as the clone, and that the root need not be configured at all.

## Decision Drivers

* One clone per repository per machine — a second one under a different path is a trap.
* One place for worktrees per machine — the user's own tool already decides it.
* The root directory differs per session host (`/data/koppor` on one remote, `$HOME/ghq` elsewhere, `c:\git-repositories` on the desktop); a guess from a sibling category is not a configuration.
* The same layout for remote, WSL and local sessions; the wizard asks nothing a newcomer cannot answer.
* Existing categories must keep working untouched — no migration.
* Directory names must not collide with a repository that could actually exist.

## Considered Options

* **A** — ghq path is the checkout, an app-owned workspaces root beside it: `<root>/github.com/JabRef/jabref` plus `<root>/github.com/JabRef/jabref=workspaces`
* **A+gwq** — ghq path is the checkout, gwq places the per-task worktrees; A's `=workspaces` only where gwq is missing
* **B** — ghq path is the workspaces root, checkout inside it: `<root>/github.com/JabRef/jabref/jabref`
* **C** — keep today's containment, shift it under the ghq root: `<root>/github.com/JabRef/jabref=workspaces/jabref`
* **D** — leave the layout alone, only make the parent configurable

## Decision Outcome

Chosen option: "**A+gwq** — ghq path is the checkout, gwq places the per-task worktrees", because it is the only one where both the primary clone and every worktree sit where the user's own tools put them, which is the entire point of adopting the conventions.
B and C put the clone one level off the ghq path, so `ghq get`, `ghq look` and any ghq-driven `cd` miss it and clone again; A gets the clone right but keeps a second worktree convention beside gwq's.

**The root is probed, not configured.**
The session host is asked `ghq root` — over ssh for a remote, through `wsl.exe` for a WSL distribution, directly on this machine — when the wizard checks the host and when a category is created from a URL.
ghq answers even when no root is set (its default `~/ghq`), so its presence is the whole condition.
Without ghq the old behaviour stays: the sibling guess, then the home, `<parent>/<name>-workspaces` with the clone inside.

**The clone** goes to the ghq path, `<root>/<host>/<owner>/<repo>`, written to the category's `CONTEXTSWITCHER.md` as `mainCheckout`.

**The worktrees** are gwq's when the host has gwq: the setup and the task prompts tell Claude to create the task's worktree with `gwq add -b <branch>` from the `mainCheckout` and to work in the directory `gwq get <branch>` prints, rather than a hand-made `git worktree add` path.
The app neither reads nor reproduces gwq's `naming.template` — the user's `{{.Host}}/{{.Owner}}/{{.Repository}}={{.Branch}}` puts the worktree beside the clone, gwq's default under `~/worktrees`; either is right, because gwq says so.
`workspacesRoot` then names the directory the session starts in — the `mainCheckout` — and no app-owned directory is created.

Without gwq, A applies: the worktrees go to the sibling `<repo>=workspaces`, with `=`, not `-`.
A GitHub repository name can only contain `A-Za-z0-9._-`, so `=` is the one separator that can never collide with a real repository checked out beside it — `jabref-workspaces` could plausibly be someone's repository, `jabref=workspaces` cannot be.
gwq's own `=` template uses the same reasoning.

```yaml
# with gwq
mainCheckout:   c:\git-repositories\github.com\JabRef\jabref
workspacesRoot: c:\git-repositories\github.com\JabRef\jabref
worktrees: gwq
# without gwq
mainCheckout:   /data/koppor/github.com/JabRef/jabref
workspacesRoot: /data/koppor/github.com/JabRef/jabref=workspaces
```

A category created *without* a URL has no ghq path, so it gets `<root>/<name>=workspaces` and no `mainCheckout`.

### Consequences

* Good, because the primary clone is exactly where ghq expects it and the worktrees exactly where gwq does, so there is one clone per repository, `ghq list` sees the app's checkouts and `gwq list` its worktrees.
* Good, because the root is the host's own answer instead of being inferred from whichever category happened to sort first — and nothing has to be typed into `settings.yaml` for it.
* Good, because owner and host stay in the path: two `jabref` repositories from different forks no longer fight over one directory name.
* Good, because local, WSL and remote categories are created the same way.
* Bad, because `mainCheckout` is no longer a subdirectory of `workspacesRoot` — the invariant behind the current template and the `CLAUDE.md` wording ("the primary clone plus per-task worktrees, all in one root") is gone.
  Both are already separate frontmatter keys and nothing in the code derives one from the other, so what this costs is prose: the workspace-root `CLAUDE.md` template and the category-setup prompt must say where the clone and the worktrees go instead of "into a subdirectory of the current directory".
* Bad, because the app no longer knows a worktree's path in advance under gwq; it learns it from the session's `claude.cwd` like it does today, and the workdir cleanup (`rejectWorkdir`, `protectedDirs`) must treat the `mainCheckout` as protected — it already does.
* Bad, because paths get long and deep, which is mostly a display problem (the category name stays the repository name).
* Neutral, because existing categories are not migrated; they keep their `<name>-workspaces` paths and keep working.

## Pros and Cons of the Options

### A — ghq checkout, app-owned `=workspaces` beside it

* Good, because the app knows every path in advance.
* Bad, because next to gwq it is a second worktree convention on the same machine: `gwq list` misses the app's worktrees, and a `gwq add` of the same branch makes another.
* Neutral, because it is exactly the fallback for hosts without gwq.

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

Revisit if ghq or gwq stops being how repositories and worktrees are found on the session hosts, or if the split between `mainCheckout` and `workspacesRoot` turns out to confuse Claude sessions in practice.
