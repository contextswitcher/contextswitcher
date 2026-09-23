# Context Switching

Related idea issues: [#18 Switch context](https://github.com/contextswitcher/contextswitcher-private/issues/18), [#22 Focus without closing](https://github.com/contextswitcher/contextswitcher-private/issues/22).
v0.1 implements the *focusing* flavor of switching ([#22](https://github.com/contextswitcher/contextswitcher-private/issues/22)): nothing is closed; the task's targets are focused or opened.

Design decisions: [0003 system ssh](../decisions/0003-system-ssh-for-remote-access.md), [0006 JetBrains Gateway URL](../decisions/0006-intellij-remote-via-jetbrains-gateway-url.md), [0008 local Windows Terminal focus via UI Automation](../decisions/0008-local-windows-terminal-focus-via-ui-automation.md), [0009 suspend ends the task's live context](../decisions/0009-suspend-ends-live-context.md), [0019 direct virtual-desktop switch with hotkey-walk fallback](../decisions/0019-direct-virtual-desktop-switch-with-hotkey-walk-fallback.md) (superseding [0013 virtual desktop focus via keyboard cycle](../decisions/0013-virtual-desktop-focus-via-keyboard-cycle.md)).

## Requirements

### Switch runs only configured actions
`req~switch-runs-configured-actions~1`

Activating a task runs exactly the actions for which the task file contains configuration; missing sections are skipped silently. Actions are independent: one failing action does not prevent the others.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Focus remote tmux window
`req~focus-remote-tmux-window~1`

Activating a task with a `tmux` section focuses the configured window of the configured session on the task's remote host, so that an attached terminal (e.g. showing an AI coding session) displays it.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Focus local Windows Terminal tab
`req~focus-local-terminal~1`

A task's session may run in a local Windows Terminal tab instead of a remote tmux window. Activating a task with a `terminal` section brings that tab (identified by its fixed tab title) to the foreground, across multiple terminal windows and — where possible — virtual desktops.

Tags: windows

Covers:
- feat~task-context-switching~1
- feat~remote-development-context~1

Needs: dsn

### Focus local folders in Explorer
`req~focus-local-folder~2`

A task can name local `folders` — a project checkout, its notes directory, a downloads folder.
Activating the task brings, for each of them, a File Explorer window already showing that folder to the foreground rather than opening another; with none open, a fresh Explorer window is opened at the folder.
The single-directory `folder` scalar stays valid as the one-entry form.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Category defaults for the switch actions
`req~category-action-defaults~1`

A category (project group) can configure `intellij`, `browser`, and `folders` in its `CONTEXTSWITCHER.md`, as the default for every task in it: switching any task of the category opens the project's IDE, pages, and directories without each task file repeating them.
A task that carries such a section of its own overrides the category's — per section, so overriding one of the three leaves the others inherited.
The category's browser URLs are project-wide, so suspending a single task does not close them (only its own tabs).
This mirrors the note fallback (`req~open-task-note~1`), which already resolves a task's note from its category.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Open the task's note
`req~open-task-note~1`

A task can carry a `note:` link to its page in a note tool (a OneNote `onenote:` desktop link or any URL).
Activating the task opens that note, so switching brings the project's notes forward alongside the terminal, IDE, and browser targets.
When the task has no `note:` of its own, its category's note is used — the group's `CONTEXTSWITCHER.md` `note:` — so every task in a project can share one page without repeating it.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Open the task's chat room
`req~open-task-chat~2`

A task can carry a `chat:` link to the Matrix room the work is discussed in — a `matrix.to` permalink as copied from Element's "Share room/message".
Activating the task opens that room in the Element desktop app, so switching brings the conversation forward alongside the terminal, IDE, and browser targets.
When the task has no `chat:` of its own, its category's chat is used — the group's `CONTEXTSWITCHER.md` `chat:` — because a project normally has one room and only some tasks a thread of their own.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Open project in remote IntelliJ
`req~open-remote-intellij-project~1`

Activating a task with an `intellij` section opens the configured project path on the remote host via JetBrains Remote Development. Accepted v0.1 limitation: an already-open project may be opened in a new window instead of focused.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Suspend ends the task's live context
`req~task-suspend-resume~2`

Suspending a task ends its live context instead of only relabeling it (MADR 0009): the task's tmux window on the remote host is ended — killing the processes inside; the Claude session's identity survives in the task's `claude:` section. Suspend runs without a confirmation in the normal case (the Claude session is recoverable); an explicit confirmation is required exactly where something would be lost — Claude is still working, or the task has no recorded Claude session to resume. Resuming a suspended task re-establishes the context like a regular switch: the tmux window is recreated and Claude resumed. Once the Firefox extension exists, suspending also closes the task's browser tabs and resuming reopens `browser.urls` (implemented with the extension server work).

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Per-action feedback
`req~per-action-feedback~1`

The user sees, per action of the activated task, whether it is pending, running, succeeded, or failed — including the failure detail (e.g. ssh stderr) — without consulting log files.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Focus a category's virtual desktop
`req~category-desktop-focus~2`

A category (project group) can name the virtual desktop it lives on in its `CONTEXTSWITCHER.md` (`desktop:`).
The category header then shows a play button — in the same place as a task row's play button — that switches the OS to that virtual desktop and does nothing else (no task switch, no other actions).
A category that names no desktop uses the fallback desktop (`req~fallback-desktop~1`), so the button is there for it too; it is missing only when the fallback is turned off (revision `~2`; `~1` showed the button only for a category naming a desktop).

Tags: windows

Covers:
- feat~task-context-switching~1

Needs: dsn

### Fallback desktop
`req~fallback-desktop~1`

Not every category names a virtual desktop, and a named one may not exist on the machine at hand — a task directory is shared between machines, but the desktops are not.
Both cases land on a configurable **fallback desktop** (`fallbackDesktop` in `settings.yaml`, default `misc`) instead of nowhere: the category's desktop button jumps there, and a link that has to be opened opens there.
An empty `fallbackDesktop` turns the fallback off, restoring the earlier behaviour (no button, and a missing desktop is reported as an error).

Tags: windows

Covers:
- feat~task-context-switching~1

Needs: dsn

### Open a category's folders from its header
`req~category-folders-button~1`

A category that names local `folders:` shows a folder button on its header, next to the desktop button, which focuses-or-opens exactly those folders and does nothing else (no task switch).
It reaches the project's directories without going through one of its tasks — and without a task that would have to repeat the same list.
The button appears only for a category that names folders, and reports the outcome in the status bar.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Open a task's link on its category's desktop
`req~pr-open-on-category-desktop~2`

Clicking one of a task's links — a PR icon, a plain link icon, or a queued qodo-review link — shows the page on the virtual desktop of the task's category, when the category names one.
A tab that already exists is brought to that desktop rather than raised where it happens to live: raising it takes the whole screen to the desktop of the browser window holding it, which drops the user out of the category they are working in.
If that desktop has no browser window, one is opened there.

Tags: windows

Covers:
- feat~task-context-switching~1

Needs: dsn

### Open a scratch tmux window from a category
`req~category-scratch-window~1`

The terminal icon on a remote category's header opens a new, empty tmux window on that category's host and focuses it.
The window appears in the app's terminal, with the keyboard, so the user can type in it right away.
It is meant for quick throwaway work: no task file is created and no Claude is started.
A window the user decides to keep is turned into a task afterwards through "Sync tmux windows…".

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

## Design

### ssh command runner
`dsn~ssh-command-runner~6`

`SshCommandRunner` invokes the system `ssh` executable via `ProcessBuilder` with the task's host alias and remote command, applies a timeout, captures exit code and stderr, and logs every invocation with full argv, exit code, and stderr.
On Windows, the built-in OpenSSH client (`%SystemRoot%\System32\OpenSSH\ssh.exe`) is addressed by absolute path when installed: a PATH-resolved `ssh` may be an MSYS build (Git for Windows, Cygwin) whose runtime re-parses the argv with POSIX quoting rules and strips the single quotes protecting remote arguments — a `tmux -F '#{…}'` format then reaches the remote shell unquoted and `#` comments it away (exit 0, default-format output).
The resolved executable is exposed (`sshExecutable()`) and used by every locally built ssh argv, including the terminal mirror's pty attach (`TmuxMirrorCommands.attachCommand`) — an MSYS ssh there eats the `\;` escaping and its output stalls under a ConPTY, freezing the mirror on a stale frame while side-channel `select-window` calls succeed.
`runWithInput` additionally pipes a payload to the remote command's stdin (dropping ssh's `-n`), written on a worker thread so a payload larger than the pipe buffer cannot deadlock against the undrained output streams — the channel for message text and base64 image data that must not appear in an argv.
At most six `ssh` processes run at once across all runner instances (a static fair semaphore around the process, `MAX_CONCURRENT`): sshd's default `MaxStartups 10:30:100` refuses connections from the tenth unauthenticated one on, and a burst of local `ssh.exe` starts — each a key exchange — starves the FX thread; the bound is the safety net, callers needing many answers batch them into one call (`dsn~claude-pr-refresh~5`).
A runner with a long timeout still holds its slot for that long, so a caller with long-running commands keeps its own concurrency to one: attachment uploads run serially (`dsn~message-queue-send~5`), holding at most one slot however many files a message carries.

Tags: windows, linux

Covers:
- req~focus-remote-tmux-window~1

Needs: impl, utest

### tmux focus action
`dsn~tmux-focus-action~3`

`TmuxFocusAction` runs `tmux select-window -t <session>:<window>` on the task's host via the ssh command runner, then switches every client **not** attached to a mirror session (`cs-mirror-*`) to the task's session via `switch-client -c <client> -t <session>` (one ssh invocation, `&&`-chained shell pipeline over `list-clients`).
A bare `switch-client -t <session>` from a detached ssh lets tmux pick the *most recently active* client — frequently the app's own terminal-mirror pty, which then gets yanked onto the base session and silently stops tracking the mirror session's window selection.
The select target is always session-qualified: a bare window id (`@17`) is ambiguous once the window is linked into a grouped mirror session too.
For a window-less config, `has-session` replaces the `select-window` so a dead server still fails the chain (the resurrect trigger); the client loop itself is best-effort and exits 0 when no eligible client is attached.

Tags: windows, linux

Covers:
- req~focus-remote-tmux-window~1

Needs: impl, utest

### Local Windows Terminal focus action
`dsn~local-terminal-focus~5`

`LocalTerminalAction` focuses the task's `terminal.tabTitle`. `WindowsTerminalFocus` builds a PowerShell UI Automation script (find the `CASCADIA_HOSTING_WINDOW_CLASS` window whose `TabItem` name equals the title, `SelectionItemPattern.Select` it, restore + Alt-tap + `SetForegroundWindow`/`BringWindowToTop`) and runs it as a base64 `-EncodedCommand` via `LocalCommandRunner` (a local child-process runner mirroring the ssh runner). The title is embedded as a single-quoted PowerShell literal with `'` doubled; exit code 0 = focused, 2 = no such tab.
After the foreground calls, `SwitchToThisWindow` is issued as well — `SetForegroundWindow` alone does not make Windows follow a window parked on another virtual desktop; `SwitchToThisWindow` does.
Windows parked on another virtual desktop are **cloaked** (`DWM_CLOAKED_SHELL`): absent from the UIA root, `Process.MainWindowHandle` skips them, and UIA cannot descend into their subtree — but `EnumWindows` does list them.
When the desktop-local sweep finds no tab, the script enumerates `CASCADIA_HOSTING_WINDOW_CLASS` windows whose **window title equals the tab title** — the title shows the active tab, so a match needs no tab selection — and jumps there (`SwitchToThisWindow` switches to the window's virtual desktop; verified live).
Accepted limitation: a non-active tab inside a window on another desktop stays unreachable — cloaked windows expose no tab list.
See MADR 0008.

`LocalCommandRunner` does not attempt a **Windows-only program** (`powershell`, `rundll32`, `explorer`, `wt`) off Windows at all: it returns the failure `<program> is Windows-only` straight away.
The integrations driving them are Windows-only by design, and one of them — the active-desktop read (`dsn~active-desktop-filter~6`) — polls, so a spawn attempt per tick filled the log with `Cannot run program "powershell"` while telling the user nothing.
The persistent active-desktop watcher, which spawns its process itself, checks the OS for the same reason.

Tags: windows

Covers:
- req~focus-local-terminal~1

Needs: impl, utest

### Explorer folder focus action
`dsn~explorer-folder-focus~3`

`ExplorerFolderAction` (chip `folder`) focuses each of the task's `folders`, in file order — so the last one ends up in front — and is configured exactly when the list is non-empty.
A folder that fails does not stop the rest: the chip fails naming the folders that did not open (`n of m failed: …`), and a single-folder task keeps the plain wording of the one result.
`TaskFileParser.parseFolders` reads the list from `folders`, accepting a `folder` scalar as the one-entry form (what a local category's generated task file writes, `dsn~group-config-apply~2`) and dropping blank entries; the same pair of keys is read from a category's `CONTEXTSWITCHER.md` (`dsn~category-action-defaults~1`).
`LocalFolderFocus` handles one folder per call: it builds a PowerShell script that enumerates open File Explorer windows via the `Shell.Application` COM object (`.Windows()`) and matches each window's `Document.Folder.Self.Path` against the target (case-insensitive, trailing separators trimmed); a match is brought forward by its `HWND` (restore + Alt-tap + `SetForegroundWindow` + `SwitchToThisWindow`, the same cross-desktop foreground dance as the terminal action). With no window showing the folder, `explorer.exe <folder>` opens a fresh one. The script runs as a base64 `-EncodedCommand` via `LocalCommandRunner`; the folder is a single-quoted PowerShell literal with `'` doubled. Exit code 0 = focused or opened, 2 = the folder does not exist.

Off Windows there is no `powershell` and no window enumeration: the folder goes to `java.awt.Desktop.open`, i.e. to whatever file manager the desktop registered (`xdg-open` under GNOME/KDE), so the `folder` chip works there instead of reporting `explorer is Windows-only`.
The file manager decides whether it reuses a window it already has for the folder — the focus half of the Windows behavior has no portable equivalent and is not attempted.
The existence check is our own, so a typo in a `folders:` entry reads `folder not found: "…"` on every platform rather than an implementation-specific `IOException`; a desktop with no `Desktop.Action.OPEN` (a bare X session, a headless test) fails with `no file manager on this desktop`.

Tags: windows, linux

Covers:
- req~focus-local-folder~2

Needs: impl, utest

### Category action defaults
`dsn~category-action-defaults~1`

`GroupConfig` gains the category's switch-action defaults — `intellij`, `browser`, `folders` — parsed by `parseGroupConfig` from the same keys, with the same parsers, a task file uses.
They are parsed leniently like the rest of the category config: a half-edited `intellij`/`browser` section yields no default instead of turning the file into an error row.

`Task.withCategoryDefaults(GroupConfig)` folds them in, per section: `intellij`/`browser` fall back when the task's own is null, `folders` when the task's list is empty, and the task is returned unchanged when nothing applies.
The fallback is deliberately per section, not per key — a task overriding one URL would otherwise have to restate the category's IDE and folders as well.
A bare `intellij:` on the task therefore keeps its own meaning (`dsn~task-file-parsing~4`: project path from `claude.workspace`/`cwd`), rather than inheriting the category's `projectPath`.

`Main.switchTo` is the single place that merges: it reads the task's category `CONTEXTSWITCHER.md` fresh from disk (like `groupDesktop`, so an edited default needs no restart) and hands the merged task to the orchestrator — which makes `configuredActions` count the inherited sections too, so their chips appear.
The suspend path (`Main.suspendTo`) does **not** merge: a category's browser URLs are shared by every task in it, so ending one task must not close the project's pages.
`note` is not merged either — `NoteFocusAction` resolves the same category fallback itself (`dsn~note-focus-action~1`) because its chip reports which of the two it opened.

Tags: windows, linux

Covers:
- req~category-action-defaults~1

Needs: impl, utest

### Note focus action
`dsn~note-focus-action~1`

`NoteFocusAction` (chip `note`) opens the task's effective note on switch.
The effective note is the task's own `note:` field; when it is absent, the action falls back to the note of the task's category — the group's `CONTEXTSWITCHER.md` `note:`, resolved through an injected group-note resolver keyed by the task's group (its subfolder path, `""` for a root-level task, which has no category note).
The URL is launched via the injected opener (`Main::openUrl`: `java.awt.Desktop.browse` → ShellExecute/xdg-open, the same OS protocol handler the group-note icon and the Gateway action use), so an `onenote:` link opens the OneNote desktop app rather than the browser; the injected opener keeps the action free of AWT/JavaFX and unit-testable.
The chip detail records whether the note came from the task or its category; the action counts as configured exactly when an effective note resolves.

Tags: windows, linux

Covers:
- req~open-task-note~1

Needs: impl, utest

### Chat focus action
`dsn~chat-focus-action~2`

`ChatFocusAction` (chip `chat`) opens the task's effective chat link on switch.
The effective link is the task's own `chat:`; when it is absent, the action falls back to the chat of the task's category — the group's `CONTEXTSWITCHER.md` `chat:`, resolved through an injected group-chat resolver keyed by the task's group (`""` for a root-level task, which has no category chat), exactly like `dsn~note-focus-action~1`.
`Main.groupLink` is that resolver for both actions: it reads the category file fresh and applies the key's extractor (`TaskFileParser.groupNote`/`groupChat`, two thin calls on one `groupString`), so an edited category link takes effect without a restart.
The chip detail records whether the link came from the task or its category; the action counts as configured exactly when an effective link resolves.
A `https://matrix.to/#/<target>` permalink is rewritten to `element://#/room/<target>` — `element://#/user/<target>` when the target's sigil is `@`, because Element's routes name the kind matrix.to leaves to the sigil.
The rewrite is what makes the link land in the Element desktop app: the `https://matrix.to/…` form would go to the default browser, and `element:` is the protocol Element registers on Windows.
Any other URL is passed through verbatim, so the key equally takes a ready-made `element:` link or a chat tool with its own protocol handler.
The URL is launched via the injected opener (`Main::openUrl`: `java.awt.Desktop.browse` → ShellExecute/xdg-open, the same handler the note and Gateway actions use); the injection keeps the action free of AWT/JavaFX and unit-testable.

Tags: windows, linux

Covers:
- req~open-task-chat~2

Needs: impl, utest

### Category folder button
`dsn~category-folders-button~1`

The group header renders an always-visible "Open folders" icon button (MDI `folder-open-outline`, id `group-folders-button`), right of the desktop button, exactly when the category's `folders:` (`dsn~category-action-defaults~1`) is non-empty — like its two neighbours it states a property of the category, so it reads next to the name rather than in the hover strip.
Its tooltip names the folder, or the count and the whole list when there are several; the list rides on `TaskListCell.GroupHeader.folders`, which `rebuildRows` fills from the `GroupConfig` it already parses per header.

The click resolves the list **fresh** from disk (`MainWindow.openGroupFolders` flushes the editor first, like `focusDesktop` — the button takes no focus, so a just-edited `folders:` would otherwise open the stale one) and hands it to `Main.reportOpenFolders` on the action executor.
That calls `ExplorerFolderAction.open(List)` — the switch action's own body, extracted so the header button and a task switch open folders identically, including the per-folder failure handling — and reports `Folders: …` or `Cannot open folders: …` in the status bar.
The button greys out for the round-trip and is re-enabled from the settle callback whatever the outcome (the async single-shot button convention in `CLAUDE.md`), because Explorer costs one local PowerShell call per folder.
An empty list settles the button without running anything: the config may have lost its `folders:` between render and click.

Tags: windows, linux

Covers:
- req~category-folders-button~1

Needs: impl, utest

### Category virtual-desktop focus action
`dsn~category-desktop-focus~4`

A category's `CONTEXTSWITCHER.md` gains an optional `desktop:` key (`GroupConfig.desktop`, parsed by `parseGroupConfig`).
The group header renders an always-visible "Jump to desktop" icon button (MDI `monitor-multiple`) only when the category names a desktop.
It is right-aligned, after the header's tags and before the note and repository icons, since like them it leads away from the list (revision `~4`, Carl, 2026-09-16; `~2` had it next to the name, right of the terminal icon; `~1` as a hover play button in the task rows' play slot).
Clicking it resolves the name fresh from disk (flushing any pending config edit, like the note button) and hands it to `WindowsVirtualDesktopFocus`, reporting the outcome as a one-off status-bar message. It runs nothing else — no task switch, no orchestrator chips.
`WindowsVirtualDesktopFocus` switches Windows to the named virtual desktop through `powershell` (base64 `-EncodedCommand` like the Explorer/Terminal actions): Windows exposes no supported API to activate a desktop by name, so the script reads the desktop order from `HKCU:\…\Explorer\VirtualDesktops\VirtualDesktopIDs` (a packed array of 16-byte GUIDs), the active desktop from `CurrentVirtualDesktop`, and each renamed desktop's label from `…\Desktops\{GUID}\Name`; it finds the target index and GUID by name (case-insensitive) and the current index.
The active desktop's GUID is read by the shared `Get-CurrentBytes` helper (`dsn~active-desktop-filter~6`), which knows both places Explorer keeps it — next to `VirtualDesktopIDs` on Windows 11, under `…\Explorer\SessionInfo\{session}\VirtualDesktops` on Windows 10 — so the walk starts from the desktop actually in view instead of stepping fully left first.
It then switches in one of two ways (revision `~3`; `~2` always walked).
First it tries the **direct jump**: `IVirtualDesktopManagerInternal::SwitchDesktop`, the interface Task View itself uses, obtained by asking the immersive shell's `IServiceProvider` (CLSID `C2F03A33-…`) for service `C5E0CDCA-…`, then `FindDesktop(targetGuid)`.
That interface is undocumented and Microsoft rebuilds its vtable — and with it its IID — across Windows builds, so the script declares only the current layout (IID `53F5CA0B-…`, Windows 11 24H2, slots through `FindDesktop`); a build whose IID differs fails `QueryService` with `E_NOINTERFACE` rather than dispatching to a wrong slot, which is what makes the fallback safe.
The **hotkey walk** is that fallback and the previous behaviour: inject the built-in `Ctrl+Win+Left/Right` hotkey the signed index difference of times via `keybd_event` — a global hotkey the shell honors whatever window is in front, so no foreground dance is needed.
Exit 0 = switched, and stdout says which route ran (`focused-direct:` / `focused-walk:`) so a moved interface shows up as a status-bar `(hotkey walk)` suffix instead of only as a slower animation; exit 2 = the name matches no desktop.
The name is a single-quoted PowerShell literal with `'` doubled. Windows-only (a Linux/GNOME `wmctrl -s`/`gdbus` analog is future work); an unavailable `powershell` or a Windows too old to name desktops fails the button with a logged status message. See MADR 0019 (superseding MADR 0013).

Tags: windows

Covers:
- req~category-desktop-focus~2

Needs: impl, utest

### Fallback desktop resolution
`dsn~fallback-desktop~1`

`AppSettings.fallbackDesktop` (default `misc`, blank = off) names the desktop both unresolved cases fall back to; it is a `SettingsCatalog` option like every other key, and `Main` refreshes it on a settings save.
The two cases are resolved in the two places they can be seen.
A category that names no `desktop:` is resolved at read time: `MainWindow.groupDesktop` and `Main.groupDesktop` return the fallback instead of null, so the header button shows for every category, the active-desktop filter (`dsn~active-desktop-filter~6`) shows such a category while the fallback desktop is the active one (it matches the fallback name directly, keeping the category nameless for that filter's "without a desktop" sub-option), and a PR/link click lands there (`dsn~pr-open-on-category-desktop~3`).
A `desktop:` naming a desktop that does not exist can only be seen when the switch runs, so it is resolved there: `WindowsVirtualDesktopFocus` takes the fallback name and, on the script's exit 2, re-runs it once for the fallback; the status bar then reports the fallback switch with the missed name appended, and a fallback that does not exist either fails naming both.
The root group (an ungrouped task) has no category and keeps resolving to null — there is nothing to fall back *for*.

Tags: windows

Covers:
- req~fallback-desktop~1

Needs: impl, utest

### Tmux window ownership check
`dsn~tmux-window-ownership~4`

A task's `tmux.window:` is written once and never re-verified, so it can end up naming a window that belongs to a *different* task: `TmuxResurrect` creates a second window for a session whose first one is still alive, a task file copied as the start of a new task carries the old id along, and `TmuxSync.sessionIdBackfills` only ever *fills* an absent `claude.sessionId` — it never corrects one.
The tmux target cannot catch this. Window ids are server-global, and `session:window` does not narrow them: sessions in a **group** — the terminal mirror's `cs-mirror-<name>` (`dsn~terminal-pane~14`) or any session made with `new-session -t` — share their entire window set, so `0:@53` and `old-group:@53` name the same window and both `select-window` calls succeed (field report 2026-07-29: two tasks with different `claude.sessionId`s both opened `@53`).
Focusing then drops the user into another task's Claude session while every chip reports success — worse than a visible failure.

`TmuxWindowOwnership` settles it from the `@cs_session_id` window option Claude Code's `SessionStart` hook publishes (the same one `TmuxDiscovery` reads): `hijacked(expected, published)` is true only when the window published a **non-blank** id that differs from the task's.
A window that publishes nothing (hook never ran, plain shell) is never called stolen — no evidence is not evidence, and treating it as a theft would resurrect every pre-hook window.

The check is only as honest as that publication, and one setup mistake makes it lie.
The `SessionStart` hook must be guarded with `[ -n "$TMUX_PANE" ]` (README, remote setup): a Claude Code started **outside** tmux leaves the variable empty, and `tmux set -w -t ""` is not a no-op — tmux resolves the empty target to the current window of the current session, which is normally the terminal mirror's `cs-mirror-<name>` and therefore the window the pane last selected.
The stray session then stamps its own id onto the task on screen, `hijacked` reports a theft that never happened, and the switch resurrects a second window for a task whose session is still running in the first (field report 2026-09-09: `@455` "Port koppor PR 629" stamped by an unrelated session 4 minutes before the switch, duplicated into `@470`, two Claude processes on one conversation).
Nothing app-side can distinguish that stamp from a real one — the id is the only evidence there is — so the guard belongs in the hook.

A belt for the remotes that still carry an unguarded hook: `foreignOwner` reads `#{window_name}` alongside the id in the **same** round-trip (first `|` splits; a task title may contain one, a session id may not), and a window still named after the task is that task's window whatever it publishes.
Only `TmuxFocusAction` passes the title.
`TmuxKillAction` passes `null` and keeps the id as its sole evidence: a task file copied to start a new task carries the old title along with the old window id, and on the one path whose mistake cannot be taken back that copy would be allowed to end the original's window.
The mirror and the delete dialog read the poll's map rather than this method and are unchanged — their false positive is a placeholder and an unticked checkbox, both of which the user can see and act on.

`TmuxSync.liveWindow` applies the same belt, and the sync then **corrects** the recorded id.
Without it the two halves disagreed once per poll: the switch's belt accepted the window and reactivated the task, the sync's id-only comparison called the very same window stolen and suspended it again, so the row flapped and the status bar kept asking to press play on a task that was running (field report 2026-09-18: `@747`, task `2026-09-08-librarytab-retention-check`, whose Claude session had taken a new id — a `/clear`, a fork or a resume — inside the window it was already in).
A stale id is not only a flapping row: everything that compares against it goes wrong as long as it stands — the mirror shows a placeholder instead of the pane, the delete dialog unticks its window, resume would start the older conversation.
So `TmuxSync.sessionIdBackfills` no longer skips a task that *has* a `sessionId`; it writes the published one whenever it differs, which only a window the belt kept can produce (an id-only mismatch still makes the window a stranger's, and a stranger's id is never written anywhere).
Ceiling: a task file copied to start a new task carries both the old title and the old window id, so the belt calls the original's window its own and the copy adopts the original's session id; the copy's next switch then resumes the original's conversation instead of resurrecting a fresh one.
The kill path stays id-only and is unaffected — the copy still cannot end the original's window.

`TmuxFocusAction.run` asks the remote itself (`tmux display-message -p -t '@53' '#{@cs_session_id}'`, one round-trip, only for a task that has both an `@`-id and a `claude.sessionId`; a failed round-trip stays silent and lets the `select-window` below report the real problem).
On a mismatch it takes the resurrect path (`dsn~tmux-resurrect~7`) rather than failing: the task gets its own window with its own session `--resume`d, and the `ResurrectListener` rewrites the stale `window:`, so the collision repairs itself on the first switch instead of needing a hand-edited task file.
The chip says `@53 belonged to another session; resurrected as @260 (claude --resume sent)`.

`TmuxKillAction` asks the same question before ending anything (suspend, complete, and the delete dialog's `endWindow`, which runs the suspend orchestrator).
A wrong kill is the one outcome on any of these paths that cannot be taken back — a wrong *focus* only confuses, a wrong `kill-window` ends a session someone is working in — so a foreign window is left alone and the chip says `@53 now hosts Claude session <id> — not ending another task's window`.
The `PaneSnapshots.capture` that normally runs just before the kill is skipped with it: capturing that pane would file another task's screen as this task's last screen.
Nothing is lost by refusing — `MainWindow.tearDown` has already dropped the task's `window:` before dispatching, so the task resurrects into a window of its own on the next switch instead of pointing at the foreign one again.

The delete dialog (`dsn~claude-session-kill~6`) reflects that instead of promising it: with a foreign owner, "End the tmux window" is unticked, disabled and renamed to name the other session, and the `KILL_END_WINDOW` preference is **not** written back — that "no" is the app's, not the user's, and persisting it would stop ending windows on every later delete. "Close browser tabs" is unrelated to tmux ownership and keeps its own tick regardless.

The terminal mirror needs the same guard — mirroring the recorded window would show that other task's terminal, the same failure `Main.previewTask` already blocks for a *missing* window — but it runs on the FX thread and must not do a round-trip.
It reads the id from the status poll instead: `TmuxStatusPoller` carries `#{@cs_session_id}` as a sixth field on the `list-windows` it already runs per host per tick, `Main` stashes the `host windowId -> session id` map in the volatile `liveSessionIds`, and `previewTask` does a map lookup.
A window the poll has not seen yet is absent from the map, which is a lookup miss and therefore again "no evidence"; on a mismatch the pane shows a placeholder naming the other session (with the last captured screen when there is one, `dsn~terminal-suspend-snapshot~3`).


A published id counts only while it is **unique**. A Claude session whose `$TMUX_PANE` is empty stamps `@cs_session_id` onto the session's *current* window rather than its own, so a second window ends up claiming an id that belongs elsewhere — and an ownership check that believes it accuses the wrong window.
`Main.uniqueSessionIds` therefore drops every id more than one window publishes before the poll's map reaches the check, logging which; the import path has refused duplicates from the start for the same reason ("no id beats a wrong one", `enrichClaudeSessions`), and only the status poll still trusted them.
Field report 2026-09-12: `@687` (Review PR 17110, whose task records exactly that id and that window) and `@693` both published `f2d88857-…`; the mirror refused `@693` with "now hosts another Claude session" for the task whose own window it was, play appeared to fail, and the reconcile suspended the task seconds later.
With the id dropped neither window is accused, the pane mirrors again, and the stray stamp stays what it is — the hook's bug, guarded separately.
Tags: windows, linux

Covers:
- req~focus-remote-tmux-window~1

Needs: impl, utest

### Link opening on the category's desktop
`dsn~pr-open-on-category-desktop~3`

A PR or link icon click carries its task along (`TaskListCell`'s focus-URL callback takes `(Task, UrlEntry)`), so `MainWindow.focusPr` can resolve the task's category — the folder part of its id — to that category's `desktop:` (`groupDesktop`, read fresh from disk like the header's desktop button) and hand it to `Main.reportFocusUrl` next to the URL; a root-level task or a category without a `desktop:` passes null and the plain focus-or-open runs as before.
With a desktop, `Main.reportFocusUrl` asks nothing first: the whole click goes through `Main.openOnDesktop`, so the desktop switch happens *before* the extension is spoken to. A focus-only probe used to run ahead of it and the desktop path was reserved for the extension's `no tab with that URL` answer (`ExtensionProtocol.NO_TAB`), on the assumption that an existing tab could be focused where it is; it cannot. `browser.windows.update({focused: true})` on a window living on another virtual desktop makes Windows switch to that desktop, so a PR that was open in a window elsewhere took the user off the category's desktop on every click (field report 2026-09-10).
`Main.openOnDesktop` switches to the desktop (`WindowsVirtualDesktopFocus`, `dsn~category-desktop-focus~4`) and raises a window of the driven browser (`dsn~drive-the-connected-browser~1`) living **there** — `BrowserWindowFocus.focusOnCurrentDesktop` enumerates the windows of that browser's class and process (`dsn~browser-choice~2`) like the title-matching raise does, but keeps the first one for which `IVirtualDesktopManager::IsWindowOnCurrentVirtualDesktop` is true (the *documented* virtual-desktop COM API, unlike the desktop switching itself). `EnumWindows` walks in Z-order, so the window kept is the topmost one on that desktop — the one the user focused last there.
The raise answers with that window's caption, and the following `focus-url` carries it as `windowTitle`: the extension matches it against its windows' titles by containment (as the complete-control capture does) and puts the tab **in that window** — creating it there (`browser.tabs.create({windowId})`) when the URL has no tab yet, and **moving** an existing tab there (`browser.tabs.move({windowId, index: -1})`) when it has one in another window. The browser's own "most recent window" is not relied on, since the OS raise and the extension request race and the most recent window may still be the one on the desktop just left.
A move the browser refuses (a pinned tab, a private window) is logged and swallowed like the tab grouping next to it — the tab is still focused where it is, since failing the click over the desktop would be worse. A tab that moved says so in the result detail (`focused existing tab (moved to this desktop)`), so the status bar explains why the tab changed windows.
Without a match (or without a caption, when the mechanism is unavailable) the extension leaves the choice to the browser and focuses an existing tab where it is, as before.
A desktop with no browser window at all has nothing to move a tab into, so there the extension is asked **focus-only** (`ExtensionServer.focusUrl(url, false)`) first: a URL that is already open is focused where it lives — the one case left in which the click still follows the tab to another desktop, and better than the second copy a blind launch would make. Only the `no tab with that URL` answer (`ExtensionProtocol.NO_TAB` — the one failure that means "the extension answered, and the URL still needs opening", as against the app-side `Browser extension not connected` and the timeout) opens a window there: `Start-Process '<browser>' -ArgumentList '<new-window flag>', '<url>'` (`firefox`/`-new-window`, `chrome`/`--new-window`; `dsn~browser-choice~2`) — the new-window flag because a plain `firefox <url>` is remoted into the running instance and would open the tab in whatever window the browser last used, on the desktop just left; `Start-Process` because it returns at once (ShellExecute, so the browser need not be on `PATH`) instead of holding the runner for the browser's lifetime. That window *is* the tab, so no extension request follows and the status bar reports `opened a window on desktop "<name>"`.
Windows-only in effect, like every desktop feature: without `powershell` the switch and the raise are silent no-ops and the extension opens the tab wherever it would have anyway.
The review-comment links (`dsn~qodo-agent-prompt-queue~11`) take the same path: the queue pane's focus-URL callback carries the shown task, and `Main` resolves its category desktop (`groupDesktop(files, task.folder())`, the same `desktop:` read `MainWindow.focusPr` uses) before handing it to `reportFocusUrl` — so a review comment that has to be opened lands on the task's desktop just like a PR link does.

Tags: windows

Covers:
- req~pr-open-on-category-desktop~2

Needs: impl, utest

### Category scratch tmux window
`dsn~category-scratch-window~2`

The remote glyph on a category header is a clickable icon button ("New tmux window on \"<host>\"") instead of a static glyph; it shows exactly when the category's `CONTEXTSWITCHER.md` sets `remote:`.
Clicking flushes a pending config edit and re-reads the config (like the desktop and note buttons), then hands the category's `remote` and its resolved workspace directory to `Main.startScratchWindow`.
There, off the FX thread, the directory is `mkdir -p`ed and `TmuxResurrect.createWindow` opens a plain window named `scratch` in the remote's usual session (`usualSession` — the session of the host's first tmux task, else `0`), which is then focused via `TmuxFocusAction.remoteCommand`.
The terminal pane is then pointed at the new window (`TerminalPane.show` + `focusTerminal`): the remote focus reaches plain tmux clients only — mirror clients are deliberately skipped (`dsn~tmux-focus-action~3`) — so with the app as the host's only client the window would be created out of sight, which read as a dead button.
No task file is written and no row is selected, so the task list keeps its selection (the next task click takes the mirror back); a failure raises the same "Cannot open a window on <host>" alert as the remote-window popup.
The button itself is disabled for the whole ssh round-trip (`mkdir -p` through the focus command) and re-enabled once `Main.startScratchWindow` hands off to the terminal pane, success or failure — the ssh/tmux latency previously had no visible feedback at all, unlike the pty attach itself which already shows "Connecting to … " (`dsn~terminal-pane~14`).
The status bar mirrors the same "Opening … " / outcome shape `focusPr` already uses for the browser round-trip.

Tags: windows, linux

Covers:
- req~category-scratch-window~1

Needs: impl, utest

### JetBrains Gateway URL action
`dsn~gateway-url-action~11`

`IntellijGatewayAction` builds a `jetbrains-gateway://connect#host=<host>[&user=<user>]&type=ssh&port=22&projectPath=<path>&idePath=<ide>&deploy=false` URL (spike outcome, amendments in MADR 0006) and launches it via the OS protocol handler (`Main.openUrl`: `java.awt.Desktop.browse` → ShellExecute/xdg-open, falling back to `HostServices.showDocument` where AWT cannot browse) — `showDocument` handed the URL to the default browser, and Firefox then asked for confirmation on every switch without an "always allow" option. `port=22` is always emitted: Gateway 2026.1 rejects the URL with "Invalid ssh link parameters: doesn't contain port" (surfaced as "Cannot Connect — There was an error in the connection provider") although the JetBrains docs call the parameter optional; non-22 ports are out of v0.1 scope. `idePath` is equally mandatory in 2026.1 ("doesn't contain idePath"): it comes from `intellij.ide` or, when unpinned, from `RemoteIdeLookup`, which resolves the newest installed backend on the remote via `ls -1td $HOME/.cache/JetBrains/RemoteDev/dist/* | head -n 1 | xargs realpath` (no quotes — Windows ssh.exe swallows embedded double quotes).
The `realpath` canonicalization matters: the backend registers itself under its real path (e.g. `/export/home/…` behind a `/home` symlink), and Gateway treats a non-canonical `idePath` as a different IDE, prompting "Requested Project Is Already Running" with an empty target version.
No pinned path and no installed backend fails the action with a message pointing at `intellij.ide`. The destination comes from the `intellij.remote` override, falling back to the task `remote`; the project path from `intellij.projectPath`, falling back to `claude.workspace`, then `claude.cwd` — the action counts as configured only when both resolve. A `user@host` value is split into the `user` and `host` parameters. All values are percent-encoded (fragment encoding, spaces as `%20`); `deploy=false` accompanies the always-present `idePath`. Before any of that, an already open local **JetBrains Client** window for the project is focused instead of launching the URL: `JetBrainsClientFocus` (PowerShell via `LocalCommandRunner`, base64 `-EncodedCommand` like MADR 0008) finds the `jetbrains_client64` **process** whose `MainWindowTitle` carries the project folder name (last path segment) and foregrounds its `MainWindowHandle` — a client titles itself `<IDE project name> – <file path>`, and the path carries the folder name; the words "JetBrains Client" appear nowhere in the title. The process route is deliberate: window enumeration (UI Automation root children, even `EnumWindows`) proved unreliable for client windows parked on another virtual desktop, while the process handle sees them regardless and `SetForegroundWindow` then also switches to that desktop. Exit 2 = none found → launch the URL; mechanism unavailable, e.g. no `powershell`, degrades the same way. Relaunching the URL while connected would trigger Gateway's "Requested Project Is Already Running" version prompt or open a second window.
After launching the URL, the action does not return immediately: Gateway uploads the worker binary, connects over ssh, and opens the IDE — seconds to minutes on a cold start — so returning at launch flips the status chip green while the IDE is still coming up. Instead it polls the same `clientFocus` detector up to `upPollAttempts` times (sleeping `pollIntervalMillis` between checks; the app wires ~3 min at a 3 s cadence, the background switch thread absorbing the wait) until the project's client window appears, keeping the chip in its RUNNING (hourglass) state until then. When the window appears the poll also focuses it. On timeout the action still reports success (the launch worked and the IDE may yet come up) rather than a failure chip. The non-waiting three-argument constructor (`upPollAttempts = 0`) keeps launch-and-return for tests and callers that do not need the wait.

Tags: windows, linux

Covers:
- req~open-remote-intellij-project~1

Needs: impl, utest

### tmux window resurrection
`dsn~tmux-resurrect~7`

When focusing fails, `TmuxResurrect` recreates the window: `tmux new-window -t <session>: -c <cwd> -n <task title> -P -F '#{window_id}'` (falling back to `new-session -d` when the whole server is fresh after a reboot) — the window is named after the task, so the tmux status line shows the same name as the task list — sends `claude --resume <sessionId>` into it when a session id is recorded, focuses it, and reports the new immutable window id, which is written back into the task file via textual `window:` line replacement (inserted after the `session:` line when the section has no `window:` line, which is the state a suspended task with a `claude:` section is left in).
Resurrection does not require a `claude:` section: without one the window is created plainly (`-c` omitted, nothing resumed).
The resumed line is `claude --resume <sessionId> || claude`: a recorded id the remote no longer knows (transcript rotated away, or an id from another host or cwd) makes `--resume` print "No conversation found with session ID" and exit, which would leave the freshly resurrected window sitting at a bare shell prompt.
The fallback starts a plain Claude in the recorded cwd instead — the closer approximation of what the task expects — and carries the same `--dangerously-skip-permissions` flag when `claudeAuto` is set.
A window-less tmux section resurrects directly — instead of focusing the bare session, which would "succeed" on an arbitrary window — when the task carries a `claude:` section **or** arrives with the suspended status: resume hands the actions the pre-flip suspended task (`dsn~task-suspend~6`), which is the "this task's window was ended" signal.
An *active* window-less task without `claude:` is a deliberate session-level config and keeps the plain session focus.

A failed focus is **not** by itself proof that the window is gone, and resurrecting on that assumption destroys a correct task file: the new id overwrites the recorded one while the original window keeps running — including the Claude session `--resume` was aiming at, which the duplicate then cannot resume.
So when the recorded `window:` is an immutable id (`@…`), `TmuxFocusAction.sessionOf` asks `tmux display-message -p -t @<id> '#{session_name}'` first; window ids are server-global, so an answer means the window is alive and only its *session* moved.
That is reported as a failure naming both sessions — the task file keeps its correct id and no duplicate is created.
It moves without anyone moving it: the terminal mirror is a grouped session (`dsn~terminal-pane~14`) and a tmux group outlives any single member, so when the base session dies the mirror keeps the whole window group under `cs-mirror-<name>` while new windows land in a fresh session of the original name — every task's `<session>:<window>` target fails at once, and unguarded every switch would duplicate its task.
A `window:` given as a *name* is skipped, since it would resolve to any same-named window on the server.

Tags: windows, linux

Covers:
- req~claude-session-capture~1
- req~focus-remote-tmux-window~1

Needs: impl, utest

### Restart running tasks after a remote reboot
`dsn~tmux-restart-running~1`

A reboot of the remote machine kills the tmux server, so every active task's recorded `window:` id is dead — and a fresh server hands the same ids out again, so focusing one can even land on an unrelated window.
The toolbar's "Restart running tasks…" menu item does in one click what suspending and resuming each task by hand does: for every **active** task with `tmux:` and `remote:`, it drops the recorded `window:` line and resurrects the window (`dsn~tmux-resurrect~7` — new window in the recorded cwd, `claude --resume <sessionId>` when an id is recorded), writing the fresh id back with the note `restarted <date>`.
Nothing is killed: the recorded id may already denote another task's window on the new server, so a window that *is* still alive is left behind as an orphan rather than risking the wrong kill — the button targets the after-a-reboot case where nothing is alive.
A confirmation names the number of affected tasks and that caveat; the button disables itself with an hourglass while the restarts run (they are sequential on the background executor) and reports a summary alert (`restarted N of M`, plus per-task errors).

Tags: windows, linux

Covers:
- req~task-suspend-resume~2

Needs: impl

### Suspend teardown
`dsn~task-suspend~6`

`TmuxKillAction` runs `tmux kill-window -t <target>` (window configs) or `tmux kill-session -t <session>` (session-only configs) on the task's host via the ssh runner; a window, session, or server that is already gone counts as success — the goal state is reached. Suspending also removes the task file's `window:` line (textual, comment-preserving) — after a suspend no window is known.
Resume passes the pre-flip suspended task to the actions; the tmux action reads that status as "recreate, do not focus the bare session", and the resurrect (`dsn~tmux-resurrect~7`) writes the fresh id back. A second `SwitchOrchestrator` instance runs the teardown actions concurrently with the usual per-action chips (the status bar is labeled "(suspend)"). Suspending is **silent** in the normal case — Claude is waiting and the `claude:` section carries what resume needs, so the kill loses nothing. The dialog decision is made on a task re-synced against its own window first (`dsn~teardown-claude-resync~1`), so a session started by hand in the window is adopted rather than warned about.
`MainWindow.confirmTeardown` asks for confirmation only where the dialog protects something: when the window's live `@cs_status` (`dsn~task-running-indicator~7`) is `working`, an explicit "Force terminate" is required because Claude would be killed mid-task; when the task has no `claude:` section, the dialog warns that resume cannot recreate the window automatically; and when the `claude:` section carries **no `sessionId`** yet, the dialog warns that resume could recreate the window but not `claude --resume` the conversation, so suspending now loses the session context (the id is published a few seconds after the session starts and recorded by the post-create backfill, `dsn~tmux-sync~7` — the warning targets suspending before then). The confirm button is "Force terminate" when working, else "<action> nevertheless"; the cancel button is "Return". When a suspended task is resumed, a regular switch is triggered — `dsn~tmux-resurrect~7` recreates the window and resumes Claude. Resuming works from both directions: the status control (label click / menu "Resume") and the play (switch) icon on a suspended row (`dsn~task-row-hover-actions~7`), which first writes the status back to `active` and then runs the switch. Closing browser tabs on suspend is `dsn~browser-close-action~2` (running in the same suspend orchestrator); reopening on resume falls out of the regular switch's focus-or-open.

Tags: windows, linux

Covers:
- req~task-suspend-resume~2

Needs: impl, utest

### Idle Claude tasks suspend themselves
`dsn~auto-suspend-idle~2`

The status poll (`dsn~task-running-indicator~7`) already computes each status-publishing window's idle seconds on the remote (`now − #{window_activity}`); `TmuxStatusPoller.parseIdleInto` hands them to a fourth consumer, `host windowId -> idle seconds`, delivered after the statuses of the same tick.
`MainWindow.autoSuspendIdle` then suspends **one** active task per tick — the suspend orchestrator shares one status bar, a burst would overwrite its own chips — that (a) has a remote, a window id and a published status, (b) has been idle for at least `autoSuspendMinutes` (`settings.yaml`, default 2880 = 48 h, `0` = off), (c) is not the selected row (the task the user is looking at), and (d) the manual suspend would take **silently** (`MainWindow.teardownIsSilent`: not `working`, a `claude:` section with a recorded `sessionId`) — the automatic path never opens the dialog, it skips what the dialog would protect.
The teardown is the manual one without its dialog: `withStatus(SUSPENDED)` (with the timestamp, `dsn~suspended-timestamp~1`), `withoutTmuxWindow`, then the suspend orchestrator (`dsn~task-suspend~6`: kill preceded by the screen snapshot, `dsn~terminal-suspend-snapshot~3`).
A plain shell window (no `@cs_status`) never qualifies — its silence means nothing — and neither does a task whose Claude has not published its session id yet.
A saved `autoSuspendMinutes` (and `mergedCleanupDays`) applies at the next poll, no restart: `Main` keeps both in fields refreshed by the settings save, like `fallbackDesktop`.
Each tick's suspend retitles the status bar with the remaining count (`Auto-suspend, 3 to go: <title> (suspend)`, `SwitchStatusBar.retitle` — the chips stay), so a lowered threshold is seen working through the pile.

Tags: windows, linux

Covers:
- req~task-suspend-resume~2

Needs: impl, utest

### A suspended task records when
`dsn~suspended-timestamp~1`

`TaskFileParser.withStatus(content, SUSPENDED)` writes a `suspended: "yyyy-MM-dd HH:mm"` line (local time, minute precision, quoted so YAML keeps it a string) right below the `status:` line; writing any other status removes the line, so a resumed or completed task carries none.
Every path that suspends goes through `withStatus` — the row's pause, the delete dialog's teardown, the sync's gone-window reconcile (`dsn~tmux-sync~7`) and the auto-suspend — so all of them stamp it.
The parser reads it into `Task.suspendedAt` (a string, shown as written); the row's second line leads with `⏸ <timestamp>` for a suspended task and the pause glyph's tooltip carries it too, so a collection of suspended tasks can be sorted through by age.

Tags: windows, linux

Covers:
- req~task-suspend-resume~2

Needs: impl, utest

### Teardown re-syncs the task against its own window
`dsn~teardown-claude-resync~1`

Before a suspend or complete confirms anything, `MainWindow.setStatus` asks the task's **own** tmux window what it is running (`Main.refreshClaudeFromWindow` on the action executor) and writes the answer into the task file; the confirmation then runs on a freshly re-read task.
Without it the teardown decides on what the file knew when the task was written: a window imported as a plain shell that the user later started `claude` in carries no `claude:` section, so the dialog claims "nothing running inside can be brought back" while a resumable session sits in the window — and killing it makes that true.
The periodic reconcile now adopts such a session on its own (`dsn~tmux-sync~7`), but only every `AUTO_RECONCILE_SECONDS`; the teardown is the one moment where being a minute stale is unrecoverable, so it re-reads then and there.
The re-sync is targeted and conditional: only a task with a remote and a **window id** whose `claude:` section lacks a `sessionId` pays the round-trip — the normal case (a recorded session) confirms without any ssh at all, so a silent suspend stays instant.
It runs one `TmuxDiscovery` listing of the host and keeps the entry for the recorded window id, writing `claude:` (created from the window's `cwd` when absent), `sessionId`, `workspace`, and `commit` in one pass — the same facts and the same "only the id the session published itself" rule as the sync, but complete immediately instead of a tick apart.
The status bar names the window being checked while the round-trip runs; the callback fires on **every** outcome (unreachable host, unparseable file, window gone), so a failed re-sync delays the teardown but never swallows it.
An editor lane showing the rewritten file is reloaded like the sync's rewrites are.

Tags: windows, linux

Covers:
- req~task-suspend-resume~2

Needs: impl, utest

### Switch orchestrator
`dsn~switch-orchestrator~3`

`SwitchOrchestrator` determines the configured actions of a task, runs them concurrently on a background executor, and publishes per-action state transitions (pending → running → ok/failed with message) to the UI via `Platform.runLater`. An optional completion callback runs on the UI executor once every configured action has reported its final state — used to re-target the terminal mirror after a switch/suspend (`dsn~terminal-pane~14`).
A caller can additionally pass a set of action names to further narrow the run to those (the rest are skipped as if unconfigured, with no state transition at all); a `null` set runs every configured action, the plain overloads' behavior.
The delete dialog's independent "End the tmux window" / "Close browser tabs" ticks (`dsn~claude-session-kill~6`) use this to run only the actions the user selected.

Tags: windows, linux

Covers:
- req~switch-runs-configured-actions~1
- req~per-action-feedback~1

Needs: impl, utest
