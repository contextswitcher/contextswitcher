# Features

Vision-level features, distilled from the [background document](../background.md).

## Task context switching
`feat~task-context-switching~1`

The user maintains a list of tasks. Activating a task brings all applications that belong to the task into the right state (focused window, opened project, focused browser tab) with a single interaction.

Needs: req

## Tasks as Markdown files
`feat~tasks-as-markdown-files~1`

A task is a human-owned Markdown document combining structured switching data with free-form notes. The application is never the only way to create or repair a task.

Needs: req

## Browser context
`feat~browser-context~1`

The browser is part of a task's context: activating a task focuses the task's web pages (e.g. the pull request under review) in the running browser instead of opening duplicates.

Needs: req

## Remote development context
`feat~remote-development-context~1`

Tasks may live on remote machines: a terminal multiplexer window (e.g. a tmux window running an AI coding session) and a remotely opened IDE project belong to the task's context and are activated with it.

Needs: req

## Task tagging
`feat~task-tagging~1`

A task can carry tags, and the task list can be narrowed to a chosen set of tags.
This lets the user show only a relevant slice of their work — e.g. during a phone call, only the tasks tagged for that context — instead of exposing every task and group.

Needs: req

## Message queue for the chat
`feat~message-queue~1`

While the AI session works, the user drafts follow-up chat messages ahead of time in a per-task queue — including pasted screenshots — reorders and edits them, and releases them to the chat one by one.

Needs: req

## Provenance of sent messages
`feat~message-provenance~1`

Every message sent to a task's AI session is kept with the answer it got and the commits that answer produced, on every machine and the phone, beyond the task's own lifetime — so work can be reported on later, and the changes behind an answer can be looked at as a diff.

Needs: req

## Refactoring insight
`feat~refactoring-insight~1`

For a task whose AI session reorganised existing code, the user sees *what was refactored* against the base branch — a semantic AST-diff view on demand, and an automatic per-task refactoring count once the session is idle — instead of reverse-engineering a large textual diff.

Needs: req

## Automatic review queue
`feat~auto-pr-tasks~1`

Reviewing other people's pull requests is recurring work whose *list* the user should not have to maintain by hand.
A category can therefore fill itself from a pull-request search: matching PRs appear as tasks, and once a PR is done its task leaves the list again on its own — so the list shows what is waiting rather than what was once added.

Needs: req
