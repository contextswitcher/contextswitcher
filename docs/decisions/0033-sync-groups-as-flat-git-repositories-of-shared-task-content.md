---
status: accepted
date: 2026-09-13
decision-makers: Oliver Kopp
---

# Sync Groups as Flat Git Repositories of Shared Task Content

## Context and Problem Statement

Tasks are private Markdown files, backed up to the user's own git remote (MADR [0011](0011-task-backup-via-system-git.md)).
A team wants to share some of them: a task one member adds should show up for the others, and edits should flow both ways.
But a task file mixes shared content (title, links, notes) with what only makes sense for one person on one machine (status, tmux window, Claude session id, IntelliJ path, local folders), and each member files tasks into categories of their own.
How are tasks shared, and what exactly is shared?

## Considered Options

* One git repository per sync group holding the shared part of each task, flat, mirrored by system git
* Mirror whole task files, category folders included, into the group repository
* A hosted sync service (GitHub issues/projects API, a CRDT store)

## Decision Outcome

Chosen option: "one git repository per sync group holding the shared part, flat", because it reuses the backup's mechanics — system git with the user's existing credentials, merge-never-rebase, the per-key `TaskMerge` conflict resolution — and keeps the file format, while sharing only what is meant to be shared.
A shared file carries `title`, `tags`, `browser`, `chat`, `note` and the notes body (a whitelist, so a key added later stays private until chosen to be shared); categories are not part of the repository, each member sorts shared tasks in locally.
Oliver chose content-only, flat, a periodic poll, and "deleting a synced task deletes it for everyone" (2026-09-13).

### Consequences

* Good, because nothing new to authenticate or host: a GitHub repository and the members' git access are the whole setup.
* Good, because switching or suspending a task never touches the shared repository, and no machine paths or session ids leak to colleagues.
* Good, because shared files are plain task files, readable and editable on GitHub.
* Bad, because freshness is the poll interval (5 minutes), not instant.
* Bad, because a task deleted by another member cannot simply be deleted locally too without losing the member's own sessions and notes; the local copy leaves the group instead, which is less of a mirror.
* Neutral, because the "what was in sync last round" memory is per machine (in the clone's `.git`), so the same user's two machines each reconcile deletions on their own.

## Pros and Cons of the Options

### Mirror whole task files, category folders included

* Good, because the least code: copy files both ways.
* Bad, because every switch rewrites `tmux:`/`claude:` and churns the shared repository, and exposes machine paths and session ids.
* Bad, because a member moving a task to their own category would move it for everyone.

### A hosted sync service

* Good, because instant updates and no merge conflicts to resolve.
* Bad, because a new service, API client and credential store for a task list that already lives in git.
