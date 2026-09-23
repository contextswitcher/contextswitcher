---
status: accepted
date: 2026-09-17
decision-makers: Oliver Kopp
---

# Message Provenance Records

## Context and Problem Statement

Work happens by sending messages into a task's Claude session — from the desktop and, since MADR 0026, from the phone.
What was asked, what Claude answered, and which commits resulted are scattered today:

* The desktop keeps the last 50 sent texts per task (`.queues/<task>.sent-history.yaml`, `dsn~last-sent-message~5`): no time, no sender, no reply; the phone records nothing.
* Claude's replies exist only in the session transcript on the remote (`~/.claude/projects/<cwd>/<sessionId>.jsonl`), outside every repository, and task cleanup deletes it (`req~claude-session-cleanup~1`, `req~merged-task-cleanup~1`).
* Commits are published per window as `@cs_commit`, which holds only the latest one.

Oliver wants a per-task record of every sent message with Claude's reply and the commits that turn produced, kept after the task is gone, to write reports from (first: Markdown for a time range) and to investigate the diffs behind an answer.
Where do these records live, how are replies and commits attached, and how do they stay out of the way of the task repo that the desktops and the phone sync every few minutes?

## Decision Drivers

* Desktop and phone both send, and neither runs all the time: a reply must not be lost because the sender was closed when Claude finished.
* Two machines write at the same moments (a send on the phone, a fill-in on the desktop): the format must not produce merge conflicts.
* Records outlive their task, its window and its transcript.
* The task repo stays small and fast to sync — the phone clones it over mobile data (MADR 0028) and the desktop now syncs it every 5 minutes (`dsn~task-git-backup~5`).
* No history rewrite of a repository several machines sync (see *More Information*).
* Commits must be reachable as diffs.

## Considered Options

* Where the records live
  * A: a `.history/` folder in the task repo
  * B: a separate provenance repository from the start
  * C: A now, moved into a separate repository on demand later
* How machines find the provenance repository
  * a git submodule of the task repo
  * a config file in the task repo naming the repository URL
  * a per-machine setting (desktop `settings.yaml`, phone settings screen)
* How a record is stored
  * one file per sent message
  * one append-only list per task
* How the reply is attached
  * the sender waits for the turn to end
  * recorded at send, filled in later by whichever machine sees the turn end or catches up

## Decision Outcome

Chosen option: **B, a separate provenance repository, one Markdown file per sent message, filled in later**, because it keeps replies out of the task repo the phone syncs every few minutes without ever needing a history rewrite, and one file per message cannot conflict between two writers.
Oliver, 2026-09-17: "Then B as chosen option. as long as the range matches a single interaction, it is OK."

*Repository.* A second git repository, named by URL in a config file at the task repo's root — `provenance.yaml` with `repo: <clone URL>` — so every desktop and the phone learn it through the task sync they already run (Oliver, 2026-09-17: "just a config file where the repo URL is noted"; a submodule was considered and rejected, see below).
Each machine clones it on first use into a directory of its own (desktop: `~/.contextswitcher/provenance/`) and syncs it with the same `TaskGitBackup` rounds as the task directory (start, every 5 minutes, close).
The file is not Markdown, so the task scanner ignores it.
Optional: without `provenance.yaml`, nothing is recorded — the same opt-in-by-presence rule as the task backup.
The phone keeps a second JGit clone and writes through the same push-or-drop path as `dsn~android-task-create~1`, but only pushes records and fills — it never needs the whole history, so a shallow clone may do (to verify: JGit pushing from a shallow clone).

