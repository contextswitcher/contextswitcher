---
status: proposed
date: 2026-09-07
decision-makers: Oliver Kopp
---

# JGit Clone of the Task Backup Repo as the Android Task Source

## Context and Problem Statement

Tasks live as Markdown files in the desktop's `~/.contextswitcher/tasks/`, backed up to a git remote via the system git CLI (MADR [0011](0011-task-backup-via-system-git.md)).
The phone (MADR 0026) has neither those files nor a git binary; where does its task list come from?
Scoped exception like 0027: the desktop keeps system git.

## Considered Options

* JGit clone/pull of the existing backup remote (read-only first)
* Read task files over sftp/ssh from the desktop or the remotes
* A sync service / CRDT store

## Decision Outcome

Chosen option: "JGit clone of the backup remote", because the backup repo already exists and is the durable source of truth, JGit is pure Java (EDL/BSD, Android-proven), and offline reading works — the couch case again.
Read-only in P4; write-back (status changes, queue edits) waits until `TaskMerge` is proven against the two-writer reality.
Status `proposed` until the P4 spike has cloned a real backup repo on a device via a deploy key.

### Consequences

* Good, because no new server or protocol — the phone pulls what the desktop already pushes.
* Bad, because freshness depends on the desktop's backup cadence; a task created on the desktop seconds ago is not on the phone yet.
* Bad, because eventual write-back means merge conflicts between phone and desktop edits; deferred, not solved.

### Update 2026-09-17: adding tasks

The first write-back is adding a task (`dsn~android-task-create~1`): commit one new file and push it at once; a non-fast-forward rejection is retried once after fetch + reset, any failure drops the commit.
A new file cannot conflict, so this does not wait for `TaskMerge`; editing existing tasks and writing queues still do.
