---
name: contextswitcher-tasks
description: Create, update, or find ContextSwitcher tasks — Markdown files with YAML frontmatter in the ContextSwitcher tasks directory. Use when the user says "create a contextswitcher task for X (in category Y)", "add a task", "note this in my task", "mark the task done", or names a contextswitcher://task/… or contextswitcher://category/… link.
---

# ContextSwitcher tasks

A task is one Markdown file; writing the file creates the task, editing it updates it.
The running app watches the directory and picks changes up within a second.
No API, no MCP server — just files.

## Where the files are

- Tasks directory: `tasksDir:` in `~/.contextswitcher/settings.yaml`; when unset, `~/.contextswitcher/tasks`.
  On Windows `~` is `%USERPROFILE%` (`C:\Users\<name>\.contextswitcher\tasks`).
- The directory is usually a git repository the app syncs itself ("ContextSwitcher auto-backup" commits every five minutes and on close).
  **Do not `git commit`, `pull` or `push` there** — the app does it and merges conflicts per frontmatter key.
- Dot directories (`.git`, `.queues`) are app-internal: never touch them.
  `.queues/` holds the message queue of running tasks; the app rewrites those files, editing them races with it.
- `TEMPLATE.md` at the root is a commented reference of every key and is ignored as a task.
  It is seeded once and never refreshed, so an old copy may be outdated — this skill wins over it.

### No settings.yaml on this machine (remote session)

The app runs on the user's desktop, not on the ssh host the session may run on.
Then:

1. Look for a clone of the task repository (ask the user once where it is if not obvious, e.g. `~/contextswitcher-tasks`).
   With a clone: write the file there, then `git add <file> && git commit -m "Add task <id>" && git pull --no-rebase && git push` — here committing is right, nobody else will; the desktop pulls it within five minutes.
2. Without one: print the complete file content and its relative path (`<category>/<file>.md`) for the user to drop in.

Never `git rebase` in the task repository.

## Categories

A category is a folder directly below the tasks directory (one level only).
Its `CONTEXTSWITCHER.md` configures the group (`repo`, `remote`, `workspacesRoot`, `desktop`, `tags`, `browser.urls`, …) — it is **never** a task; do not create tasks named like it.
`contextswitcher://category/<name>` names the folder `<name>`.
Tasks may also sit at the root (no category).

Picking the category when the user names none:

1. List the folders and read each `CONTEXTSWITCHER.md` frontmatter (`repo:`, `remote:`).
2. Match the current repository (`git remote get-url origin`) against `repo:`.
3. Several matches differ by host suffix: `-swkgpu1`, `-local`, none — pick the one whose `remote:` is the machine the work will run on (`remote:` absent = local).
4. Still ambiguous: ask, offering the two or three candidates.

Creating a new category: make the folder (lowercase, `a-z0-9._-`); a `CONTEXTSWITCHER.md` is optional — only `---`/`---` frontmatter with the keys needed, e.g.:

```markdown
---
repo: https://github.com/owner/repo
remote: devbox
---
```

Prefer telling the user that the app's *Add category…* dialog sets up worktrees and the workspace root.

## Creating a task

Path: `<category>/<YYYY-MM-DD>-<slug>.md` with today's date.
The path without `.md` is the task id (`jabref/2026-11-26-fix-groups-npe`); its link is `contextswitcher://task/<category>/<file-name-without-.md>`.
Slug: lowercase, whitespace → `-`, drop everything but `a-z0-9._-`, collapse `--`, at most 60 characters (cut at a `-`).
Never overwrite an existing file — append `-2` instead.

```markdown
---
title: "Fix the groups NPE"
status: suspended
remote: devbox
browser:
  urls:
    - https://github.com/owner/repo/issues/123
---

# Notes

What the task is about, in free Markdown.
Context that a later session needs: links, decisions, open questions.

Created on 2026-11-26.
```

- `title`: short, shown in the list. Quote it (YAML).
- `status`: exactly one of `active | suspended | done` (there is no `paused`).
  A collected task nobody works on yet is `suspended`; *Switch* (or a click on ▶) resumes it, starting a session.
  `active` means a session is (or should be) running.
  Note: activating a browser tab whose URL belongs to a suspended task resumes it — a fresh task can turn `active` on its own.
- `remote`: copy the category's `remote:` (the ssh destination); omit for local work or when the category has none.
- `browser.urls`: the issue/PR/doc URLs; an entry may be `URL — short title`.
- Optional: `tags: [a, b]`, `pinned: true`, `folders:` (local paths), `note:` (`onenote:` link), `chat:` (matrix.to link).
- Body: `# Notes`, free Markdown, one sentence per line, closing line `Created on <date>.`.
  Attachments as `[file: <absolute path>]` and `[image: <absolute path>]` lines.

Do **not** write these — the app writes them once a session exists: `tmux:`, `claude:` (`sessionId`, `cwd`, `workspace`, `commit`), `intellij:`, `storedTabs:`, `suspended:`, `autoPrClosed:`, `syncId:`.

## Updating a task

- Edit the file in place, textually: keep comments, key order and every key you were not asked to change.
- Status changes: `done` archives it; `suspended`/`active` as above.
  Suspending in the file does not end a running tmux window — the app's button does; say so if it matters.
- Appending notes: add lines at the end of the body, before `Created on …` is fine either way.
- Adding a URL: append under `browser.urls` (create `browser:`/`urls:` if absent).
- Renaming the title does not rename the file; the id stays (links keep working).

## Finding the current session's own task

A session started by ContextSwitcher is not told its task file.
Find it by the tmux window id the app recorded:

```sh
TASKS_DIR=$(sed -n 's/^tasksDir: *//p' ~/.contextswitcher/settings.yaml | tr -d "\"'\r")
TASKS_DIR=${TASKS_DIR:-$HOME/.contextswitcher/tasks}
w=$(tmux display -p -t "$TMUX_PANE" '#{window_id}')   # e.g. @17
grep -slE "window: ['\"]?$w['\"]?[[:space:]]*\$" "$TASKS_DIR"/*.md "$TASKS_DIR"/*/*.md
```

Alternatively grep for the Claude session id (`sessionId: <uuid>`, the current transcript's file name).
This only works where the tasks directory is (the desktop); in a remote session use the task-repository clone, which may lag by five minutes.

## Out of scope

Queueing a message to a running task, triggering a switch, or anything else live needs the app — not possible through files without racing it.
Tell the user to use the app (or the `contextswitcher://switch/<id>` link on the desktop).
