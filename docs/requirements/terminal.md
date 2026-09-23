# Terminal Integration

The remote tmux window running e.g. a Claude session is part of the task's context.
v0.1 provided a static capture-pane snapshot; since v0.2 the middle lane is a **live terminal mirror** (JediTermFX, [decision 0007](../decisions/0007-jeditermfx-for-the-embedded-terminal.md)).

## Requirements

### Live terminal mirror
`req~terminal-live-mirror~1`

Selecting a task shows a live, interactive terminal mirroring its tmux window next to the task list: the user sees what e.g. Claude produces as it happens, can scroll, and can type into the session without leaving the application.
Mirroring one task's window must not change which window the real tmux session (and a human attached to it) has selected.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Interrupt the session with a button
`req~terminal-interrupt~1`

The terminal offers a *Ctrl+C* button that sends Ctrl+C to the task's session, so Claude — or a command it runs — can be interrupted without a keyboard at hand: on the phone, and on the desktop next to the terminal bar's other keys.
One click sends one Ctrl+C, so a single click never exits Claude (which takes two in quick succession).

Tags: windows, linux, android

Covers:
- feat~remote-development-context~1

Needs: dsn

## Design

### Terminal pane with grouped-session mirror
`dsn~terminal-pane~14`

`TerminalPane` (middle lane) hosts a `JediTermFxWidget` (JediTermFX, MADR 0007; colors from `TerminalSettings`, `dsn~terminal-theme~2`) fed by a pty4j ConPTY pty (`PtyTtyConnector`, promoted from the [#34](https://github.com/contextswitcher/contextswitcher-private/issues/34) spike; resize propagates to the pty and thus the remote).
The connector drops every CSI whose first byte is `<` or `=` before the emulator sees it (`LeakingCsiFilter`, also when a read ends inside one): JediTermFX knows only `!`, `?` and `>` there and prints any other prefix as text, so Claude Code's kitty keyboard pop `ESC[<u` left a `<` in its input line each time, until the line was redrawn (field report 2026-09-15).
JediTermFX implements no sequence with those prefixes, so nothing it could act on is lost.
The pty runs `ssh -t <remote> tmux new-session -A -s cs-mirror-<session> -t <session> \; select-window -t cs-mirror-<session>:<window>` (`TmuxMirrorCommands`): a **grouped session** shares the base session's windows but has its own current window, so the mirror never disturbs the real session's selection; the leftover detached mirror session on the remote is harmless by design.
One pty per (remote, base session): selecting another task of the same session sends `tmux select-window -t cs-mirror-<session>:<window>` over the plain ssh side channel instead of reconnecting; a different remote/session closes the pty and attaches anew.
The fast path re-issues that `select-window` on **every** selection, even one whose target equals the window the pane last remembered: the mirror's real current window can drift from that memory (a select that failed and left the remembered value advanced, a window resurrected under another session, anything that moved the pty out from under the pane), and since keystrokes go to the mirror session's *current* window, a stale memory would send them to the wrong window while showing the right one. The select is idempotent and is itself the correction, so local memory is never trusted to skip the round-trip; the "Switching…" overlay still shows only for a believed change, so a same-window re-selection does not blink.
Two things bound the cost of that, because every `select-window` is an `ssh` process competing for the six global slots of `dsn~ssh-command-runner~6` and a switch the user is waiting for can end up queued behind them:
- **Superseded selects never run.** A select takes a sequence number before it is submitted and drops out if a newer one was taken while it waited for a slot. Without this, clicking through several tasks faster than the round-trip queued one select per click and the mirror visibly walked through every window on the way to the last — each intermediate window on screen long enough to read.
- **A confirmed select is trusted for ten seconds.** A re-show that believes nothing changed skips the verifying round-trip when the remote confirmed that exact connection and window less than ten seconds ago; a believed change always selects, and a failed select clears the confirmation. This is what keeps the background list rebuilds — which re-apply the selection every few seconds — from spending an ssh slot each on re-selecting the window that is already current. Drift the pane cannot see is still corrected, just up to one interval later.
That `select-window` **carries its own verification**: `\; display-message -p -t cs-mirror-<session> '#{window_id}'` prints the window the mirror really ended on, in the same command and the same ssh slot, so the pane confirms what it is *showing* instead of an exit code (`TmuxMirrorCommands.selectedWindow` reads the answer; MADR 0029).
A mismatch is treated exactly like a failed select — no uncovering, re-attach through the repair — because a select that "succeeds" onto another window is the one failure this pane cannot see for itself: all tasks of a base session share the one mirror client, so its current window is the only thing that says which task is on screen (field report 2026-09-09).
An empty answer (a tmux that printed nothing) reads as "cannot tell" and accepts the select, never as a mismatch.
While that same-session `select-window` round-trips, an opaque "Switching…" overlay covers the live terminal (and the stale header title is cleared) so the previous window's content cannot be mistaken for the newly selected task's.
A **successful** select does not lift the overlay by itself: the reply only says tmux accepted the select, while the mirror's repaint travels the pty a moment later, and uncovering on the reply flashed the previous task's window for that gap (field report 2026-09-10).
`TmuxSession.uncoverOnRedraw` therefore waits for the repaint — `PtyTtyConnector.onNextOutput` fires the pane's action on the pty's next batch of output — and a 750 ms `PauseTransition` backs that wait up, so a select that changes nothing on screen (the mirror was already on that window, and tmux redraws nothing) can never leave the cover stuck.
Both routes carry the same guard: the pane must still show that select's connection and window, so a newer selection is never uncovered early.
That target check, not the sequence number above, is the *uncovering* guard: background list rebuilds re-apply the selection every few seconds and each re-show takes a new number, so with an ssh round-trip slower than that cadence no select ever returned "current" and the overlay stayed up over a live window. The sequence number only decides whether a select still runs at all — a superseded one never reaches the point of uncovering anything.
A different-session attach already blanks with its "Connecting…" placeholder.
Before any of that, a click on a *different* task blanks the pane at once and only then points it at the task (`TerminalPane.blankThen`, one rendered frame later).
A selection runs as one FX event — snapshot widget, pty teardown, editor load — so the previous task's terminal otherwise stayed on screen until all of it had finished, which read as the click doing nothing (field report 2026-09-13).
A re-selection of the same task (the list rebuilds) is not deferred and does not blink; a fast-path select that believes nothing changed lifts the blank right away.
A *failing* `select-window` (tmux `can't find window`) never uncovers the pane — that would restore the previous task's window as if it were this one's, and a live pane underneath would take keystrokes meant for the selected task.
It re-attaches instead, because the usual cause is repairable and the full attach is where the repair lives.
That failure is not hypothetical: the mirror is grouped onto the base session, so once the base session is gone (a host reboot, `dsn~tmux-restart-running~1`) the mirror keeps the *old* window group while newly created windows land in a fresh session of the same name — and `new-session -A` *attaches* the surviving mirror by name, silently ignoring its `-t`, so the mirror is never re-grouped and every later select fails.
Unreported, every task then mirrored the one frozen window.
So every full attach first runs `TmuxMirrorCommands.repairMirrorCommand` over the plain ssh side channel: a mirror whose group has no other member (`session_group_size` below 2) is **stranded** — its leftover windows are `move-window`d into the live base session, not killed with it (they are live Claude sessions the dead base session was the last link to, and the sync imports them as tasks again), and the mirror session is killed so the attach recreates it correctly grouped.
A healthy mirror matches neither condition and is left alone.
The same round-trip reports whether the task's window exists on the server at all; `gone` stops the attach with a placeholder naming the window, rather than letting the attach's own `select-window` fail silently and land the pty on the mirror's arbitrary current window.
A generation counter discards async outcomes that finish after the selection moved on.
Placeholders replace the terminal (and end the connection) for tasks without tmux config and for suspended tasks — after a suspend the mirror must not silently drift to another window.
A task whose window is **gone but expected** (`TmuxFocusAction.windowGone`: no `window:` key, plus a `claude:` section or suspended status — the very condition the focus action resurrects on) gets the same treatment, because a window-less config addresses only the *session*, whose current window is whatever task was mirrored last: the fast path finds the pty already serving that session, has no window to select, and leaves the previous task's Claude on screen as if it were the selected task's.
Deliberately no attempt to *find* the window by title or cwd — that guess is what "Sync tmux windows…" does explicitly, and a wrong guess shows another session's work without saying so; the placeholder names the one action that restores the window.
After a switch/suspend of the previewed task finishes, `MainWindow.refreshPreview` re-targets the mirror with the freshest entry (a resurrect writes a new window id).
The header shows the terminal's application title (with tmux `set-titles on`: the pane title, i.e. Claude's task summary).
An unexpected pty exit auto-reconnects (`dsn~terminal-reconnect~2`); an intentional teardown does not.
The canvas is focused on attach (and JediTermFX refocuses it on click) so typing reaches the tty and the cursor renders; the cursor is set steady (no blink) — the port draws nothing on the blink-off phase, which read as no cursor.
The attach takes the keyboard only when it was asked to, or when nothing else holds it (changed in `~12`): an attach also happens without a user action (auto-reconnect, failed-select re-attach, and every task selection), and an unconditional grab stole the keyboard mid-word out of the queue pane's add box — and, since `~12`, off the task list, which pressing play focuses on purpose (`dsn~task-row-hover-actions~7`).
"Asked to" is a `TerminalPane.focusTerminal` that arrived before the attach completed (`focusOnAttach`, reset at the start of every attach so a request that never got its terminal cannot surface in a later reconnect); "nothing holds it" is the scene's focus owner being null, which is what an auto-reconnect that just removed its own focused canvas leaves behind — so typing in the terminal survives a reconnect.
A focused text input is never interrupted, asked or not.
The block cursor renders in the theme foreground (light), not black: JediTermFX 1.1.0 seeds the terminal's `StyleState` from the deprecated `getDefaultStyle()` (black-on-white) rather than the overridden `getDefaultForeground`/`getDefaultBackground`, and fills the cursor block with that StyleState foreground — so `TerminalSettings` overrides the deprecated method too, aligned with the color overrides (techsenger/jeditermfx#26; fixed on upstream master `32b3066`, whose next release removes the method and with it this override).
A mouse click on a task row also hands the keyboard to the mirror (`TaskListCell` → `TerminalPane.focusTerminal`, remembered while a placeholder shows or an attach is in flight — the attach focuses on completion), so after clicking a task the user can type straight away; keyboard selection (arrowing through the list) and the row's play button (`dsn~task-row-hover-actions~7`) deliberately keep the list focused.
`Ctrl`+letter (including `Ctrl+C`), `Shift+Tab` (as the reverse-tab escape `ESC [ Z` that Claude Code's TUI cycles modes with) and `Escape` (as `0x1b` — JediTermFX emits nothing for it, so `vi` never left insert mode and Claude Code's TUI could not be interrupted) are forwarded to the tty by a `KEY_PRESSED` filter on the **outer pane** (`installControlKeyForwarding`): capture-phase runs an ancestor's filter before the canvas's, so it wins over JediTermFX (which consumes `Shift+Tab` and can swallow `Ctrl+C` via its Windows copy handling) and over JavaFX focus traversal (which would otherwise steal `Shift+Tab`); `Ctrl+Shift+…` (e.g. copy) is left to JediTermFX.
The mouse wheel scrolls the window's tmux scrollback and a plain mouse selection (drag, double-click word, triple-click line) copies locally via `dsn~terminal-mouse-scroll~9`; copy/paste is `Ctrl+Shift+C`/`Ctrl+Shift+V`.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### A local Claude session is a tmux host like any remote
`dsn~terminal-local-mirror~2`

A local Claude session runs in **this machine's** tmux server (`dsn~task-create-local~4`), so its task carries a `tmux:` section but no `remote:`.
That absence is the whole marker: `TmuxHost.of(task)` answers `(local)` — a pseudo-host whose parentheses keep it apart from any real host name or ssh alias — where a remote task answers its `remote:`.
Everything tmux-facing is addressed by host, so naming the local one is what lets a local session behave like a remote one instead of being a dead end: before, the pane said "No tmux configured for this task." while a session had just been started, the delete dialog offered no way to end its window, and the queue said the task had nowhere to send to.

`HostCommandRunner` wraps the app's one ssh runner and routes by that host: `(local)` goes to `LocalTmuxRunner`, everything else over `ssh` exactly as before (nothing but a local task ever names the pseudo-host).
`LocalTmuxRunner` runs `sh -c "<the words the remote shell would have seen>"` from the user's **home directory** — where a fresh ssh exec channel starts too, which is what the relative paths in the command lines (the attachment drop directory) assume — and pipes stdin through for `tmux load-buffer -`/`base64 -d`.
One shell on either route is what makes the sharing work: the `\;` separators and the single-quoted tmux formats mean the same thing to a local `sh` as to a remote login shell, so no command builder needs a local dialect.
It prepends tmux's own directory to `PATH` (`RequiredTools.find`, `dsn~startup-tool-check~1`) — a GUI-launched app's `PATH` is the desktop session's, and the commands name `tmux` in several places.

With that in place the local session joins the remote features unchanged:

- **The mirror** (`dsn~terminal-pane~14`): `TmuxMirrorCommands.attachCommand` drops its `ssh -t <host>` prefix for the local host and hands the identical tmux command line to `sh -c`; the grouped mirror session (`cs-mirror-0` onto the local session `0`) works as remotely, so mirroring never moves the current window of a session the user has attached in a terminal of their own.
  `Show diff` and `Files` are disabled while a local session is mirrored — both are ssh round-trips (`dsn~terminal-diff-window~2`, `dsn~generated-file-download~2`) with no host to reach; disabling them says so, rather than letting them fail against a host called `(local)`.
- **Ending the window** (`dsn~task-suspend~6`, `dsn~claude-session-kill~6`): `TmuxKillAction` is configured for any task with a tmux host, so suspend, complete and the delete dialog's "End the tmux window" reach a local window too — and `MainWindow.hasLiveContext` counts such a task as running, which is what puts the checkbox in the dialog and the task into the category delete's one-by-one confirmation.
  The transcript and working-directory boxes stay remote-only: both are `remote:`-gated cleanups of a remote machine.
  The pane snapshot before the kill (`dsn~terminal-suspend-snapshot~3`) is taken over the same route.
- **Focusing** (`dsn~tmux-focus-action~3`): `TmuxFocusAction` likewise, so a switch selects the window in the local session (and resurrects it when the recorded window is gone).
- **Sending queued messages** (`dsn~message-queue-send~5`): the send buttons are enabled by the tmux host, not by a `remote:`, and the delivery takes the same `load-buffer`/`paste-buffer` route through the local shell.
  Only the attachments differ: a local chat reads the image where it already is, so nothing is uploaded and the marker is rewritten to its own absolute path.
- **Status polling** (`dsn~task-running-indicator~7`): the pseudo-host is one of the polled hosts, so a local session gets the same running dots, `@cs_title` adoption, `@cs_pr` capture and model report — through a local shell, without an ssh round-trip.

On Windows there is no tmux at all, so a local session is not mirrored but **owned** by the app instead (`dsn~terminal-owned-session~3`).

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1
- req~task-suspend-resume~2
- req~message-queue-send~2

Needs: impl, utest

### On Windows the app owns the local Claude session
`dsn~terminal-owned-session~3`

Windows has no tmux, so there is nothing for the pane to attach to and a local session used to be a dead end there: a Windows Terminal tab the app could not type into, and a pane saying so.
The app therefore **hosts the session itself** — `cmd /k claude [--model <alias>] [description]` in the pane's own ConPTY (`TerminalPane.startOwned`, pty4j `setUseWinConPty(true)`, working directory the task's `claude.cwd`), the same mechanism that already hosts `ssh.exe` for the mirror.
`cmd /k` for the two reasons `dsn~task-create-local~4` already gives: `claude` is a `.cmd` shim and ConPTY starts the command line via `CreateProcess`, which does not apply `PATHEXT`; and the shell survives Claude exiting or being absent, so the pane shows the error instead of going blank.
A `"` in the description becomes `'`: `cmd` knows no escaped quote, so one ended the quoted prompt and `cmd` parsed the rest — `"<"` became an input redirect from a missing file and Claude never started (field report 2026-09-15).
Typing works because the app owns the pty.

