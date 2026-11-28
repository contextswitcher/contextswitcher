# Changelog

All notable changes to ContextSwitcher are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions are release dates ([CalVer](https://calver.org/), `YYYY-MM-DD`), tagged `v<date>` on GitHub.
Changes collect under *Unreleased* until the next morning's automatic release turns them into that day's version.

## [Unreleased]

### Added

- A **Conversation** tab behind the terminal shows the task's Claude chat as bubbles — your messages on the right, Claude's replies on the left with their Markdown rendered — so it is easy to see who wrote what; **Reply** on a message starts an answer to it in the queue.
- The *Add task* dialog has a **Claude folder** field: *Add local Claude* starts the session there, in any category. A category with a `workspacesRoot` suggests a subfolder per task, named after the description, so tasks no longer share one directory; the *Folders* field stays context only.

### Fixed

- The *Sync tmux windows…* summary now lists each imported task with its category and title, and its text no longer sticks to the dialog's left edge.
- The Android app no longer crashes when a task is opened just as the app goes to the background (Android refuses to start the finish watch then); the watch starts once the app is back in front.

### Changed

- The task dots are green when Claude is done, yellow while it works and red when it needs you (question, permission, usage limit); a legend at the left of the status bar names them. Before, a finished turn showed red. The Android app uses the same colours.
- A category header shows only its play button until you hover it; the desktop, note, repository and *Add task* icons then appear just left of play, like a task row's actions.

## [2026-11-27] - 2026-11-27

### Added

- **Win+Shift+F** switches to the task of the window in front, e.g. a Teams meeting: a task (or category) names its windows with `windowTitle:`, text the window title contains; several matches select the first and name them all, none says so in the status bar. The key is `switchByWindowHotkey` in `settings.yaml` (Windows; `Win+F` itself belongs to Feedback Hub).
- The *Add task* dialog asks for optional *Folders* (one per line), a *Note* link and a *Chat* link and writes the filled ones into the new task; a folder or OneNote's *Copy Link to Page* on the clipboard fills them in, the note keeps the `onenote:` link, and folders are stored for this computer only (`folders-<computer name>:`), so a synced task still works on the other one.

### Changed

- The Android app's *Update* also offers the newest day release, not only the dev build, whichever is newer; dev builds are Actions artifacts now, not the `android-dev` release.
- The Android APK of a day release is named after it, e.g. `contextswitcher-2026-11-27.apk`, instead of `android-debug.apk`.
- RefactoringMiner no longer runs in the background for every task: the refactoring-count badge is a per-task *Refactoring badge* checkbox, off by default; without it RefactoringMiner runs only when you open the refactoring view.
- A category's settings show *Defaults for its tasks* (host, IntelliJ, browser, folders, terminal) above *Repository* instead of at the very bottom.
- An empty *Windows Terminal tab folder* shows in gray the folder it falls back to, the first *Local folders* entry.
- A category's *Add task…* row shows only while its header is selected, right under the header, with a new *Find task…* row above it that searches just that category.
- *Switch actions* has an *Open in Windows Terminal* checkbox; the tab title and folder fields show only while it is ticked, and an empty tab title uses the task's title.
- A `file:///C:/…` URL pasted into a *Folders* field (Explorer's address bar, a browser) turns into the folder path `C:\…`.

### Fixed

- The refactoring badge no longer starts a new RefactoringMiner run every two minutes on a large repository while the previous one is still running: a run may take up to two hours, a failed one is not retried until the task's HEAD moves, and no orphaned run is left on the remote.
- Clicking a file path or link Claude wrapped across two lines in the terminal opens it; before, the click did nothing or opened a cut-off path.
- Waking up from sleep at the desk no longer moves windows around with `gatherWhen: docking`: screens that come back as they were before the sleep are not taken for docking.

## [2026-11-26] - 2026-11-26

### Added

- An agent skill, `contextswitcher-tasks`, lets Claude Code (or another coding agent) create and update tasks on request, e.g. "create a contextswitcher task for X in category Y"; install it with `npx skills add contextswitcher/contextswitcher --skill contextswitcher-tasks -g`.
- With AnchorHub installed, each OneNote page a task or category links to with `note:` shows a link back to it at the top, struck through once the task is done and gone once it is deleted; `oneNoteBacklinks: false` in `settings.yaml` turns this off. Needs an AnchorHub release with `onenote-backlinks-sync`.
- `terminal.folder` (task or category) names the directory a gone *Windows Terminal tab* is reopened in, e.g. after a reboot, without adding it to `folders` and so to File Explorer.

### Changed

- When the virtual desktop changes, the log names the window that came to the front and the one in front before, so a jump to another desktop can be traced to the app that caused it.
- A folder that no Explorer window shows yet opens as a new tab in an Explorer window on the current desktop instead of a new window.

## [2026-11-25] - 2026-11-25

### Added

- A mode without Claude for managing TODOs: the setup wizard's *Without Claude — only contexts* (or `contextsOnly: true`) opens the window without terminal and queue and hides the Claude and tmux actions, so tasks only switch their notes, browser tabs, folders and desktop.
- An environment profile can set `gatherWhen: docking`: `gatherWindows` then only sweeps the windows over right after docking, and a window opened later on the laptop or a second monitor stays there.
- A Claude task's right-click menu has *Remove after 24 hours of inactivity*, the Add-task option to turn on (or off) later.
- Three more values for `theme`: `muted-slate`, `dawn` and `matrix`, the palettes of the tuxedo todo app.
- Android: a message waiting under *Sent when Claude is idle* has *Transfer*, which moves it into the desktop's queue for the task.

### Fixed

- On Windows, the main window's title bar shows the app icon again, and the run loop's *What's new* window shows it in the taskbar instead of Java's.
- Switching to a task or category no longer jumps to another virtual desktop because one of its pages is open in a browser window there: the tab comes to the desktop you are on, into a browser window there or into a new one when it has none. Needs the browser extension 0.9.2.
- *Open note* opens the OneNote desktop app when the `note:` field holds both lines of OneNote's *Copy Link to Page* pasted together, instead of the web page.

- The queue's add box grows with the message typed into it.
- Switching to another window while typing a queue message and coming back keeps the caret in that message instead of an empty add box.
- The settings dialog is wide enough for the host table: the *Active* column header and the paths are no longer cut off.
- `gatherWindows:` no longer pulls a window that would not stay (moved back three times) over again after it was minimized or its virtual desktop switched.
- Switching virtual desktops scrolls the task list to that desktop's selected task when it is out of view.
- A link copied from OneNote pastes into the queue (and the Add-task description) as the link, not as a picture of it.

## [2026-11-22] - 2026-11-22

### Added

- *Jump to category* has an airplane button in the task-list toolbar, lists the five categories jumped to last on top, has *Jump* and *Cancel* buttons, and switches to the category's virtual desktop and then to the category itself.

### Changed

- *Jump to bottom* below the terminal also brings Claude Code's own view back to the bottom (it sends Claude's `Ctrl+End`), not only the terminal's scrollback.
- The environment-profile menu's first item reads "Automatic (desk + lecture)" instead of "Category profile": it names the profiles the connected screens and the category chose, and the status-bar button shows the same while no profile is picked by hand.
- The settings dialog shows one tab per group instead of one long scrolling page; the general settings are split into General, Claude and Interface.
- The app-wide buttons (energy saver, browser, sync groups, update, settings, panes) sit in the window's title bar, next to minimize, maximize and close, instead of in a toolbar row of their own below it; the window draws its own title bar for that.
- The Android app offers *Update* from the `android-dev` pre-release without a GitHub token; the *Update token* setting is gone.

### Fixed

- `gatherWindows:` moves maximized windows too (on a screen without a taskbar they were taken for a slide show and left on the laptop), gathers the windows of every virtual desktop at once, and stops retrying a window that will not move (an elevated Task Manager) instead of logging it every half second.
- Windows: the packaged app shows the ContextSwitcher icon in the taskbar, Alt+Tab and Task Manager instead of the Java icon; `just run-loop` is now `just loop`.
- Android: swiping from the terminal to the queue and back keeps the terminal at the line you were reading instead of jumping back to the end.
- Android: a long task title takes at most two lines in the task list too (was three), leaving more room for the rest.

## [2026-11-21] - 2026-11-21

### Added

- Android: the *Go to category* dialog (tap on a category header) lists the recently used categories on top, like *Add task*; picks in either count.
- Android: a long press on a task in the list offers *Cancel* (Esc to Claude) and *Delete task…*, which cancels Claude first.
- Android Auto: when Claude finishes in the watched task, the car announces it like an incoming message, reads Claude's answer aloud and takes a spoken reply into the session; on the phone the alert now shows the answer.

### Changed

- The *Add task* buttons and hints name the picked agent (*Add remote Vibe*); *Add local* stays Claude-only and is disabled for Vibe and Codex.
- With *Labels* or *PR status* grouping, a task row shows its category directly before the title instead of on the second line.

### Fixed

- Android: reaching the top of the terminal while a refresh is still running loads the older output once the refresh is done, instead of not at all until you scroll away and back.
- Android: scrolling up in the terminal past the loaded output keeps the line you are reading in place when older output loads, instead of jumping to the start of the loaded block.
- A Vibe task no longer gets its first prompt pasted several times: Vibe shows a long paste only as `[Pasted … characters]`, which the app now accepts as delivered.
- When Claude quits right after a new task's start, the task description is no longer typed into the shell; the window stays open with the shell.
- Android: a very long task title no longer fills the whole task screen; it shows two lines, and a tap on it expands it into a scrollable area.

## [2026-11-20] - 2026-11-20

### Added

- *Add task* picks the coding agent (Claude Code, Claude Code on an API key, Vibe, Codex) with that agent's models; Vibe and Codex tasks show the running dot too.
- With `koppterSync: true`, a category's `project:` and `contexts:` and the context of the screen-chosen or overridden profile are shared with koppter's todo.txt filter, both ways; the filter menu gains *Projects and contexts*, and `contextswitcher://filter?project=…&context=…` sets it.
- A single browser URL of a task can open in Chrome (or Firefox) while the other browser handles the rest: start its line with `[chrome] `.
- The connected screens pick an environment profile of their own (`screenProfiles:` in `settings.yaml`), applied on docking and undocking without a task switch; a profile with `gatherWindows: DELL U3223QE` then pulls the windows onto that monitor. The status bar shows it as `desk (screens)`.

### Changed

- The height the *Last sent* area in the Queue pane is dragged to is kept for all tasks and after a restart.
- A task removed after a day of inactivity also takes its worktree's git branch with it.
- The config form no longer insists on a title: leaving the field blank removes `title:`, and the task shows its id. The *The title cannot be empty.* error no longer pops up when focus leaves the form.

### Fixed

- Switching virtual desktops after clicking a queued message selects the new desktop's task again; the switch now waits only while you are actually typing in the Queue pane (a keystroke within the last 3 seconds).
- Switching to a virtual desktop without tasks empties the Terminal, Queue and Notes panes instead of leaving the previous desktop's task in them.
- Adding a URL or a Claude PR to a task whose `browser.urls` items sit at the same indentation as `urls:` no longer breaks the task file with "Invalid YAML frontmatter"; the new item takes the indentation of the existing ones.
- *Cancel* in the "Claude sessions are running" dialog on closing the window keeps ContextSwitcher open again; it exited anyway.

## [2026-11-19] - 2026-11-19

### Added

- A category can run Mistral Vibe, OpenAI Codex or Claude Code on an API key instead of Claude Code (`agent:` in `CONTEXTSWITCHER.md`, per task `claude.agent:`); the API key comes from `CS_ANTHROPIC_API_KEY` in the remote shell, e.g. via direnv.
- Android: the task list shows the desktop's running dot per task — orange while Claude works, red when it waits for you.
- *Add task* offers *Remove session and worktree after 1 day of inactivity*: once the task has been quiet for a day, ContextSwitcher ends its window, removes the transcript and the git worktree (never a full checkout) and deletes the task.
- A task whose Claude turn was interrupted (Esc, Ctrl+C) shows a magenta dot instead of the ordinary red, sorts near the top under *Action needed* and is listed by *Awaits input only*; the Android task list shows the same magenta dot, and its watched task notifies *Claude was interrupted*.

### Changed

- Android: *Back* from a task shows the task list right away instead of an empty screen first.
- Android: *Add task*'s category list starts with the five categories used last, followed by all categories.
- The *Last sent* area in the Queue pane can be dragged taller at its bottom edge, to see more of the earlier messages at once.
- The selected task is filled with a muted accent tint instead of grey, and the active category's name takes the accent colour, so both stand out at a glance.

### Fixed

- Clicking a task's link no longer jumps to another virtual desktop when a browser window there shows the same page as the window on the category's desktop (needs the updated browser extension).
- An environment profile with `screens:` or `gatherWindows` (such as `lecture`) applies again instead of failing with `CreateProcess error=206, The filename or extension is too long`.

## [2026-11-18] - 2026-11-18

### Fixed

- A category can be collapsed again while the find bar holds a query or a tag filter is selected.
- *Add category…* also recognises a folder copied in File Explorer with Ctrl+C: the wizard selects *On this machine* and opens on that folder, as it already did for a copied path.

## [2026-11-17] - 2026-11-17

### Added

- The delayed-send clock in the queue works for a chat running in the app's own terminal on Windows; the session reports when it is idle (takes effect once the session is restarted or resumed).
- An environment profile can set `gatherWindows: internal` (or another screen): while it is in effect, windows that open on another screen, such as the projector, move to that screen; full-screen windows such as the slide show stay, and a window dragged there by hand stays too.
- The setup wizard's *Virtual desktop* field suggests the known desktops while typing, as the configuration form does.

### Fixed

- Switching to a task or category with `folders:` no longer leaves File Explorer in front: the Explorer window opens or comes forward behind ContextSwitcher, which keeps the foreground.
  The category header's folder button still brings Explorer to the front.

## [2026-11-16] - 2026-11-16

### Added

- A category can set a *Windows Terminal tab* on its Switch actions; every task of it without its own tab focuses that one.
- The setting `todoTxt` names a todo.txt file, such as koppter's; a task row shows each open line of it tagged `cs:<task id>`.
- Android: swiping left past a task's Queue page opens the next task of the list, swiping right past its Terminal page the previous one.

- The deep link `contextswitcher://switch-category/<name>` selects a category and runs its switch, opening its note.

### Changed

- A category's *Virtual desktop* and *Clear that desktop on suspend* moved from Configuration to Switch actions.
- A task's empty *Local folders* field shows the category's folders as its hint, as the switch opens those.
- Starting ContextSwitcher while it already runs brings the running window to the front instead of opening a second copy; `--new-instance` starts one anyway.

### Fixed

- Android: sending (or enqueueing) a message edited from the desktop queue now removes the original from that queue, so it is not delivered twice; a message with attachments stays, since its files are on the desktop.
- Running the tests no longer writes into (and rotates away) the app's own logs in `~/.contextswitcher/logs`; test logs go to `app/build/test-logs/`.
- The desktop-filter UI tests pass on Windows: the app under test no longer watches the real virtual desktop.
- The empty terminal pane no longer says "tmux window" — a local Windows task runs in the app's own terminal, not in tmux.

## [2026-11-15] - 2026-11-15

### Added

- *Local folders* has a browse button per row, and on Windows a pasted `/c/…` (Git Bash/Cygwin) path is converted to `C:\…`.
- A task's *Windows Terminal tab* that is gone, e.g. after a reboot, is reopened in the task's first local folder on switch.
- Android: *Add task* has an optional description with *Attach* (photo or camera); for a started Claude the images are uploaded with the prompt.
- Android: the terminal page has *Bottom*, right of *Refresh*, jumping back to the latest output after scrolling up.

### Fixed

- A switch no longer pulls you to another virtual desktop when an Explorer, Windows Terminal, JetBrains Client or browser window it focuses sits there: the window is moved to the desktop in view first, and when that does not work it is left alone (Explorer opens a new window here instead).
- The UI tests no longer open Explorer, switch virtual desktops or move windows on the machine running them.
- Clicking another category's header opens its config in *Configuration* and *Switch actions* again; before, they kept showing the previously opened file.
- Android: the task list's top bar no longer grows to half the screen; *Tour* and *Settings* moved into a ⋮ menu, and a long task title wraps (at most three lines) instead of squeezing its status into a vertical column of letters.

## [2026-11-13] - 2026-11-13

### Added

- The *Windows Terminal tab* field moved from *Configuration* to *Switch actions*: it focuses a local terminal tab (e.g. a shell for gradlew) on every switch, also next to a tmux window.
- *Switch actions* has *Open in VS Code*: a switch opens a new VS Code window on the current desktop at the task's project directory (via Remote-SSH for a task on a host).
- Android: the task list has a search button (🔍) that narrows it to the tasks whose title, tag or category matches.
- Android: tapping a category header lists all categories; picking one scrolls to it.
- Android: a top-bar button switches the task list between all tasks, active ones only, and active ones plus suspended tasks changed in the last 14 days.
- A new local category lists the Claude sessions already recorded for its folder as suspended tasks, each resuming its own conversation.
- A local category's header has a sync button that adds the Claude sessions recorded for its folder since; *Sync tmux windows…* does the same for every local category.
- *Show running tasks only* has a sub-option *Also show suspended tasks of the last 14 days*, keeping recently paused tasks at hand for resuming.
- Opening a OneNote note or a folder on switch moves the OneNote or Explorer window to the desktop in view, instead of jumping to the desktop it was on.

### Fixed

- Android: the app registers the `contextswitcher://` scheme; `task/<id>` and `switch/<id>` links open the task.
- The terminal's status messages ("Connection lost — reconnecting…", "Connecting to …") show dashes and ellipses instead of garbled characters.
- Deleting a local task offers to remove its Claude transcript on this machine, as it already did for remote tasks.
- A task whose creation failed before its tmux window came up shows play instead of pause on hover; play opens the same dialog as the terminal's *Start Claude* button.
- Closing the app (or restarting it for an update) while it runs Claude sessions on Windows asks first, listing their tasks by category; clicking one shows its terminal instead of closing.
- Imported Claude sessions carry the session's last activity as their date (shown on the row, used by *last update* sorting), not the time of the import.
- Importing Claude sessions into a local category switches *Show running tasks only* off so the new (suspended) tasks show, and selects the newest one.
- A category just added stays in the list while *Show running tasks only* hides its tasks, instead of vanishing.
- With *Show active desktop only* on, a category belonging to the desktop in view stays in the list even when *Show running tasks only* hides all its tasks.
- *Awaits input only (N)* counts only the tasks the filter would show — with *Show active desktop only* on, those of the desktop in view.
- A task whose folder path is long and contains spaces no longer gets a broken (unparseable) `claude:` section when the app writes it.
- After the daily release, the "What's new" window no longer lists every change of that release again as pending, although they had already been seen; a change seen once also stays seen after going back to an older version and forward again.
- A local task in a folder whose path contains spaces (OneDrive's do) finds its Claude transcript again, so it resumes the right conversation and *Delete* can remove the transcript.

## [2026-11-12] - 2026-11-12

### Added

- A category's header has a play button that switches to the category itself — its desktop, folders, note, chat, URLs and environment profile — when there is no task to pick.
- A category's environment profile can be set on its *Switch actions* tab.
- The setup wizard's category page asks for the category's virtual desktop, pre-filled with the current one; empty it to use the fallback desktop.

### Changed

- *Add category…* in the add menu and the task list's context menu now opens the setup wizard, which asks for everything a category needs; the former *Setup wizard…* item and the name-only *Add category* dialog are gone.
- A project created by the setup wizard for a local folder lists that folder under *Local folders*, so switching to it opens the folder in the file manager.

### Fixed

- The setup wizard's *Finish* stays disabled while a local folder for a new category does not exist or cannot be read, and says so under the field.
- The setup wizard's buttons no longer slip out of the window when the check of this machine reports a missing tool.
- Qodo review prompts are found again in comments with Windows line endings.

## [2026-11-09] - 2026-11-09

### Changed

- *Browser URLs* in the Switch actions pane is now a list of links: clicking one selects that page's browser tab (opening it if needed), and Edit and Delete appear when you hover over a row.
- Hovering a browser URL shows its description, or else the page's title, instead of the field's help text.

### Added

- The browser URL of the tab in view is highlighted in the Switch actions pane.
- *Browser URLs* has a button that opens a new browser window with a tab group holding all the task's URLs in order, or brings that window to the front if it already exists; a tab whose URL left the list leaves the group but stays open. Update the browser extension to 0.9.1 for it.
- *Browser URLs* can be re-arranged by drag and drop, and a *Sync order* button arranges them the way the tabs stand in the browser.

### Fixed

- *Switch actions* and *Configuration* keep their scroll position when the task file changes in the background, instead of jumping to the top.
- A URL mentioned in LaTeX (`\href{…}{…}`, `\url{…}`), between `<…>` or in backticks is now collected on its own, without the markup around it, in *Browser URLs*.
- A task row no longer stays stuck over the top of the task list, hiding the rows beneath it, after a search, a filter or a background update changes the list while it is scrolled into a category.

## [2026-11-05] - 2026-11-05

### Changed

- The energy saver's leaf in the task-list toolbar is filled green (the send button's accent) while it is on. Before, on and off looked almost the same, and armed queue messages silently waited for a click.

### Fixed

- A file path followed by a sentence's full stop is a link in the terminal now.
- Right-clicking a URL or file path that Claude wrapped across rows now offers "Copy link" or "Copy path" with the whole target. Before, such a path had no entry and a wrapped URL was copied cut off.
- *Review comments sync* picks up findings qodo adds to its edited "Code Review by Qodo" summary after you replied below it. Qodo now posts its findings only there, so new ones were never queued.

## [2026-11-03] - 2026-11-03

### Fixed

- A live task that takes over the title Claude publishes no longer leaves a copy under its old file name. The copy showed up as a second row bound to the same tmux window and Claude session.

## [2026-11-02] - 2026-11-02

### Added

- Terminal: right-clicking a file path offers *Copy path*, a URL or issue link *Copy link*.

## [2026-11-01] - 2026-11-01

### Added

- Setup wizard: a folder path on the clipboard (say, `c:\git-repositories\git.overleaf.com\…`, also with *Copy as path*'s quotes) preselects *On this machine* and opens the category page on *A local folder* with that path filled in.
- Setup wizard: a local folder category can be created without a Claude session — untick *Start a Claude session that writes its CLAUDE.md*. Claude missing on this machine no longer blocks the wizard either: *Next* once more goes on without it.

### Changed

- The switch actions (IntelliJ, browser URLs, local folders, chat room, OneNote) have a *Switch actions* pane of their own, next to *Configuration*. A list there is a field per entry plus an empty one to add the next; hovering an entry shows a trash can to remove it.
- *Switch actions* and *Configuration* save when the focus leaves the pane; the *Apply* button is gone, and *Reload* drops what was not saved yet.
- The *Note link* field is now labelled *OneNote*.

- Android: a task started with *Start Claude* opens right away, so its terminal shows Claude starting.
- Category headers in the task list stand out from task rows: a divider line on top and the name bold at full contrast. The header of the selected task's category keeps its own background and is marked by the accent edge only, so it no longer looks like a second selected task.

### Fixed

- A local Claude task whose description is itself an instruction ("Set up the folder … write a CLAUDE.md") no longer gets a stray tag such as `<invoke_tool>` as its title; it gets a short title, or the description's first words.

## [2026-10-31] - 2026-10-31

### Added

- Setup wizard: the category page offers *A local folder — no remote, no git* next to a repository: a folder (say, one synced by OneDrive) becomes a category as it is, and a Claude session writes its `CLAUDE.md` without cloning or running `git init`.
- Android: *Call (Android Auto)* on the voice tour screen runs the tour as a phone call, so Android Auto shows it like a call and uses the car's microphone and speakers; "next", "previous", "repeat" and "hang up" steer, anything else is sent as the reply.
- Android: *Jump hosts* in the settings — `user@host via user@jump[:port]` per line — reach a remote through another ssh host, like `ProxyJump` on the desktop; the jump host then also resolves a short name the phone cannot. *Check connections* tries every listed remote through its jump host and reports each result; saving does not depend on it.

### Changed

- The task list sorts by *Last update* by default, so a new task shows up at the top of its group; a sort order picked in the Sort menu is kept as before.
- Claude sessions are no longer sent `/exit` on their own when Claude Code shows *Restart to update*; set `claudeUpdateRestart: true` in the settings to get the automatic restart back.

### Fixed
- Switching virtual desktops (with *Show active desktop only*) selects that desktop's last task and shows its terminal again; an empty message box in the queue pane no longer holds the previous desktop's task in place.
- Setup wizard: the browser-extension page is skipped for the Microsoft Store build of Firefox too, when the extension is installed there.
- Setup wizard: the browser-extension page is also skipped when Firefox runs the extension as a temporary add-on loaded through `about:debugging`.
- The remote-window dialog no longer shows a scrollbar next to a short two-line question; only an overlong pasted title scrolls.
- Android: a host name that does not resolve on the phone's network is reported as such, with a VPN hint, instead of a bare "Cannot connect to user@host: host".
- Android: a host that rejects the SSH key says which host (the remote or the jump host) and whose `authorized_keys` needs the public key, instead of "Exhausted available authentication methods".
- Android: the voice tour read "Loading" instead of the first task's card after the overview; the card is now spoken.
- Android: the voice tour's card names the task's PR number ("Task 1 of 3, PR 16245: …") when the title does not.
- Android: the voice tour reads English text with an English voice and German with a German one, instead of everything in the phone's language.
- Android: the SSH private key is checked as you paste it — the settings screen says "Key parses: ssh-ed25519" or why it does not — and indentation, stray blank lines and Windows line ends from a messy paste no longer break it; a key that does not parse is reported as such instead of as a rejected key.
- Settings: the help text of *Clear that desktop on suspend* said Firefox *windows* are closed; it now says *tabs* are stored and closed, which is what resuming reopens.
- A task no longer vanishes from the list (and from search) until the next restart when a background write to its file coincides with an error in the task list's UI: the file watcher applies the rest of its batch even when one step throws, and logs the error instead.
- The task editor failed with `NoSuchMethodError` on every task selection in the packaged app since this morning's build (a JDK bundling its own JavaFX had misled the compile); the editor loads again.

## [2026-10-27] - 2026-10-27

### Changed

- Android: the *Add task* dialog preselects the category scrolled to the top of the task list instead of the alphabetically first one.

### Fixed

- *Files* → *Custom path…*: a path pasted from the terminal with the line break and indentation of Claude's own wrapping is joined back into one path instead of being reported as unreadable.
- Android: when *Add task* cannot create the tmux window, the dialog shows the remote's error (a refused connection, a missing key, tmux's own message) under the headline instead of the headline alone.
- Android: a category's `~/…` workspaces root is created in the home directory before the window starts there, rather than a directory literally named `~`.

## [2026-10-25] - 2026-10-25

### Added

- Android: a voice tour through a category's tasks — *Tour* in the task list (and on the Android Auto head unit) reads out how many tasks wait, then task by task the title, status and Claude's last answer, takes a dictated reply into the task's session and reads the finished answer; the reply asks Claude for a few spoken sentences instead of tables or diffs.

### Changed

- The browser extension, switched off in its popup, shows a red *OFF* badge on its toolbar icon in addition to the faded icon.

### Fixed

- A live task of a category that was renamed out of a name clash (`contextswitcher` → `contextswitcher-swkgpu1`) tells Claude to branch from the clone's actual path instead of a subdirectory named after the new category, which did not exist. The rename now also records `mainCheckout:` in the category's `CONTEXTSWITCHER.md`.
- The "What's new", sync groups and diff windows show the app's icon in the title bar and taskbar instead of Java's default.

## [2026-10-22] - 2026-10-22

### Added

- The Add-task dialog's category list shows the current desktop's categories first, under a heading, and a host picker next to it narrows the list to the local categories or to those of one remote.
- Environment profiles: `settings.yaml` can define named looks for the machine (`profiles:` with a `resolution` and Windows `scale` per screen under `screens:` — picked by monitor name, `internal`, `external` or `primary`; the scale is 100% when left out —, projector mode `displays`, `taskbar: autohide`, Firefox `bookmarksBar: hidden`), and a category picks one with `profile:` in its `CONTEXTSWITCHER.md`. Switching to a task applies its category's profile, and a task of a category without one restores the `default` profile. A status-bar menu lays an override such as `screenshare` on top until switched back. Hiding the bookmarks bar needs a one-time `userChrome.css` rule (see the Firefox extension README).
- Browser extensions 0.8.0: the icon's popup names the installed extension version, and in Firefox says when a profile marked the bookmarks bar hidden, pointing at the `userChrome.css` setup when the bar still shows.
### Fixed

- Android: the Add-task dialog offers *Start Claude on …* with the model and effort pickers for a category whose `CONTEXTSWITCHER.md` names no remote, like the desktop: the remote then is the first one a task in the synced repository names.
- The update window's hint shows `just run-loop` in a monospaced font.
- Activating the browser tab of a suspended task resumes the task without touching the browser: the tab stays in front, and the task's other pages are no longer opened in a tab group with the first of them activated (which could resume a second suspended task listing that page).
- On Windows, two tasks working in the same folder each resume their own Claude conversation. Before, both showed the folder's newest conversation. A task that recorded no conversation yet now skips the ones other tasks own.
- A task row shows play instead of pause when its Claude is known not to run: on Windows when the task has no session running, or on a remote when the window was last seen at a bare shell. On Windows, play starts or resumes the session directly. The button switches as soon as a session starts or exits.

## [2026-10-19] - 2026-10-19

### Added

- A refresh button next to a category's "Virtual desktop" field reads this machine's virtual desktops again and lists them, so a desktop created or renamed while the form is open can be picked. The field now also suggests every named desktop on the machine, not only those other categories use.

### Fixed

- Two attachments placed directly next to each other in a message reach the chat as two separate paths. Before, the chat showed them as one path with stray backticks in the middle.

## [2026-10-18] - 2026-10-18

### Added

- Clicking "N on other desktops" next to the task search lists those tasks; picking one switches to its desktop and selects it.

### Fixed

- With "Show active desktop only" on, a category whose virtual desktop does not exist on this computer is shown on the fallback desktop (`fallbackDesktop`, `misc` by default), where its desktop button already led. Before, it was hidden on every desktop.

## [2026-10-12] - 2026-10-12

### Added

- The "Virtual desktop" field of a category's configuration suggests desktop names while typing: the desktops the other categories use, the active desktop and the fallback desktop. Press Down to see them all.
- The setup wizard installs Claude Code's status reporting hooks on the session host, keeping the hooks and settings already there. With them, a session reports its status, its id and the directory it works in, so tasks get their status dot and deleting a task offers to remove its worktree and transcript. Before, a host set up through the wizard reported none of this.

### Fixed

- The setup wizard's last page shows "Finish and check up" in full instead of "Finish and che…".
- A category can be added from an ssh clone URL such as `git@github.com:owner/project.git`, in the setup wizard and in "Add category from URL…"; the repository is then cloned over ssh. Before, only `https://` URLs were accepted and Finish stayed disabled.
- A link into a repository, such as a file on GitHub, adds a category for that repository. Before, the category was named after the file.
- The Add-task dialog shows its buttons ("Add remote Claude", "Add local Claude", "Add plain task") and hints in full instead of cutting them off with "…"; the hints wrap and the dialog is as wide as its buttons need.
- The setup wizard's "Test connection" button, the tools page's "Install" and "Recheck", and the field captions are no longer cut off on a narrow page: the field next to a button gives way and long captions wrap.
- On a host whose login shell is fish, a task whose title contains an apostrophe ("can't …") could not get its tmux window: fish rejected the command as having unbalanced quotes.
- A remote directory written with `~` (such as `~/workspaces/…`) works: before, the app looked for a directory literally named `~` there, so creating a window or looking up a workspace's pull request failed.
- Dialogs — "Add task", confirmations, error messages — open over the app's window, on its monitor, instead of on the primary monitor.
- Closing the app no longer logs an error from a pull-request check that was still scheduled.
- The pull-request lookup no longer logs a warning every few minutes when a task's directory has no pull request or is not a git checkout.
- When creating a task's tmux window fails, the error alert and the task's terminal area show the remote's error message instead of only "(see log)"; before, the terminal area kept saying "Creating the tmux window …" after the failure.

### Removed

- "Add category from URL…" is gone from the add menu and the task list's context menu: the setup wizard's last page ("Setup wizard…" in the add menu) sets up a category from a repository URL the same way. "Add category…" stays for an empty category.

## [2026-10-11] - 2026-10-11

### Added

- The settings dialog shows the hosts as one table: ssh destination, whether it is switched on, its workspaces directory and its RefactoringMiner directory — each per host instead of one RefactoringMiner directory for all of them. An older settings file is rewritten in the new shape when the app starts.
- Adding a host in the setup wizard asks for its workspaces directory, and so does the first category on a host that has none yet.
- The setup wizard lists the configured hosts to pick from, next to "Another machine" for a new one, and fills in a known host's workspaces directory right away.
- The setup wizard's recommended-tools page has a "Recheck" button next to "Install", so a tool installed some other way while the page is open shows as found.
- Hovering "Install" on that page says what it does and where it puts it: RefactoringMiner under `~/.contextswitcher`, ast-grep and codegraph in `~/.local/bin` with their Claude Code skills.
- When a category of that name already exists, the setup wizard and "Add category from URL" offer to rename the existing category (proposed as `<name>-<host>`, or `<name>-local`) instead of only reporting the clash; "Back" returns to the wizard.

### Changed

- The task and category forms call the remote field "Host" and offer the configured hosts in a drop-down; any other ssh destination can still be typed in.
- The setup wizard proposes a workspaces root next to the existing ones on the chosen host, no longer next to another machine's.
- The setup wizard skips the browser-extension page when the extension is already installed in Firefox or Chrome, even with the browser closed or on the first start, not only when it is connected.
- The setup wizard's last page is headed "First category", or "Add a category" when categories already exist, instead of "Your first project".
- The setup wizard's "Finish and check up" button sits right after "Finish", and the wizard is wider, so the button labels and the last page's explanation are no longer cut off.
- Pressing *Restart to update* in the What's new window now greys out its buttons and reads "Closing …" while the app saves and leaves, instead of seeming to hang.

### Fixed

- A category's setup task keeps the CLAUDE.md template in its notes, so it can be started again from the task itself when the first launch went wrong.
- A remote whose login shell is fish now works: task status, the terminal mirror's reattach and the pull request lookup no longer fail there with fish syntax errors.
- Creating a task or category whose workspaces root cannot be created on the remote now stops with the remote's error, instead of starting the session in the home directory.

## [2026-10-10] - 2026-10-10

### Added

- Hosts can be switched off from the filter menu (Hosts) or in the settings: a switched-off host is no longer contacted in the background, and its tasks and categories are hidden until it is switched on again.
- Double-clicking the divider beside the task list widens the list just enough that no category name is cut off with "…".

### Changed

- A remote that stops answering is no longer asked every few seconds: after three failed attempts the background checks pause for a minute, then for up to five minutes, while the other hosts keep updating.

### Fixed

- Adding a remote Claude task whose description starts with a pasted image no longer fails: the tmux window is named after the first line of text instead.
- Saving the settings dialog no longer resets "Remove merged tasks after (days)" and "Copy each finished Claude reply as Markdown" to their defaults.

## [2026-10-01] - 2026-10-01

### Fixed

- On a busy start the main window is drawn in the color theme from the first frames on, instead of in unthemed greys until about 20 seconds after start.

## [2026-09-28] - 2026-09-28

### Added

- The setup wizard's task-repository page offers "Start right away", finishing the wizard without setting up a first project.

### Fixed

- A browser tab on a pull request is matched to a task listing a link to a comment of that pull request.
- The first-start setup wizard is drawn in the configured color theme.
- The "Start a Claude session" dialog of a task with a very long title scrolls the title instead of growing past the screen.

## [2026-09-27] - 2026-09-27

### Changed

- On Windows, resuming a local task's Claude session continues the task's `claude.sessionId` when its transcript exists, instead of always the newest one in the folder — so an existing local session can be taken over by a task file naming its id.

### Fixed

- **Messages armed for a delayed send stay armed across an app restart**, instead of coming back as plain queued messages nothing sends.

## [2026-09-23] - 2026-09-23

### Added

- Initial release.
- A task's context menu offers "Copy link" for its `contextswitcher://task/…` deep link, e.g. to link it from OneNote.

### Changed

- A task's Firefox tab group is named after its first PR, MR or issue number (`#17148`) instead of its tmux window number, which remains the fallback.

### Fixed

- *Restart to update* in the "What's new" window works while "Checking remote …" is still running.
- "Review comments sync" no longer queues qodo findings with a stack of repeated "The issue below was found during a code review…" lines: the preamble is stripped again now that qodo writes its headings without `##`.

[Unreleased]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-27...main
[2026-11-27]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-26...v2026-11-27
[2026-11-26]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-25...v2026-11-26
[2026-11-25]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-22...v2026-11-25
[2026-11-22]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-21...v2026-11-22
[2026-11-21]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-20...v2026-11-21
[2026-11-20]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-19...v2026-11-20
[2026-11-19]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-18...v2026-11-19
[2026-11-18]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-17...v2026-11-18
[2026-11-17]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-16...v2026-11-17
[2026-11-16]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-15...v2026-11-16
[2026-11-15]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-13...v2026-11-15
[2026-11-13]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-12...v2026-11-13
[2026-11-12]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-09...v2026-11-12
[2026-11-09]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-05...v2026-11-09
[2026-11-05]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-03...v2026-11-05
[2026-11-03]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-02...v2026-11-03
[2026-11-02]: https://github.com/contextswitcher/contextswitcher/compare/v2026-11-01...v2026-11-02
[2026-11-01]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-31...v2026-11-01
[2026-10-31]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-27...v2026-10-31
[2026-10-27]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-25...v2026-10-27
[2026-10-25]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-22...v2026-10-25
[2026-10-22]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-19...v2026-10-22
[2026-10-19]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-18...v2026-10-19
[2026-10-18]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-12...v2026-10-18
[2026-10-12]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-11...v2026-10-12
[2026-10-11]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-10...v2026-10-11
[2026-10-10]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-01...v2026-10-10
[2026-10-01]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-28...v2026-10-01
[2026-09-28]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-27...v2026-09-28
[2026-09-27]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-23...v2026-09-27
[2026-09-23]: https://github.com/contextswitcher/contextswitcher/commits/v2026-09-23
