# ContextSwitcher

[![Build](https://github.com/contextswitcher/contextswitcher/actions/workflows/build.yml/badge.svg)](https://github.com/contextswitcher/contextswitcher/actions/workflows/build.yml)

> ContextSwitcher is [Mylyn](https://www.eclipse.org/mylyn/) for the desktop: one click switches your whole working context — remote tmux window (e.g. a running Claude session), remote IntelliJ project, browser tabs, notes, chat room, folders.

Why this exists and the long-term vision: [docs/background.md](docs/background.md); related work: [docs/related-work.md](docs/related-work.md).

**Status: in daily use; released by date (see [CHANGELOG.md](CHANGELOG.md)).**
Everything described here is implemented; each section links to the requirement that specifies it in detail, so this file stays a guide rather than a specification.
Known limitations: the Firefox extension is MV2 and unsigned (temporary add-on; the Chrome one installs permanently), and virtual-desktop features are Windows-only (they no-op elsewhere).

## Contents

* [How it works](#how-it-works) · [Install and run](#install-and-run) · [First steps](#first-steps)
* [The window](#the-window) · [Tasks](#tasks) · [Categories](#categories) · [Auto categories](#auto-categories-a-review-list-that-fills-itself)
* [Switching](#switching) · [The terminal lane](#the-terminal-lane) · [The message queue](#the-message-queue) · [Finding, filtering, sorting](#finding-filtering-sorting)
* [Settings](#settings) · [Let Claude report its state](#let-claude-report-its-state-remote-setup) · [Browser extension](#browser-extension) · [Backup and sync](#backup-and-sync-of-the-tasks-directory) · [Android companion](#android-companion-app)
* [Recovery and troubleshooting](#recovery-and-troubleshooting) · [Project state and docs](#project-state-and-docs)

## How it works

A **task** is one Markdown file with YAML frontmatter in `~/.contextswitcher/tasks/`: the frontmatter says where the work lives (a tmux window on a remote, an IntelliJ project, browser URLs, folders, a note page, a chat room), the body is your notes.
A **category** is a subfolder of that directory, optionally carrying a `CONTEXTSWITCHER.md` with the defaults every task in it inherits.
Switching to a task runs every action its file configures, in parallel, and reports each one.
Nothing is hidden in a database — the files are the state, and editing them by hand is a supported way of working ([`feat~tasks-as-markdown-files~1`](docs/requirements/features.md)).

## Install and run

```
gradlew :app:run
```

With [`just`](https://just.systems) installed, the `justfile` wraps the common commands (Windows and Unix variants each):
`just run-debug` (Gradle run, fast, for development), `just run` (build the packaged app image and launch the bundled exe), `just pull-run` (`git pull` first, then `just run`), `just run-loop` (run it until you really quit — the toolbar's update button restarts the app into the newly pulled and rebuilt version), `just register` (build the image and register the `contextswitcher://` handler), `just jpackage` (just the image), `just uitest` (the TestFX UI tests, on Xvfb where there is no desktop).
`just` on its own lists them.

Installing `just`: `cargo binstall just` — which needs [`cargo-binstall`](https://github.com/cargo-bins/cargo-binstall) (`cargo install cargo-binstall`), which needs Rust ([rustup.rs](https://rustup.rs), or `winget install Rustlang.Rustup`).
No Rust and no wish for it? `winget install Casey.Just` on Windows, or any other route from [just's install docs](https://just.systems/man/en/packages.html).

**Requirements:** `ssh` on the `PATH` (ships with Windows 10+), key-based access to your remote host(s) via `~/.ssh/config`, `tmux` on the remote.
Java is provisioned by the Gradle toolchain automatically.
Optional, each enabling one feature: the [`gh` CLI](https://cli.github.com) (PR state icons, review-comment queueing, auto categories), the [`glab` CLI](https://gitlab.com/gitlab-org/cli) (the same state icons for GitLab merge requests), `jq` on the remote (exact Claude session ids, reported model), [delta](https://github.com/dandavison/delta) on the remote (syntax-highlighted diffs), a RefactoringMiner install (refactoring view).
A fresh install names the tools it misses in the status bar rather than failing silently.

**NixOS:** `nix-shell` in the repo root first — everything above then works as written.
JavaFX comes from plain Maven jars, so its native libraries are prebuilt `.so` files that expect the GTK/X11 stack in the usual system paths; on NixOS there is none, and the app dies at startup with `UnsatisfiedLinkError: no glassgtk3 in java.library.path`.
`shell.nix` supplies those libraries via `LD_LIBRARY_PATH`, plus the JDK, `just`, `jbang` (the changelog check) and `xvfb-run` (the UI tests).
With [direnv](https://direnv.net) installed, `direnv allow` once in the repo is enough — the checked-in `.envrc` then enters that shell on `cd`, and the commands above need no prefix.
Forgetting the shell is cheap for `just run-loop`: it looks for the GTK library before it pulls and builds, and names the shell instead of failing in the launcher after a full jpackage build.

**Without a JDK:** every green [Build run](https://github.com/contextswitcher/contextswitcher/actions/workflows/build.yml) uploads two artifacts (MADR 0012):
`ContextSwitcher-windows` — unzip, run `ContextSwitcher\ContextSwitcher.exe` (bundled JRE, nothing to install; `gradlew :app:packageApp` builds the same image for your own OS) —
and the browser extensions — `contextswitcher-firefox-xpi` (unsigned, [install notes](extension/firefox/README.md)) and `contextswitcher-chrome-zip` ([install notes](extension/chrome/README.md)).

**Deep links:** register the `contextswitcher://` protocol once with `scripts/register-url-handler.cmd` (Windows, per-user registry) or `scripts/register-url-handler.sh` (Linux, `.desktop` + `xdg-mime`) — both shipped in the app zip next to the app image, re-run after moving it.
From a source checkout, `gradlew :app:registerUrlHandler` (or `just register`) builds an image and registers against it in one step.

**Staying current:** the app checks its own checkout against its upstream every half hour and shows an update button in the toolbar when a newer commit exists.
Clicking it quits the app with exit code 55, which `just run-loop` takes as "pull, rebuild, start again" ([`dsn~restart-to-update~11`](docs/requirements/ui.md)).

## First steps

1. Start the app. `~/.contextswitcher/` is created with `settings.yaml` (including a random WebSocket token) and a commented `TEMPLATE.md` in `tasks/`.
   On the first start a wizard asks where your Claude sessions run (this machine, or a remote over ssh — either checked on the spot for `tmux` and `claude`), offers to install RefactoringMiner, ast-grep and codegraph with their skills on that host, asks whether to clone an existing task repository, and for a first project's repository URL; it writes the remote to the settings and sets the project up like **Add category from URL…** below.
   A page of it shows the port and token the browser extension's options page needs, with the install notes one click away.
   **Setup wizard…** in the toolbar's add menu runs it again later, for another remote or host.
2. Later remotes go into **Settings** (the gear at the top-right); everything else has a working default.
3. Get tasks in, whichever fits:
   * **Sync tmux windows…** (toolbar, ⋮ menu) — one task per running tmux window on your remotes, including working directory, Claude session id and the PR the session published.
   * **Add task…** (toolbar `+`, `Ctrl+T`) — type a title *or* paste a GitHub PR or GitLab MR URL; the app creates the tmux window on the category's remote, starts Claude in it, and primes it with the description or the PR. Pick the category, model and reasoning effort in the same dialog. **Add plain task** just writes the file.
   * **Add category from URL…** — hand it a repository URL and it sets the whole project up on the remote (workspaces root, clone, `CLAUDE.md`) and writes the category config for it.
4. Select a task: the Terminal pane mirrors its terminal live, Notes and Configuration edit its file, and the Queue drafts messages for its chat.
5. Press play (or double-click) to switch: tmux window focused, IntelliJ project opened, browser tabs focused, note and chat opened, folders raised.

For the running-status dots, the reported working directory, model and effort — the things that make the list *live* — do the one-time remote setup under [Let Claude report its state](#let-claude-report-its-state-remote-setup).

## The window

Every pane — task list, terminal, notes, configuration, queue — is a tab: drag one onto another pane's tabs or edge to regroup them, close one from its right-click menu, and bring it back from the panes menu at the top right ([decision 0032](docs/decisions/0032-shellfx-docking-shell-for-the-main-window.md)).

**Task list** (left) — categories as collapsible sections, root-level tasks ungrouped on top, completed tasks in a collapsed **Done** section at the bottom.
A row carries: the running dot, the title (one line, pinned tasks marked and listed first), the status, its PR-state icon (open, draft, merged, closed) and link icons for its other URLs, a queued-message count, and — where RefactoringMiner is configured — a circled count of the refactorings its commits contain.
Hovering shows the row actions (switch, pause, …); right-clicking gives the full menu (rename, mark done, pin, move to category, open in IntelliJ, add link, delete, …).
Drag a row onto a group header to move the task into that folder; drop it on a root-level row to ungroup it.

**Toolbar** (above the find field, icon-only) — back/forward through visited tasks, add menu, filter menu (with a badge while anything is narrowed), sort menu, group menu (folder, labels, or PR status), and at its right end a ⋮ menu with open tasks directory, sync tmux windows, restart running tasks, and refresh now ([`req~task-list-toolbar~2`](docs/requirements/ui.md)).
The toolbar across the top of the window holds what is app-wide — the energy saver, the browser extension status, the update button, the settings gear — and the panes menu at its right end; the window title names the selected category and how its tasks are doing (`JabRef | 1 waiting | 2 working | ContextSwitcher`).

**Terminal** — the live mirror of the selected task's tmux window, [described below](#the-terminal-lane).

**Notes** — the task's Markdown notes, editable in place.
It auto-saves when it loses focus, `Ctrl+S` saves manually, **Revert** reloads from disk, and changes made on disk are picked up live.

**Configuration** — the same file's frontmatter as a **form**: every key the file accepts, grouped and pre-filled; emptying a field removes its key, **Apply** writes back only the changed fields (comments and machine-specific keys survive), and **Raw YAML** shows the YAML for anything the form does not cover — including broken YAML, so it can be repaired here.
**Pop out** moves it into a window of its own, which docks back.
`F1` in a field explains the key under the caret.

**Queue** — the per-task message queue, [described below](#the-message-queue).

**Status bar** — the per-action chips of the last switch, one-line outcomes of everything asynchronous, the URL a hovered terminal link would open, and a button that opens the log.

The window remembers its geometry across restarts; `showOnAllDesktops` pins it to every Windows virtual desktop, and each desktop then keeps its own position and size.

## Tasks

One Markdown file per task in `~/.contextswitcher/tasks/`, the file name being the task id.
Copy `TEMPLATE.md`, give it a speaking name, and keep only the sections you need — switching runs exactly what is configured.

```yaml
---
title: "JabRef: fix groups NPE"
status: active            # active | suspended | done
tags: [phone, jabref]     # filter the list by tag (colors: settings.yaml)
pinned: true              # heads its block in the list (row menu -> Pinned)
remote: devbox            # ssh destination (alias or user@host) for all remote actions
tmux:
  session: "2"
  window: "@17"           # immutable tmux window id (or a window name)
# terminal:               # local alternative to tmux: focus a Windows Terminal tab
#   tabTitle: jabref-npe  # pin the tab title via WT right-click -> "Rename Tab"
claude:
  cwd: /home/you/repos/jabref
  sessionId: 8924d3ac-…   # enables resume after a host reboot
  workspace: /home/you/repos/jabref   # working subdir; auto-filled from @cs_workspace
  commit: 861a46a                     # last commit; auto-filled from @cs_commit
# intellij:
#   projectPath: /home/you/repos/jabref
# browser:
#   urls:
#     - https://github.com/JabRef/jabref/pull/12345
#     - url: https://ci.example.org/job/42     # optional per-URL title
#       title: nightly build
# folders:                # local directories to focus/open in File Explorer
#   - C:\Users\me\nextcloud\SE2
# note: onenote:…         # note page opened on switch
# chat: https://matrix.to/#/…             # Matrix room opened on switch (Element)
---

# Notes
```

`intellij:`, `browser:`, `folders:`, `note:` and `chat:` fall back to the task's [category](#categories) when the task does not set them, so a task file usually carries only what is specific to it.
Keys the app maintains itself (`suspended:`, `storedTabs:`, `autoPrClosed:`) appear as it needs them and are none of your business.
A file the parser cannot read becomes a red error row naming the problem — it never hides the rest of the list, and the editor opens it anyway.
Specification: [`docs/requirements/tasks.md`](docs/requirements/tasks.md).

### Machine-specific values

The tasks directory is synced between machines, but its paths are not: the checkout is `C:\git\jabref` on the desktop and `/data/koppor/jabref` on the Linux box.
Any key — in a task file and in a category config — can therefore be suffixed with a machine, and the most specific one wins: `<key>-<computer name>` beats `<key>-<os>` (`windows`, `linux`, `mac`) beats the plain `<key>`.

```yaml
folders: [C:\git\jabref]              # the fallback
folders-linux: [/data/koppor/jabref]    # on any Linux machine
folders-devbox: [/data/koppor/jabref-workspaces]   # on devbox specifically
intellij:
  projectPath: C:\git\jabref
  projectPath-devbox: /data/koppor/jabref   # keys inside a section work too
```

A suffix naming a machine that is not this one is ignored, so one synced file serves them all.
A misspelled suffix silently does nothing — like any other misspelled optional key.

## Categories

A category is a subfolder of the tasks directory — a project group, shown as a collapsible section (pinnable from its header, renamable, deletable).
It becomes a *configured* category by holding a `CONTEXTSWITCHER.md`: YAML frontmatter with the defaults every task in the folder inherits, the body free for project notes.
The app writes the file for you as a fully commented skeleton — group header right-click → **Add CS config…** (**Edit CS config…** once it exists), or **Add category…** for a new folder — and the editor's form edits it like a task file.
**Add category from URL…** does the whole setup from a repository URL: it names the category after the repository, writes the config with real values (remote, `workspacesRoot: <parent>/<name>-workspaces` next to your other roots, `mainCheckout`, `repo`, tag), asks how you work with that repository (fork and PR upstream, write access, your own, semantic fork), and starts a Claude session on the remote that creates the root, clones the repository and writes the root's `CLAUDE.md` from the matching shipped template — optionally continuing straight into a first message.

Every key is optional:

```yaml
---
note: onenote:https://d.docs.live.net/…/Projects.one#Page&…&end  # project page
chat: https://matrix.to/#/#jabref:matrix.org    # project's Matrix room
remote: koppor@devbox                          # ssh destination a new task inherits
workspacesRoot: /data/koppor/jabref-workspaces    # where a task's Claude session starts
workdir: /data/koppor/jabref                      # fixed session directory instead
mainCheckout: /data/koppor/jabref-workspaces/jabref   # permanent checkout, for "Show diff"
repo: https://github.com/JabRef/jabref          # header's repository icon, bootstrap prompt
baseBranch: upstream/develop                    # base of the refactoring diff
desktop: jabref                                 # virtual desktop this category lives on
pinned: true                                    # category listed above the unpinned ones
autoDelete: true                                # let automatisms delete its tasks (off by default)
tags: [jabref]                                  # inherited by every task in the category
auto:                                           # fill this category from a PR search
  query: "repo:JabRef/jabref review-requested:@me"
  maxSloc: 50                                   # skip PRs changing more non-test lines
  deleteHours: 24                               # keep a finished PR's task this long
intellij:                                       # switch-action defaults, for every task
  projectPath: /data/koppor/jabref                # in the category — see below
browser:
  urls:
    - https://github.com/JabRef/jabref
folders:
  - /data/koppor/jabref-workspaces
---

# jabref — group defaults
```

A task's own frontmatter always wins over these defaults.

* `note:` and `chat:` are opened on switch and from the header (note icon / right-click → **Open note**); a task's own value wins.
  Use OneNote's `onenote:…` link form so the desktop app opens instead of the browser, and Element's `matrix.to` permalink for the room.
* `workspacesRoot:` is the shared root under which Claude creates and manages each task's own working directory — normally the only one of the two you set.
  `workdir:` pins one fixed directory for every session instead and wins when both are set.
* `mainCheckout:` is a permanent checkout of `repo` (not a per-task worktree): what **Show diff** falls back to once a task's own worktree is deleted, to show the commit the session published.
* `baseBranch:` is what the category's task worktrees branch from — the base of the refactoring view and badge; unset means `origin/main`.
* `desktop:` is the virtual desktop's name as shown in the Windows Task View; the header's play button switches to it, and the filter menu's **active desktop** entry narrows the list to it.
  A category naming none uses the `fallbackDesktop` setting (`misc` by default; empty turns the fallback off).
  The nested form `desktop: {name: jabref, completeControl: true}` puts the desktop under **complete control**: suspending a task of the category stores the tab URLs of every browser window on that desktop into the task file (`storedTabs:`) and closes those windows — a clean desktop whenever you switch to it manually — and resuming reopens them there (tabs still open are kept, not reopened; windows pinned to all desktops are never touched).
* `autoDelete: true` lets the app delete the category's tasks unattended — a merged task, an auto category's expired one, a closed plain shell; without it they are kept (suspended), and a pinned task is never deleted automatically.
* `tags:` are inherited as if each task carried them, so filtering by the category's tag shows the whole category; colors come from `settings.yaml`.
* `folders:` additionally puts a folder button on the category header that opens just those directories — no task switch.
* `repo:` puts a repository icon there, and a remote category's header also carries a terminal icon that opens a **scratch tmux window** on its host — focused and typeable in the app's terminal, no task file, no Claude; turn it into a task later via **Sync tmux windows…**.
* `intellij:`, `browser:` and `folders:` are the **switch-action defaults**: written exactly as in a task file, they run for every task of the category that carries no such section itself.
  The fallback is per section: a task that overrides `browser:` still inherits the category's `intellij:` and `folders:`.
  Suspending a task closes only the task's *own* browser tabs — the category's URLs are shared by every task in it.

### Auto categories: a review list that fills itself

Reviewing other people's pull requests is recurring work whose *list* you should not have to maintain.
Give a category an `auto:` block — a GitHub search string, as you would type it into GitHub's own search — and every open pull request it matches becomes a task there, so the list shows what is waiting rather than what was once added ([`req~auto-pr-category~3`](docs/requirements/tasks.md)).

```yaml
auto:
  query: "repo:JabRef/jabref review-requested:@me"   # any GitHub PR search
  maxSloc: 50        # optional: skip PRs changing more than 50 lines outside their tests (0 = no limit, the default)
  deleteHours: 24    # optional: how long a finished PR's task is kept (0 = keep forever)
```

The search runs every ten minutes through your local `gh`, and never acts on a failed search — an offline `gh` empties nothing.
The rules, in short:

* **The search decides what is added, the pull request's own state what stays.**
  A PR that stops matching while staying open — it grew past `maxSloc`, the review request went to somebody else, or you dragged a PR of your own into the category — keeps its task.
  `maxSloc` is an admission filter, never an eviction rule.
* **Merged or closed ends a task:** it is suspended, and — with `autoDelete: true` — its file deleted once `deleteHours` are up.
  A **reopened** PR brings its task back to active — that suspend was the app's, and its reason is gone.
* **Pausing a row is how you dismiss a PR.** Your own pause is never undone and its task is never deleted while the PR is open, so the next round cannot add it back.
* **A PR another task already carries is never added twice**, and a task you have started a session in is yours from then on — never suspended, resumed or deleted by any of this.

Everything else about such a task is a normal task: switch to it, mirror its terminal, queue messages, let the [review-comment sync](#the-message-queue) fill that queue with what the reviewers wrote.

## Switching

Switching (play icon on row hover, double-click, or `contextswitcher://switch/<id>`) runs every configured action at once and shows one status chip per action, failure detail included ([`docs/requirements/switching.md`](docs/requirements/switching.md)):

* **tmux** — focuses the task's window on its remote; if the window is gone (the host rebooted), it is recreated in the recorded working directory and `claude --resume <sessionId>` is issued.
  A task with a `terminal:` section focuses the matching **local Windows Terminal** tab by its pinned title instead, across windows and virtual desktops.
* **IntelliJ** — opens the project on the remote via JetBrains Gateway; an already-open JetBrains Client window is focused instead (also across virtual desktops).
* **Browser** — focuses the tab showing each of the task's URLs, opening what is missing, collecting them in a browser tab group of the task's own; only the first URL is activated.
* **Folders** — focuses or opens the local directories in File Explorer.
* **Note and chat** — opens the task's (or its category's) note page and Matrix room.

**Status and suspend.** Click a row's status label (or the hover pause icon) to suspend; the row menu adds **Mark done**, **Rename task** (which also renames the tmux window on the remote), and **Delete task**.
Suspending **ends the task's tmux window** — silently in the normal case, with a dialog only while Claude is working (*Force terminate*) or when the task has no `claude:` section — and closes the task's browser tabs; the last screen is kept as the task's snapshot.
Resuming runs a regular switch: the window is recreated and Claude resumed.
Deleting is confirmed, tears the live context down first, and offers to clean up the remote leftovers individually (tmux window, Claude transcript, worktree) — or to send the session a canned wrap-up prompt so it tidies up after itself.

**Auto-suspend.** An active Claude task whose chat has been idle for `autoSuspendMinutes` (2880 = 48 hours by default, `0` turns it off) suspends itself exactly as the pause button would — a changed value applies on save, one due task per 5 s poll with the status bar counting down — and the row shows when (⏸ 2026-09-05 14:32) so a pile of suspended tasks can be sorted by age.
Idle is measured on the remote as "the pane produced no output"; the selected task, a working Claude, and a task whose session id is not recorded yet are never auto-suspended.
Sending a queued message to a suspended task resumes it first — and so does merely clicking into one of its message fields, so the window is back by the time the message is written.

**Virtual desktops** (Windows). A category's header play button switches to its `desktop:` and does nothing else.
Opening one of a task's links opens the page on its category's desktop (a tab that already exists is focused where it is), and while the active-desktop filter is on, switching desktops re-selects the task you last had selected there.

**Deep links.** `contextswitcher://task/<id>` selects a task and raises the app, `contextswitcher://switch/<id>` runs the full switch, and a category's **Copy link** hands out `contextswitcher://category/<name>` — paste them into OneNote pages or READMEs to link back.
A click reaches the running instance; with none running, the app starts and handles the link itself.

## The terminal lane

A live, typeable terminal mirroring the selected task's tmux window through `ssh -t` (a grouped mirror session — previewing never changes which window the real session shows), reconnecting on its own and verifying after every switch that it really shows the selected task's window.
A local Claude session is a tmux host like any remote and mirrors the same way.
Recommended on the remote: `tmux set -g mouse on` (wheel scrolls inside tmux) and `set -g set-titles on` (the header shows Claude's task summary).

* **Colors** follow the app theme: Everforest under an Everforest theme, plain black or white otherwise. The mirror can only recolour ANSI 0–15, so a remote program emitting 24-bit colour keeps its own — see [Theme](#theme) for making Claude Code match.
* **Scrollback, copy, Jump to bottom, Clear input** — the ordinary terminal affordances, plus a button that clears a half-typed prompt.
  `Ctrl`+wheel zooms the terminal font (`Ctrl`+`0` resets it); the size is remembered across restarts and applies to every task's terminal.
* **Markdown copy** — a selection of Claude's text is copied as the Markdown Claude wrote, and **Copy reply** copies the whole last reply; `autoCopyReplies: true` copies each reply as it finishes.
* **Links** — URLs are clickable, and so are issue numbers: `#17038` or `PR 17038` opens that issue or PR of the category's `repo:` (guessed from the checkout's git remote when the category names none). Hovering any link spells out its target in the status bar.
* **Show diff** — pages the task workspace's uncommitted changes, or the last commit when the worktree is clean, through the remote's own git pager in a throwaway tmux window. With `@cs_commit` published and the category's `mainCheckout:` set, it still works after the session deleted its worktree — [see below](#let-claude-report-its-state-remote-setup).
* **Files** — downloads a file the session generated: the menu lists the newest files in the remote's Claude scratchpad directories, plus **Custom path…** for anything else. The file lands in `~/Downloads` and opens with its registered application (32 MB limit).
* **Fork…** — starts a new task in the same category whose Claude session continues this task's conversation (`claude --resume … --fork-session`, the source session is left running): a dialog asks what the fork should do, with the same model/effort pickers as **Add task…**. Needs a remote, a tmux window and a recorded Claude session id; the new task runs in the background and does not take the selection.
* **`<model> · <effort>`** — what the mirrored chat currently runs, once the session reports it.

Details and ceilings: [`docs/requirements/terminal.md`](docs/requirements/terminal.md).

## The message queue

While Claude works, draft the follow-ups instead of waiting: the pane below the editor holds a per-task queue that survives restarts and travels with the task file ([`req~message-queue~2`](docs/requirements/ui.md)).

* **Write** into the box on top to add a message; existing cards are edited in place, reordered by drag'n'drop, deleted with 🗑. A pasted clipboard image (or an attached file) travels with the message: it is transferred to the remote and the chat receives its remote path. Hovering an attachment previews it.
* **Send** (➤) types the message into the task's tmux window and submits it, multi-line text included.
* **Delayed next** (the clock) arms a message instead: it goes out on the first status tick that reports the chat idle, so it is never buried in a running turn. Several armed messages form a delayed queue that drains one message per idle turn, in the order the ➊➋➌ handles show; only armed cards are numbered. Arming a suspended task's message resumes the task first.
* **Read (N)** — tick a card off and it moves into that collapsed section, still editable and sendable; untick to bring it back. This is what keeps a twenty-comment review queue workable.
* **Last sent** stays visible below the queue, with the earlier sends one scroll up in the same box.
* **Review comments** — a task's queue is also fed from its PR's open review threads: Qodo Merge's agent prompts and what human reviewers wrote, each with a button that opens the comment it came from. The background poll picks up new ones (cheaply: it only fetches when the PR moved), and the **Review comments sync** button does a full reconcile on demand, adding what is new and removing what was resolved. Your own replies close a thread, hand-typed and edited messages are never touched, and comments on a closed PR are dropped.
* **Keys** — plain Enter and Shift+Enter insert newlines; **three** Enters at the end of a box commit it, **three** Ctrl+Enters do the box's stronger thing (queue *and* send, or create the task in the Add-task dialog). Each press colours the box a shade greener, and any other key or click drops the run. The same chords work in every compose box of the app.

## Finding, filtering, sorting

* **Find** (`Ctrl+F`, the always-visible field) narrows the list by free text over everything you would search by: title, id, remote, note, configuration, the Markdown body, the task's URLs (paste a PR link, or just `16245`), and the text of its queued and sent messages.
* **Jump to category** (`Ctrl+J`) — a popup narrows the category names as you type, the current virtual desktop's categories first; Enter selects the category in the list.
  While a query is typed, the tag / awaits-input / running-tasks filters step aside so a hit is never hidden; the active-desktop filter stays on and the bar reports the hits it does not show as "· N on other desktops".
* **Filter menu** (toolbar) — **Awaits input** (waiting, asking, or hit the usage limit; the item carries the count even when off), **Active desktop**, **Running tasks**, and the **tag** submenu (several tags combine with AND; a filtered row shows only the selected tags, so a shared screen reveals nothing else). The button carries a badge while anything narrows the list, and the whole selection is restored on the next launch.
* **Sort menu** — **Alphabetical** (default), **Last update**, or **Action needed** (usage limit, then attention, then waiting, then working). Error rows stay first, active before suspended, pinned tasks head their block.
* **Group by labels** — group the list by tags instead of folders; a task with several tags appears under each, and every row then shows its category.
* **Energy saver** (the leaf) — stops all background polling for working on battery; only the selected task's mirror keeps running, selecting a task refreshes that task, and the refresh button runs every poll once. Remembered across restarts.

## Settings

`~/.contextswitcher/settings.yaml` is created with defaults (including a random WebSocket token) on first run, and the gear opens it as a form with one field per key.
Everything applies on Save.

```yaml
tasksDir: C:\Users\you\.contextswitcher\tasks   # where the task .md files live
hints: true                  # onboarding hints in generated task files; false = compact
claudeAuto: false            # true = start/resume Claude with --dangerously-skip-permissions
theme: everforest            # everforest | everforest-light | everforest-dark | system | light | dark
showOnAllDesktops: false     # pin the window to every Windows virtual desktop at startup
fallbackDesktop: misc        # desktop for a category naming none, or naming a missing one; empty = off
readlineKeys: false          # bash cursor chords in every text field (Ctrl+A/E, Alt+B/F, Ctrl+K/U/W …)
autoSuspendMinutes: 2880     # suspend an active Claude task idle this long (48 h); 0 = never
mergedCleanupDays: 7         # remove a paused task whose PRs are all merged after this many days (only in autoDelete categories); 0 = never
autoCopyReplies: false       # true = each finished reply of the mirrored Claude chat lands on the clipboard as Markdown
browser: firefox             # browser to start when no extension is connected (a connected one wins): firefox or chrome
wsPort: 17872                # loopback WebSocket port the browser extension connects to
wsToken: <generated>         # shared secret; keep the generated value
remotes:                     # ssh destinations the tmux sync scans
  - devbox                   # empty list: falls back to the remotes of existing tasks
refactoringMinerHome:        # unzipped RefactoringMiner release, path on the remotes; empty = off
refactoringMinerPort: 6789   # loopback port the refactoring web view is served and tunnelled on
tags:                        # tag palette for the filter and the row chips
  - name: jabref             # tag name (spaces allowed), referenced from a task's `tags:`
    color: "#2da44e"         # optional CSS hex; omitted = stable auto color from the name
  - name: phone
```

`claudeAuto` caveat: the first `--dangerously-skip-permissions` run on a remote shows a one-time interactive acceptance dialog, which blocks an unattended bootstrap.
Accept it once by running `claude --dangerously-skip-permissions` in any interactive shell there, or pre-seed it: set `"bypassPermissionsModeAccepted": true` in the remote's `~/.claude.json`.

`readlineKeys` takes `Ctrl+A` away from select-all — that is the trade, and it holds in every text input except the embedded terminal, whose remote shell has readline of its own.

### Theme

`theme` defaults to `everforest`, which draws the UI in the [Everforest](https://github.com/sainnhe/everforest) palette and follows the OS's light/dark setting; `everforest-light` and `everforest-dark` force a variant, and `system`, `light` and `dark` give the plain AtlantaFX Nord palette instead.
The change applies on Save, without a restart, and the embedded terminal follows: Everforest under the `everforest` themes, a plain black or white terminal under the Nord ones.

The terminal's colours stop at ANSI 0–15, so a **remote** program that emits 24-bit colour keeps its own — a Claude Code session in a dark theme stays dark on a light mirror, and no local setting can change that.
Fix it on the remote by giving Claude Code a matching theme: copy the two files from [koppor/everforest-configurations](https://github.com/koppor/everforest-configurations/tree/main/claude-code) into `~/.claude/themes/` there, restart it once (Claude Code only watches that folder if it existed at startup), and pick the theme with `/theme`.

### RefactoringMiner (optional)

For a semantic answer to "what did the session actually refactor", ContextSwitcher can drive [RefactoringMiner](https://github.com/tsantalis/RefactoringMiner) on the task's remote ([#50](https://github.com/contextswitcher/contextswitcher-private/issues/50), MADR 0022).
**Set up** next to the `refactoringMinerHome` field in the settings dialog installs it on every configured remote (Java 17+ required there, no Docker) and fills the directory in.
To install by hand, unzip the release once on the remote and point `refactoringMinerHome:` at it.

A remote task's row then offers a hover compare button opening the Monaco AST-diff web view of the task's worktree — uncommitted work included — against the category's `baseBranch:` (default `origin/main`), and once the session is idle the row shows a circled count of the refactorings its commits contain (click = same view).
The view diffs against the base as last fetched on the remote — fetch there if it looks stale.
It shines when the session *reorganised existing* code; a mostly-new-code diff degenerates into a plain file diff, where **Show diff** serves just as well.

## Let Claude report its state (remote setup)

A Claude session can publish where it works and what it is doing, so the app shows the real working subdirectory, a live status dot, the session id, the PR, the commit, and the model.
All of them are tmux **window** user options (no confirmation, scoped to the one window) that ContextSwitcher reads on the poll it already runs.
Nothing here is required — every part left out simply leaves its display empty — but the status dots and `@cs_workspace` are what make the list live, so do at least those two.

**Status — use Claude Code hooks (deterministic).** Do not rely on Claude *remembering* to reset the status; drive it from hooks so it can never get stuck on "working". In the remote's `~/.claude/settings.json`:

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {"hooks": [{"type": "command", "command": "[ -n \"$TMUX_PANE\" ] && tmux set -w -t \"$TMUX_PANE\" @cs_status working 2>/dev/null || true"}]}
    ],
    "Stop": [
      {"hooks": [{"type": "command", "command": "[ -n \"$TMUX_PANE\" ] && tmux set -w -t \"$TMUX_PANE\" @cs_status waiting 2>/dev/null || true"}]}
    ],
    "Notification": [
      {"hooks": [{"type": "command", "command": "[ -n \"$TMUX_PANE\" ] && tmux set -w -t \"$TMUX_PANE\" @cs_status attention 2>/dev/null || true"}]}
    ],
    "SessionStart": [
      {"hooks": [{"type": "command", "command": "[ -n \"$TMUX_PANE\" ] && tmux set -w -t \"$TMUX_PANE\" @cs_session_id \"$(jq -r .session_id)\" 2>/dev/null || true"}]},
      {"matcher": "startup|resume|clear", "hooks": [{"type": "command", "command": "[ -n \"$TMUX_PANE\" ] && tmux set -w -t \"$TMUX_PANE\" @cs_status waiting 2>/dev/null || true"}]}
    ]
  }
}
```

Every hook targets `-t "$TMUX_PANE"` — the pane the hook runs in — **not** a bare `tmux set -w`, which targets the session's *active* window and so lands the option on whatever window the user switched to (verified: `$TMUX_PANE` alone does not steer `set -w`).
Without the explicit target, a session started while another window is focused publishes its status and id onto the wrong window, and its own row stays blank.

Every hook is also guarded with `[ -n "$TMUX_PANE" ]`, and so is the `statusLine` script below.
Claude Code run **outside** tmux on the same host — an ordinary ssh shell, an editor's terminal — leaves `$TMUX_PANE` empty, and `-t ""` is not "no target": tmux resolves it to the current window of the current session, which collapses the explicit target right back into the bare `set -w` it was meant to replace.
That window is normally ContextSwitcher's own `cs-mirror-<name>` mirror session, whose current window is whatever the terminal pane last selected — so the stray session publishes its id, status, workspace and model onto **the task you are looking at**.
A stamped `@cs_session_id` then differs from that task's recorded one, `TmuxWindowOwnership` (`dsn~tmux-window-ownership~4`) reads it as a stolen window, and the next switch resurrects a second window for a task whose own session is still running in the first (field report 2026-09-09).

`UserPromptSubmit` → **working**, `Stop` (Claude finished responding) → **waiting**, `Notification` (Claude asks a question or waits for a permission) → **attention**.
The row's dot follows traffic-light semantics: orange while working, one red for everything that needs you — waiting, attention and the usage limit, told apart by the dot's tooltip — and gray once done.
No hook fires when a session runs into its **usage limit** — the turn just ends, so the `Stop` hook reports the ordinary "waiting".
The app therefore greps the tail of each Claude pane for Claude Code's limit message and marks those tasks as **limit**, sorted and counted as awaiting your input.
A turn cut short by an **API error** is detected the same way and simply told to `continue`, retried every five minutes while the error is still on screen.
As a safety net, the app also downgrades a "working" window to "waiting" after ~90 s with no pane output, so a missed hook does not leave the dot stuck.

`SessionStart` publishes the **exact Claude session id** as `@cs_session_id` (needs `jq` on the remote) — the import then records the right `claude.sessionId` per window.
Its second hook reports a freshly started (or resumed) session as **waiting** — until the first `Stop`, the window would otherwise publish no status at all, and a message queued for a resumed task waits for exactly that report; the `matcher` keeps it off `compact`, which fires mid-turn.
Without it, the import guesses from the newest transcript of the pane's cwd, which goes wrong when two Claude sessions started in the same directory (the guess is dropped when it would assign the same id twice).

**PR link.** The import also records the task's pull request (or GitLab merge request) into `browser.urls`: publish it as `tmux set -w -t "$TMUX_PANE" @cs_pr <url>` (e.g. from `CLAUDE.md`: "when you create or learn the PR for your work, run `tmux set -w -t \"$TMUX_PANE\" @cs_pr <url>`"), or just let Claude print its usual footer (`… · PR: https://github.com/...`) — the import scrapes the labeled footer as fallback.
The `-t "$TMUX_PANE"` target matters for the same reason as the status hooks above.

**Workspace — from `CLAUDE.md`.** Have Claude publish its working directory once it settles in; add to the remote `CLAUDE.md`:

```
When you settle into your working directory — and again whenever you change it — run:

    tmux set -w -t "$TMUX_PANE" @cs_workspace "$PWD"
```

Publishing it later than the import is fine: every tmux sync picks up a new or changed workspace and writes it into the task file, so a session that creates its worktree minutes after the task was added still ends up with the real directory — which is what **Show diff** and **Open in IntelliJ** then use, instead of the start directory.
Windows that set neither option simply show nothing.

**Commit — for mainline development.** If your workflow has Claude commit and then delete its own worktree (`git worktree remove`), the workspace above is a dead path by the time you press **Show diff**.
Have Claude publish the commit instead:

```
After you commit, run:

    tmux set -w -t "$TMUX_PANE" @cs_commit "$(git rev-parse HEAD)"
```

and give the category a `mainCheckout:` — a permanent checkout of the repository to read the commit from.
**Show diff** then prefers the worktree while it exists and falls back to `git show <commit>` in that checkout once it is gone, saying which of the two you are looking at.
Both parts are needed — with only one configured the button behaves exactly as before — and only the most recent published commit is shown.

**Model and effort — via `statusLine`, not a hook.** No hook input carries the *current* model: `SessionStart` reports the model the session started with (stale the moment `/model` runs), and only `PreToolUse`/`Stop` carry `.effort.level`.
The `statusLine` command, however, is fed both live and re-runs on every assistant message plus its own `refreshInterval`.
Put this in the remote's `~/.claude/cs-report-mode.sh` (`chmod +x`, needs `jq`):

```bash
#!/usr/bin/env bash
input=$(cat)
model=$(printf '%s' "$input" | jq -r '.model.display_name // empty')
effort=$(printf '%s' "$input" | jq -r '.effort.level // empty')
if [ -n "$TMUX_PANE" ]; then
  tmux set -w -t "$TMUX_PANE" @cs_model "$model" 2>/dev/null || true
  tmux set -w -t "$TMUX_PANE" @cs_effort "$effort" 2>/dev/null || true
fi
# Whatever this prints *is* the status line — keep your own rendering here.
printf '%s %s' "$model" "$effort"
```

and reference it in `~/.claude/settings.json`:

```json
{
  "statusLine": {"type": "command", "command": "~/.claude/cs-report-mode.sh", "refreshInterval": 5}
}
```

**If you already have a status line, do not replace it** — add the two `tmux set` lines to your own script instead; the last `printf` is only a placeholder rendering.
ContextSwitcher shows `<model> · <effort>` next to the mirrored terminal, so switching tasks tells you what the session in front of you runs.

**Title.** A task created from a typed description keeps that text as its title until the session publishes a short one as `@cs_title`; the app picks it up once and never overrides a rename you make later.

**Pretty diffs — optional.** Install [delta](https://github.com/dandavison/delta) on the remote and set it as git's pager for syntax-highlighted, navigable diffs (`n`/`N` jump between files):

```
[core]
    pager = delta
[delta]
    navigate = true
```

Without delta, stock `less` pages the plain diff — nothing breaks.

## Browser extension

The browser half of a task's context: focus-or-open the task's URLs on switch, close them on suspend, collect them in the task's own tab group, and report back which tab you activated ([`docs/requirements/browser-integration.md`](docs/requirements/browser-integration.md)).

There is one for **Firefox** and one for **Chrome**; they do the same things and speak the same protocol (MADR 0030).
Install either and enter the `wsPort` and `wsToken` from `settings.yaml` in its options page — [extension/firefox/README.md](extension/firefox/README.md) (unsigned: temporary add-on, or a Firefox that allows unsigned installs) or [extension/chrome/README.md](extension/chrome/README.md) (`chrome://extensions` → Developer mode → Load unpacked, permanent, no signing).
App and browser may start in any order; the connection re-establishes itself.

The extension connects **out** to the app and names its browser, so nothing has to be configured for it to be found — and the windows the app raises, launches, and (on a complete-control desktop) closes follow whichever extension is connected.
The `browser:` setting decides only when none is: it is what gets *started* when no browser is there to talk to, so set it to `chrome` if Chrome is your task browser.

Only one extension is driven at a time — the most recent one to connect. Both may be installed; to park one without uninstalling it, click its toolbar icon and switch it from *✓ Enabled* to *✗ Disabled* (its icon fades and it never connects).

* Activating a tab that belongs to a task **selects that task** in the app (and resumes it if it was suspended) — without raising the app, since you are working in the browser.
* The toolbar icon carries the **number of tasks listing the page in view**, and its popup names them, best match first: click one to select it and raise the app, `▶` to switch to it outright.
* Without a connected extension the browser chip of a switch fails after a short timeout — every other action runs regardless.

## Backup and sync of the tasks directory

Make `~/.contextswitcher/tasks/` a git repository with a remote and the app keeps it synced: it pulls on startup, commits, pulls and pushes every five minutes while running, and commits and pushes everything the run changed on close — no commit per keystroke in between ([`req~task-git-backup~4`](docs/requirements/tasks.md)).
A rejected push is retried once after pulling.
On a fresh machine (directory missing or empty) the app offers to clone the repository for you, which is the whole recovery story.

Conflicts between two machines are resolved by the app: a deletion wins over an edit, task files are merged per part of the file (one machine set the status, the other added a tag), and whatever is left goes to a headless Claude run before you are ever asked.
The app never creates or configures the repository, and a failed sync never disrupts anything.

## Android companion app

A native Android app reads the same task repository from your phone: the task list grouped by folder with status and tags, a terminal page showing the task's tmux window (`capture-pane`, refreshed while visible), and a queue page listing the pending messages and sending a new one straight into the chat over SSH ([`docs/requirements/android.md`](docs/requirements/android.md), MADR 0026–0028).

The sync runs over HTTPS with a personal access token (a new task is the phone's only push); SSH uses an in-process client (no `ssh` binary on Android) with trust-on-first-use host keys and a single OpenSSH private key from the settings screen.
Build it with `gradlew :android:assembleDebug`; the module is only included when an Android SDK is configured (`ANDROID_HOME` or `local.properties`), so SDK-less machines build exactly as before.
Connected to a car, the same APK shows on the Android Auto head unit — the tasks with a Claude session, those waiting for you first, and per task a dictated or canned message; being sideloaded, it needs *Unknown sources* enabled in Android Auto's developer settings (tap the version there ten times).

**GitHub tokens.** The settings screen's *Username* is your GitHub login; the two token fields are personal access tokens:

* *Token* — your task backup repository, **Contents: Read and write** (sync, and pushing a task created on the phone);
* *Update token* — `contextswitcher/contextswitcher`, **Contents: Read** (the update hint reads the `android-dev` tag and downloads the APK from that pre-release).

A fine-grained token (GitHub → Settings → Developer settings → Fine-grained tokens) has a single resource owner, so a task repository outside the `contextswitcher` organization needs two tokens: one with your account as owner, one with `contextswitcher`.
Left blank, *Update token* falls back to *Token* — enough for a classic token with the `repo` scope.
A token that cannot read ContextSwitcher shows "Update check failed: the token cannot read contextswitcher/contextswitcher (GitHub answered 404)".
The desktop app needs no token: its update check is a `git fetch` in its own checkout with whatever credentials that clone already uses.

## Recovery and troubleshooting

**After a reboot or power outage** you never have to remember which tasks were running — the task files are the memory, and each task's `status:` lives in `~/.contextswitcher/tasks/`.

* **The machine running the app** was off: nothing to do.
  The tmux sessions on the remote kept running; start the app and the periodic sync reconnects everything.
* **The remote host** rebooted: its tmux server died, taking every task's window with it.
  Start (or keep) the app, then pick **Restart running tasks…** from the toolbar's ⋮ menu.
  It recreates a tmux window for every *active* task in its recorded working directory and issues `claude --resume <sessionId>`; suspended tasks stay paused.
  While the remote's tmux server is down the automatic sync deliberately changes nothing — do **not** start a tmux server there by hand before pressing the button, or an empty but running server makes the sync read every window as "gone" and suspend the active tasks within a minute.
* A task that was mid-turn when the power went loses that in-flight turn; `claude --resume` restores the conversation, but you may need to re-send the last prompt.

**Other things worth knowing:**

* A broken task file shows as an error row naming the problem; fix it in the editor lane or delete it from the row.
* A dot stuck on "working" means a missed hook — the app downgrades it after ~90 s of silence anyway.
* The browser chip failing on every switch means the extension is not connected: check `wsPort`/`wsToken` in its options.
* Logs: `~/.contextswitcher/logs/`, and a button in the status bar opens them.

## Project state and docs

* [CHANGELOG.md](CHANGELOG.md) — what changed, per release day
* [docs/requirements/](docs/requirements/README.md) — requirements, traced with OpenFastTrace (`gradlew traceRequirements`) — the refinement of everything above
* [docs/decisions/](docs/decisions/README.md) — architectural decisions (MADR)
* [docs/manual-test.md](docs/manual-test.md) — manual E2E checklist (run before tagging)
* [CONSISTENCY.md](CONSISTENCY.md) — what the docs must agree with in the code, and `scripts/consistency.sh` to check the mechanical part
* [docs/background.md](docs/background.md) — motivation, vision
* [docs/related-work.md](docs/related-work.md) — related work

License: MIT — see [LICENSE](LICENSE).

<!-- markdownlint-disable-file MD026 -->