The **message queue** types into it the same way (`~1`→`~2`; field report 2026-09-12: "no tmux window to send messages to").
`ChatRoute.of` routes such a task to the owned session (`LocalClaudeLauncher.ownsSession`), so `QueuePane` counts it as sendable and `Main`'s sender hands the text to `TerminalPane.sendToOwned`, which writes it as one bracketed paste (`LocalClaudeLauncher.ownedPaste`, line breaks as `CR` like `tmux paste-buffer`) and the `Enter` a second later — the pause `dsn~message-queue-send~5` takes, for the same reason.
Attachment markers become the files' own absolute paths (`MessageSender.localText`), as for a local mirror.
With no session running the send fails with a status line saying to start or resume it, and the card stays.
Delayed send stays off for such a task: it waits for the status poll's idle report, which an owned process does not publish.

This is a **second mode** of the pane, not a variant of the mirror: it shares the widget, the hyperlink filters, the theme and the key forwarding, and none of the tmux machinery — there is no window to select, no mirror session to repair, and no auto-reconnect (an `OwnedSession` has none of it, and the pane asks the session in view rather than checking its mode).
The bar below the terminal (`Clear input`, `Jump to bottom`, `Show diff`, `Files`) is tmux and ssh round-trips on a mirror, so an owned session gives each button a **local route** instead (`~2`→`~3`; field report 2026-09-13: "the four buttons are not working with a local terminal" — `~2` had disabled all four).
`Clear input` writes the same `TmuxMirrorCommands.CLEAR_INPUT_KILLS` × `^U` straight into the pty: the copy-mode trap that sends the mirror's kills over the side channel (`dsn~terminal-clear-input~3`) needs a tmux, and the widget scrolls its own history.
`Jump to bottom` scrolls that history back to the live output (`TerminalPanel.scrollToShowAllOutput`).
`Show diff` runs git on this machine in the task's `claude.workspace`, else the session's directory (`LocalDiff`, the same changes-else-last-commit choice as `dsn~terminal-diff-window~2`), and opens the colored output in a read-only terminal window (`AnsiTextWindow`).
`Files` lists this machine's own scratchpads — `<java.io.tmpdir>/claude/<encoded cwd>/<session>/scratchpad`, `RemoteFiles.listLocal` — and opens the picked file in place, with nothing to download; its "Custom path…" asks for a local path.
No running dot is claimed for such a task either: the dots come from tmux options an owned process does not publish (`dsn~task-running-indicator~7`), and "the process is up" is not "Claude is waiting for you".
A live theme switch leaves an owned session in the old colors — `TerminalPane.retheme` re-attaches a mirror, and re-attaching this would mean killing the Claude it hosts.

