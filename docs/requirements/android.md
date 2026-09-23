# Android Companion App

Design decision: [0026 — Android companion app on a shared plain-Java core](../decisions/0026-android-companion-app-on-shared-java-core.md).

## Requirements

### Android companion app
`feat~android-companion-app~1`

ContextSwitcher also runs as a native Android app, reading the same task data the desktop maintains, so the user can check on their tasks from a phone when the desktop is off.

Needs: req

### Sync the task backup repo onto the phone
`req~android-task-repo-sync~2`

The app keeps a local copy of the task backup repo (`req~task-git-backup~4`'s remote) in app-private storage, over HTTPS with a username and a personal access token.
A first sync clones the repo; every later sync brings the local copy exactly to the remote's current default branch.
The phone's only change to the repo, adding a task, is pushed at once or not kept, so there is no local state to preserve across a sync.
A sync that cannot reach or read the remote fails without touching the local copy, so the last successful sync's tasks stay available.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Read-only task list on the phone
`req~android-task-list~1`

The app's main screen lists the synced repo's tasks, grouped by folder like the desktop's default view, with each task's title, status, and tags.
A task file the parser cannot read is shown as an error row naming the file and the problem, the same as the desktop's `req~task-parse-error-handling~1` — a broken file never hides the rest of the list.
With no repo configured yet, the app shows a settings screen (URL, username, token) instead of an empty list.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Talk to the phone's tasks over SSH
`req~android-ssh~1`

The app runs remote commands (tmux pane capture, message send) directly from the phone, over an in-process SSH client — there is no system `ssh` binary on Android (MADR 0027).
Host keys are trusted on first use and pinned: a host whose key later changes is refused, naming both fingerprints, until the user clears the app's known-hosts list.
Auth is a single OpenSSH private key entered in settings; no passphrase support.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Terminal snapshot of a task's tmux window
`req~android-terminal-snapshot~3`

Tapping a task with a `tmux:` section opens a terminal page showing that window's current screen (`tmux capture-pane -e`), colored like the desktop's mirror.
A task with no `tmux:` section shows a placeholder instead.
The page refreshes on demand and automatically every few seconds while visible; a capture failure is reported without losing the last good screen.
Like a terminal, the page opens at the end of the screen, where Claude's latest output and its prompt are, and stays at the end across refreshes — unless the user has scrolled up to read, in which case it stays where they are until they scroll back to the end.
Earlier output can be read too: scrolling to the top of what is shown loads older output from the session's scrollback, step by step, as far back as tmux keeps it — enough to reach the user's own last message — while the page does not update under a reader who scrolled up.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Keep message drafts on the phone
`req~android-message-drafts~3`

A message typed on the phone but not sent is kept as a draft of that task, the way the desktop's queue pane stores unsent text when the user clicks elsewhere: tapping outside the message box and a *Save draft* button keep it.
Text still in the message box is never lost either: leaving the task, rotating the phone, or the app restarting — an update replacing it, a force stop — brings it back into the box.
Drafts are listed on the queue page, each with *Edit* (back into the message box), *Delete* and *Send*.
A message queued on the desktop has *Edit* too, which puts a copy into the message box — the desktop's queue keeps its message, since the phone cannot change it.
Drafts stay on the phone — the desktop's queue is not changed, since the phone's copy of the task repo is replaced on every sync.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Notify when Claude finishes in the watched task
`req~android-finish-notification~1`

The task opened last on the phone is watched: when its Claude session ends a turn, the phone shows a notification naming the task, also while the app is in the background.
A question or permission prompt mid-turn notifies that Claude needs the user, and hitting the usage limit notifies that too; tapping a notification opens the task.
A task that is already idle when opened, and the task the user is looking at in the app, raise no alert.
Watching shows as an ongoing notification with a *Stop* action; opening another task watches that one instead.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Offer the newest build of the app
`req~android-update-hint~3`

The `android-dev` pre-release holds a debug APK of main, replaced only when Oliver asks for a new one — not on every push.
The app's task list offers *Update* when that APK was built from a different commit than the running app, and installs it on a tap; a check that cannot reach or read the release says why once per start.
The settings take a separate update token for that, since a fine-grained GitHub token cannot span two owners; left blank, the task repo token is used and needs read access to the ContextSwitcher repository too.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Send a message into a task's tmux window
`req~android-queue-send~1`

The task detail screen's queue page lists the task's pending queued messages (read-only, from the synced clone) and lets the user compose and send a new message straight into the task's tmux window, the same way the desktop's queue pane does.
A send failure is reported and the composed text is kept for retry.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### A crash can be reported without a cable
`req~android-crash-report~1`

When the app crashes, the next start shows what happened — the error, the build and the device — before anything else, ready to copy into a message, so a crash on a device without developer tools can still be diagnosed.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### Answer Claude's selection prompts on the phone
`req~android-terminal-keys~1`

When Claude asks with a list to pick from — a checklist, numbered choices — the user answers it from the terminal page without a keyboard: keys for moving up and down, toggling an entry, confirming, switching and cancelling are pressed in the task's session, and the terminal shows the result right after.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### A task opens on its current window
`req~android-window-lookup~1`

A task file synced to the phone may still name a tmux window the desktop has since replaced — resuming or restarting a Claude session creates a new window — and the desktop's next push can be minutes away.
Opening such a task still shows and drives the session: the phone finds the window that session runs in now and says that it did.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

### The phone's tasks on the Android Auto head unit
`req~android-auto~1`

While the phone is connected to a car, the head unit lists the tasks that have a Claude session, those waiting for the user first, each with its session's status.
Picking a task lets the user send it a message without touching the phone: dictated over the car's microphone and shown for confirmation before it is sent, or one of a few canned replies.
A message that cannot be sent is not lost.

Tags: android

Covers:
- feat~android-companion-app~1

Needs: dsn

## Design

### `TaskRepoSync`
`dsn~android-task-repo-sync~1`

`com.contextswitcher.android.sync.TaskRepoSync.syncInto(dir, url, username, token)` wraps JGit (`org.eclipse.jgit`): a `dir` without a `.git` subdirectory is cloned; an existing clone is fetched and **hard-reset** to `refs/remotes/origin/<the branch checked out at clone time>` — never a merge, since the phone can hold no local commit to preserve.
Credentials are `UsernamePasswordCredentialsProvider` over HTTPS only; ssh deploy keys are out of scope here.
The result is the sealed `SyncResult` (`UpToDate` / `Updated` / `Failed(message)`) — no exception crosses the call into the UI layer.
Settings (URL, username, token) live in app-private `SharedPreferences` via `com.contextswitcher.android.settings.SettingsStore`.

Tags: android

Covers:
- req~android-task-repo-sync~2

Needs: impl, utest

### Task list screen
`dsn~android-task-list~1`

`com.contextswitcher.android.ui.buildTaskListModel(tasksDir)` is a plain `Path -> List<TaskGroupUi>` function (no Android/Compose types, so it is unit-testable on the JVM without Robolectric): it scans `tasksDir` with `:core`'s `TaskRepository` (`Runnable::run` executor, no `startWatching` — the phone reads a fresh snapshot after each sync) and groups the resulting `TaskEntry` list by folder, folders sorted case-insensitively and tasks within a folder ordered by `TaskOrder.ALPHABETICAL`.
`com.contextswitcher.android.ui.TaskListBody` (Compose) renders that model: a group header row per folder (sticky at the top while its tasks scroll), then its task rows — title, status (suspended dimmed), and tag chips coloured via `:core`'s `TaskTags.autoColor`; a `TaskEntry.Failed` row renders in red with the file name and parse message.
`MainActivity`'s `ContextSwitcherApp` picks the settings screen or the task list depending on whether `SettingsStore.load()` returns settings, and the task list's top app bar carries a sync action (disabled while a sync runs, CLAUDE.md's async-single-shot-button convention) and a settings action; a failed sync reports through a Snackbar while the previously loaded list stays on screen.
The settings screen holds its unsaved input in `rememberSaveable`, so text typed before switching to a password manager survives Android killing the backgrounded app.
The screen scrolls, and the SSH key field is capped at eight lines (scrolling inside), so a pasted key never pushes the save button out of reach.
The open screen is saved the same way (`screenSaver`): a task detail as its task id, re-read from the synced repo on restore, falling back to the task list when that task is gone.
The task list shows the last synced clone as soon as it opens, before the sync round-trip, so a restored app is not empty while offline.
Its `LazyListState` lives in `ContextSwitcherApp`, above the screen switch, so *Back* from a task (which replaces the list on a phone) returns to the scroll position the user left.
While the list is empty, the last sync failure stays on screen with a Retry button instead of disappearing with its Snackbar. The manifest requests `android.permission.INTERNET`, which the sync and SSH both need.
The window has no action bar (`Theme.Material.Light.NoActionBar`) — each screen draws its own top bar — and the settings form pads itself by the system bars, so nothing lies over its first field under edge-to-edge.

Tags: android

Covers:
- req~android-task-list~1

Needs: impl, utest

### Tablet layout
`dsn~android-large-screen~1`

From a window width of 840 dp on (Material's expanded size class, `EXPANDED_WIDTH`, e.g. a tablet in landscape), the app shows side by side what a phone pages through.
`ContextSwitcherApp` puts the task list (360 dp) beside the open task's detail, with *Select a task* while none is open; the detail is keyed by task id, so switching tasks starts it afresh.
`TaskDetailScreen` shows the terminal page (60 %) and the queue page (40 %) next to each other instead of the tab row and pager.
Narrower windows, including the same tablet in portrait, keep the phone layout; the width is measured with `BoxWithConstraints`, so a rotation or a resized split-screen window switches layouts.

Tags: android

Covers:
- req~android-task-list~1

Needs: impl, utest

### `SshjCommandRunner`
`dsn~android-ssh-runner~1`

`com.contextswitcher.android.ssh.SshjCommandRunner` implements `:core`'s `SshCommandRunner` with sshj (MADR 0027) instead of a spawned process.
`host` must be `user@hostname[:port]` — there is no `~/.ssh/config` to resolve an alias or a bare host's default user.
One connected `SSHClient` per host is kept in a synchronized map, reconnecting when a stored one has died (ceiling: no idle disconnect yet).
Each `run`/`runWithInput` call is a fresh exec channel on that connection, with the command's argv joined by spaces (the same convention the desktop's `ProcessSshRunner` relies on: the remote shell reassembles the SSH exec string itself) and, for `runWithInput`, `input` written to the channel's stdin before it is read.
Auth is a single OpenSSH private key pasted into settings and held in app-private storage (`SettingsStore`, same Keystore-later ceiling as the repo sync token); no passphrase support (ceiling).
Host keys are verified TOFU-style through `com.contextswitcher.android.ssh.KnownHostsStore` (`host:port -> fingerprint`, persisted as a `.properties` file under `filesDir`): first contact records the fingerprint, a later mismatch throws `HostKeyMismatchException` naming both fingerprints — never an always-accept verifier.
The settings screen's "Clear known hosts" action calls `KnownHostsStore.clear()`.
Android's built-in "BC" JCE provider is a stripped subset that trips up sshj's algorithm probing; `SshjCommandRunner` registers a full `org.bouncycastle:bcprov-jdk18on` at the highest provider priority once, before any `SSHClient` is built (the standard sshj-on-Android workaround).

Tags: android

Covers:
- req~android-ssh~1

Needs: impl, utest

### Terminal page
`dsn~android-terminal-snapshot~3`

`com.contextswitcher.android.terminal.SgrParser.parse(text)` is a pure `String -> AnnotatedString` function: it walks `tmux capture-pane -e` output for CSI (`ESC [ … final`) sequences, updates a running "pen" (foreground/background color, bold) on each SGR (`m`-final) sequence — 16-color, bright 16-color, 256-color (palette and grayscale ramp), 24-bit truecolor, `1`/`22` bold, `0` reset, `39`/`49` default fg/bg — and strips every other CSI sequence without touching the pen.
`com.contextswitcher.android.ui.TaskDetailScreen` reaches the terminal page as the pager's first page when the task carries both `remote` and `tmux`; otherwise `NoLiveSessionPage` shows instead.
`com.contextswitcher.android.ui.TerminalPage` runs `PaneSnapshots.captureCommand(tmux)` over `SshjCommandRunner`, parses the result with `SgrParser`, and renders it monospace on a black background; a manual refresh button is disabled while a capture is in flight (CLAUDE.md's async-button convention), and the page also refreshes every 5s while visible (ceiling: polling, not a live stream — P6).
A failed capture reports through a Snackbar and keeps the last rendered snapshot.
Trailing blank lines of the capture are dropped, so the end of the page is the last output line.
The page follows the end: whenever the scroll range changes, it scrolls to the new end while `followEnd` holds, and every change of the scroll position recomputes `followEnd` as "within `FOLLOW_SLACK_PX` of the end" — so scrolling up stops the following and scrolling back down resumes it.
Scrollback is fetched on demand, so the 5 s poll stays a screenful: `loadEarlier` raises the capture's history by 500 lines (`PaneSnapshots.captureCommand(tmux, historyLines)`, `-S -<n>`, at most 20000) when the reader reaches the top while not following the end — keeping the line they read in place once the longer text is measured — or on *Earlier*, which shows the start of what was loaded.
A capture that does not grow marks the history exhausted and disables *Earlier*.
The poll skips while the reader is scrolled up, and the first capture back at the end drops the history again.

Tags: android

Covers:
- req~android-terminal-snapshot~3

Needs: impl, utest

### Queue page
`dsn~android-queue-send~2`

`com.contextswitcher.android.ui.QueuePage` lists a task's pending messages, loaded read-only via `:core`'s `QueueFile.load(QueueFile.file(queuesDir, task.id()))` against the synced clone's `.queues/` directory (a hard-reset mirror — the phone never writes a queue file of its own), plus a compose `TextField` and a Send button (disabled while sending, and while the field is blank).
Sending calls `:core`'s `MessageSender.send(remote, tmux.target(), text)` — the same `remote`/`target()` pair `com.contextswitcher.android.ui.TaskDetailScreen` reads directly off the task (there is no category-default resolution for `remote`/`tmux`, only for `intellij`/`browser`/`folders` — see `Task#withCategoryDefaults` — so no desktop helper needed moving for this).
A send failure reports through a Snackbar and keeps the composed text in the field for retry.
The page pads itself by the keyboard's height (`imePadding`; the detail screen consumes the bar insets its `Scaffold` already applied) and the message box shows at most six lines before scrolling inside, so *Send* stays visible above the keyboard in the edge-to-edge window; `adjustResize` does the same on Android versions that are not edge-to-edge.
The unsent text is held in `rememberSaveable`, so it survives Android killing the backgrounded app.

Tags: android

Covers:
- req~android-queue-send~1

Needs: impl, utest

### Message drafts
`dsn~android-message-drafts~3`

`com.contextswitcher.android.queue.DraftStore` keeps a task's drafts in app-private storage (`files/drafts/`), one file per task in the desktop queue's format — `:core`'s `QueueFile.file`/`load`/`save` — and never syncs it; adding a blank text or one already kept is a no-op.
`DraftStore` also holds the message box's current text per task (`<task>.unsent.txt`, removed when the box is blank).
`com.contextswitcher.android.ui.QueuePage` writes the box's text there on every change and reads it back when the page is composed — screen state (`rememberSaveable`) alone does not survive an APK update or a force stop.
It keeps the box's text as a draft (and empties the box) when the box loses focus — a tap on the page's empty space clears focus — and on *Save draft*.
The drafts are listed above the desktop's queued messages; a draft's *Send* goes through the same `send` as the box and removes the draft on success, *Delete* removes it, and *Edit* (or tapping its text) moves it back into the box.
*Edit* on a desktop-queued message puts a copy into the box and leaves the list untouched.
Both keep whatever the box held as a draft first and focus the box, so the keyboard opens.

Tags: android

Covers:
- req~android-message-drafts~3

Needs: impl, utest

### Last crash report
`dsn~android-crash-report~1`

`ContextSwitcherApplication` (the manifest's `android:name`) installs `CrashReport` before any activity or service runs: the default uncaught-exception handler writes `BuildConfig.GIT_COMMIT_LINE`, Android version, API level, manufacturer and model, the thread and the stack trace to `filesDir/last-crash.txt`, then calls the previous handler, so Android still ends the process as before.
`MainActivity` wraps the whole screen flow in `LastCrashDialog`: while that file exists, only a dialog with the selectable report, *Copy* (clipboard) and *Close* (deletes the file) is shown, and the app starts behind it only after *Close* — a crash during start cannot take the report down with it.

Tags: android

Covers:
- req~android-crash-report~1

Needs: impl, utest

### Images in a message
`dsn~android-message-images~1`

`QueuePage` has an *Attach* menu with *Photo* and *Camera* before *Save draft* (one control, so the button row keeps room for *Enqueue*). *Photo* opens the system photo picker (`ActivityResultContracts.PickVisualMedia`, images only, no storage permission); *Camera* hands the camera app (`TakePicture`) a `FileProvider` URI (authority `<applicationId>.files`, `res/xml/file_paths.xml` exposing only `files/attachments/`) for a file from `AttachmentStore.newCameraFile()`, whose path is `rememberSaveable` because the camera in front may get the app killed. No `CAMERA` permission: the app does not declare one, so the camera app may be used.
`AttachmentStore.store` decodes the image (sampled down while decoding), scales it to at most 2048 px on the long side, turns it upright per its EXIF orientation and writes a JPEG (quality 85) as `<yyyyMMdd-HHmmss-SSS>-<sanitized name>.jpg` into the attachments directory `MessageSender` was built with; the raw camera file is deleted afterwards.
The box gets the image's `Attachments.marker` (on a new line after any text), so the send uploads it and rewrites the marker to the remote path like a desktop message (`dsn~message-queue-send~5`). A picked file that does not decode reports through the Snackbar.

Tags: android

Covers:
- req~message-queue~2

Needs: impl, utest

### Start Claude for a new task from the phone
`dsn~android-live-task~1`

`AddTaskDialog` offers *Start Claude on <remote>* (checked by default) when the picked category's `CONTEXTSWITCHER.md` names a `remote`, with *Model* and *Effort* pickers (`ClaudeMode.MODELS`/`EFFORTS`, "as is" leaves the session's) and *Skip permission prompts* (the desktop's `claudeAuto`, remembered in `SettingsStore`); *Start* runs `LiveTaskStarter.start`, the dialog showing each step. Without a remote the dialog adds a plain task as before (`dsn~android-task-create~1`).
`LiveTaskStarter` does the desktop's live-task creation (`dsn~task-create-live~8`) with the same `:core` code, moved there from `:app` for it: `ClaudeWindowLauncher` (window, Claude start, trust dialog, model/effort, prompt paste with the visibility retries), `ClaudeWindowLauncher.liveTaskPrompt` and `liveTaskContent`. Remote, working directory (`resolveWorkdir`, `mkdir -p`ed first), repository and worktree bootstrap come from the category config; the tmux session is the first one a task on that remote uses, else `0`.
The task file — `liveTaskContent` plus `addUrlsFrom`, named `<date>-<description>` like the desktop's, with `TaskFileParser.withTmuxSection` for the fresh window — is pushed through `TaskRepoSync.createTaskFile` from the launcher's window callback, before Claude is up; the dialog closes then and the list shows the task, while the start continues and ends in a Snackbar. A window that cannot be created leaves the dialog open with the error; a push that fails after the window exists is reported with the window id.
The desktop adopts the task — its title from `@cs_title`, its session id — once its periodic sync (`dsn~task-git-backup~5`) has pulled the file.

Tags: android

Covers:
- req~task-create~1

Needs: impl, utest

### Enqueue on the phone
`dsn~android-enqueue~1`

The desktop arms a message to go out when Claude falls idle (`dsn~message-queue-delayed-send~3`); the phone has its own queue for that, never synced (MADR 0036): *Enqueue* after *Save draft* moves the box's text into `DraftStore(filesDir/enqueued)` (`TaskWatchService.enqueuedStore`), and the page lists it under *Sent when Claude is idle* with *Edit* and *Delete*, re-reading the store every 2 s to follow the sends. Offered only for a task with a tmux window and remote, since the watch sends them.
`TaskWatchService.pollOnce` sends the watched task's oldest enqueued message through `MessageSender` when `EnqueuedDelivery.ready` says so — a `waiting` or `done` reading, once per turn: after a send (or a failed one) it waits for `working` before the next, as the reading right after a paste can still be `waiting`. A sent message leaves the store; a failure keeps it and posts *Enqueued message not sent*.
Ceiling: only the watched task — the one opened last — sends; the queues of other tasks wait until they are opened again.

Tags: android

Covers:
- req~message-queue~2

Needs: impl, utest

### Delete a desktop-queued message
`dsn~android-queue-delete~1`

The desktop deletes a queued message through a hover button; a touch screen has none, so `QueuePage` gives every message under *Queued on the desktop* a *Delete* button next to *Edit*, confirmed in a dialog and reading *Deleting…* during the round-trip.
`TaskRepoSync.deleteQueuedMessage(dir, username, token, taskId, message)` removes the first equal message from `.queues/<task>.yaml` (`QueueFile.save`, which deletes an emptied file) and pushes it through the same write-back as `dsn~android-task-create~1` (fetch + reset, commit, push, one retry after a non-fast-forward, reset on any failure); a message already gone from the fetched queue — sent or deleted on the desktop meanwhile — needs no commit.
`TaskDetailScreen` reloads the queue from the clone afterwards, success or not, and a failure shows in the Snackbar.
The desktop pulls the deletion within its periodic sync (`dsn~task-git-backup~5`, five minutes); a desktop that changed the same queue meanwhile meets it in that pull, where git's line merge usually keeps both.

Tags: android

Covers:
- req~message-queue~2

Needs: impl, utest

### Finish notification
`dsn~android-finish-notification~1`

`com.contextswitcher.android.watch.TaskWatchService` is a foreground service (type `specialUse`) that `TaskDetailScreen` starts for a task with `remote` and `tmux`, requesting `POST_NOTIFICATIONS` first where Android needs it; a new start replaces the watched task.
Every 10 s it runs `:core`'s read-only `TmuxStatusPoller.statusCommand()` over its own `SshjCommandRunner` and reads the watched window's status with `TmuxStatusPoller.parseInto(…, idleThresholdSeconds)` at 120 s — the desktop poller itself never runs on the phone, since it also types into windows.
A window recorded by name is resolved to its id once (`tmux display-message -p '#{window_id}'`).
`com.contextswitcher.android.watch.FinishWatch` alerts only on a status change after `working` was seen: `waiting`/`done` → *Claude finished*, `attention` → *Claude needs you* (the turn stays open), `limit` → *Claude hit its usage limit*.
The alert is skipped while the app is started and shows that task's detail screen; its tap intent opens `MainActivity` (`singleTop`) on the task.
The ongoing notification shows the last status and poll time, or why the poll failed, and its *Stop* action ends the service.

Tags: android

Covers:
- req~android-finish-notification~1

Needs: impl, utest

### Update hint
`dsn~android-update-hint~3`

The `android-dev.yml` workflow, started by hand only (`workflow_dispatch`), builds the debug APK, uploads it to the `android-dev` pre-release with `--clobber` and then moves the `android-dev` tag to `$GITHUB_SHA`; `build.yml`'s `android` job only builds on a push, and day tags still get their own pre-release.
`android/build.gradle.kts` bakes `git rev-parse HEAD` into `BuildConfig.GIT_COMMIT` (`unknown` outside a checkout).
`com.contextswitcher.android.update.AppUpdates.check()` reads the tag's commit (`GET /repos/{repo}/git/ref/tags/android-dev`) with `RepoSettings.effectiveUpdateToken` (the *Update token* field, or *Token* when blank) and answers `UpToDate`, `Available(commit)` or `Failed(message)` — a 401/403/404 names the token as unable to read the repository, and an `unknown` build never offers an update.
`download` finds the `android-debug.apk` asset of the release, requests it as `application/octet-stream` without following redirects, and fetches the storage redirect's `Location` without the token.
`TaskListScreen` checks after every sync (a blank effective update token skips it), shows *Update* in the top bar for `Available` — disabled while downloading — and reports the first failure of a start in a Snackbar.
`com.contextswitcher.android.update.ApkInstaller` writes the APK into a `PackageInstaller` session with `USER_ACTION_NOT_REQUIRED` (Android 12+); `InstallResultReceiver` starts the installer's confirmation intent when user action is pending and toasts a failure.

Tags: android

Covers:
- req~android-update-hint~3

Needs: impl, utest

### Ctrl+C on the terminal page
`dsn~android-terminal-interrupt~1`

`com.contextswitcher.android.ui.TerminalPage` has a bar below the snapshot with a *Ctrl+C* button.
It runs `TmuxMirrorCommands.interruptCommand(target)` on the task's remote with the task's own window as target (`SshCommandRunner.quote(tmux.target())` — the phone has no mirror session), disabled while it runs; success captures the pane again right away, a failure reports through the Snackbar.

Tags: android

Covers:
- req~terminal-interrupt~1

Needs: impl, utest

### Window lookup on open
`dsn~android-window-lookup~1`

`TaskDetailScreen` lists the task's remote's windows once when it opens (`TmuxDiscovery.listSessions`, one ssh round-trip) when the synced `tmux.window` is an id, and asks `TmuxSync.currentWindowId(task, sessions)`: the recorded window while it is live (`liveWindow`, with its hijack check), else the single window of the task's tmux session publishing the task's `claude.sessionId` as `@cs_session_id`, else null (none, or several claimants — no guess).
A different id replaces the window for the whole screen (`Task.withTmuxWindow`): the terminal page (restarted under `key(window)`), keys, sends and the finish watch all target it, and a Snackbar names the old and the new id. A failed lookup leaves the synced task as it is.

Tags: android

Covers:
- req~android-window-lookup~1

Needs: impl, utest

### Terminal keys
`dsn~android-terminal-keys~1`

The *Ctrl+C* bar below the snapshot (`dsn~android-terminal-interrupt~1`) carries *↑*, *↓*, *Space*, *Enter* and *Tab* before *Ctrl+C*, and *Esc* after it — the right edge, where a thumb lands by accident, is the harmless key — wrapping onto a second line (`FlowRow`) where they do not fit, so no key hides off screen.
Each runs `TmuxMirrorCommands.keyCommand(target, key)` — `copy-mode -q`, then `send-keys` of the tmux key name (`Up`, `Down`, `Space`, `Enter`, `Tab`, `Escape`; `interruptCommand` is its `C-c` case) — on the task's own window, all keys disabled while one runs; success captures the pane again, a failure reports through the Snackbar.

Tags: android

Covers:
- req~android-terminal-keys~1

Needs: impl, utest

### Build commit line
`dsn~android-running-commit~1`

`android/build.gradle.kts` bakes `git show --no-patch --date=format:%Y-%m-%d %H:%M --format=%h (%cd) HEAD` — the desktop commit label's command — into `BuildConfig.GIT_COMMIT_LINE`.
`com.contextswitcher.android.ui.BuildCommitLine` shows it muted at the bottom of the task list (the `Scaffold`'s bottom bar) and of the settings screen, so it is on screen before a repo is configured too; a long press copies the bare sha (the line up to its first space) and toasts it — the phone's counterpart of the desktop's double-click.

Tags: android

Covers:
- req~running-commit~1

Needs: impl, utest

### Restart Claude on request
`dsn~android-claude-restart~1`

`com.contextswitcher.android.ui.TerminalPage` checks every capture with `TmuxStatusPoller.updatePendingIn` — the remote grep's rule applied to the screen it already holds: the bottom four non-blank lines, escapes removed — and while the footer shows "Restart to update", the bar below the snapshot offers *Restart Claude*.
The button runs `com.contextswitcher.discovery.ClaudeUpdateRestart.restart` for the task's window, disabled and reading *Restarting…* meanwhile, then recaptures and reports the outcome in the Snackbar.
`restart` is the desktop poller's sequence (`dsn~claude-update-restart~6`) in one blocking call, from its commands: read the window's `@cs_status`, `@cs_session_id` and `pane_current_command`; refuse unless `TmuxStatusPoller.restartable` (`NotIdle`); read the pane process's and its children's arguments (Linux `ps`) for `--dangerously-skip-permissions`, since the phone has no `claudeAuto` setting; send `exitCommand`; ask every 2 s, up to 15 times, until the window no longer runs `claude` (else `DidNotQuit`, no resume); then `resumeCommand` with the session id and the flag.
Not automatic on the phone: with the desktop also running, both would count their own `/exit` attempts and could type a resume into the other's fresh Claude.
`TaskWatchService` adds "update pending: Restart Claude" to its finished alert when the status query's `update|<window>` marker names the watched window (`TmuxStatusPoller.parseUpdatePending`).

Tags: android

Covers:
- req~claude-update-restart~1

Needs: impl, utest

### Add a task from the phone
`dsn~android-task-create~1`

The task list's floating *+* button (shown once a clone exists) opens `com.contextswitcher.android.ui.AddTaskDialog`: a category picker over the list's groups (default: the first named one) and a title; *Add* is disabled while the title is blank and reads *Adding…* during the round-trip. The dialog stays open on failure with the error under the field, so a title typed on a weak connection survives; its input is `rememberSaveable`.
`TaskRepoSync.createTask(dir, username, token, category, title)` fetches and hard-resets the clone, writes `<category>/<slug(title)>.md` (`-2`, `-3`… when taken; `slug`, not `newTaskFileName`, so a `/` in the title picks no folder) with `TaskFileParser.newTaskContent(title, remote, null, false)` + `addUrlsFrom` — the desktop's title task in compact form, `remote` from the category's `CONTEXTSWITCHER.md`, no `workdir`, whose paths are per machine — commits it as *ContextSwitcher Android* and pushes.
A push rejected as non-fast-forward (the desktop pushed in between) is retried once from the fetch: a new file cannot conflict, so no `TaskMerge` is needed. Any failure hard-resets to `origin/<branch>` again, so the clone stays a mirror and the next sync's reset loses nothing.
The desktop pulls the task within its periodic sync (`req~task-git-backup~4`, every five minutes).

Tags: android

Covers:
- req~task-create~1

Needs: impl, utest

### Task list on the head unit
`dsn~android-auto-tasks~1`

`com.contextswitcher.android.car.ContextSwitcherCarAppService` is a Car App Library service (`androidx.car.app`, category `IOT`, minimum car API level 5) that accepts any host: the APK is sideloaded, so Android Auto shows it only with *Unknown sources* enabled in its developer settings.
`CarTaskListScreen` reads the clone the phone app synced last — the car never syncs — and polls `TmuxStatusPoller.statusCommand()` once per remote (`pollStatuses`, the finish notification's 120 s idle threshold); *Refresh* does both again.
`carTasks` keeps the tasks a message can reach (not done, with `remote` and `tmux`) and orders them `attention`/`limit`, then `waiting`/`done`, then `working`, then unknown, by title within a block, cut to the host's list limit (`ConstraintManager`).
Ceiling: a window recorded by name instead of id shows no status.

Tags: android

Covers:
- req~android-auto~1

Needs: impl, utest

### Message from the head unit
`dsn~android-auto-message~1`

`CarTaskScreen` offers *Dictate message* and the canned replies `continue`, `yes` and `no`, and tries to make the task the watched one (`dsn~android-finish-notification~1`); Android may refuse that start from a car session, which only costs the finish alert.
`sendFromCar` sends at once through `TaskWatchService.messageSender` — Claude Code queues a message itself while a turn runs — and on failure adds the text to the phone's enqueued store (`dsn~android-enqueue~1`), saying so in a toast.
`CarDictationScreen` runs `CarDictation`: `CarAudioRecord` (the car's microphone, `RECORD_AUDIO`, requested on the phone when missing) piped into Android's `SpeechRecognizer` as an external audio source, which needs Android 13.
The recognized text is shown with *Send* and *Retry*; nothing is sent without *Send*, since a misheard sentence must not reach a session that may run without permission prompts.
Not verifiable without a head unit: the dictation path is untested until tried on a car or the Desktop Head Unit.

Tags: android

Covers:
- req~android-auto~1

Needs: impl, utest