*Record.* `<category>/<task id>/<UTC time>-<sender>.md` (sender: `desktop-<machine>` or `android-<device>`): frontmatter with task id and title at send time, remote, tmux window, Claude session id, the category's `repo`, `@cs_workspace`, and `commitBefore` (the window's `@cs_commit` at send time); the body holds the text **as delivered** (attachment markers rewritten to remote paths, so it matches the transcript) under `# Sent`.
Filling in adds `# Reply` (the last assistant message of the turn), `repliedAt`, `commitAfter` (the window's `@cs_commit` when the turn ended) and, when the category has a GitHub `repo`, a compare link `https://github.com/<owner>/<repo>/compare/<commitBefore>...<commitAfter>`.
One file per message means two machines never edit the same file at the same time except for the fill-in, and a fill-in only appends to a file whose `# Reply` is still missing, so the first one wins and the other skips.

*Reply.* Whoever sees the turn end — the desktop's status poller, the phone's watch service — or a catch-up pass over records without a reply reads the transcript over ssh: the first user entry matching the delivered text after `sentAt`, then the last assistant text before the next user entry.
Task cleanup runs the catch-up for its task before it deletes the transcript.

*After the task.* Records stay where they are: the task id in the path is enough for a report, and a deleted task simply has no current file next to them.
A task rename writes nothing into old records (their frontmatter keeps the id at send time).

*Report.* Desktop, on demand: Markdown for a time range, grouped by category and task, each message with time, reply and compare link.

### Consequences

* Good, because the task repo does not grow with replies, and its 5-minute syncs stay cheap on the phone.
* Good, because nothing ever has to be rewritten: the records are in their own history from the first entry.
* Good, because one file per message cannot conflict, and a report is a directory walk.
* Bad, because a second repository to create and sync — one more remote, and on the phone a token that must also cover that repository (a fine-grained GitHub token has a single owner).
* Good, because it is configured once, in the task repo, not per machine.
* Bad, because matching a reply by text is heuristic: the same text sent twice in a turn, or a paste Claude never received, can attach the wrong reply or none.
* Bad, because `commitBefore`/`commitAfter` rely on the session publishing `@cs_commit`; commits the session makes without publishing, commits in a second repository, and unpushed commits (the compare link 404s until pushed) are not covered.
* Neutral, because the desktop's existing sent history stays for what it does today (resend, last-sent box).

### Confirmation

Oliver chose B (2026-09-17); a commit range per interaction is enough for investigating the diffs, so `commitBefore`/`commitAfter` must bound exactly one sent message's turn — a record whose turn overlaps the next send (a message sent before the previous reply) gets no `commitAfter` rather than a range spanning two interactions.
Then per step, tests on the record writer, the transcript matcher (fixtures from real transcripts), the fill-in race (two writers), and the report.

## Pros and Cons of the Options

### A: a `.history/` folder in the task repo

* Good, because no new repository, remote or setting — works for everyone with a task repo today.
* Good, because the phone already has the clone and the push path.
* Bad, because every reply lands in the repo the phone clones in full and every machine syncs every 5 minutes; long replies make it grow steadily.
* Bad, because moving it out later leaves every byte in the task repo's history unless that history is rewritten (see below).

### B: a separate provenance repository from the start

See *Decision Outcome*.

### C: A now, moved into a separate repository on demand

* Good, because it starts without setup and defers the decision until the size is felt.
* Bad, because "move" means copying the files into the new repository and deleting them from the task repo: the task repo's size stays in its history, so the move frees nothing for a fresh clone — only a history rewrite would.
* Bad, because two code paths (in-repo and separate) have to exist and be tested.

### A git submodule of the task repo

* Good, because git itself records which repository and which state belongs to the task repo.
* Bad, because the task repo then holds a commit pointer to the provenance repository: every new record or fill-in needs a second commit in the task repo, and two machines moving the pointer at once is a merge conflict — the very thing one file per message avoids.
* Bad, because `git pull` does not update submodules without `--recurse-submodules` (the desktops' sync would need changing), and JGit's submodule support on the phone is partial.

### A per-machine setting

* Good, because no file in the task repo.
* Bad, because every desktop and the phone have to be configured by hand, and a machine that is not simply records nothing.

### One append-only list per task

* Good, because one file per task is easy to read by hand.
* Bad, because every send and every fill-in rewrites the same file, and a phone send plus a desktop fill-in within one sync interval is a merge conflict.

### The sender waits for the turn to end

* Good, because simple: no matching of old records.
* Bad, because a phone closed or out of coverage before Claude finishes loses the reply for good.

## More Information

*Rewriting history in a synced repository* — the question behind option C.
Rewriting the task repo (e.g. `git filter-repo` to drop a folder, then a force push) breaks the desktops' sync: `TaskGitBackup` integrates the remote with `git pull --no-rebase` (MADR 0011, never rebase), so a machine still holding the old commits merges the rewritten history into its own — both histories side by side, the dropped files back — and its next push restores them on the remote.
Every desktop would have to be reset by hand to the rewritten remote (`git fetch` and `git reset --hard origin/<branch>`) before its next sync round, which now comes within 5 minutes, and any change it had not yet pushed would be lost.
The phone would follow without help: its sync is a fetch and a hard reset (`dsn~android-task-repo-sync~1`).
ContextSwitcher has no support for a rewritten remote and should not need it; option B avoids the situation instead of handling it.