What is given up deliberately is **detach**: closing the app ends the process, because no tmux holds it.
The way back is a resume, so the pane keeps owned sessions across selections — one widget per task in `TerminalPane.owned`, only the one in view changes — and offers a **resume** when none is running: `ClaudeSessionLookup.findLatestLocalSession` reads `%USERPROFILE%\.claude\projects\<encoded cwd>` directly (no host to ask) and the newest transcript there is continued with `claude --resume <id>`, neither model nor prompt passed — both belong to the conversation being continued.
The encoding replaces every separator and dot with `-`, the drive colon and the backslashes included: `C:\git-repositories\github.com\contextswitcher\contextswitcher` → `C--git-repositories-github-com-contextswitcher-contextswitcher`.
With no transcript yet the same placeholder offers a plain **start**.
`Main.ownsSession` is the whole marker: on Windows, a task with no `remote:` and no `tmux:`.
It is checked **before** the remote-window offer of `dsn~remote-window-choice~6`, and it is deliberately not gated on a `claude:` section — such a task has no host for those two buttons to open a window on, and a local one whose creation died before the write-back would otherwise sit in the remote flow's placeholder with nothing to click.
The directory is the `claude.cwd` a started session recorded, else the task's own `folder:` (seeded from the group defaults at creation, `dsn~group-config-apply~2`); a task naming neither has nowhere to run and keeps the plain "No tmux configured for this task." placeholder.

Linux and macOS are untouched: they have a real tmux, and the mirror of `dsn~terminal-local-mirror~2` is the better answer — it detaches, survives an app restart, and carries the status dots.

Tags: windows

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Sessions in WSL
`dsn~wsl-sessions~1`

On Windows the third place a session can live is a **WSL distribution**: a real Linux with a real tmux, on the user's own machine.
It is the answer to what `dsn~terminal-owned-session~3` gives up — a session that detaches, survives an app restart and carries the status dots — without a second machine or an sshd to set up.

A WSL session is a **remote** in every way the app already understands.
Its task carries a `remote:` line, a `tmux:` section and a mirrored pane, so every `remote() != null` branch — the pollers, the message queue, the attachment drop, the kill on suspend, `Show diff`, `Files` — is reached unchanged.
The host string is `wsl:<distro>` (`TmuxHost.WSL_PREFIX`; `wsl:` alone means whichever distribution `wsl.exe` starts by default), whose colon keeps it apart from any ssh alias the way the parentheses of `(local)` do.

Only the **transport** differs, which is what `HostCommandRunner` exists for: `(local)` routes to `LocalTmuxRunner`, a `wsl:` host to `WslTmuxRunner`, everything else over `ssh` as before.
`WslTmuxRunner` runs `wsl.exe [-d <distro>] -- sh -lc "<the words the remote shell would have seen>"` and pipes stdin through for `tmux load-buffer -`/`base64 -d`.
A **login** shell, unlike the local runner's `sh -c`, and with `PATH=$PATH:$HOME/.local/bin` prepended: the tools live inside the distribution and must be found the way the tmux window finds them — the same reason the wizard's ssh probe uses one.
One shell on all three routes is again what makes the sharing work: no command builder learns a WSL dialect.

Three commands build their own argv rather than going through the runner, and each gains the one branch:

- `TmuxMirrorCommands.attachCommand` — the mirror, spawned into the pane's pty. The `ssh -t` prefix and its keepalives fall away: nothing can go half-open on this machine, and the pty the pane spawns already gives `wsl.exe` a terminal.
- `RefactoringMinerCommands.viewCommand` — WSL2 shares the loopback interface with Windows, so the port the view serves inside the distribution answers at `127.0.0.1` with no `ssh -L` tunnel to build.
- `LocalCommandRunner` lists `wsl.exe` as Windows-only, so nothing is spawned off Windows — the same guard `powershell` has.

The wizard offers it (`dsn~setup-wizard~8`) and it is the only place a `wsl:` host is composed, so nothing else needs to know the prefix exists.

Not addressed: **paths across the boundary**.
A WSL task's `workspacesRoot` is a Linux path, and the Windows-side actions that open one by path (the category `folders:`, IntelliJ) would need a `\\wsl$\<distro>\…` translation.
Deferred until someone runs into it: the session, the mirror and the queue — what the app is for — need no translation at all.

Tags: windows

Covers:
- req~terminal-live-mirror~1
- req~setup-wizard~1

Needs: impl, utest

### URLs in the terminal are clickable
`dsn~terminal-hyperlinks~1`

Both terminal widgets — the live mirror and the frozen suspend snapshot — get JediTermFX's `DefaultHyperlinkFilter` via `widget.addHyperlinkFilter`, which marks `http(s)://`, `ftp://`, `mailto:` and `www.` runs in the emulated text as links.
Hovering one shows a hand cursor and underlines it (the provider default `HOVER_WITH_BOTH_COLORS`); a primary click opens it in the system default browser (the filter's own `Desktop.browse`).
Neither tmux's mouse mode nor the local-selection rewrite of `dsn~terminal-mouse-scroll~9` swallows the click: JediTermFX's `TerminalPanel.doOnMouseClicked` checks for a hovered link *before* the selection and mouse-reporting paths.
Deliberately the system browser, not the Firefox-extension tab focus of `dsn~browser-focus-action~4`: the link under the mouse is arbitrary terminal text, not a task's registered `browser.urls` entry, and the extension may not be connected at all.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl

### Issue numbers in the terminal are clickable
`dsn~terminal-issue-links~2`

Claude writes issue and pull-request numbers as `#17038` or `PR 17038`, the one link in the mirror with no URL to click.
`IssueHyperlinkFilter` (package `terminal`) marks such a token in both terminal widgets — matching `#` plus digits, but not inside a longer token, so a `#1a2b3c` color and a `pane#2` label stay plain text, and also a standalone `PR` word (any case) followed by digits — and a click opens `<repo>/issues/<number>` in the system browser, like the URL links above.
GitHub redirects an issue URL to the pull request when the number is one, so issues and PRs need no telling apart.

The repository is the mirrored task's category `repo:`, handed to the pane by `TerminalPane.setIssueRepo` on every selection and read again on each click; without one, nothing is a link.
A category with no `repo:` gets it guessed once from the task checkout's `remote.origin.url` (`git config --get`, over the same host route the mirror uses, `GitRemote.repoUrl` mapping the scp-like, `ssh://` and `https://` clone forms to the web URL) and written back to its `CONTEXTSWITCHER.md`, so later sessions need no round-trip.
A category without a config file keeps the guess for the session only — creating that file behind the user's back is not this feature's business.

A number may name its own repository, which is how Claude refers to a *foreign* one while working in another: `JabRef/jabref#17103` spells out owner and name and opens on the forge of the mirrored task's repository (a self-hosted GitLab as much as github.com — the only forge the pane has evidence of; github.com without a repository at all), the short `JabRef#17103` is resolved against every category's `repo:` by owner or name, case-insensitively like the forges themselves.
Handing the pane those repositories (`TerminalPane.setKnownRepos`, refreshed on every selection) is what keeps a `pane#2` label plain text: a prefix naming no repository the app knows is no link, and guessing `<prefix>/<prefix>` instead would turn every such label into a 404.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### File paths in the terminal are clickable
`dsn~terminal-file-links~1`

A path Claude prints — the attachment an image paste uploaded, the report it just wrote into its scratchpad — is opened by clicking it, instead of retyping it into the "Files" menu's "Custom path…" dialog (`dsn~generated-file-download~2`).
`FileHyperlinkFilter` (package `terminal`) marks an absolute or `~`-rooted path whose last segment ends in an extension; a directory is nothing to open, and the extension is what keeps the slashes of ordinary prose out of the links.
A click opens the file in its registered application when it is on *this* machine (the attachment case: the upload keeps the name, so `Attachments.localCopy` maps `…/.contextswitcher/attachments/<name>` to the local copy even when the remote home differs from the local one), and otherwise downloads it from the mirrored host first — the "Files" menu's own route, `RemoteFiles.stat` then `downloadGeneratedFile`, so the size limit and the "no readable file" alert apply unchanged.

A hyperlink filter sees one line at a time, so a path Claude Code hard-wrapped across two lines stays plain text; the menu remains the way to reach those.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### A hovered terminal link shows its URL in the status bar
`dsn~terminal-link-hover~1`

The terminal shows link *text*, not the target: `#17071` says nothing about the repository it resolves against, and a long URL is cut off by the pane's width.
`HoverLinkFilter` (package `terminal`) wraps both filters above and gives each of their links a JediTermFX `LinkInfoEx` hover consumer, which reports the URL to `MainWindow`'s status bar while the pointer rests on it and clears it again on leave — `SwitchStatusBar.hover`, the same sink the task rows' PR icons use, so a switch running meanwhile keeps its chips.
Wrapping keeps the click behavior of the wrapped filter untouched.
For a `#123` link the reported URL is the one the click would open (`IssueHyperlinkFilter.urlFor`); without a repository the token is no link at all and nothing is reported.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Terminal colors follow the app theme
`dsn~terminal-theme~2`

Under an **Everforest** app theme (`everforest`, `everforest-light`, `everforest-dark`) the mirror is drawn in the Everforest palette ([decision 0024](../decisions/0024-everforest-palette-for-the-terminal-mirror.md)), medium dark variant on a dark app theme and medium light on a light one, so a light UI does not frame a dark terminal.
Under the plain Nord themes (`system`, `light`, `dark`) it keeps JediTermFX's stock ANSI palette, on black (white text) for a dark theme and on white (black text) for a light one — Everforest is opt-in with the app theme, not forced on every user.
`TerminalSettings(boolean dark, boolean everforest)` (package `terminal`) is the JediTermFX `SettingsProvider`: it overrides `getDefaultBackground`/`getDefaultForeground` (Everforest: `#2d353b`/`#d3c6aa` dark, `#fdf6e3`/`#5c6a72` light; plain: `#000000`/`#ffffff` and the reverse) and `getTerminalColorPalette` with the variant's ANSI 0–15 (plain: the provider's stock palette).
The mapping mirrors between the variants: `bg3` is index 0 on dark and index 7 on light, `fg` index 7 on dark and index 0 on light, `grey1` is bright black in both, and the brights otherwise equal the normals — Everforest has one shade per hue and a second, invented one would break its low-contrast intent.
`TerminalPane` builds every widget — the live mirror and the frozen snapshot view — with `new TerminalSettings(Themes.isDark(), Themes.isEverforest())`, and paints the JavaFX regions framing the canvas (placeholder area, "Switching…" cover, snapshot scroller) with `TerminalSettings.backgroundColor(dark, everforest)`, the same color the canvas uses, so no seam shows.

