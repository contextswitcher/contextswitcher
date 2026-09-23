# Provenance of sent messages

Records of what was sent to a task's Claude session, what Claude answered and which commits the answer produced ([MADR 0037](../decisions/0037-message-provenance-records.md)).

## Requirements

### The provenance repository is named once, in the task repo
`req~provenance-repository~1`

Records are kept in a git repository of their own, separate from the task repository.
The task repository names it — its clone URL in a file at the task repository's root — so every machine and the phone that sync the tasks find it without being configured one by one.
Each of them keeps its own copy of the provenance repository and syncs it as often as the tasks.
When the task repository names no provenance repository, nothing is recorded and nothing else changes.

Tags: windows, linux, android

Covers:
- feat~message-provenance~1

Needs: dsn

### Every sent message is recorded
`req~provenance-record~1`

Every message delivered to a task's Claude session is recorded the moment it is sent — from the desktop and from the phone, whether sent by hand, sent later when Claude fell idle, or as the start prompt of a new or forked task.
A record holds the text as the session received it, when and from which machine or phone it was sent, the task it was sent to (as it was called then) and its category's repository, and the commit the session had published last.
Records are kept after their task is deleted or renamed, and two machines recording at the same time never conflict.

Tags: windows, linux, android

Covers:
- feat~message-provenance~1

Needs: dsn

### A record gets its reply and its commits
`req~provenance-reply~1`

Once Claude's turn for a recorded message has ended, the record gets Claude's final answer of that turn, when it came, and the commit the session had published by then — with a link to the diff between the commit at send and that one, when the category names its GitHub repository.
A reply is filled in by whichever machine or phone notices the turn has ended, or later by one catching up on records still without a reply; it is filled in once, and never lost because the machine that sent the message was closed.
The commit range belongs to exactly one sent message: when the next message was sent before the turn ended, the record gets the answer but no range.
Deleting a task's Claude transcript first fills in the records that still need it.

Tags: windows, linux, android

Covers:
- feat~message-provenance~1

Needs: dsn

### A report over a time range
`req~provenance-report~1`

The user gets a Markdown report of the recorded messages for a time range, grouped by category and task, each message with its time, Claude's answer and the link to its diff — ready to paste into a weekly report or a pull request description.

Tags: windows, linux

Covers:
- feat~message-provenance~1

Needs: dsn

## Design

### Provenance repository and its copies
`dsn~provenance-repository~1`

`com.contextswitcher.provenance.ProvenanceConfig.repository(tasksDir)` reads `provenance.yaml` at the task directory's root (`repo: <clone URL>`, SnakeYAML safe loader); a missing file, a file without `repo` or an unreadable one mean "not configured" and switch every other part of this design off.
The file is not `*.md`, so `TaskRepository` never scans it.
**Desktop:** the copy is `<configDir>/provenance/` (`~/.contextswitcher/provenance/`).
`Main.syncProvenance` clones it with system git when the config names a repository and the directory is missing — at startup after the task directory's pull, and again in every 5-minute round, which catches a config the pull brought later — and runs a second `TaskGitBackup` on it with the task directory's rounds: `syncWhileRunning("ContextSwitcher provenance")` every `TASK_BACKUP_MINUTES`, `syncOnClose` at close.
A clone of an empty repository already tracks its default branch, so the first round's plain `git push` creates it.
Records only ever add files or append to one, so the pull's merges do not conflict in practice; the automatic resolution stays as the fallback.
**Phone:** the copy is `filesDir/provenance/`, created by `ProvenanceSync` with JGit and the task repo's credentials on the first round after a task sync found the config; JGit cannot clone an empty repository, so for a remote without branches (`lsRemote`) the copy is `init`ed on `main` with `origin` added, and the first push creates the branch.
Unlike the task clone it is not a mirror: a round (`PhoneProvenance.syncSoon`, one at a time, after every task sync and every new record) commits what was recorded, fetches, merges `origin/<branch>` when it exists (new files only) and pushes `HEAD:refs/heads/<branch>`, so a record written offline goes out with a later round instead of being dropped.
A clone or sync failure only logs, and never blocks sending.

Tags: windows, linux, android

Covers:
- req~provenance-repository~1

Needs: impl, utest

### Record at send
`dsn~provenance-record~1`

