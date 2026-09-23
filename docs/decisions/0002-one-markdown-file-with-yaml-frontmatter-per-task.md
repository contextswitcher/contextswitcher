---
status: accepted
date: 2026-07-12
decision-makers: Oliver Kopp
---

# One Markdown File with YAML Frontmatter per Task

## Context and Problem Statement

Each task stores structured switching data (remote host, tmux session/window, IntelliJ project path, browser URLs) and unstructured working notes.
How is a task persisted?

The 2021 prototype drafted (but never finished) a decision for one JSON file per application state per context.

## Decision Drivers

* Tasks must be human-readable and editable with any editor — the app must never be the only way in.
* Notes and structured data belong together; a task is also a thinking document.
* Files should diff/merge well (Git-friendly, sync-friendly).
* The schema must grow as application plugins are added, without migrations.

## Considered Options

* One Markdown file per task with YAML frontmatter (structured) + free-form body (notes)
* One JSON file per application state per context (2021 draft)
* SQLite database
* One single YAML file for all tasks

## Decision Outcome

Chosen option: "One Markdown file per task with YAML frontmatter", because it combines structured data and notes in one human-owned artifact, needs no database or migration tooling, and matches how static-site and note-taking ecosystems already work.

Details: files live in `%USERPROFILE%\.contextswitcher\tasks\*.md` (directory configurable), filename stem = task id, all action keys (`tmux`, `intellij`, `browser`) optional — only configured actions run. Parsing = split on `---` fences + SnakeYAML; the body is kept verbatim (no Markdown parser needed).

### Consequences

* Good, because users can create/fix tasks with a text editor even when the app is broken.
* Good, because a new integration is just a new optional frontmatter key.
* Bad, because YAML indentation errors are easy to make — the app must surface parse errors gently (error row in the UI, never a crash).
* Bad, because concurrent edits (app + editor) need file watching and last-write-wins semantics.

## Pros and Cons of the Options

### JSON per application state (2021 draft)

* Good, because trivially machine-readable.
* Bad, because notes have no home; splits one task across many files.

### SQLite

* Good, because transactional and queryable.
* Bad, because opaque to the user; kills the "task is a document" idea.

### Single YAML file for all tasks

* Good, because one file to load.
* Bad, because merge conflicts across unrelated tasks; no per-task notes body.