A JediTermFX widget takes its colors from the provider it was **built** with, so a live theme switch cannot restyle an open one.
`MainWindow`'s settings-Save path therefore calls `onRethemeTerminal` (wired to `TerminalPane.retheme`) right after `Themes.apply`: `retheme` re-grounds the framing regions and **re-attaches** a live mirror. The re-attach is cheap and lossless — the session and its scrollback live in the remote tmux, not in the widget. A placeholder or a snapshot has nothing to re-attach; the snapshot re-renders on the next selection.

Known ceiling — this is the boundary of what the local side can theme.
`ColorPalette` asserts the color index is below 16, and the 256-color cube above it is fixed by xterm, so only ANSI 0–15 is ours: a remote program emitting 256-index or 24-bit color (Claude Code under a dark theme, a hex-styled tmux status line) keeps its dark colors on the light ground.
Nothing local can change that — `ssh -t` carries no environment, so `COLORFGBG` never reaches the remote, and the mirror attaches to a session whose programs already chose their colors at launch.
The fix belongs on the remote: give the program a light theme of its own (Claude Code's own theme setting, ideally an ANSI-only variant, which then inherits the palette above).
By the same token a program writing bright white on the default ground vanishes under the light variant, as it does in every light terminal theme.

Tags: windows, linux

Covers:
- req~theme-select~1

Needs: impl, utest

### Mouse-wheel scrollback and copy in the terminal pane
`dsn~terminal-mouse-scroll~9`

A tmux attach owns the whole screen, so the mouse wheel has nothing local to scroll — the window's scrollback lives in tmux copy-mode. `TmuxMirrorCommands.attachCommand` therefore appends `set-option -t cs-mirror-<session> mouse on` to the attach, so the wheel enters copy-mode and scrolls the tmux scrollback. Mouse mode is set on the **mirror** session only (explicit `-t`), leaving the user's real session and its clients untouched.
Mouse mode alone is not enough: stock tmux hands the wheel to any application that turned mouse reporting on, so in a Claude window the wheel paged through Claude's own message history and the scrollback stayed unreachable. The attach therefore also rebinds `WheelUpPane`/`WheelDownPane` on the root table with the condition `TmuxMirrorCommands.WHEEL_TO_APPLICATION` — "in copy-mode, **or** mouse reporting on and not a `cs-mirror-*` session". In a mirror the application's request is ignored (wheel up enters copy-mode, wheel down is swallowed outside it); key tables are server-global, so everywhere else the condition reduces to tmux's stock `#{||:#{pane_in_mode},#{mouse_any_flag}}` and the user's own sessions keep the default behaviour.
With mouse mode on, a plain click-drag would be handled by tmux copy-mode: on release tmux copies into its **remote** paste buffer and drops the highlight — the text never reaches the local clipboard (JediTermFX has no OSC 52 support, so tmux `set-clipboard` cannot bridge it).
Requiring `Shift`+drag for a local selection (JediTermFX's built-in mouse-reporting bypass) is the xterm/PuTTY convention but reads as broken in a mirror the user only ever reads and copies from.
`TerminalPane.installLocalSelection` therefore makes the plain gesture the local one: a capture-phase filter on the outer pane consumes a button event that would be reported to the tty (`isRemoteMouseAction`) and re-fires it at the canvas with `Shift` set, which is the bypass JediTermFX already implements — so drag highlights, double-click selects the word, triple-click the line, and `TerminalSettings.copyOnSelect()` puts the selection into the system clipboard on release (`Ctrl+Shift+C` on the still-highlighted selection works too).
Only the four button events are rewritten (press, drag, release, click); the wheel is left alone and still drives tmux's scrollback, which is what the mirror's mouse mode is there for.
Only events on the canvas itself are rewritten: the filter sits on the widget's pane, which holds the scrollbar too, and rewriting a click there turned it into a text selection instead of a scroll. A drag that began on the canvas keeps the canvas as its target while it crosses the scrollbar, so a selection can still be dragged to the edge.
The scrollbar itself is only shown while JediTermFX holds a scrollback (`TerminalPane.hideScrollbarWithoutScrollback`): Claude Code runs in the alternate screen buffer, and so does a tmux client, which has no scrollback — JediTermFX pins the scrollbar to one full screen there, the thumb fills the whole track, and the program scrolls its own transcript without telling the terminal where it is, so there is nothing for a scrollbar to show.
JediTermFX marks a real scrollback with a negative scrollbar minimum (the history lines above the screen), and the visibility follows that; a plain shell with history, e.g. after Claude exits, gets its scrollbar back.
It is hidden, not taken out of the layout: that would widen the canvas and resize the pty every time a program enters or leaves the alternate buffer.
The trade-off is that buttons no longer reach the remote at all — a click cannot select a tmux pane or hit a widget in a TUI. Hyperlink clicks are unaffected (`dsn~terminal-hyperlinks~1`): JediTermFX checks the hovered link before the selection path.
JediTermFX 1.1.0 builds the xterm wheel report with Swing's sign convention (`FxMouseWheelEvent`: `deltaY > 0` → button 5), but a JavaFX `deltaY > 0` means wheel **up** — the reported direction was inverted.
`TerminalPane.installWheelDirectionFix` (capture-phase filter on the outer pane) consumes a scroll that would be reported and re-fires it at the canvas with the Y deltas negated, so JediTermFX's own coordinate and protocol handling emits the correct button; local scrolling (`Shift`+wheel, no reporting) already negates correctly and is left alone. Remove once a jeditermfx release carries the upstream fix ([techsenger/jeditermfx#24](https://github.com/techsenger/jeditermfx/issues/24)).

An **app-owned** local session (`dsn~terminal-owned-session~3`) takes the same wheel route as the mirror: the wheel is reported to the program, direction-corrected, and Claude Code scrolls its own view.
The pane has no local scrollback worth scrolling there — ConPTY repaints the viewport in place rather than scrolling lines into the terminal's history — so an attempt to make the plain wheel scroll the widget's own scrollbar instead (`~6`) left the wheel doing nothing at all, and was reverted.
`TerminalSettings.sendArrowKeysInAlternativeMode()` is `false` for both session kinds: JediTermFX sends `Up`/`Down` keys for a wheel in the alternate buffer from a handler with no `Shift` guard and no `else` against the mouse-reporting branch beside it, so a program that asked for the wheel would get the report *and* the keys.
The keys read the raw sign while the report is direction-corrected, which fits what was seen in an owned session — a wheel down scrolled Claude Code down and moved its prompt cursor **up**, then into the previous message; a mirror, a tmux client, is always in the alternate buffer and would double the same way.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Optional control sequences
`dsn~terminal-optional-sequences~1`

Claude Code sends control sequences for optional terminal features that JediTermFX 1.1.0 does not implement, and JediTermFX logs each one as an "Unhandled Control Sequence" warning — synchronized output alone twice per frame, about 186,000 lines in one day's log, to the console as well as the file.
They are not faults: each feature's specification makes it optional and tells a terminal without it to ignore it, so ignoring them is the correct handling, not a workaround.
`OptionalSequencesEmulator`, the emulator of every live pane widget (via `TerminalPane.liveWidget`'s `TerminalStarter`), recognizes exactly these and does nothing:
`CSI ? 2026 h`/`l` (synchronized output — a frame then paints as it arrives), `CSI > flags u`, `CSI < u` and `CSI ? u` (kitty keyboard push, pop and query — a terminal without the protocol must not answer, which keeps the program on the classic key encoding the pane sends), `CSI > 4 ; n m` and `CSI > 4 m` (xterm modifyOtherKeys), `CSI ? 2031 h`/`l` (color-scheme change notifications, which the pane never sends) and `CSI 1 t` (de-iconify — the pane is never iconified).
Every other sequence reaches JediTermFX untouched and still warns when it is unknown: `JediEmulator`'s dispatch is private, so the CSI is read ahead and, when it is not one of these, pushed back to be parsed as if it had never been looked at — a control char inside the CSI or a body longer than any of these hands it back at once.
`OptionalSequencesEmulatorTest` checks the recognized set, that an ignored sequence leaves no trace while the next one still acts, and that every other input — a control char inside a CSI, an overlong SGR, a plain `CSI u`, a charset switch, erase and cursor moves — renders exactly as JediTermFX alone renders it.
`LeakingCsiFilter` (`dsn~terminal-pane~14`) stays in front of it: it removes `CSI <`/`CSI =` sequences before JediTermFX's parser could print half of one.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Closing a terminal ends its process once
`dsn~terminal-process-close~1`

Closing a live terminal — an owned session at shutdown or on its end, a mirror on a disconnect — ends the pty's process tree, and destroys the process exactly once.
JediTermFX closes its connector twice per widget close, on two threads (the cancelled emulator task in its `finally`, and `TerminalStarter.close`), and the sessions destroyed their process a third time; `ProcessTtyConnector.close` destroys unconditionally, and pty4j's `isAlive` reads an exit code its waiter thread fills in a moment after the exit.
So every close sent `TerminateProcess` to a process the first destroy had already set exiting, which Windows refuses: "Failed to terminate process … Zugriff verweigert" twice per close, although a probe showed the shell and the program under it gone at once — noise, not a leak.
`PtyTtyConnector.close` now destroys and closes its streams once (an `AtomicBoolean`), and `OwnedSession`/`TmuxSession` leave the process to the widget's close, destroying it themselves only while no widget exists yet.
`PtyTtyConnectorCloseTest` closes one connector from three threads and counts one destroy; `OwnedSessionCloseUiTest` (Windows) closes a real owned `cmd /k ping -t` session and requires the shell and `ping` both gone within five seconds.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Zooming the terminal font
`dsn~terminal-zoom~4`

`Ctrl`+wheel over the terminal pane changes its font size by one point per notch, `Ctrl`+`0` puts it back to JediTermFX's 14 points — the gesture every browser and IDE has, and the only way to read the pane on a 4K screen without changing the whole app's font.
`TerminalZoom` keeps the size (clamped to 6–40 points: below that a line is unreadable, above it a few columns fill the pane, and a mirror would reflow the remote window for nothing) in `Preferences`, one size for every terminal in the app — the size is how the user reads the pane, the same reasoning `dsn~task-sort-modes~2` persists the sort order by.
`TerminalSettings.getTerminalFontSize` reads it back, so a changed size reaches a widget when it rebuilds its font; JediTermFX only does that on a resize, so `TerminalPane.PanePanel` (its own `TerminalPanel` subclass, which also carries the mirror's Markdown copy handler) exposes the `protected reinitFontAndResize`.
`TerminalPane.refreshFonts` calls it on **every** widget the pane still holds (a weak registry), not only the one under the pointer: the sessions of other tasks stay alive off-screen (`dsn~terminal-owned-session~3`) and must not come back in the old size.
A resize reaches the pty like any window resize, so a mirror's tmux reflows the remote window to the new geometry.
Zooming in shrinks the grid in both directions at once, and in JediTermFX 1.1.0 (as in JetBrains' jediterm it ports) such a resize can throw: `ChangeWidthOperation` re-wraps the buffer for the new width and starts the new screen at `cursorY - newHeight + 1` to keep the cursor visible, but in Claude Code's alternate-buffer screen the cursor is tracked below the buffer's last non-blank line, so a big enough height cut puts that start past the end of the line list and `subList` throws `IndexOutOfBoundsException`.
The resize runs as a task on JediTermFX's executor, which swallows the exception — the emulator and the pty kept the old size while the panel already painted the new one: the right and bottom edges were cut off, Claude Code's input box with them, and every later shrink failed the same way (growing never did).
`StepwiseResizeTerminal`, the `JediTerminal` every pane widget is built with (`createTerminal`), splits a resize that narrows and shortens at once: first the new width at the old height, where the start index cannot pass the cursor's own line, then the height alone, which never re-wraps; the pty still receives one resize, the final size.
Found with a UI probe of every layer on Windows (the panel's stored size, the size that fits, the emulator buffer, pty4j's pty size): real Claude zoomed to the minimum font and back failed every time, and calling the emulator's resize directly surfaced the exception; `StepwiseResizeTerminalTest` reproduces it headless.
The same class keeps the cursor on screen after every resize: a shorter alternate buffer keeps the cursor's old row (`TerminalTextBuffer.resize` only moves it outside the alternate buffer), so it could sit below the last row, and the next write that wrapped read the line under it — JediTermFX logged "Attempt to get line out of bounds: 32 >= 32" (the index always equal to the new height). xterm keeps the cursor on screen across a resize; the clamp sets the raw row, since `cursorPosition` counts from the scrolling region's top in origin mode.
The wheel-direction filter of `dsn~terminal-mouse-scroll~9` leaves a `Ctrl`+wheel alone: a consumed event still reaches the filters registered beside it, so it guards itself.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Jump to bottom
`dsn~terminal-jump-to-bottom~1`

A "Jump to bottom" button sits in a bar below the terminal and returns the mirror to the live output after the user scrolled up.
Scrolling the mirror means tmux copy-mode (`dsn~terminal-mouse-scroll~9`), not a local scrollbar, so the button sends `tmux send-keys -t cs-mirror-<session> -X cancel` over the same plain ssh side channel the window switch uses, then puts the keyboard back into the terminal.
The command targets the mirror **session**, not a window: the mirror's current window is what the pane shows, and a task may pin no window at all.
The button is a no-op while nothing is connected (a placeholder is showing); outside copy-mode tmux merely reports an error on the side channel's stderr, which never reaches the terminal.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Markdown copy
`dsn~terminal-markdown-copy~1`

Text copied off the mirror of a Claude chat reaches the clipboard as the Markdown Claude wrote, not as the rendered screen: Claude Code drops `**`, backticks, `#` and fences when it renders, and hard-wraps with its own gutter, so the screen cannot give the source back.
The source is the session transcript, `~/.claude/projects/<encoded claude.cwd>/<claude.sessionId>.jsonl`, read over the side channel with `grep` for assistant text lines and `tail -n 40` (`ClaudeReplies`); consecutive lines of one message id form one reply.

* A **Copy reply** button in the bar below the terminal puts the last reply on the clipboard (disabled for the round-trip, outcome in the status bar).
* A **selection** is copied as rendered at once (copy-on-select and `Ctrl+Shift+C` share JediTermFX's copy handler, which the mirror's widget replaces); when it has at least 20 letters and digits, the transcript is fetched and the reply containing it found by comparing letters and digits only — everything rendering changes is punctuation or whitespace.
  The matched span widens over hugging `*_`~` marks and, when only syntax precedes or follows it on its line, to the whole line (`- item`, `## Heading`), and replaces the clipboard unless the clipboard changed meanwhile.
  No match (a link rendered without its URL, a selection across replies) leaves the rendered text.
* `AppSettings.autoCopyReplies` (default `false`; only an explicit `true` counts, since it overwrites the clipboard unasked) copies the last reply on a `working` → `waiting`/`done` edge of the **mirrored** window's poll status (`dsn~task-running-indicator~7`) — other tasks' replies never touch the clipboard.

A task without a recorded session id, and an app-owned session (no side channel), copy the rendered text only.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Clear input
`dsn~terminal-clear-input~3`

A "Clear input" button sits left of "Jump to bottom" in the bar below the terminal and discards whatever the user typed but has not sent.
It sends `C-u` (kill line) — the readline convention a shell and Claude Code's input box both honour — over the same plain ssh side channel "Jump to bottom" uses, prefixed by `copy-mode -q` in the **same** tmux invocation, and then puts the keyboard back into the terminal.
The `copy-mode -q` (leave copy-mode; a silent no-op outside it) is why the side channel is used instead of the pty, as version ~2 did: while the pane is in copy-mode — which is where the mouse wheel scroll puts it (`dsn~terminal-mouse-scroll~9`) — a pty-written `^U` scrolls half a page instead of reaching the application, so the button was dead exactly after the user had scrolled.
One tmux invocation orders the cancel strictly before the kills; both commands target the mirror **session**, like "Jump to bottom", since a task may pin no window.
The button is a no-op while nothing is connected.

One `^U` kills **one** line, so a multi-line draft kept its earlier lines; the button therefore sends `C-u` `TmuxMirrorCommands.CLEAR_INPUT_KILLS` times.
`^U` on an empty box is a no-op, so the surplus repeats cost nothing — unlike the two alternatives: `Esc Esc` opens Claude Code's *Rewind* dialog on an empty box, and `^C` is its interrupt.
No cursor homing is sent: Claude Code's `^U` kills the whole line regardless of cursor column (verified with the cursor mid-line), and `^E` is bound to something else there and swallows a following `^U`.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Ctrl+C in the terminal bar
`dsn~terminal-interrupt~1`

A "Ctrl+C" button right of "Clear input" calls `TerminalSession.interrupt`.
A tmux mirror sends `TmuxMirrorCommands.interruptCommand(tmux)` over the ssh side channel — `copy-mode -q`, for the reason "Clear input" gives, then one `C-c` to the mirror session — and puts the keyboard back into the terminal; an app-owned session writes ETX (`0x03`) straight into its pty.
The button is a no-op while nothing is connected.

Tags: windows, linux

Covers:
- req~terminal-interrupt~1

Needs: impl, utest

### Accept suggestion
`dsn~terminal-accept-suggestion~1`

An "Accept suggestion" button left of "Clear input" sends Claude Code's greyed-out prompt suggestion without the user reaching for the keyboard.
It sends `Tab` (which fills the suggestion into the input box) and, a second later, `Enter`, over the same ssh side channel "Clear input" uses and after the same `copy-mode -q`, then puts the keyboard back into the terminal.
The pause exists because an `Enter` arriving in the same burst lands as a newline in Claude's input box, the split the auto-continue and the queue send make too.
An app-owned session writes both keys straight into the pty with the same pause.
With no suggestion shown, `Tab` does nothing and `Enter` submits whatever draft is in the box.
The button is a no-op while nothing is connected.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Terminal auto-reconnect
`dsn~terminal-reconnect~2`

When the mirror's pty exits and the pane is still showing that connection (its generation is current — an intentional teardown bumps the generation, so a suspend or a reselection does not trigger this), `TerminalPane` reconnects on its own: it re-attaches the same remote/tmux under a new generation after an exponential backoff (1, 2, 4, … capped at 30 s), showing an `reconnecting (attempt n/N)…` message; the backoff sleep re-checks the generation, so selecting another task during the wait cancels the pending reconnect.
A connection that stayed up longer than a stability threshold resets the backoff, so a genuine later drop gets the full retry budget; consecutive short-lived or failed attempts climb the counter and, after `MAX_RECONNECT_ATTEMPTS`, give up with a "select the task again to reconnect" message.
The reconnect target (the `TmuxSession`'s window) tracks the window the user is actually viewing — the same-session fast path (`select-window` without a re-attach) updates it too, so resuming after sleep re-attaches the viewed window rather than the one from the last full attach.
So that a **silently dropped network** (not just a killed pty) triggers this path, the mirror's `ssh` (`TmuxMirrorCommands.attachCommand`) carries keepalive options (`ServerAliveInterval=5`, `ServerAliveCountMax=3`): a dead link makes ssh exit within ~15 s rather than hanging half-open with a frozen terminal, and `ConnectTimeout=10` keeps each retry attempt from stalling while the link is still down.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl

### Show the task's diff
`req~terminal-diff-view~2`

A "Show diff" button right of "Jump to bottom" shows what changed in the mirrored task's workspace as a scrollable view in the terminal: the uncommitted changes, or — when the worktree is clean because Claude committed for itself — the last commit.
The diff renders through the remote's own git pager, so a configured pager like `delta` provides syntax highlighting without the app knowing about it.

In a mainline-development flow the session commits and then deletes its own worktree, so by the time the user asks for the diff the workspace is a path that no longer exists.
The button still shows that task's change: the session publishes the commit it made, and the category names a permanent checkout the diff can be read from.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### A stranded mirror's windows are still there
`dsn~stranded-mirror-windows~1`

`TmuxDiscovery.parse` drops the lines of `cs-mirror-*` sessions, because a grouped mirror **shares** the base session's windows and every one of them would otherwise be listed — and imported — twice (`dsn~tmux-task-import~12`).
That holds only while the base session is alive. When it is gone, the mirror is the last session holding those windows, and dropping its lines makes live windows disappear from the listing entirely — whereupon `dsn~tmux-sync~7` reconciles every task pointing at one to *suspended*.

Field report 2026-09-12: eight tasks went suspended in a single round, seconds after the app had pasted a message into `@646` and pointed the mirror at `@644`.
`tmux list-sessions` on the remote showed `cs-mirror-old-group` with `session_group_size` 1 and eight windows, and no `old-group` — discovery reported "2 sessions, 6 windows" where the round before had seen 3 and 14.
The windows were alive the whole time; only the session that named them was gone.

So mirror lines are held back, not dropped, and a window **no non-mirror session reported** is added under the base session's name (the mirror prefix stripped — what the tasks carry, and what a re-grouping attach addresses).
A window the base session did report is still ignored on its mirror line, so nothing is duplicated and the import is unchanged.

The repair of `dsn~terminal-pane~14` covers the same state from the other side, and two things were wrong with it for *this* shape of it:
it moved the mirror's windows into the base session without checking the base session exists — `move-window` into a missing session fails, so with the base gone rather than recreated every window stayed put — and it then ran `tmux kill-session` on the mirror.
A session tmux has emptied is destroyed by tmux itself, so that kill could only ever fire **after the moves had failed**, and it would have taken the live Claude sessions the mirror was the last link to.
The repair now creates the base session when it is missing and never kills the mirror.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Which task is this terminal showing
`dsn~terminal-mirrored-task~1`

All tasks of one base session share a single mirror client, so the mirror's current window is the only thing that says which task is on screen (`dsn~terminal-pane~14`).
Every guard there is one-way — it refuses to *uncover* a window it could not confirm — and none of them lets the user ask the question the other way round: "the list says A, the terminal reads like B — which is it?"
The context menu of the Terminal pane's toolbar (right-click its title or anywhere on the band, `dsn~shell-layout~2`) answers it with **Select the mirrored task**: one `tmux display-message -p -t cs-mirror-<session> '#{window_id}'` over the ssh side channel (`TmuxMirrorCommands.currentWindowCommand`, the select's confirming half on its own), and `Main.selectWindowTask` moves the list to the task owning that window — resolved by remote + window id over the repository entries, the same match `dsn~terminal-diff-window~2` uses.
Landing on the task that was already selected *is* the answer "the terminal is not lying"; landing on another row names the task whose session is really on screen and repairs the disagreement in the same click, since the selection re-targets the mirror.

The window must come from the remote, never from what the pane was asked to show: that request is precisely the claim under suspicion, and repeating it would confirm the mismatch being checked.
So a round-trip that fails or prints nothing says *that* — unreachable host included (`stderr` is quoted) — instead of falling back to the requested window.
For the same reason there is nothing to answer while no pty is connected: a placeholder and a suspend snapshot (`dsn~terminal-suspend-snapshot~3`) are rendered from the selected task's own id, so the list already matches by construction and the item says so.
A window no task owns points at "Sync tmux windows…", which is what imports one.

In the header's context menu rather than in the button bar below: it is a diagnostic for a disagreement that the `~13` guards make rare, and a permanent button for it would suggest the terminal is routinely untrustworthy.

Tags: windows, linux

Covers:
- req~terminal-live-mirror~1

Needs: impl, utest

### Diff in a throwaway tmux window
`dsn~terminal-diff-window~2`

The button hands the mirrored remote + tmux config to `Main.showDiffWindow` (wired via `TerminalPane.setDiffOpener` — the pane knows windows, not tasks; a no-op while a placeholder shows, like the bar's other buttons).
`Main` resolves the window back to its task by matching remote + window id over the repository entries and takes the task's `claude.workspace`, falling back to `claude.cwd` (the IntelliJ action's resolution, `dsn~gateway-url-action~11`); no match or no `claude:` section raises an info alert naming the requirement instead of guessing a directory.
`TaskDiffWindow.open` then runs `tmux new-window` in the task's own session with a **command instead of a shell** — the window closes when the command ends, and tmux returns to the previously selected window by itself:
`if ! git rev-parse --git-dir >/dev/null 2>&1; then echo Not a git repository: $PWD; elif git diff --quiet HEAD 2>/dev/null; then git show; else git diff HEAD; fi; echo; echo Enter closes this window...; read -r _` — tracked uncommitted changes when there are any, else the last commit (`git show`); both page through git's configured pager (delta when set up, stock `less` otherwise).
The `rev-parse` guard turns a workspace that is no git worktree — e.g. a task whose recorded cwd is the workspaces root because the session never published a `@cs_workspace` — into one readable line instead of git's multi-screen `--no-index` usage dump (`~1` shipped without it and the dump was mistaken for a broken diff).
The trailing `read` keeps the window open after the pager quit: git's default `less -FRX` exits by itself when a short diff fits one screen, and the window must not flash away unread.
The command deliberately contains **no quotes of either kind**: single quotes would need escaping through the single-quoted transport, and double quotes are swallowed by Windows ssh.exe (the `dsn~message-queue-send~5` constraint — `~1`'s `printf "\n…"` arrived on the remote without its quotes and printed garbage); the unit test pins both invariants.
Like the scratch window (`dsn~category-scratch-window~2`), the new window is focused for plain tmux clients via `TmuxFocusAction.remoteCommand` and mirrored in the app's terminal with keyboard focus (`TerminalPane.show` + `focusTerminal`), so the pager's keys work right away; the task-list selection is untouched.
Untracked new files never show — `git diff` cannot; RefactoringMiner ([#50](https://github.com/contextswitcher/contextswitcher-private/issues/50)) remains the full answer for semantic change insight.

Tags: windows, linux

Covers:
- req~terminal-diff-view~2

Needs: impl, utest

### Diff after the worktree was removed
`dsn~diff-after-worktree-removal~1`

The throwaway window of `dsn~terminal-diff-window~2` opens **in** `claude.workspace`, which is fatal once Claude deleted that worktree: `tmux new-window -c <gone>` fails outright, so no window appears at all and the button reads as broken.
Two pieces fix it, and the fallback engages only when **both** are present — half the configuration must behave exactly as before.

*The commit.* A session publishes the commit it just made as the `@cs_commit` window user option, alongside the `@cs_workspace`/`@cs_status`/`@cs_pr` it already publishes (`tmux set -w -t "$TMUX_PANE" @cs_commit <sha>` — the same `-t "$TMUX_PANE"` targeting every other `@cs_*` option needs).
`TmuxDiscovery`'s list-windows format reads it via `#{@cs_commit}` (between `@cs_pr` and `#{host}`, so `#{window_name}` stays last — it is the field most likely to contain the `|` separator), and `TmuxSync.commitRefreshes` — shaped exactly like `workspaceRefreshes`, matching by window id, skipping `claude:`-less tasks, ignoring blank and unchanged values — reports it into `Task.ClaudeConfig.commit` via `TaskFileParser.withClaudeCommit`, counted in the sync summary as `commits recorded N`.
The sha is deliberately *not* derived by the app: `claude.workspace` is polled every 60 s, but a worktree that is committed and deleted within seconds would never be caught, and after the deletion there is nothing left to ask.
The published sha outlives the worktree because the commit itself survives in the repository.

*The place to read it.* A category's `CONTEXTSWITCHER.md` may name `mainCheckout:` — a permanent checkout of `repo`, not a per-task worktree (`Main.groupMainCheckout`, read fresh like `groupDesktop` so an edit needs no restart).
This cannot be derived either: in a worktree-per-task layout the primary clone is a sibling directory the app never sees, and a task file only ever records its own worktree.

With both known, `TaskDiffWindow.open` starts the window in `mainCheckout` (a directory that always exists) and runs `diffCommand`, which prefers the live worktree and falls back only when it is truly gone:
`if cd <workspace> 2>/dev/null && git rev-parse --git-dir >/dev/null 2>&1; then <the DIFF_COMMAND branches>; elif git rev-parse --git-dir >/dev/null 2>&1; then echo Worktree <workspace> is gone - showing commit <sha>; echo; git show <sha>; else echo Not a git repository: $PWD; fi; …`
The `cd` **is** the existence test — no extra ssh round-trip to probe the directory — and the announcement line says which of the two the user is looking at.
Same no-quotes constraint as `dsn~terminal-diff-window~2` (this command interpolates a path and a sha, so it is the likelier one to grow one); the unit test pins it.

Known ceiling: the sha is the last one the session published, so a task that lands several commits shows only the most recent.
A range would need a recorded base ref, which nothing publishes today.

Tags: windows, linux

Covers:
- req~terminal-diff-view~2

Needs: impl, utest

### Download a file the session generated
`req~terminal-download-generated~1`

A remote Claude session generates files the user wants on their own machine — a drafted mail, a report, a rendered chart.
The terminal offers the newest of them for download in one click, and the downloaded file lands in the user's download folder and opens with its registered application.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Generated files listed from the remote, not from the terminal
`dsn~generated-file-download~2`

A "Files" button right of "Show diff" opens a menu above itself: **"Custom path…"**, a separator, then the newest files under the remote user's Claude scratchpad directories, newest first.
Choosing any of them downloads the file to `~/Downloads` (the home directory where no such folder exists) and hands it to the OS via AWT `Desktop.open` — the `Main.openTasksDir` shape, not `HostServices.showDocument`, whose `file://` URI would land in the browser.

The list is asked of the remote and **never** read out of the terminal's text.
The obvious designs — a clickable-path hyperlink filter (jeditermfx's `addHyperlinkFilter`, which even joins terminal-wrapped lines for the filter) or copying the selection — both fail on the case that motivates the feature: Claude Code's TUI hard-wraps a long path itself, across lines, with its own gutter text between the halves (`…/3314d9aa-` / `[file] 89e4-431a-…`), so the path is not contiguous text in the buffer at all and no filter can recover it.
Asking the remote sidesteps the terminal entirely and costs one ssh round-trip.

`RemoteFiles.listCommand` is that round-trip: `find /tmp/claude-$(id -u)/*/*/scratchpad -maxdepth 2 -type f -printf '%T@\t%s\t%p\n' 2>/dev/null | sort -rn | head -n 20`.
Claude Code puts a session's working files in `/tmp/claude-<uid>/<encoded-cwd>/<session-uuid>/scratchpad` — the same cwd encoding `dsn~claude-session-capture~3` relies on — so the glob finds every session of that user without the app knowing a single session id.
Glob, `$(id -u)`, redirect and pipe are expanded by the **remote** shell (one call, the `ClaudeSessionLookup` pattern); `2>/dev/null` swallows the error a glob that matched nothing leaves; the format is single-quoted so `find`, not the shell, expands the `\t`/`\n`; and the command carries no double quote, which Windows `ssh.exe` would eat (`dsn~ssh-command-runner~6`).
An unparsable output line is skipped rather than failing the listing, and a path containing a single quote is dropped outright — it is the one character the download command's quoting cannot carry, so such a file has no business in a download menu.

The transfer is `base64 '<path>'` on the remote decoded locally: the mirror image of the attachment upload (`dsn~message-queue-send~5`), ASCII through the ssh channel, no quoting rule able to mangle the bytes.
Two guards, because the payload is held in memory as a string several times over: `RemoteFiles` runs on its own two-minute `SshCommandRunner` (the shared side channel's 10 s fits a tmux round-trip, not a megabyte over a slow link), and a file above 32 MB is refused with a message naming the limit instead of being attempted.
The local name is the last path segment with every character outside `[A-Za-z0-9._-]` replaced by `_` (the `dsn~terminal-suspend-snapshot~3` sanitising — the remote names the file, the local filesystem must not have to accept that name verbatim), and an existing file of that name is never overwritten: the download becomes `name-2`, `name-3`, ….

**"Custom path…"** is the escape hatch, and it heads the menu because the scratchpad scan is a shortcut, not the only way to name a file: a file written into the task's workspace, or into a `/tmp` path the session chose itself, has no entry.
It asks for a path, `RemoteFiles.cleanPath` makes it fit for the transport, and `RemoteFiles.statCommand` — `find '<path>' -maxdepth 0 -type f -printf …`, the *same* format so `parseList` reads it — resolves it on the remote before anything is transferred.
`find` rather than `stat` for one reason beyond the shared parser: `-type f` turns a directory or a missing path into no output at all, so an unusable answer needs no second check.
Resolving first means a typo is one alert (`No readable file X on Y.`) instead of a failed transfer, and the size limit applies to a typed path exactly as it does to a listed one.
`cleanPath` strips a leading `~/` and refuses a bare `~`: the path travels single-quoted, so the remote shell would never expand a tilde, and the ssh exec channel already starts in the remote home — `~/x` and the relative `x` are the same file.
A path containing a single quote is refused with the reason, the same character the listing drops.

The button is disabled for the listing round-trip so a slow remote does not read as a dead button, and is a no-op while nothing is connected, like the bar's other buttons.
A host with no scratchpad files still opens the menu — "Custom path…" is always useful — with one disabled line saying so; a failed download names the reason; a download the OS has no application for names the local path, because the file *did* arrive.

Known ceilings: the list spans **all** of that user's Claude sessions on the host, not only the mirrored task's — the app cannot map a tmux window to a session scratchpad — so a machine running many sessions shows other tasks' files too, distinguishable only by name and age; and the scratchpad path is Claude Code's own layout, so a future change there empties the list — visibly, as the disabled "no files" line, not silently wrong, and "Custom path…" remains as the way through.

Tags: windows, linux

Covers:
- req~terminal-download-generated~1

Needs: impl, utest

### Send a queued message into the chat
`req~message-queue-send~2`

Releasing a queued message types it into the task's remote tmux window (the Claude chat) and submits it, exactly as if the user had pasted it there — including multi-line text.
A pasted image or attached file referenced by the message is transferred to the remote machine first, and the message reaches the chat with the attachment's remote path, so the chat can open it locally.

Tags: windows, linux

Covers:
- feat~message-queue~1

Needs: dsn

### Message delivery via tmux paste
`dsn~message-queue-send~5`

`MessageSender` delivers one message in ssh steps that keep the payload out of every argv (arbitrary quotes and newlines survive, and remote commands stay free of double quotes — the Windows ssh.exe constraint): the text goes over **stdin** into `tmux load-buffer -b cs-queue -` (`SshCommandRunner.runWithInput`), then one chained tmux call pastes it **bracketed** (`paste-buffer -d -p -t <target>`, so a multi-line message lands in Claude's input box as one block) and submits it with a separate `Enter` after a server-side `run-shell 'sleep 1'` — an `Enter` in the same burst would be absorbed as a newline (the `ClaudeWindowLauncher` trick).
Before pasting, each `[image: …]` or `[file: …]` marker pointing into the local attachments dir (`Attachments.localRefs`) is uploaded base64-over-stdin (`base64 -d > '~/.contextswitcher/attachments/<name>'` — pure ASCII through any ssh stdin, content-agnostic; the target is single-quoted, so a file name carrying a space stays one redirect target instead of extra `base64` operands — double quotes are off limits per the Windows ssh.exe constraint) and rewritten to the attachment's absolute remote path in backticks (a Markdown code span, so a path whose name carries a space stays one token for the reading chat), resolved against the remote home (`pwd`, cached per host per run).
The target is `Task.TmuxConfig.target()` (window id `@17` when available); argv builders live in `QueueSendCommands`.
Uploads run on a runner of their own with a 5-minute timeout (`Main.UPLOAD_SSH_TIMEOUT`): the side channel's 10 s is sized for a tmux round-trip, and a batch of 36–56 MB jars timed out on the first file (field report 2026-09-13).
Files above `MessageSender.MAX_UPLOAD_BYTES` (64 MB) are refused without an upload, with the advice to fetch the file on the remote instead (`gh release download`, a URL, `scp`); 64 MB rather than 10 MB because the report that set it was exactly such a batch of release jars, and the bound also caps the file's bytes and base64 held in memory at once.
One attachment that cannot be uploaded — refused, unreadable, failed — does not stop the others or the message: its marker becomes `<name> (not attached)`, so the remote chat is never handed a local path it cannot read, and the outcome names every such file with its reason and says the files are kept in the local attachments dir; only a message with no text besides failed attachments is not sent (and stays queued).
The send reports `Uploading <i>/<n>: <name> …` per file and ends on an outcome line (`Sent with <n> attachments.` or `Sent without <k> of <n> attachments (kept in …): …`), shown in the queue pane's status line.
Uploads run **one at a time app-wide** (a static lock in `MessageSender`): each holds one of the six global ssh slots of `dsn~ssh-command-runner~6` for as long as it runs, and a batch in parallel would hold several for minutes and stall the mirror attach and the pollers behind them — serial, an upload batch costs at most one slot and simply takes longer.

Tags: windows, linux

Covers:
- req~message-queue-send~2

Needs: impl, utest

### Pick the chat's model and effort when sending
`req~claude-mode-select~2`

When creating a task that starts a fresh chat, the user can pick the model and the reasoning effort that chat should answer with, or leave both as they are.
The pick is visible in the dialog that creates the task, so it is chosen in the same gesture as the creation.
Messages sent into an existing chat carry no such pick — the model is switched inside the chat itself.

Tags: windows, linux

Covers:
- feat~message-queue~1

Needs: dsn

### Model and effort as slash commands ahead of the message
`dsn~claude-mode-select~3`

`ClaudeMode(model, effort)` — both nullable, `ClaudeMode.DEFAULT` for "leave the session as it is" — renders Claude's own `/model <alias>` and `/effort <level>` slash commands (`ClaudeMode.commands()`, empty for `DEFAULT`).
`MessageSender.send(remote, target, text, mode)` submits each of them as its **own** message ahead of the fresh session's first prompt, through the same load-buffer-and-paste round (`dsn~message-queue-send~5`): Claude runs one command per submission, and each paste already carries the delayed `Enter`, so the commands are naturally spaced.
A command's paste carries a **second** delayed `Enter` (`QueueSendCommands.pasteCommand(target, true)`): `/effort` answers a submitted level with a modal ("Change effort level?", `1. Yes` preselected) once the conversation is cached, and an unanswered modal swallows every later paste — the following command and the message itself vanish and the chat looks hung.
The extra `Enter` takes the preselected `Yes`; where no modal appears (`/model`, or `/effort` on a fresh session) it lands on an empty input box and does nothing.
The message itself is pasted **without** it — its reply may legitimately raise a prompt that is the user's to answer.
A failing command aborts before the message — sending it at the wrong model is worse than not sending it at all.
Before switching a fresh session, `ClaudeWindowLauncher` drops each half the session already starts with — the model when the remote's `~/.claude/settings.json` `model` (where `/model` saves the default) equals the pick, the effort when Claude's start banner (`Opus 5 with medium effort`) names it; the banner, not `effortLevel`, because `CLAUDE_EFFORT` and per-model `modelSettings` override that key — so a pick equal to the configured default costs no paste round and no TUI restart.
Unreadable settings drop nothing; a project settings file or `ANTHROPIC_MODEL` overriding the user default is not considered, so there a pick equal to the user default is wrongly skipped.
Both combos carry `Styles.SMALL`; AtlantaFX ships no `.combo-box.small`, so `main.css` adds it (mirroring its `.button.small` padding) — without it a combo towers over the small buttons beside it.

The offered arguments are the model **aliases** (`opus`, `opus[1m]`, `sonnet`, …, `default`, `best`), not full model ids, so a model release does not stale the list; efforts are `low`…`max`.

Both commands change the **session**, not one turn: Claude keeps the picked model and effort for every later message too — there is no per-message override. That is why only the *creating* dialogs offer the pick: an existing chat is switched inside the chat itself (`/model`), which the terminal is right there for.
Each sent half is remembered (`MainWindow.lastMode`/`rememberMode`, `java.util.prefs`; a null half never erases the other) and pre-selects the dialog's pickers, so the model the user keeps choosing is one Ctrl+Enter away.
The dialog deliberately does **not** use the reported `@cs_model` (`dsn~claude-mode-report~2`) for this: a task being created has no session to report one.

Two `QueuePane.modeBox` combos (first entry "as is" = null, read back by `QueuePane.modeValue`) carry the choice in every dialog that starts a chat: the `Add task…` dialog, where the pick reaches the fresh session through `ClaudeWindowLauncher.launch(…, mode)` — ahead of the context prompt — for both the live-task and the PR flow (`dsn~task-create-live~8`, `dsn~task-from-pr~6`), the category-from-URL dialog's setup session (`dsn~category-from-url~4`), and the remote-window choice play and the terminal placeholder share (`dsn~remote-window-choice~6`). `Add plain task` writes only a file, so the pick is ignored there.
A window started without asking ran whatever the remote CLI defaults to, which is the wrong default for a choice that "differs from task to task" (field report 2026-09-12).
Resuming an existing session (`dsn~start-claude-button~2`) asks nothing — that session already has a model and an effort, and the repair is about getting it back, not re-configuring it.

Tags: windows, linux

Covers:
- req~claude-mode-select~2

Needs: impl, utest

### The chat reports the model and effort it currently uses
`req~claude-mode-report~2`

The mirrored chat's model and effort are shown next to the terminal, so switching tasks tells the user what the session in front of them runs — and whether it needs switching inside the chat.

Tags: windows, linux

Covers:
- feat~message-queue~1

Needs: dsn

### Model and effort reported via `@cs_model`/`@cs_effort`
`dsn~claude-mode-report~2`

A session publishes its current model and effort as the tmux **window** options `@cs_model` and `@cs_effort`, like `@cs_status` and `@cs_workspace`.
The source is Claude's `statusLine` command, **not** a hook: no hook input carries the current model (`SessionStart` reports only the model the session started with, stale after a `/model`), while the `statusLine` input carries `.model.display_name` and `.effort.level` live and re-runs on every assistant message plus its `refreshInterval`. The remote-side script is documented in the README; taking over an existing status line is the user's call, so the app only ever *reads* the options.

`TmuxStatusPoller` reads both on the tick it already runs — two more `#{…}` fields in the same `list-windows` format (`statusCommand`), parsed by `parseModesInto` into `host windowId -> ClaudeMode` — rather than costing a poller of its own.
Nothing is inferred: a window publishing neither option gets no entry, and a line lacking the two fields (an older tmux format) is skipped instead of half-parsed.

The terminal pane looks the window it currently shows up in the map itself (`TmuxStatusPoller.key`) — the poll callback has no notion of the selection, and the pane already tracks its own.

`TerminalPane.showSessionModes` puts the mirrored window's `<model> · <effort>` as a muted label left of the `Jump to bottom` button (`dsn~terminal-jump-to-bottom~1`), where it answers "what is *this* chat running" at a glance; the label blanks the moment the mirror switches windows or shows a placeholder, so it never labels one task's terminal with another's model.

The options are a display hint only — everything works without them, and the label then stays empty.

Tags: windows, linux

Covers:
- req~claude-mode-report~2

Needs: impl, utest

### Last screen kept when a task ends
`req~terminal-suspend-snapshot~1`

Suspending or completing a task ends its tmux window, and with it everything the terminal showed.
Selecting such a task must still show the window's last screen, so the user can re-read what Claude said last without resuming the session and waiting for it to come up.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Pane snapshot taken before the kill
`dsn~terminal-suspend-snapshot~3`

`PaneSnapshots` captures the window's **visible screen in color** — `tmux capture-pane -e -p -t <target>`, no `-S` scrollback — over the ssh side channel and stores it as one file per task under `<configDir>/snapshots/`, named after the task id with every character outside `[A-Za-z0-9._-]` replaced by `_` (ids carry the group folder, e.g. `jabref/fix-npe`).
The `-e` keeps the SGR color escapes so the snapshot renders exactly as the mirror showed it (see below); the visible screen is deliberately all that is kept: it is what the mirror showed and what the user was looking at; scrollback is what resuming is for.
The capture runs inside `TmuxKillAction.run`, immediately **before** the kill — the only point where the window still exists and the teardown is already known to happen; it therefore covers both suspend and complete (`dsn~task-suspend~6`, `dsn~task-complete-suspend~1`) without a second trigger.
It is best effort: an unreachable remote, a window already gone, or an unwritable directory only logs and leaves any previous snapshot in place, because a failed capture must never fail the suspend it precedes.

`Main.previewTask` shows it in the two placeholder cases that mean "no live window" — a suspended task and a task whose `window:` is gone (`dsn~terminal-pane~14`) — via `TerminalPane.showSnapshot`.
Rather than parse the escapes itself, it renders them in the **same JediTermFX emulator the live mirror uses**, fed the stored string by a read-only `StringTtyConnector` (which replays the bytes and then ends the stream) instead of a pty — so the colors match the mirror exactly with no ANSI parser of our own, and the screen stays selectable and copyable (plain-drag select, `Ctrl+Shift+C`).
The connector replays the capture with **CRLF** line ends: `capture-pane` separates the screen's lines with a bare LF, which a terminal emulator reads as "one row down, same column" (the LNM mode adding the carriage return is off, as on a real terminal), so the screen rendered as a staircase — every line starting where the previous one ended, wrapping around, and an indented screen (Claude's output) overlapping itself into nonsense.
The snapshot widget is sized to the captured content (its longest escape-stripped line × its line count, floored at 80×1) so the lines do not re-wrap, and it is tracked as the pane's `widget` so the next selection's `disconnect()` tears it down like any live terminal.
That size is then **frozen** — the canvas pane's min and max are pinned to its preferred size — and the widget is put in a `ScrollPane` that does not fit its content to the viewport, so resizing the application window scrolls the snapshot instead of resizing the emulator.
A stretched-to-fill widget re-wrapped it: `capture-pane` writes each already-wrapped screen line as its own line, a line that filled the last column leaves the emulator's wrap flag set, and widening the terminal merges such a line with its successor and re-splits elsewhere — the stored screen turned into ragged nonsense on every window resize.
The same explanation label as before sits above it; a task without a stored snapshot keeps the plain placeholder.

Tags: windows, linux

Covers:
- req~terminal-suspend-snapshot~1

Needs: impl, utest