`com.contextswitcher.provenance.ProvenanceRecord` (`:core`) is one sent message: `sentAt` (UTC instant), `sender`, `taskId`, `taskTitle`, `repo` (the category's `repo`, may be null), `remote`, `window`, `sessionId`, `workspace` (`@cs_workspace`), `commitBefore` (`@cs_commit`, may be null) and the delivered text; `ProvenanceStore.write(dir, record)` puts it at `<category or "_">/<task id's last segment>/<sentAt as yyyyMMdd'T'HHmmss.SSS'Z'>-<sender>.md` — frontmatter for the fields, the text under `# Sent` — and never overwrites (a taken name gets `-2`).
`sender` is `desktop-<host name>` or `android-<Build.MODEL>`, slugged.
The window's `@cs_session_id`/`@cs_workspace`/`@cs_commit` come from one `tmux display-message` round-trip made right after the paste — before Claude can have committed anything for the new message.
The text is the one `MessageSender` pasted — attachment markers already rewritten to remote paths: `MessageSender.setDeliveryListener` installs a `DeliveryListener` told after every successful paste of a message (not of a mode switch's slash commands), and `ProvenanceRecorder` is that listener.
It maps remote and window back to a task (`ProvenanceRecorder.taskIn`: the task whose `remote` and `tmux.target()` match) and reads the category's `repo` from its `CONTEXTSWITCHER.md`; a window no task names is not recorded.
Because every tmux send goes through `MessageSender`, one listener covers them all: on the desktop the queue's sends by hand and delayed and the live and fork start prompts (`ClaudeWindowLauncher`); on the phone `MainActivity`'s sender (queue page, live task) and the watch service's (enqueued messages), both installed by `PhoneProvenance.install`.
Not recorded (ceiling): the desktop's Windows-owned sessions, typed into their ConPTY without tmux.
A write failure logs and never fails the send.

Tags: windows, linux, android

Covers:
- req~provenance-record~1

Needs: impl, utest

### Reply and commit range fill-in
`dsn~provenance-reply~1`

`com.contextswitcher.provenance.TranscriptReply.find(jsonlLines, deliveredText, sentAt)` (`:core`, pure) scans a Claude transcript: the first `user` entry at or after `sentAt` whose text equals the delivered text (whitespace-normalized) starts the turn; the turn ends at the next `user` entry that is a real prompt (not a tool result); the reply is the text of the last `assistant` entry in between, with its timestamp.
No start, or no end yet while the session still works, means "not yet".
`ProvenanceStore.fill(file, reply, repliedAt, commitAfter)` appends `# Reply` and adds `repliedAt`, `commitAfter` and — when `repo` is a GitHub URL and both commits are known and differ — `compare: https://github.com/<owner>/<repo>/compare/<commitBefore>...<commitAfter>`; a file that already has `# Reply` is left alone, so the first filler wins.
Single interaction: `commitAfter` and `compare` are omitted when another record of the same `sessionId` has a `sentAt` between this record's `sentAt` and `repliedAt`.
The transcript is read over ssh (`~/.claude/projects/<ClaudeSessionLookup.encodeProjectDir(cwd)>/<sessionId>.jsonl`, only the lines after the record's `sentAt` via a remote `awk` on the timestamp field, so a long session is not transferred whole), and `@cs_commit` with one `display-message`.
Triggers: the desktop's `TmuxStatusPoller` on a window's `working` → `waiting`/`done`, and the phone's `TaskWatchService` on `FinishWatch`'s finished alert, both for records of that window's session without a reply; a catch-up over all records without a reply newer than 14 days at every provenance sync round (desktop); and `Main`'s transcript deletion (`req~claude-session-cleanup~1`, `req~merged-task-cleanup~1`), which runs the catch-up for the task's session first.

Tags: windows, linux, android

Covers:
- req~provenance-reply~1

### Markdown report
`dsn~provenance-report~1`

`com.contextswitcher.provenance.ProvenanceReport.markdown(dir, from, to)` (`:core`, pure over the files) walks the records with `sentAt` in `[from, to)` and renders `# Provenance <from> – <to>`, a `##` per category, a `###` per task title (the latest title among its records), and per record the local send time, the sender, the sent text as a quote, the reply, and the compare link.
The desktop's "more" toolbar menu gets *Provenance report…*: a dialog with a from/to date (default: the last 7 days) and *Copy* / *Save as…* for the Markdown.

Tags: windows, linux

Covers:
- req~provenance-report~1
