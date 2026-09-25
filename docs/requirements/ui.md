# User Interface

UX reference: task list on the left (as in JetBrains Air), details and status to the right.

## Requirements

### Task list window
`req~task-list-window~1`

The main window lists all tasks (title and status) from the task directory and lets the user activate a task with a single click.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Find a task
`req~task-find~3`

The user can find a task by free text (Ctrl+F focuses the always-visible find field): the task list narrows to the tasks matching the query.
Matching is case-insensitive and covers the fields a user searches by — title, id/group, remote, note, the task's configuration (tmux, terminal, IntelliJ, Claude), the Markdown note body, and especially the task's URLs, so a task is findable by its PR link (the full `https://github.com/JabRef/jabref/pull/16245` or just `16245`).
A task is also found by the text of its chat messages — the ones still queued and the ones already sent — so what the user wrote to Claude finds the task back even when nothing of it was ever copied into the note.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Filter the task list by tag
`req~task-tag-filter~1`

The user can narrow the task list to a chosen set of tags from a picker in the toolbar.
Selecting several tags combines them with AND: only tasks carrying every selected tag are shown.
While a filter is active, each visible row shows only the selected tags, not the task's other tags, so a shared or on-call view never reveals unrelated labels.
With no filter active, every task shows and all its tags are displayed.
A tag on a project group applies to all its tasks: filtering by that tag shows the whole group, as if each task carried the tag.
The filter is not persisted — the app always starts with no filter active.

Tags: windows, linux

Covers:
- feat~task-tagging~1

Needs: dsn

### Filter the task list to tasks awaiting input
`req~awaits-input-filter~1`

The user can narrow the task list to only the tasks awaiting their input from the task-list toolbar's filter menu (`req~task-list-toolbar~2`), for a quick overview of where to work next.
An awaiting task is an active task whose live `@cs_status` is `waiting` (Claude finished its turn), `attention` (Claude asked a question or waits for a permission) or `limit` (the session ran into its usage limit); `working`, done, and suspended tasks never qualify.
The filter item carries the current awaiting count, so it also serves as an at-a-glance overview without turning the filter on.
The filter is not persisted — the app always starts with it off.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Filter the task list to the active desktop's categories
`req~active-desktop-filter~2`

The user can narrow the task list to only the categories on the currently active Windows virtual desktop, from the task-list toolbar's filter menu (`req~task-list-toolbar~2`).
A category is on the active desktop when its `CONTEXTSWITCHER.md` `desktop:` (`req~category-desktop-focus~2`) — or, naming none, the fallback desktop (`req~fallback-desktop~1`) — equals the active virtual desktop's name; ungrouped tasks, and categories without a `desktop:` while the fallback is off, drop out while the filter is on.
As the user switches desktops the list follows the desktop in view.
A find query (`req~task-find~3`) does not lift the filter — the desktop in view is where the user is working — but the find bar reports how many hits lie on other desktops next to the match count, so a query that finds nothing here still says the task exists.
The filter item's text carries the active desktop's name once known.
When the active desktop cannot be determined — non-Windows, an unnamed active desktop, or not yet read — the filter is a no-op and the full list stays visible.
The filter is not persisted — the app always starts with it off.

Tags: windows

Covers:
- feat~task-context-switching~1

Needs: dsn

### Focus the desktop's last task on a desktop switch
`req~desktop-last-task-selection~4`

While the active-desktop filter is on and the user switches to a virtual desktop, the task they last had selected while on that desktop is selected again.
With the filter off the list does not follow the desktop either, so the selection stays where the user put it.
Without this, the selection — and with it the terminal, editor, and queue panes — keeps showing the task of the *previous* desktop next to the new desktop's task list, an inconsistent view (field report 2026-08-23).
A desktop with nothing remembered — never selected on, or its remembered task deleted — selects its first task instead; the previous desktop's task is the foreign view to get rid of, so keeping it is no fallback (field report 2026-09-08).
The memory survives app restarts: it is the switch right after a launch that most needs it.

Tags: windows

Covers:
- feat~task-context-switching~1

Needs: dsn

### Filter the task list to running tasks
`req~running-tasks-filter~1`

The user can narrow the task list to only the running tasks, from the task-list toolbar's filter menu (`req~task-list-toolbar~2`).
A running task is a task with status `active`; paused (`suspended`) and `done` tasks are hidden while the filter is on.
The filter is not persisted — the app always starts with it off.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Energy saver
`req~energy-saver~1`

The user can put the application into an energy saver from the task-list toolbar (`req~task-list-toolbar~2`), for working on battery — on a train, away from power.
While it is on, the application stops all background polling: no running-status poll, no PR-state or review-comment lookups, no refactoring analysis, no periodic tmux reconcile.
Only the mirrored terminal of the selected task keeps updating, so the session the user is actually working in stays live.
The state of a task is fetched when it is needed: selecting a task refreshes that task's live status, and a refresh button in the toolbar runs every poll once.
The setting is remembered across restarts.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Remember the filters across restarts
`req~filter-persistence~1`

The task list comes up filtered the way the user left it: the toolbar filter menu's toggles and the selected tags are restored on the next launch, like the sort order (`req~task-sort-modes~1`).
This deliberately overrides the "the filter is not persisted — the app always starts with it off" sentence of `req~task-tag-filter~1`, `req~awaits-input-filter~1`, `req~active-desktop-filter~2` and `req~running-tasks-filter~1` (without bumping them).
A narrowed list is how the user chose to read their tasks — re-ticking the same two filters after every launch is busywork, and the filter button's badge already says the view is narrowed.
A tag that no longer exists anywhere is dropped from the restored selection instead of hiding every task.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Sort the task list
`req~task-sort-modes~1`

The user can choose the order of the tasks within their groups from the task-list toolbar's Sort menu (`req~task-list-toolbar~2`): alphabetical (the default), last update (most recently changed task file first), or action needed (tasks awaiting the user first: usage limit, then attention, then waiting, then working, then status-less).
Error rows stay first and active tasks stay before suspended ones in every mode; pinned tasks (`req~pinned-tasks~2`) head their block.
The chosen order is kept across restarts.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Group the task list by labels
`req~task-label-grouping~3`

The user can switch the task list from folder grouping to label grouping, from the group menu in the task-list toolbar (`req~task-list-toolbar~2`): tasks then group under their (effective) tags, a task carrying several tags appearing under each; untagged tasks gather in the top ungrouped block, and completed tasks keep gathering in the Done section.
Because the surrounding headers are labels, each task row shows its category (its folder) so "where does this task live" stays answerable at a glance; a root-level task shows none.
The choice is kept across restarts.

Tags: windows, linux

Covers:
- feat~task-tagging~1

Needs: dsn

### Group by PR status
`req~pr-status-grouping~1`

The user can group the task list by the status of each task's pull request, from the same group menu (`req~task-label-grouping~3`).
In a repository that tracks reviews with `status:` labels, the labels are the groups; elsewhere GitHub's own states are: draft, changes requested, ready.
Either way, PRs in the merge queue, merged, and closed PRs form groups of their own, and the groups follow the review workflow's order.
Tasks without a PR stay in the ungrouped block.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Nest groupings
`req~nested-grouping~1`

The group menu (`req~task-label-grouping~3`, `req~pr-status-grouping~1`) takes several ticks at once: each further ticked grouping nests inside the previous one, in menu order — e.g. categories, within each its labels, within each label its PR status.
Tasks without a value at a nested level stay directly under the enclosing header, and each nested header collapses on its own.
With a single tick the list groups exactly as before; the last tick cannot be removed.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Task-list toolbar
`req~task-list-toolbar~2`

The task list carries a compact icon toolbar at the top of its pane, above the find field, gathering the actions that operate on the list as a whole.
It offers, left to right: an add menu (add task, add category), a filter menu (the narrowing filters — awaits input, active desktop, running tasks — plus the tag filter as a submenu), a sort menu, a group menu (folder, labels, PR status), and a "more" menu gathering the occasional actions (open tasks directory, sync tmux windows, restart running tasks, refresh now).
Whatever is used only now and then goes into the "more" menu rather than a button of its own, so the bar does not grow with every new action.
Every control is icon-only so the whole bar stays no larger than its glyph; the filter button shows a small count badge while any narrowing (including a tag selection) is active, and hovering any control names it.
Settings is not part of this toolbar: it is the window's own gear pinned at the top-right corner.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Back and forward through visited tasks
`req~task-history-navigation~1`

The user can step back to the previously selected task and forward again, from a pair of arrow buttons at the left end of the task-list toolbar (`req~task-list-toolbar~2`) and with `Alt+Left` / `Alt+Right`.
Returning to the task worked on before is then one click, rather than a search of the left list — which sorting, filtering and concurrent status updates may have reordered since.
An arrow is greyed out at the respective end of the trail; tasks deleted meanwhile are skipped.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Edit task files in the app
`req~task-file-editing~1`

The selected task's Markdown file — including unparseable ones, so broken YAML can be repaired in place — is shown in an editor lane and can be modified and saved without leaving the application.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Rename a task
`req~task-rename~2`

The user can rename a task from the task list (context menu). Renaming changes the task's title without touching any other content of the file — comments and formatting survive. A task with a tmux window also gets the window renamed on its remote, so the tmux status line shows the same "nice" name as the task list.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Toggle task status from the list
`req~task-status-toggle~2`

The user can change a task's status directly from the task list: clicking the status label toggles between `active` and `suspended` (a `done` task resumes to `active`), and the row's right-click menu offers the same plus `Mark done`. The change is written back to the task file, touching nothing else — comments and formatting survive. Suspend/resume side effects are specified in `req~task-suspend-resume~2`.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### A pending Claude update gets its restart
`req~claude-update-restart~1`

When Claude Code has installed an update and asks for a restart ("Restart to update" in its footer), the session is restarted on the new version — back in the same window on the same conversation — once its turn is over, never mid-answer.
The desktop does this on its own while it runs; on the phone the user asks for it, since two apps restarting the same session would collide.

Tags: windows, linux, android

Covers:
- feat~task-context-switching~1

Needs: dsn

### Running indicator per task
`req~task-running-indicator~2`

Each task row shows a live indicator of what its Claude window is doing, so the user can see at a glance which tasks are working, waiting for input, or explicitly asking for something.
A Claude session publishes its own state as the `@cs_status` tmux window user option (`working`, `waiting`, `attention`, `done`); ContextSwitcher polls it and colours the indicator accordingly.
A session blocked on its usage limit publishes nothing — no hook fires for it — so ContextSwitcher detects it from the pane and shows a red dot (`dsn~claude-limit-detection~2`).
A session whose turn was cut short by an API error is detected the same way and simply told to `continue` (`dsn~api-error-auto-continue~1`) — nothing there needs the user.
A session that has unpacked a Claude Code update is restarted the same way (`dsn~claude-update-restart~6`), since only a fresh process runs the new version.
`attention` is set by Claude Code's `Notification` hook — Claude asked a question or waits for a permission — and is distinct from the mere end-of-turn `waiting`.
A window that never publishes a status shows no indicator.
A window that is alive but runs no Claude at all — a start that went wrong, or a session that exited — offers a one-click repair instead (`dsn~start-claude-button~2`).

Tags: windows, linux

Covers:
- feat~task-context-switching~1
- feat~remote-development-context~1

Needs: dsn

### PR-state indicator per task
`req~pr-state-indicator~2`

A task whose `browser.urls` carries a GitHub pull-request URL shows a small icon reflecting the PR's live state — open, draft, merged, or closed — so the user can see at a glance which tasks' work has landed without opening the browser.
The state is looked up with the local `gh` CLI and refreshed periodically; a task without a PR URL (or whose state cannot be resolved) shows no icon.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Link icons for a task's other URLs
`req~task-link-icons~2`

A task row shows the `browser.urls` entries that are not pull requests left of its PR icons, so the repository, issue, or build page a task carries is one click away like its PRs are — without switching to the task and without opening its file.
A single link is one icon; several links share one counted icon that lists them on click, because a row of identical chain links crowds out the task title.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Delete a task
`req~task-delete~2`

The user can delete a task from the task list's right-click menu.
Deletion removes the task's Markdown file and requires an explicit confirmation, because it cannot be undone.
A task whose live context still exists gets the suspend teardown first — deleting the file must not orphan a running tmux window or open browser tabs that nothing references anymore.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Add a task from the app
`req~task-create~1`

The user can create a task from the main window by entering a title: the app creates the Markdown task file — optionally inside a project-group folder given as `folder/` prefix — and opens it in the editor lane for manual completion. A group folder without any tasks is still shown as a group, offering a button to add its first task.

Tags: windows, linux, android

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Fork a task's Claude conversation into a new task
`req~task-fork~1`

The user can fork the task shown in the terminal: a new task in the same category, whose Claude session continues the source task's conversation (not the source session itself, which is left running) with an instruction the user types.
The new task is created in the background: the user stays on the task they forked from.
The dialog asks what the fork should do and offers the same model and effort choice as adding a task.

Tags: windows, linux, android

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Task title from the Claude session
`req~claude-title-sync~1`

A live-created task starts from a task **description** the user types; a good short title only emerges once the session works.
The creation prompt asks Claude to derive one and publish it (as the `@cs_title` tmux window option); ContextSwitcher picks it up and replaces the task's title, while the original description remains in the task's notes.
A published title is consumed once — it never overrides a rename the user makes later.

Tags: windows, linux

Covers:
- feat~remote-development-context~1
- feat~tasks-as-markdown-files~1

Needs: dsn

### Claude sessions start in auto mode
`req~claude-auto-permissions~1`

The user can choose that every Claude session ContextSwitcher starts runs without permission prompts ("auto mode"), so a freshly created live task bootstraps unattended — worktree setup, `@cs_title`/`@cs_workspace` publishing — while the user is already writing the next task's description.
Off by default: skipping permission prompts is a trust decision the user takes explicitly.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Create a task from a pull request
`req~task-from-pr~1`

The user can create a task by entering a GitHub PR URL.
When an existing task already carries the URL in its `browser.urls`, that task is selected instead of duplicated.
Otherwise a fresh tmux window is created on a chosen remote, a Claude session is started in it primed with the PR as context, and the task file records the tmux target and the PR URL.

Tags: windows, linux

Covers:
- feat~task-context-switching~1
- feat~remote-development-context~1

Needs: dsn

### Per-group configuration file
`req~group-config-file~1`

A project-group folder can hold a `CONTEXTSWITCHER.md` defaults file. It is never listed as a task. The user can create it from the group's header when it is missing (a mostly-commented skeleton) and open it for editing when it exists. Its `remote` and working-directory values seed a new task created in the group (`dsn~group-config-apply~2`).

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Task folder grouping
`req~task-folder-grouping~1`

Subfolders of the tasks directory are project groups: the task list shows one collapsible section per subfolder (folder name as header), with root-level tasks in an ungrouped section on top. A group's collapsed/expanded state is kept while the app runs, even when the list refreshes.

Tags: windows, linux

Covers:
- feat~task-context-switching~1
- feat~tasks-as-markdown-files~1

Needs: dsn

### Pinned categories
`req~pinned-categories~1`

A category can be pinned from its header's context menu: pinned categories are listed above the unpinned ones, so the few categories worked on daily stay at the top of a long list.
The pin is a property of the category (kept in its `CONTEXTSWITCHER.md`), not a view setting, so it survives restarts and reaches every machine the tasks directory is synchronised to.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Pinned tasks
`req~pinned-tasks~2`

A task can be pinned from its row's context menu: pinned tasks are listed above the unpinned ones of their block, in every sort mode, so the tasks worked on right now stay in view within a long category.
A pinned task is marked as such in the list — the row carries a pin next to its title.
The pin is a property of the task (kept in its file), not a view setting, so it survives restarts and reaches every machine the tasks directory is synchronised to.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Edit settings from the app
`req~settings-editor~1`

The user can open the application settings (`settings.yaml`) in a popup editor from a gear button in the status bar, edit and save them without leaving the app.
Settings are read at startup, so a restart applies the changes.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Select the UI theme
`req~theme-select~1`

The user can choose the application's color theme from the settings dialog — light, dark, or system (follow the operating system's light/dark preference), each in the Everforest recolouring or the plain palette — and the choice takes effect immediately, without a restart.
The choice is stored in `settings.yaml` (`theme:`); an absent or unrecognized value means the default theme, Everforest following the operating system.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Bash cursor keys in text fields
`req~readline-keys~1`

The user can switch the application's text inputs to the readline (bash/emacs) cursor chords — Ctrl+A and Ctrl+E for the start and end of the line, Ctrl+B/Ctrl+F and Alt+B/Alt+F to move by character and word, Ctrl+D/Ctrl+H/Ctrl+K/Ctrl+U/Ctrl+W and Alt+D to delete around the caret.
The switch is a `settings.yaml` option, off by default, because the chords take Ctrl+A away from select-all; turning it on in the settings dialog applies immediately, without a restart.
It holds in every text input of the app — the message boxes, the task notes editor, the find field, and the dialog fields — and never in the embedded terminal, whose remote shell has readline of its own.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Keep the window on all virtual desktops
`req~window-desktop-pin~2`

The user can make the main window show on **all** Windows virtual desktops, and that choice survives an application restart.
Windows' own Task View toggle ("Show this window on all desktops") is bound to the window handle and silently lost when the app exits, so the app re-applies the pin itself at startup, driven by a `settings.yaml` option.
Turning the option on in the settings dialog pins immediately on Save — no restart needed — and the outcome (pinned, or why not) shows in the status bar.

Tags: windows

Covers:
- feat~task-context-switching~1

Needs: dsn

### Window position remembered per virtual desktop
`req~window-position-per-desktop~1`

While the window shows on all virtual desktops, each desktop keeps its own window position and size: different contexts arrange their browser and apps differently, so the pinned window should sit where it fits *that* desktop's layout.
The geometry is remembered when the user switches away from a desktop and restored when they return, and it survives an application restart (like git gui's geometry entry in its config).
A remembered position that lies on no current screen — the monitor configuration changed — is discarded instead of applied, so the window never comes back out of reach.

Tags: windows

Covers:
- feat~task-context-switching~1

Needs: dsn

### Window geometry restored at startup
`req~window-geometry-restore~1`

The window opens where it was closed: position and size from the last exit are restored at startup, on every platform and independent of the all-desktops pin, instead of the fixed default geometry.
The same off-screen rule applies — a last-exit position on a no-longer-connected monitor is discarded and the default geometry used.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Add a category from the app
`req~category-create~1`

The user can create a new project group (category = subfolder of the tasks directory) from the app: a toolbar button and a context menu on the task list's empty area both open a name dialog; the folder is created and shows as an empty group offering its first task.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Add a category from a repository URL
`req~category-from-url~3`

The user can create a category from a project's repository URL alone: the category is named after the repository and configured for it, and the project is set up on the remote by Claude — the workspaces root created, the repository cloned into it, and the root's `CLAUDE.md` written from a shipped template — without the user editing a config or typing a setup prompt.
The user picks how the repository is worked with (fork and PR upstream, write access, own repository, semantic fork); that choice decides both whether a fork is cloned and which template is filled in.
A first message can be given right away, so the setup session continues into that work instead of idling once the root is in shape.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Guided first start
`req~setup-wizard~1`

On its first start — no tasks yet — the app walks the user through the initial setup in dialogs instead of leaving them to read `settings.yaml` and `TEMPLATE.md` comments: where Claude sessions run (this machine or a remote over ssh), which remote — checked right there, so a missing key or a missing `tmux` is found before the first task rather than at its first failure — an existing task repository to clone, and a first project's repository URL, which is set up the way *Add category from URL…* sets one up.
Every page can be skipped or the wizard cancelled; the app then starts as before.

Tags: windows, linux

Covers:
- feat~remote-development-context~1

Needs: dsn

### Per-group note link
`req~group-note-link~1`

A project group's `CONTEXTSWITCHER.md` can carry a `note:` URL pointing to the project's page in a note tool (metadata for the folder). The group header offers opening it. For OneNote, the stored URL is the `onenote:…` link form so the **desktop** app opens — the `onedrive.live.com` web URL would open the browser instead.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Per-group repository link
`req~group-repo-link~1`

A project group's `CONTEXTSWITCHER.md` already names the project's GitHub repository in `repo:` (for a live task's bootstrap prompt).
The group header offers opening that URL too, so the project's home page is one click away instead of buried in the config — the same affordance as the note link, on the key that is already there.
No second URL key is introduced: one project, one repository, one setting.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Deep links into the app
`req~deep-link-url~1`

Other tools (OneNote project pages, READMEs, browsers) can link **to** a task: `contextswitcher://task/<id>` selects the task and brings the app to the front, `contextswitcher://switch/<id>` additionally runs the full switch.
The counterpart of the per-group note link (`req~group-note-link~1`), completing bidirectional navigation between notes and tasks.
The link works whether or not the app is already running; a second instance is never opened.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Copy a category's deep link
`req~category-link-copy~1`

A category's context menu offers "Copy link", putting `contextswitcher://category/<name>` on the clipboard.
Pasted into the project's OneNote page (or a README), it links back from the note to the category — the other direction of the per-group note link (`req~group-note-link~1`), which so far existed for tasks only.
Following such a link selects the category's header row and raises the app.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Copy a task's deep link
`req~task-link-copy~1`

A task's context menu offers "Copy link", putting `contextswitcher://task/<id>` on the clipboard.
Pasted into a OneNote page (or a README), it links back from the note to the task; following it selects the task and raises the app.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Rename a project group
`req~group-rename~1`

The user can rename a project group from its header's context menu.
The folder on disk is renamed; the group's tasks keep their files and follow with new ids (`newgroup/name`).
An existing folder of the target name is never merged into — the rename is refused.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Delete a project group
`req~category-delete~1`

The user can delete a project group (category) from its header's context menu.
A confirmation names how many tasks the category holds and that the action cannot be undone; on confirm the folder and everything in it (task files, its `CONTEXTSWITCHER.md`, note) is removed from disk.
Each running task in the category (a live tmux window on a remote) is then confirmed one by one with the normal task-delete dialog, so its window / transcript / working directory can be torn down; cancelling one of those aborts the whole category delete, and tasks already deleted stay deleted.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1

Needs: dsn

### Move a task between groups
`req~task-move-group~3`

The user can drag a task row onto a group header (or a row inside the group, or its "Add task…" row) to move the task into that project group; dropping it onto a root-level row moves it out of its folder.
While dragging near the list's top or bottom edge, the list auto-scrolls so drop targets outside the viewport become reachable.
The task row's context menu additionally offers "Move to category" listing every category (and the root) directly.
After the move the task stays visible: the row is selected and scrolled into view, a collapsed target category expands.
The task's Markdown file moves on disk accordingly (the task id follows the new path); an existing file of the same name in the target folder is never overwritten — the moved file gets a suffixed name instead.

Tags: windows, linux

Covers:
- feat~tasks-as-markdown-files~1
- feat~task-context-switching~1

Needs: dsn

### Queue messages for a task's chat
`req~message-queue~2`

Below the task-file editor, the user maintains a per-task queue of draft chat messages: an empty box on top adds a new message, existing boxes are edited in place, deleted via a hover button, and reordered by drag'n'drop.
The top box is the next message to send and carries a send button; a box accepts a pasted clipboard image (e.g. a screenshot), which travels with the message.
The queue survives an app restart.

Tags: windows, linux, android

Covers:
- feat~message-queue~1

Needs: dsn

### Compose-box key conventions stay uniform
`req~compose-key-conventions~5`

Every box that holds a message for a Claude chat — the queue's add box, its card edit boxes, and the Add-task dialog's title/description field — advances with the same keys:
plain Enter and Shift+Enter insert a newline, and **three** presses in a row take the text out of the box — three plain Enters **at the end of the box** commit it (the two blank lines marking the submit are dropped from the stored text, and an all-blank box never commits; with the caret anywhere else — e.g. moved up into the text — Enters only insert newlines, changed in `~2`), three Ctrl+Enters do the box's stronger thing: queue **and send** for the queue's add box, create the task for the dialog.
Three, not one, everywhere (changed in `~4`): a single Ctrl+Enter used to fire at once, and a chord that has to be meant cannot go off mid-sentence.
Each Ctrl+Enter colours the box a shade stronger so the run is visible before it fires, and a key or a click between two presses drops it.
The chords are one shared implementation, not a convention to copy, so a chord added or changed for one box appears in the others and muscle memory carries across the app.
Card edit boxes carry the Ctrl+Enter chord too (added in `~5`), where the stronger thing is saving the edit and sending that very message; they stay exempt from the triple-Enter commit, since blank lines are content while editing an existing message.
Before `~5` a card's single Ctrl+Enter saved the edit and jumped to the add box: the first press of an intended chord left the card silently, and the two presses behind it landed in the add box, so the chord read as doing nothing at all.

Tags: windows, linux

Covers:
- feat~message-queue~1
- feat~tasks-as-markdown-files~1

Needs: dsn

### Last sent message stays visible
`req~last-sent-message~2`

Below the queued messages, the task's most recently sent message stays visible in its own read-only box, visually separated by a different background — switching back to a task recalls what its chat was last asked to do.
The earlier sends are kept too and sit above it, out of view until the user scrolls up in that box: what was asked before is one scroll away, without the pane growing for it.
The record survives an app restart and follows the task across machines like the queue itself.

Tags: windows, linux

Covers:
- feat~message-queue~1

Needs: dsn

### Failure diagnostics
`req~freeze-diagnostics~2`

When the window stops responding — drawn, but hover and clicks dead — the log names what the UI thread was doing at the time, so a field report of "not responding" can be traced without a profiler attached.
Motivated by the 2026-08-27 report of a slow, unresponsive startup on Windows that the log could not explain: a busy FX thread logs nothing by itself.

A background job that dies of an unexpected exception says so in the status bar as well as the log.
Motivated by the 2026-09-12 report of `Exception in thread "" java.lang.NoClassDefFoundError: com/contextswitcher/discovery/WorkspacePrLookup` on the console: the PR lookup had stopped running and the running app showed nothing about it.

Tags: windows, linux

Needs: dsn

### Responsive during startup
`req~startup-responsiveness~1`

Startup work that does not have to run on the UI thread — disk IO, remote sync, heavyweight class loading — runs in the background, so the window responds as soon as it shows.
Motivated by the 2026-08-31 startup log: 1 s+ FX stalls while task files parsed and the terminal widget's classes loaded from a cold Windows disk.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### The app shows the commit it runs on
`req~running-commit~1`

Every app shows the commit it was built from — short sha with the commit's date and time — so which version is running, and whether it is the latest, can be read off the screen; the sha can be copied for a bug report.

Tags: windows, linux, android

Covers:
- feat~task-context-switching~1

Needs: dsn

### Restart to update
`req~restart-to-update~2`

The application tells the user when a newer version exists and offers to take it: a control appears in the task-list toolbar (`req~task-list-toolbar~2`) once the checkout the app runs out of is behind its upstream, and clicking it closes the app and starts the new version.
It also names the version it is running itself, so a field report ("it still does X") carries the state it was seen on.
The user decides when — an update never interrupts a switch, a note being written, or a mirrored session on its own.
There is no live code reload: JavaFX classes and the packaged runtime image cannot be swapped inside a running process, so "update" means "restart into the rebuilt app".

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

### Memory diagnostics in the log
`req~memory-diagnostics~1`

The app periodically writes its memory figures to the log (`~/.contextswitcher/logs/`), so a crash in the field leaves a growth curve behind: whether the process's memory rose over hours (a leak, and which kind) or the machine itself ran out.
Motivated by the 2026-08-19 field crash on Windows — a *native* `Chunk::new` malloc failure (the JIT compiler's arena), which no heap monitoring would have seen coming.

Tags: windows, linux

Needs: dsn

## Design

### Rename via textual title replacement
`dsn~task-rename-title~2`

The context menu's rename dialog replaces (or inserts) the frontmatter `title:` line textually — no YAML round-trip; the scalar is serialized by SnakeYAML (`TaskFileParser.yamlScalar`). The directory watcher refreshes the row; an editor lane showing the file is reloaded. After the save, the rename listener runs `tmux rename-window -t <target> '<new title>'` (`TmuxResurrect.renameWindowCommand`, POSIX single-quoting via `SshCommandRunner.quote`) on the task's remote in the background; a failure only logs — the file rename already happened.

Tags: windows, linux

Covers:
- req~task-rename~2

Needs: impl, utest

### Toggle status via textual replacement
`dsn~task-status-cycle~2`

Clicking a task row's status label calls `TaskStatus.toggled()` (active → suspended, suspended/done → active); the row's right-click menu (`TaskListCell.taskMenu`) offers Suspend/Resume plus `Mark done`, reaching every status. Both route through `TaskFileParser.withStatus(content, status)`, which replaces (or, when absent, inserts after `title:`) the frontmatter `status:` line textually — no YAML round-trip. `MainWindow` saves the file; the directory watcher refreshes the row and, if that file is open in the editor lane, reloads it. The label click is consumed so the row's double-click-to-switch does not also fire. The suspend confirmation and teardown/resume side effects are designed in `dsn~task-suspend~6`; marking a task `done` triggers the same teardown (`dsn~task-complete-suspend~1`).

Tags: windows, linux

Covers:
- req~task-status-toggle~2

Needs: impl, utest

### A status flip keeps its row in view
`dsn~status-flip-keeps-row-in-view~1`

`status:` is the task list's primary sort key (`dsn~task-sort-modes~2`), so suspending or resuming a task moves its row into another block of the list — several screens away in a long list.
The rebuild the watcher triggers preserves the selection by id but deliberately does not scroll (a background rebuild must not move the viewport, `dsn~task-move-dnd~6`), so the highlight follows the row off screen and the list reads as having lost the task — field report 2026-09-11: a suspended task resumed from its browser tab was selected and started, but its row was nowhere in view afterwards.
`MainWindow.rewriteFrontmatter` therefore re-requests the selection of the task it just wrote whenever that task is the selected one (`selectedTaskId`), which makes the next rebuild select *and* reveal the moved row.
It sits in the shared rewrite rather than in the status writers, so a `pinned:` flip — which lifts the row to the head of its block — is covered by the same line; a write to a task the user is not on scrolls nothing.

Tags: windows, linux

Covers:
- req~task-status-toggle~2
- req~browser-tab-selects-task~2

Needs: impl, utest

### Marking done ends the live context
`dsn~task-complete-suspend~1`

Marking a task `done` is a form of completion, so it ends the task's live context exactly as suspend does rather than leaving a finished project holding a remote session. In `MainWindow.setStatus`, the `done` transition joins the suspend branch (`completing || suspending` → `tearingDown`): after the shared confirmation (`confirmTeardown(task, "Complete")` — same dialog as suspend, only the title verb differs; still silent when Claude is idle and a `claude:` section exists), it strips the `window:` line and writes `status: done` textually, then runs the suspend orchestrator (`dsn~task-suspend~6`: `TmuxKillAction` + `BrowserCloseAction`, per-action chips labelled "(suspend)"). No resurrect is wired on `done` — a completed task is not meant to resume automatically; the row's "Resume (active)" menu still flips it back to active.

Tags: windows, linux

Covers:
- req~task-suspend-resume~2

Needs: impl

### Category creation UI
`dsn~category-create-ui~2`

`MainWindow.addCategory` (the toolbar add menu's `Add category…` item, `dsn~task-list-toolbar~3`, and the `ListView`'s own context menu, which JavaFX shows where a cell sets none — i.e. the empty area) asks for a name, slugs it like task file names (`TaskFileParser.slug`), and creates the folder via `TaskFileAccess.createFolder` (`Files.createDirectories`, idempotent). The repository's folder watcher picks the directory up and shows it as an empty group with its "Add task…" row.
While the active-desktop filter is on (`dsn~active-desktop-filter~6`) and the active desktop is known, both entry points create the category *for that desktop*: `addCategory` and `addCategoryFromUrl` (`dsn~category-from-url~4`) pass `MainWindow.filterDesktop` down, so the written `CONTEXTSWITCHER.md` carries a real `desktop:` line — the same pre-fill the filter's empty state does (`dsn~desktop-filter-empty-state~2`). Without it a category created under the filter would be hidden by that very filter the moment it appears.
Creation then focuses the new category (`openGroupConfig`): its `CONTEXTSWITCHER.md` opens in the editor and its header row is selected. Because the watcher has not delivered the folder yet, the header row does not exist when `addCategory` runs, so the group name is held as `pendingGroupSelection` and the old (task) selection is cleared; the next `rebuildRows` selects the header once it appears, ahead of any preserved task selection. Without this the previously selected task would be restored on that rebuild, switching the editor and terminal mirror away from the new category.

Tags: windows, linux

Covers:
- req~category-create~1

Needs: impl, utest

### Category from repository URL
`dsn~category-from-url~4`

`MainWindow.addCategoryFromUrl` (next to `Add category…` in the toolbar add menu and the empty-area context menu) asks for the repository URL — pre-filled from the clipboard when that holds one — and the workspaces root, plus a `Repository type` combo (`RepoType`, `dsn~claude-md-templates~1`, defaulting to write access) and an optional first message.
The root follows the URL as `<parent>/<name>-workspaces` until edited by hand; `name` is `TaskFileParser.repoName` (the URL's last path segment, `.git` dropped, slugged like a folder name; non-URLs disable OK), `parent` is the parent directory of the first configured category's `workspacesRoot` (`MainWindow.workspacesParent`), else the skeleton's `/data/koppor`, so the new root lands next to the existing ones.
The remote is the `Add remote Claude` fallback (`effectiveRemote` on an empty config: the first configured remote); without one the dialog does not open.
OK creates the folder and writes `TaskFileParser.groupConfigForRepo` — the skeleton's example lines as real values: `remote`, `workspacesRoot`, `mainCheckout: <root>/<name>`, `repo` (no `tags:` line since `~4` — a category is not a tag), plus `desktop:` when the desktop filter is on (`dsn~category-create-ui~2`) — then starts a live task in the new group (`dsn~task-create-live~8`, whose `mkdir -p` of the workspaces root is the remote-side setup step; no worktree clause, since there is no clone yet) with `MainWindow.categorySetupDescription` as the description and the picked type's template as the prompt's appendix: clone the repository into the `<name>` subdirectory (the `mainCheckout`), then write the root's `CLAUDE.md` from that template — placeholders replaced, the build/test and pull-request sections filled from the clone's README, build files and CI workflows, inapplicable sections deleted, the rest kept.
The type and the first message only shape that description: a `fork()` type's clone clause asks for a fork of the repository (`gh repo fork --clone`, which reuses an existing fork) with the original kept as `upstream`; a non-blank first message is appended as "After setting things up, please do the following: …", so the setup session runs straight into the actual work instead of idling.
No extra queue entry and no second prompt — the initial prompt carries both, which is one round-trip and nothing to keep in sync.
Model and effort are the `Add task…` dialog's boxes (`dsn~claude-mode-select~3`), pre-filled from and remembered as the last pick — nothing hardcoded, so the setup session runs at whatever model the user is currently working with.
The setup task is an ordinary live task: Claude publishes its title, the row shows its progress, and it is completed like any other once the root is in shape. An existing folder of that name refuses.

Tags: windows, linux

Covers:
- req~category-from-url~3

Needs: impl, utest

### Workspace-root CLAUDE.md templates
`dsn~claude-md-templates~1`

`RepoType` is the shipped answer to "what kind of repository is this": a fork the user PRs upstream from, a repository they have write access to, their own one-person repository, or a semantic fork carrying upstream plus a stack of open PRs.
Each constant names a workspace-root `CLAUDE.md` template — a classpath resource next to the class, `com/contextswitcher/ui/claudemd/<type>.md` — and carries whether that way of working clones a fork (`fork()`, true only for the PR-upstream type).
`template()` reads the resource; a missing one is a packaging bug and throws.

The templates describe the *workspaces root*, not the project: purpose and location, the layout (primary clone plus per-task worktrees named `<YYYY-MM-DD>-<slug>`), the remotes and which one is pushed to, the branch base, resuming and cleanup, and code-comment expectations.
What only the clone can answer — build, test and lint commands, PR conventions — is left as `<placeholder>` text for the setup session to fill in from the README, the build files and the CI workflows.
The repository's own `CLAUDE.md`/`AGENTS.md` inside the checkout is a separate file and stays untouched.

Shipping the text beats describing it: the previous prompt asked Claude to model the file on the sibling `*-workspaces/CLAUDE.md` files, which reads two or three unrelated projects' conventions into the new one and produces nothing at all on a machine where this is the first workspaces root.
The template travels as the live task's prompt appendix (`dsn~task-create-live~8`) rather than inside the description, because the description also becomes the task's `title:`, its file name and the tmux window name — none of which survive a multi-kilobyte multi-line value.

Tags: windows, linux

Covers:
- req~category-from-url~3

Needs: impl, utest

### First-start wizard
`dsn~setup-wizard~8`

`Main.runSetupWizard` replaces the clone offer at the fresh-machine moment (`dsn~task-git-clone-setup~2`: the task directory missing or empty and not a git checkout — an existing directory is never re-prompted) with `SetupWizard.show`, one `Dialog` whose content is swapped between four pages by *Back*/*Next* (their `ACTION` consumed, so the dialog stays open; the last page's *Finish* closes it with a `Result`, *Cancel* with none).
Page one asks where sessions run — *remote over ssh* (default), *in WSL on this machine* (Windows only, `dsn~wsl-sessions~1`) or *this machine*, worded per OS: on Windows a local session runs in the app's own terminal pane and cannot be detached (`dsn~task-create-local~4`).
The WSL choice is offered only on Windows, where WSL exists at all, and it is the one place a `wsl:<distro>` host is composed.
This machine is checked as soon as the wizard opens (`localVerdict`, off the FX thread, the `Supplier` injected so a test needs no tools): `tmux -V` — not on Windows — and `claude --version`, each resolved like the local launcher resolves them (`RequiredTools.resolve`), since a GUI app's `PATH` is thinner than the login shell's; the verdict sits under the local choice, and *Next* with that choice repeats the check and only moves on when it passes.
Page two is about the chosen machine and shows the fields that choice needs — the ssh destination, or the WSL distribution in an **editable** `ComboBox` filled from `wsl.exe --list --quiet` (blank means the default distribution).
One page with two field groups toggled by `visible`/`managed`, not a seventh page, so the *Back*/*Next* skipping stays as it was.
The listing arrives as UTF-16LE decoded as UTF-8, so its non-printable bytes are dropped rather than teaching the runner a second charset for one call; the box is editable for a name that does not survive that.
The check is the same for both — *Test*, and *Next* itself while no check has succeeded — and routes by host, so a WSL distribution is probed through `wsl.exe` and a remote through ssh, both with the same `REMOTE_PROBE` and the same `remoteVerdict`; only the wording of the retry hint differs (`wsl -l -v` rather than `~/.ssh/config`).
For the ssh destination it takes: *Test connection*, and *Next* itself while no check has succeeded, run `REMOTE_PROBE` through the `SshCommandRunner` off the FX thread — `sh -lc 'PATH=$PATH:$HOME/.local/bin; echo $HOME; tmux -V; claude --version'`, a login shell plus the native installer's directory, because a non-interactive ssh command's `PATH` would miss a `claude` the tmux window later finds.
`remoteVerdict` reads the answer: no output at all is ssh's failure (its first stderr line shown); otherwise the first line is the home and a missing `tmux …` or `… (Claude Code)` line names the tool to install (`toolsVerdict`, shared with the local check).
A success shows both versions and moves on, a failure keeps the page — the verdict names what to fix, and the user can still cancel. The remote's `$HOME` is kept as the parent of the proposed workspaces root, since the app cannot know a remote home otherwise.
Claude's authorization is deliberately not checked: Claude Code has no offline "logged in" query, and a credentials-file heuristic misreads an Enterprise or Bedrock setup; the first session's own login prompt is visible in the mirrored terminal.
Page three recommends tools (`dsn~recommended-tools~1`), one row each with a live status and an *Install* button; nothing on it gates *Next*. It is skipped for a local choice on Windows, where the installers cannot run.
Page four is the browser extension, the one part the app cannot install: what it does, the WebSocket port and token its options page needs (the token in a read-only field with a *Copy* button, since retyping it from `settings.yaml` is where people stall), a button opening the configured browser's install notes on GitHub through the app's URL opener, and — on a re-run, when `ExtensionServer` is up — which browser's extension is connected right now (`SetupWizard.Extension`, filled by `Main` from the settings and the server).
Page five is the old clone offer as a field: a task-repository URL, empty to start fresh.
Page six is the first project — repository URL (clipboard-prefilled like `dsn~category-from-url~4`), the workspaces root following it as `<remote home or user.home>/<name>-workspaces` until edited, and the `RepoType` — all optional.
Only a session on Windows itself gets Windows path separators there: a WSL root lives on the Linux side of the boundary and is proposed under the probed `$HOME` like a remote one.
Back and Next step over the skipped pages (`skipped`: the remote page for a local choice, the tools page for local on Windows), so a skipped page stays skipped in both directions.
Each page swap calls `sizeToScene`: a `Dialog` sizes itself once at show, and a taller later page pushed *Finish* out of the window.

Applying the answers is `Main`'s: a remote not yet in `settings.yaml`'s `remotes` is patched in (`YamlPatch.set`, like the settings editor), and so is `refactoringMinerHome` when the tools page found or installed RefactoringMiner, and the settings re-read, so the tmux sync, the add-task dialog and the refactoring badge see them on this very start; a clone URL runs the clone as before; the first project waits until the window is shown and then goes through `MainWindow.createCategoryFromRepo` — the body of `addCategoryFromUrl` factored out of its dialog, so the wizard and the toolbar create categories identically.
For a local choice that method writes a `CONTEXTSWITCHER.md` without `remote:` (`TaskFileParser.groupConfigForRepo` with a null remote — a local category) and starts a local Claude in the workspaces root with the same setup description, the template appended to the prompt since the local launcher carries no appendix.
Model and effort are the last remembered pick (`dsn~claude-mode-select~3`); the wizard asks nothing a newcomer cannot answer.
Next to *Finish*, the last page offers *Finish and check up* — enabled once a first project is named — which additionally has that setup session go on to ask Claude `SetupWizard.CHECKUP_PROMPT` ("If I gave you a task, can you work on it?", the `firstMessage` of `createCategoryFromRepo`), so a missing permission, tool or login surfaces before the first real task.

The wizard is also re-run from the app — *Setup wizard…* in the toolbar add menu and the list's context menu, and a link in the no-tasks placeholder, where a newcomer who cancelled it is looking — through `Main.rerunSetupWizard`: the same pages with the clone one skipped (`offerClone` false; a clone into the existing directory would refuse anyway), the settings patched by the same `applyWizardSettings`, and the first project created right away, the window being up.
A newly configured remote works at once (every consumer reads the remotes fresh); the refactoring badge's poller starts at startup only, so a RefactoringMiner installed on a re-run is announced on the status bar as needing a restart.
A re-run starts from the configuration (`SetupWizard.Prefill`): the host field holds the first configured remote and the matching choice is selected — the WSL one when that remote is a `wsl:` host, the remote one otherwise, or, with no remote configured at all, the local choice; the workspaces root is proposed under the existing categories' roots' parent (`MainWindow.configuredWorkspacesParent`, the toolbar dialog's derivation) rather than the checked home; and the tools page probes the configured `refactoringMinerHome`, so an install under a custom path shows as found and keeps its setting — the installer itself always targets the default directory.
The first start passes `Prefill.EMPTY`, and the connection check still runs on Next either way.

Tags: windows, linux

Covers:
- req~setup-wizard~1

Needs: impl, utest

### Recommended session tools
`dsn~recommended-tools~1`

`RecommendedTools` names what makes a session good rather than what the app needs — RefactoringMiner (the badge and the AST-diff view, `dsn~refactoring-miner-commands~1`), ast-grep and codegraph, the two search tools Claude reaches for with their skills — as pure `sh -c '<script>'` argv builders: a `probe` printing the version (non-zero exit when missing) and an `install` printing it last, both free of double quotes (`dsn~ssh-command-runner~6`).
The wizard's tools page runs them through an `SshCommandRunner` handed in by `Main`: a `HostCommandRunner` over a five-minute `ProcessSshRunner` for the remote and a `LocalTmuxRunner` for `TmuxHost.LOCAL`, so this machine (Linux/macOS) gets the same page minus RefactoringMiner, which the app only ever runs over ssh (`local()`).
Every row is probed when the page is entered; *Install* runs the installer, disabled for the round-trip, and the version it prints becomes the status — a failure shows the installer's first stderr line and leaves the button enabled.
A row whose tool is found (probed or installed) hides *Install*: the installers do not update.
RefactoringMiner reuses `RefactoringMinerCommands.setupCommand` (the settings dialog's *Set up*, `dsn~refactoring-miner-setup~1`); found or installed, its directory — `REMOTE_HOME` with the checked home — travels in the `Result` and `Main` patches it into `refactoringMinerHome`, the setting the badge is gated on.
ast-grep is the pinned release zip's `ast-grep` binary unzipped into `~/.local/bin` (`sg` deliberately left in the zip: it would shadow shadow-utils' `sg`), then `npx -y skills add ast-grep/agent-skill -g -a claude-code -s '*' -y` for its two skills — a missing `npx` only warns on stderr, the binary is still there.
codegraph is its pinned `install.sh` (`CODEGRAPH_VERSION`), `codegraph install -y` registering the MCP server with the agents it detects, and the shipped `skills/codegraph-SKILL.md` written to `~/.claude/skills/codegraph/` first through the install's stdin (`runWithInput`), since the tool ships no skill of its own and the file is what tells a session to prefer it over grep.
Both look for their binaries with `~/.local/bin` appended to `PATH`, where the native installers put them and a non-interactive shell does not look.

Tags: windows, linux

Covers:
- req~setup-wizard~1

Needs: impl, utest

### Group note opening
`dsn~group-note-open~5`

`TaskFileParser.groupNote` reads the `note:` scalar from the `CONTEXTSWITCHER.md` frontmatter (SnakeYAML `SafeConstructor`; null when absent, blank, or unparseable — a half-edited config must not break the menu). The group header's context menu shows "Open note" when set, and the header row carries an always-visible note icon (Material Design `NOTE_TEXT_OUTLINE` via SvgNode, MADR 0010) doing the same.
The icon is built by `TaskListCell.urlIconButton` and so behaves like every other address in the list (`dsn~pr-state-indicator~3`): a hand cursor, and the URL named in the status bar while hovered.
Both flush the editor's unsaved changes and resolve the URL fresh from disk at click time — the icons take no focus, so a just-edited `CONTEXTSWITCHER.md` would otherwise yield the stale URL — and launch it via the OS protocol handler (`Main.openUrl`: `java.awt.Desktop.browse` → ShellExecute, no browser in the loop), which resolves `onenote:` to the OneNote desktop app.
`onenote:` page links carry spaces and braces and are no valid `java.net.URI` — `openUrl` falls back to `HostServices.showDocument`, which passes the raw string to the OS, when `browse` refuses. Driving OneNote via COM `Application.NavigateTo(pageId)` plus Win32 foregrounding would be precise, but needs page ids and a Windows-only COM bridge; deferred in favour of the portable URL launch.

Tags: windows, linux

Covers:
- req~group-note-link~1

Needs: impl, utest

### Group repository opening
`dsn~group-repo-open~2`

`MainWindow.groupRepoUrl` reads the group's `repo:` through the existing `GroupConfig` parse (`dsn~group-config-apply~2`) — no new key, no new parser: the value a live task's bootstrap prompt already uses is the one the header opens.
When set, the header row carries an always-visible link icon (Material Design `LINK_VARIANT`) next to the note icon, and the header's context menu an "Open repository" item; the tooltip is the URL itself, so the icon says where it goes.
The icon is a `TaskListCell.urlIconButton`: a hand cursor, and the URL in the status bar on hover — the PR icons' behavior, which is what a reader expects of anything in this list that leads somewhere.
The chain link rather than `SOURCE_REPOSITORY`, whose branch-and-node glyph reads as a fork at the row's 14px and says nothing about following a link; the task rows' "Add link…" icon already makes the chain link this app's mark for a URL.
`MainWindow.openGroupRepo` flushes the editor and re-reads the config before launching (`Main.openUrl`), for the same reason the note does: the icon takes no focus, so a just-edited `CONTEXTSWITCHER.md` would otherwise open the stale URL.

Tags: windows, linux

Covers:
- req~group-repo-link~1

Needs: impl, utest

### Deep-link URL handling
`dsn~deep-link-url~1`

`DeepLink.parse` maps `contextswitcher://<task|switch>/<task id>` onto kind and (percent-decoded) id — the kind parses as the URI host, the id as the path, so grouped ids (`jabref/fix-npe`) need no encoding.
`Main.handleDeepLink` (FX thread) selects the task, de-iconifies and raises the stage, and for `switch` runs the regular switch orchestration; a malformed link or an unknown task id reports in the status bar — the click happened in another application, so silence would read as a dead link.
Registration is per-OS and not code: `scripts/register-url-handler.cmd` (HKCU registry, no admin) and `scripts/register-url-handler.sh` (`.desktop` file + `xdg-mime`), shipped in the packageApp zip next to the app image so their default launcher path holds (MADR 0016).

Tags: windows, linux

Covers:
- req~deep-link-url~1

Needs: impl, utest

### Category deep link and its "Copy link" menu item
`dsn~category-link-copy~1`

`DeepLink.categoryUrl` builds `contextswitcher://category/<name>` with the name percent-encoded (so a category with spaces survives), `DeepLink.parse` maps the `category` host back onto `Kind.CATEGORY` plus the decoded name.
The category context menu's "Copy link" puts that URL on the system clipboard; `Main.handleDeepLink` routes a `CATEGORY` link to `MainWindow.selectCategory`, which expands the category if collapsed and selects and scrolls to its header row, reporting an unknown name in the status bar like an unknown task id does.

Tags: windows, linux

Covers:
- req~category-link-copy~1

Needs: impl, utest

### Task deep link and its "Copy link" menu item
`dsn~task-link-copy~1`

`DeepLink.taskUrl` builds `contextswitcher://task/<id>`, percent-encoding each `/`-separated segment of the id, which `DeepLink.parse` decodes back to the same id.
The task context menu's "Copy link" puts that URL on the system clipboard; following it is handled like any `task` link (`dsn~deep-link-url~1`).

Tags: windows, linux

Covers:
- req~task-link-copy~1

Needs: impl, utest

### Deep-link single instance via extension server
`dsn~deep-link-forward~1`

A deep-link launch (`Main.main` sees a `contextswitcher://` program argument) first tries `DeepLinkForwarder.forward`: a WebSocket client connects to the loopback extension server and sends one `{"type":"deeplink","token":…,"url":…}` message; the running instance validates the token, dispatches the URL to the FX thread, and acknowledges by closing the connection normally (1000), whereupon the forwarding process exits without opening a window.
When no instance answers (connect failure, token rejection, or no acknowledgment within bounded timeouts) the process starts the app normally and handles the URL itself once the window is up.

Tags: windows, linux

Covers:
- req~deep-link-url~1

Needs: impl, utest

### Group rename via folder move
`dsn~group-rename~1`

The group header's context menu offers `Rename group…`: a dialog (pre-filled, name slugged like creation) leads to `TaskFileAccess.renameFolder` = `Files.move` of the directory.
The watcher handles the rest — the delete event drops the old group and its rows, the create event registers and scans the renamed directory in place.
Collapsed state and an open editor file follow the new name; an existing target folder refuses (no implicit merge).

Tags: windows, linux

Covers:
- req~group-rename~1

Needs: impl

### Category delete via recursive folder removal
`dsn~category-delete~1`

The group header's context menu offers `Delete category…`: `MainWindow.deleteGroup` counts the group's tasks (`TaskEntry.group()` over the entry list) and confirms with a dialog naming that count, the irreversibility, and how many running tasks will be confirmed next.
On confirm it first walks the group's running tasks — those with a live context (`hasLiveContext`: not suspended and configuring a tmux window — on a remote or in this machine's own tmux server, `dsn~terminal-local-mirror~2`) — and runs each through `confirmAndDeleteTask`, the extracted body of the single-task delete (its end-window / transcript / workdir kill options, `dsn~claude-session-kill~6`). That call returns `DELETED` / `TIDY_FIRST` / `CANCELLED`; anything but `DELETED` aborts the whole category delete (`return`), leaving already-deleted tasks deleted — no undo. When every running task has been deleted it calls `TaskFileAccess.deleteFolder` = a reverse-ordered `Files.walk` delete of the directory tree (children before their parent), sweeping the remaining non-running task files, the `CONTEXTSWITCHER.md`, and the folder.
The watcher's delete events drop the group and its rows; `MainWindow` clears the collapsed-state entry and, when the editor lane shows a file inside the folder, empties it.
Suspended tasks' remote transcripts / working directories and any orphaned message-queue files are still left as-is — the same ceiling as single-task delete (`dsn~task-delete~6`); only running tasks get the teardown prompt.

Tags: windows, linux

Covers:
- req~category-delete~1

Needs: impl

### Task move via drag'n'drop
`dsn~task-move-dnd~6`

A task row starts a `TransferMode.MOVE` drag carrying the task id — the drag source is the **cell**, not the row graphic: pressing an unselected row selects it first, and the selection side effects can replace the row graphic mid-gesture, so a drag handler on the graphic only ever fired on already-selected rows. Group headers, "Add task…" rows, and task rows accept the drop (highlighted while hovered) and resolve to their group — a task row resolves to its own group, so root-level rows move a task out of its folder. `MainWindow.moveTask` never overwrites: a same-named file in the target folder gives the moved task a `-2`/`-3`… suffixed name (like task creation and import); `TaskFileAccess.move` moves the file (`Files.move`, creating the target folder); the directory watcher updates both rows.
While the drag hovers within 32 px of the list's top or bottom edge, `MainWindow.installDragAutoScroll` scrolls the `VirtualFlow` toward that edge (`scrollPixels` on a 25 ms `Timeline`, speed ramping up toward the edge) — a Timeline rather than scrolling in the drag-over handler because drag-over events stop while the mouse rests, and the handlers are event *filters* on the list so the cells' own drop handlers stay untouched.
The task row's context menu carries a "Move to category" submenu (every category sorted, plus "(no category)" for a filed task; the current one omitted) invoking the same move — a drop target scrolled out of view or in a collapsed region is always reachable this way.
After the move `moveTask` keeps the task in sight regardless of prior selection: it expands a collapsed target group (`collapsedGroups.remove`) and calls `selectTask`, which now also scrolls the row into view once the watcher delivers it — via a `scrollToPendingSelection` flag consumed when the pending selection is applied, so rebuilds that merely preserve the current selection never move the viewport.
The task's message-queue file follows the id (`QueueFile.rename` inside the `move` implementation — the queue is keyed by task id, `dsn~message-queue-store~2`), and `MainWindow.taskRenamed` keeps a selected row selected and an editor lane showing the file on the new id (shared with the title-adoption rename, `dsn~claude-title-sync~3`).

Tags: windows, linux

Covers:
- req~task-move-group~3

Needs: impl, utest

### Running indicator via @cs_status polling
`dsn~task-running-indicator~7`

`TmuxStatusPoller` runs, per task host on a fixed interval (single daemon scheduler thread, fixed-*delay* so slow round-trips do not pile up), a one-line remote shell command that snapshots the host clock and prints `windowId|@cs_status|idleSeconds` per window (idle = now − `#{window_activity}`, computed remotely so a Windows/remote clock difference cannot skew it). It builds a `host windowId -> status` map handed to a callback that marshals to the FX thread. As a safety net for a status stuck on `working` (e.g. Claude's `Stop` hook never fired), a `working` window idle for at least `IDLE_SECONDS` (15) is reported as `waiting` — a short threshold so a status stuck on `working` recovers quickly; it only ever downgrades, so it does not fight correct status. `MainWindow` stores the map and, per row, `TaskListCell` draws a small dot coloured by status, in traffic-light semantics: orange `working` (`#FF8A00`), red for every state that needs the user — `waiting`, `attention` (Claude asked a question or waits for a permission, set by the `Notification` hook) and `limit` (`dsn~claude-limit-detection~2`) all share the one red `#FF1010`, told apart by the tooltip —, muted `done`, transparent when unknown.
The dot carries a style class per state (`running-dot-working`, `running-dot-needs-you`, `running-dot-done`); `main.css` names the two semaphore colours once in `.root` (`-cs-working`, `-cs-needs-you`) and gives `done` the theme's subtle foreground (`~6`→`~7`: no colour set from Java, and `done` follows the theme instead of a fixed `#9E9E9E`).
The three values are taken from claude-semaphore (MIT, `github.com/TaulantSela/claude-semaphore`), a tray traffic light for Claude sessions, so a user running both reads the same colour the same way; its fourth colour (green `#0AE254`, "task finished") has no counterpart here — a finished session is `waiting` for the next prompt, which is exactly the state that must be red. A **suspended** task never shows a dot: its window was killed, so a window on the host matching a lingering id is some other context's (tmux window ids restart per server). `Main` recomputes the polled host set on the FX thread whenever the task list changes and reads it (volatile) from the poller thread; suspended tasks do not contribute their host, so a host whose tmux tasks are all suspended is not polled at all. Status is state Claude sets for itself with `tmux set -w @cs_status …`, best driven by Claude Code `UserPromptSubmit`/`Stop` hooks.

Tags: windows, linux

Covers:
- req~task-running-indicator~2

Needs: impl, utest

### Running-task accent and header count
`dsn~running-task-accent~1`

Every active task (`TaskStatus.ACTIVE` — what the running-tasks filter keeps, `dsn~running-tasks-filter~1`) carries an accent bar on its row's left edge: `TaskListCell` adds `running-task-cell` to the cell, and `main.css` colours the reserved 3 px left border `-color-accent-muted`; a selected row keeps the selection's stronger `-color-accent-emphasis` edge.
A **collapsed** category header counts them as a bare, accent-coloured number (`running-count`, tooltip "N running tasks", only when above zero) just left of the header's buttons; expanded, the bars show them and the number is left out. `MainWindow.rebuildRows` counts the active entries of the category's rows on screen into `GroupHeader.running`.
Carl, 2026-09-16: "Accent bar marks each running task; the header counts them."

Tags: windows, linux

Covers:
- req~task-running-indicator~2

Needs: impl

### Usage-limit detection
`dsn~claude-limit-detection~2`

Claude Code publishes no hook when a session runs into its usage limit: the turn simply ends, so the `Stop` hook reports the ordinary `waiting` and the blocked session is indistinguishable from a finished one.
`TmuxStatusPoller` therefore appends a second pass to its existing per-host round-trip (`dsn~task-running-indicator~7`): for every window that publishes a `@cs_status` (selected on the remote with `#{?@cs_status,#{window_id},}`, so non-Claude windows cost nothing), it greps the pane's visible screen plus twelve lines of scrollback (`capture-pane -p -J -S -12`) for Claude's limit messages — `limit reached` or `reached your <model> limit` — and prints a `limit|windowId` marker line per hit.
The trailing `true` keeps the command's exit code at 0 when the last window does not match.
`parseInto` reports the status `limit` for every marked window unless its effective status is `working` — a working window has moved on and the message is stale scrollback.
`TaskListCell` colours a `limit` dot red (`#FF1010`, the shared needs-you red of `dsn~task-running-indicator~7`) with the tooltip "usage limit reached — pay attention"; `TaskOrder.actionRank` ranks it ahead of `attention` in the "Action needed" sort, and `MainWindow.awaitsInput` counts it as awaiting the user (`dsn~awaits-input-filter~1`), since the session cannot continue without the user picking another model or buying credits.

Tags: windows, linux

Covers:
- req~task-running-indicator~2

Needs: impl, utest

### Auto-continue after an API error
`dsn~api-error-auto-continue~1`

A transient API error ("API Error: Connection lost mid-response.") ends Claude's turn where it stands; the session then sits idle until someone types `continue`, which is the only sensible answer to it.
`TmuxStatusPoller` therefore greps, in the same per-window pane pass as the limit detection (`dsn~claude-limit-detection~2`), the **tail** of the visible pane — the last eight non-blank lines of `capture-pane -p -J` — for `API Error`, and prints an `apierror|windowId` marker line per hit.
The tail rather than the twelve-line scrollback window of the limit grep, because the error must be the last thing that happened: an error the session has already moved past is scrollback, and a nudge for it would type `continue` into a chat that is doing something else.
For every marked window that the same tick does not report `working`, the poller sends `continue` to it (`send-keys -l`, then the submitting `Enter` a second later — Claude's input box absorbs an `Enter` in the same burst, as in `dsn~message-queue-send~5`).
The nudge is repeated at most every `CONTINUE_COOLDOWN_SECONDS` (300) per window: the error stays on the pane while the session works on the answer, and after a nudge that did not take it stays there for good, so a per-tick retry would be a flood.
A window that reports `working` drops its cooldown, so the next error is answered on the tick it appears.

Tags: windows, linux

Covers:
- req~task-running-indicator~2

Needs: impl, utest

### Restart on a pending Claude update
`dsn~claude-update-restart~6`

Claude Code updates itself in the background and then asks for a restart — "Update installed · Restart to update" in its footer — which every session keeps showing until someone quits and comes back.
`TmuxStatusPoller` therefore greps, in the same per-window pane pass as the limit detection (`dsn~claude-limit-detection~2`), the **bottom four non-blank lines** of `capture-pane -p -J` — the footer below the input box, with the hint lines under it — for `Restart to update`, and prints an `update|windowId` marker line per hit.
It used to grep the whole visible screen (`~4`→`~5`, field report 2026-09-15, with a screenshot): the footer cannot be stale scrollback, but the screen above it can quote the phrase — a JabRef diff of a *Restart to update* button label on screen got its session the `/exit`, which the user read as ContextSwitcher hitting the wrong session on suspend.
For every marked window whose status is `waiting` or `done` — a turn that is over — the poller types, in one round-trip (`exitCommand`): `Escape` (a draft in the input box would swallow the next keys and submit them as a prompt), `/exit` literally, and the submitting `Enter` a second later (Claude's input box absorbs an `Enter` in the same burst, as in `dsn~message-queue-send~5`).
The resume, `claude --resume <sessionId> || claude --continue` (`resumeCommand`) — the conversation that just ended, in the same window and directory, now on the new version — is typed in a **later tick**, the first in which the window's `#{pane_current_command}` (parsed anyway for `dsn~start-claude-button~2`) is no longer `claude`.
It used to follow a blind server-side `sleep 5` (`~2`→`~3`, field report 2026-09-14): a `done` window whose Claude did not quit — two background shells still running, or a queued prompt the status does not show — got the resume line submitted as a prompt, and the cooldown retried the pair until one session had taken a dozen `/exit`s.
Now a pane still running `claude` a tick later gets no resume at all (logged as a warning), and a window gets at most `MAX_RESTART_ATTEMPTS` (2) `/exit`s per pending update — the attempt count resets when the footer's message is gone.
The `/exit` itself is followed by one more `Enter` a second later (`~3`→`~4`, field report 2026-09-14, with a screenshot): a session with background shells does not quit on `/exit` but asks "Background work is running — the following will stop when you exit", *Exit and stop tasks* preselected, *Move to background and exit* and *Stay* below it, and that second `Enter` confirms the preselected choice — the question the two-shell window of the earlier report had been standing at, each retry's `Escape` choosing *Stay* and the next `/exit` asking again.
Blind on purpose, unlike the resume: where there is no question, the same `Enter` lands on the shell prompt Claude has left behind or in an empty input box and does nothing in either place, so it needs no `capture-pane` of its own to be safe.
The session is the window's own published `@cs_session_id`, taken from the very same `list-windows` tick the markers came from (`dsn~tmux-window-ownership~4` already parses it, so this costs no round-trip of its own); the same `--resume`-by-id `TmuxResurrect.startCommand` uses.
`--continue` alone was wrong and is only the fallback (changed in `~2`): it means "the most recent conversation in **this directory**", and tasks routinely share one Claude cwd — a category's workspaces root, with a git worktree per task — so a restarted window came back holding a *neighbouring* task's conversation.
Everything downstream then followed the wrong chat: the task's terminal mirror showed foreign work, messages queued for the task were delivered into that other conversation, and with two windows publishing one session id `Main.uniqueSessionIds` dropped it for ownership (`dsn~tmux-window-ownership~4`), so the task auto-suspended seconds after every switch to it (field report 2026-09-13).
The fallback still covers a window that published no id at all and an id the remote no longer knows — no worse than what `--continue` alone used to do, and the only way back into *some* conversation.
`--dangerously-skip-permissions` is typed again when `claudeAuto` is set: the flag belongs to the process, not to the conversation.
`working` is deliberately excluded (mid-answer), and so are `attention` (a question or permission prompt on screen that the restart would throw away) and `limit` (a new process does not lift it).
The restart is repeated at most every `RESTART_COOLDOWN_SECONDS` (300) per window, since a restart that did not take leaves the message on the footer and a per-tick retry would quit the session over and over.
No window an attached tmux client shows is restarted (`~5`→`~6`, field report 2026-09-17: Oliver's own session, the task in focus, got the `/exit` while he worked in it): the status command ends with `tmux list-clients -F 'viewed|#{window_id}'`, and `autoRestart` skips every window listed there — the app's terminal mirror counts as a client, as does a terminal the user attached themselves — since the `Escape` would throw away what is being typed and the `/exit` end the session in front of the user; the first tick after they moved to another task restarts it.

Tags: windows, linux

Covers:
- req~task-running-indicator~2
- req~claude-update-restart~1

Needs: impl, utest

### PR-state indicator
`dsn~pr-state-indicator~3`

`Task.prUrls()` is every `browser.urls` entry that is a GitHub pull-request URL, in file order (a task can carry several — one Claude session often opens a code PR and a docs PR); `Task.prUrl()` is the first of them, for the single-PR consumers.
`PrStatePoller` polls the distinct task PR URLs on a long fixed interval (single daemon scheduler thread, fixed-*delay*) via `PrStateLookup`, which runs `gh pr view <url> --json state,isDraft` (`LocalCommandRunner`, MADR 0003) and parses the raw JSON in Java — `state` (`OPEN`/`MERGED`/`CLOSED`) plus `isDraft` map to `PrState` (`OPEN`, `DRAFT`, `MERGED`, `CLOSED`), the JSON parsed with regexes rather than a `-q`/jq expression to dodge the Windows argv double-quote trap.
The merged `url -> state` map is handed to a callback that marshals to the FX thread; `MainWindow.updatePrStates` swaps it in and repaints only when it changed (no periodic flicker, like the running indicator).
Per row, `TaskListCell.prLink` puts each PR **on the second line, directly under the title** (`~3`, Carl, 2026-09-16; before, icons sat in a fixed-width slot at the row's right edge): the PR's number — `#123`, GitLab's `!45` (`MainWindow.prNumber`), `PR` for an address without one — led by a small coloured Material Design state icon (open = green `source-pull`, draft = muted `source-pull`, merged = accent-coloured `source-merge`, closed = red `source-pull` — the same PR glyph in red; closed without merging, not an error; the colours are the theme's, through `pr-<state>` style classes in `main.css` — AtlantaFX has no purple, so merged takes the accent), followed by the PR's `status:` label chips (`dsn~pr-header-line~4`).
Until the state is loaded — `gh` missing, offline, or the first poll pending — the number shows alone, shaped like a tag chip (`pr-chip`: the chip's padding and size) but without its background or border, so a PR is never invisible and does not look like a tag; the tooltip says the state is not loaded yet and shows the PR. Once loaded, the tooltip names the state and shows the PR URL.
Clicking a PR focuses (or opens) *that* PR's tab in the browser via the extension server (`ExtensionServer.focusUrl`, off the FX thread) — the same focus-or-open as the switch's browser action, reusing an existing tab rather than the OS handler's new one — and the status bar shows `Opening <url> …`, then the outcome (`Main.reportFocusUrl` marshals the `focusUrl` result back to the FX thread): the extension's detail (`focused existing tab` / `focused related tab` / `opened new tab`) on success, or the failure reason (`Browser extension not connected`, the 5 s timeout) — the result was previously discarded, so a not-connected extension or an opened-but-unraised tab looked like nothing happened at all.
On success it also raises the Firefox window (`BrowserWindowFocus`): the extension activates the tab but Firefox cannot pull itself in front of the app under Windows' foreground-stealing lock. The *right* window is targeted by **title** — the extension returns the focused tab's title (the `result`'s new `title` field), and `BrowserWindowFocus` walks the top-level windows with `EnumWindows` (all Firefox windows share one process, so the process handle can only ever surface one of many) to `SetForegroundWindow` the `MozillaWindowClass` window whose caption contains it, preceded by the synthetic ALT-tap that clears the lock — the same PowerShell mechanism as `JetBrainsClientFocus`. A freshly opened tab is created active and focused; the extension then waits (bounded, ~3 s, under the request timeout) for its page title to settle and returns it, so its window is raised too — only a page too slow to finish in that budget opens in the background (no title, no raise). Non-Windows is a silent no-op.
Hovering a PR names it in the status bar (`SwitchStatusBar.hover`, null on leave) as `UrlEntry.display()` — the entry's title ahead of its URL (`<title> — <url>`), the bare URL when it has none; the `Opening …` line of a click uses the same form, while the clipboard's `Copy URL` and the browser itself always get the bare URL — the row has no room for it, and with several PRs the numbers alone do not say which PR is which; the tooltip carries it too, but the status line shows up without waiting out the tooltip delay. Only the bar's label is swapped, never its chips, so a switch running meanwhile keeps reporting its actions; a `message`/`beginSwitch` arriving mid-hover wins and the leave then restores nothing stale. The hover adds the label to the bar when it is not there yet — until the first switch or message the bar has no children at all, and text set on a label outside the scene shows nothing.
Right-clicking a PR opens a small menu on it (`Open PR`, `Copy URL` — the latter puts *that* PR's URL on the system clipboard) and consumes the event so the row's own context menu does not also pop up. Handlers sit on the whole icon-and-number box, not the glyph: an `SvgNode` picks on its shape, so the thin pull-request strokes would otherwise be the only clickable area.
`Main` recomputes the polled URL set on the FX thread whenever the task list changes and reads it (volatile) from the poller thread.

Tags: windows, linux

Covers:
- req~pr-state-indicator~2

Needs: impl, utest

### PR header line above the terminal
`dsn~pr-header-line~4`

The terminal's own header shows the tmux window title — Claude's summary of what it is doing, a soft fact.
Above it, `MainWindow` shows a line per PR URL of the previewed task in the layout of GitHub's PR list, reduced to the hard facts: the PR-state glyph of `dsn~pr-state-indicator~3` (`TaskListCell.prStateIcon`), the PR's **title** as a `Hyperlink`, and its `status:` labels as chips (`TagChips`, in the label's GitHub color, so a chip looks like the label on GitHub's PR page); the author, age, and task counts of GitHub's row are left out.
Only the `status:` labels: a JabRef PR carries a dozen `component:`/`dev:` labels saying what it touches — readable from the diff — while the status is the fact that changes under you and decides whether the PR needs you now.
At most `MainWindow.MAX_PR_LABELS` chips are shown, the rest replaced by a `…` whose tooltip names them, and every chip in this line has `minWidth` 0 (unlike the task rows'): a chip insisting on its preferred width made the terminal lane's minimum width the sum of all chips, which pushed the queue pane — the message input — out of the window (field report 2026-09-07).
Clicking the title focuses (or opens) that PR's tab exactly like the row icon (`focusPr`); hovering names the URL in the status bar; right-clicking it offers `Open PR` and `Copy link` (the bare URL to the clipboard).
Title and labels ride the existing `PrStateLookup` GraphQL batch (`title`, `labels(first: 30) { name color }`), so the line costs no extra API request and follows the same poll cadence (`dsn~pr-poll-economy~2`); the lookup's map value is `PrInfo(state, title, labels)` with `labels` a name → `#rrggbb` map in GitHub's order, and `MainWindow.prStateFor` projects it to the state for the rows.
Until `gh` resolves the PR the line shows the task file's own URL title (or the bare URL) with the muted `?` glyph; a task without a PR URL shows no line at all (the box collapses).
The line is rebuilt on every selection change, `refreshPreview`, and PR-state update, and cleared with the preview.
The task row shows the same `status:` chips (`TaskListCell`, at most `MAX_PR_LABELS` across the task's PRs) left of its link and PR icons, so a PR's status is visible without selecting the task (`~2`→`~3`).
A chip reads `17148 · ready-for-review` rather than `status: ready-for-review` (`MainWindow.statusChipText`, the number from `PrTitleLookup.PR_URL`): a task with two PRs in the same status showed two identical chips, and two identical header lines' chips, with nothing saying which PR each belongs to (field report 2026-09-14).
Dropping the `status:` prefix pays for the number, so a single-PR row is no wider than before; a header line whose PR has no `status:` label shows the number as a muted label instead (`~3`→`~4`).

Tags: windows, linux

Covers:
- req~pr-state-indicator~2

Needs: impl, utest

### GitLab merge requests count as pull requests
`dsn~gitlab-mr-state~1`

A category can live on GitLab (gitlab.com or self-hosted), whose pull requests are merge requests at `https://<host>/<group>/…/<project>/-/merge_requests/<iid>`.
`Task.PR_URL` matches that shape on any host as well as GitHub's `…/pull/<n>` — the `/-/merge_requests/` path is GitLab's own — so an MR in `browser.urls` gets the row icon of `dsn~pr-state-indicator~3`, the header line of `dsn~pr-header-line~4`, and the poll cadence of `dsn~pr-poll-economy~2`, instead of a plain link icon.
`PrStateLookup` hands the MR URLs to `GitLabMrLookup`, which batches them like the GitHub query: one `glab api graphql --hostname <host>` call per host, one `project(fullPath:) { mergeRequest(iid:) { state draft title labels } }` alias per MR, path and iid as raw-string variables (no double quote in the argv), `glab` logged in to that host.
GitLab's `opened` maps to `OPEN` (`DRAFT` with `draft`), `merged` to `MERGED`, `closed` and `locked` to `CLOSED`; label colors come with their `#` already.
Merge trains and review decisions are not read, so an MR never groups as *merge queue* or *changes requested* (`dsn~pr-status-grouping~1`).
A status chip and the header line's number spell the iid GitLab's way, `!<iid>`.
`PrTitleLookup` answers an MR URL from the same query (fallback `MR !<iid> (<path>)`), so `Add task…` takes an MR URL like a PR URL (`dsn~task-from-pr~6`), the dialog's label reading `from PR/MR URL`.
`@cs_pr` needs nothing of its own: `TmuxPrPoller` and the import take the published value verbatim (`dsn~claude-pr-refresh~5`), so a session that publishes its MR URL there gets it recorded first in `browser.urls` like a PR.
The other discovery paths stay GitHub-only: Claude's footer PR, the workspace `gh pr view`, `auto:` searches and the Qodo review sync do not find MRs — without `@cs_pr` an MR enters a task by being added to `browser.urls` (pasted into the task, or the `Add task…` dialog).

Tags: windows, linux

Covers:
- req~pr-state-indicator~2
- req~task-from-pr~1

Needs: impl, utest

### PR-state refresh when a Claude session stops or a task is selected
`dsn~pr-state-refresh-on-stop~2`

Claude changes PR state itself — converts to draft, marks ready, merges — and reports it in its answer, while `dsn~pr-state-indicator~3`'s icon follows only on the next 120 s tick, so the row contradicted the terminal for up to two minutes.
A window whose polled `@cs_status` was `working` and is not any more (Claude finished its turn, or the window vanished) triggers an immediate re-read of the PR URLs of the tasks in that window (`Main.stoppedWindows` diffs the previous and current status maps; `PrStatePoller.refresh` runs the `gh pr view` calls on its own thread, ahead of the regular tick).
Bounded by the status poll's 5 s cadence and one `gh` call per PR of that task only — never a full round; a session without PRs triggers nothing.
Selecting a task does the same for that task's own PR URLs (`Main.refreshPrStatesOf`, on the preview callback): the task on screen is the one whose PR state is being read, and the toolbar's refresh button (`dsn~energy-saver~1`) stays the way to re-read *every* row.
Both paths run past the energy-saver gate, since both are a user action asking for state.
Known ceiling: a state changed *mid*-turn shows only once the turn ends, and a change made outside Claude (the browser) waits for the tick unless the task is re-selected.

Tags: windows, linux

Covers:
- req~pr-state-indicator~2

Needs: impl, utest

### GitHub polling economy
`dsn~pr-poll-economy~2`

Per-PR `gh` rounds got Oliver's token blocked by GitHub's secondary rate limit ("too many requests" — GitHub has no push notification for PR state, polling is the only option), so every poll is scoped and batched:

- **One request per round**: `PrStateLookup.states` puts all URLs into a single aliased `gh api graphql` query (`p<i>: repository(owner: $o<i>, name: $r<i>) { pullRequest(number: $n<i>) { state isDraft } }`) instead of one `gh pr view` per PR; owner/repo/number travel as GraphQL variables so the argv carries no double quote (the Windows trap), owner/repo via `-f` since `-F` would auto-type an all-digit owner login into an Int. An alias that fails to resolve (deleted repo, no access) drops out of the answer, the rest of the batch survives.
- **Only visible rows**: `Main.recomputePrUrls` keeps only tasks whose row is currently on screen (`MainWindow.visibleRowsObservable` — after find/tag/desktop filters, collapsed groups, and the collapsed Done section, where most merged PRs live), re-runs on every row change, and `PrStatePoller.refreshMissing` resolves URLs never fetched in this run — already-resolved URLs keep their cached icon (`MainWindow.prStateByUrl` never forgets) and cost nothing, so scrolling filters on and off is free. The Qodo review poll reads the same narrowed `prUrlsByTask`, so hidden rows stop costing its head-SHA gate calls too; a row becoming visible again re-opens its catch-up window (empty `lastSha`).
- **Merged is final**: a PR last seen `MERGED` is re-read only on the first tick after startup and then every 600 s (a slow re-read only heals a mis-parse), instead of every 120 s tick.

- **One round per burst, retried**: row changes arrive in bursts — the task list streams in row by row at startup, and every filter toggle rebuilds it — so `refreshMissing` does not fetch per change but coalesces into a single round 2 s after the last one, over the URL set as it then stands. That burst is what left a fresh start without any PR icons: dozens of back-to-back `gh` calls hit the secondary rate limit, every one of them failed, and the list stayed blank until the first regular tick two minutes later. A round that leaves URLs unresolved is retried every 10 s, at most 5 times, so a `gh` that was briefly unhappy still fills the icons in seconds rather than minutes.

No jitter on the intervals: the 5 s status poll is tmux-over-ssh (no GitHub involved), and with one batched request per 120 s there is no burst left to spread.
Known ceiling: "visible" is the row list, not the viewport — rows scrolled out of a long list still count (ListView virtualization makes true viewport tracking brittle for little API savings).

Tags: windows, linux

Covers:
- req~pr-state-indicator~2

Needs: impl, utest

### Link icons per non-PR browser URL
`dsn~task-link-icons~2`

`Task.linkEntries()` is the complement of `Task.prEntries()`: every `browser.urls` entry whose URL is not a GitHub pull request, deduplicated, in file order.
`TaskListCell` renders them among the row's always-visible trailing icons at its right edge — after the refactoring count, before the note — with no reserved width, so a task without links loses no space; the hover action gutter (`dsn~task-row-hover-actions~7`) is laid over the row just left of them, so it never covers a link.
A single entry is one always-visible chain-link icon (MDI `link-variant`, the app's mark for "a URL", as on the category header's repository icon `dsn~group-repo-open~2`), built by `TaskListCell.urlIconButton`, so it behaves like every other address in the list: hand cursor, `UrlEntry.display()` in the status bar while hovered, tooltip `Open <display>`, and a click running the same handler as a PR icon (`MainWindow.focusPr` — "Opening … " in the status bar, then focus-or-open the tab via the extension), so the link lands in an existing tab rather than a fresh one.
Several entries collapse into a single chain-link button carrying the negative-circled count (`CircledCount`, as the queue badge `dsn~message-queue-count-badge~1`): left- or right-clicking it pops up one submenu per link, titled `UrlEntry.display()`, holding `Open` (that same focus-or-open handler) and `Copy URL` (the bare URL).
No state polling — a plain link has no state to show — and no per-icon status-bar hover for the aggregated button, whose menu names the addresses instead.

Tags: windows, linux

Covers:
- req~task-link-icons~2

Needs: impl, utest

### Add a link to a task
`dsn~task-add-link~3`

A task row's hover icons include a link-plus that opens one `Add link…` dialog, pre-filled from the clipboard, with a live label showing which interpretation the current input takes (OneNote note or browser URL) before the user commits.
`MainWindow.addLinkToTask` auto-detects the kind: an `onenote:` link — or a OneNote "Copy Link to Page" clipboard, whose two lines (a `https://onedrive.live.com/…` web URL and an `onenote:` desktop URL) are reduced to the `onenote:` one by `OneNoteLink.extract` — is written to the task's top-level `note:` (`TaskFileParser.withNote`), opened via an always-visible note icon on the row (a `TaskListCell.urlIconButton` — hand cursor, URL in the status bar on hover; `MainWindow.openTaskNote` resolves it fresh from disk and launches it through `Main.openUrl` so `onenote:` reaches the desktop app); an `http(s)` URL is appended to `browser.urls` (`TaskFileParser.addBrowserUrl`).
Both are textual, comment-preserving edits (`addBrowserUrl` extends an existing uncommented `browser: urls:` list, or inserts a fresh `browser:` block before the closing fence — a commented example is ignored; `withNote` sets/inserts the `note:` line after `title:`); the editor is flushed first so a just-edited file is not clobbered.
A URL the list already has is not added a second time — `addBrowserUrl` returns the content unchanged, so every writer (the dialog, `addUrlsFrom`, the PR discovery) is free to offer a link it may already have filed.
Into an existing list a new entry goes **in sorted position**: before the first entry that sorts after it in plain string order (compared on the URL, so the map form's `- url: …` compares like the scalar form, and the new entry lands before that entry's first line), falling back to after the last item.
Existing entries are never reordered — an out-of-order list stays as it is, only the new entry is placed; on a sorted list this keeps it sorted, which is what a task's several PRs want (`dsn~pr-state-indicator~3`). String order, not PR number: `…/pull/16287` sorts before `…/pull/643`, and a number that is a prefix of another sorts oddly — accepted, since sorting by parsed PR number would order GitHub PR links only and leave every other link arbitrary.

Tags: windows, linux

Covers:
- req~task-file-editing~1

Needs: impl, utest

### Remove a link from a task
`dsn~task-remove-link~2`

Every URL Claude mentions is filed automatically (`dsn~task-url-collect~1`), so a task's `browser.urls` only ever grows; each line of the PR header line above the terminal (`dsn~pr-header-line~4`) therefore carries its own way out.
A trash icon (MDI `trash-can-outline`) sits at the end of the line, visible only while the mouse is on that line — the task rows' hover-action shape (`dsn~task-row-hover-actions~7`) — and stays managed, so the line does not jump on hover; the title's right-click menu offers the same as `Remove link`.
Before it, a copy icon (MDI `content-copy`) with the same hover visibility puts the URL on the clipboard, like the menu's `Copy link`.
`MainWindow.removeTaskUrl` flushes the editor, re-reads the file and drops the entry via `TaskFileParser.removeBrowserUrl`, a textual comment-preserving edit like `addBrowserUrl` (`dsn~task-add-link~3`): the item line goes with the map form's continuation lines, URLs are compared normalized (`Task.normalizeUrl`, so a trailing slash still matches), and a URL the list does not have is a no-op.
Removing the **last** entry drops the whole `browser:` block: a bare `urls:` parses as "no URLs" but would make the next `addBrowserUrl` write a second `urls:` key.
Removal asks for confirmation first — the entry can carry a hand-written title, which goes with it — and the status bar names the removed URL; the header is rebuilt and an editor lane showing the file reloaded.

Tags: windows, linux

Covers:
- req~task-file-editing~1

Needs: impl, utest

### Open in IntelliJ from the task menu
`dsn~open-in-intellij~3`

A task row's right-click menu offers `Open in IntelliJ`, and the PR header line above the terminal (`dsn~pr-header-line~4`) carries the same action as a flat `IDEA` button at its right edge — the IDE is opened for the task you are looking at, without going back to the row.
The button is shown whenever a task is previewed (also for a task without any PR, where the header line itself is empty) and runs `MainWindow.openInIntellij` on the previewed task, so enabling the section, the project-path prompt, and the switch are exactly the menu item's.
Button and menu item are disabled while the task lacks a worktree (`Task.lacksWorktree()`: a `claude:` session that published no workspace other than its start `cwd`, and no explicit `intellij.projectPath`) — the project would be the shared workspaces root.
`MainWindow.openInIntellij` reads the task fresh from disk and, when it has no `intellij` section yet, inserts a top-level `intellij:` section before the closing fence via `TaskFileParser.withIntellij` (a comment-preserving textual edit; a no-op when an uncommented `intellij:` already exists, so an explicit config is never clobbered) and saves it.
A bare section is enough only when a project path can be derived (`Task.intellijProjectPath()`: an explicit `intellij.projectPath`, else the `claude:` session's workspace/cwd); when none resolves — e.g. a PR-created task with no Claude session — the IntelliJ action would stay unconfigured and silently do nothing, so `openInIntellij` first prompts (a `TextInputDialog` naming the resolved remote) for the project path and writes it as `intellij.projectPath` (`TaskFileParser.withIntellijProjectPath` — creating the section, or adding the child to an existing bare `intellij:`); a cancelled/blank prompt aborts without touching the file. The gate is `Task.intellijProjectPath() == null` (path unresolvable), not mere section presence, so a task left with a useless bare `intellij:` still gets fixed.
It then selects the task's row (`MainWindow.selectTask` — id-based, so the selection survives the watcher rebuilds its own file write triggers, like every switch highlighting the row it acts on) and routes through `switchTask`, which re-reads the file and runs the normal switch, so the `IntellijGatewayAction` opens the project exactly as pressing play would (the switch action itself is specified by `req~open-remote-intellij-project~1`). An editor lane showing the file is reloaded after the write.

Tags: windows, linux

Covers:
- req~task-file-editing~1

Needs: impl, utest

### Start Claude in a window that lost it
`dsn~start-claude-button~2`

A start can go wrong: the tmux window comes up but Claude never does (a bad `claude` invocation, a remote hiccup), or the session exits later — the terminal mirror then shows a bare shell and the task looks dead without being it.
A flat `Claude` button sits **left of the `IDEA` button** on the PR header line (`dsn~open-in-intellij~3`), visible only while the previewed task's own window is known to run something other than Claude.
The evidence is `#{pane_current_command}`, which `TmuxStatusPoller` reads as a seventh field of its existing per-host round-trip (`dsn~task-running-indicator~7`) and hands to `MainWindow.updateWindowCommands` as a `host windowId -> command` map; a window the poll has not seen is simply absent, and absence counts as "has Claude" — the button appears on evidence, never on silence. A suspended task never shows it (its window was killed, so a matching id is some other context's, as for the running dot).
The pane command is the same test `TmuxTaskImporter` uses on import: `claude` means a session, anything else (`bash`, `zsh`, …) does not.
Pressing it types `claude --resume <sessionId> || claude` into the recorded window — `TmuxResurrect.startClaude`, the command a resurrect already sends (`dsn~tmux-resurrect~7`), so the conversation is continued where one is recorded and a fresh session started where it is not (or where the id no longer resolves), `--dangerously-skip-permissions` following the `claudeAuto` setting.
The round-trip runs off the FX thread with the button disabled and the status bar reporting `Starting Claude …` → outcome, the async single-shot button shape.
No confirmation: typing a command into a window that is not running Claude cannot destroy anything.
Play on such a task does the same (`~2`): `MainWindow.switchResolved` runs the switch actions and then, when the last poll's evidence says the window lacks Claude, the very start the button sends — so a killed Claude comes back by the one gesture a row is reached for, without switching away and back or finding the button (field report 2026-09-14: "if claude is killed, play button should resume it?"). No separate *restart* button: restarting a *running* Claude is the update path's business (`dsn~claude-update-restart~6`), and a window the poll has not seen is left alone as before.

Tags: windows, linux

Covers:
- req~task-running-indicator~2

Needs: impl, utest

### Delete via confirmed file removal with kill checkboxes
`dsn~task-delete~6`

The task row's right-click menu has a `Delete task…` item (the hover trash icon shares the handler).
`MainWindow.deleteTask` shows one confirmation `Alert` whose content carries the per-target cleanup checkboxes of `dsn~claude-session-kill~6` (end window, close browser tabs, remove transcript, remove working directory — each only when applicable), plus an **Ask Claude to tidy up first** button for a live window that sends the canned wrap-up prompt and deletes nothing.
On **Delete** the ticked cleanup is handed to `Main.killSession` and the file is removed via `TaskFileAccess.delete` (`Files.deleteIfExists`).
When the window's `@cs_status` is `working` the dialog warns that ending the window kills Claude mid-task (the former "Force terminate" escalation, now a warning on the same checkbox).
When the task still has messages in its queue (`dsn~message-queue-store~2`), the dialog shows red warning text naming how many queued messages the delete would lose.
The directory watcher drops the row; if the deleted file was open in the editor lane it is cleared.
After the delete, the selection moves to the neighbouring task (next, else previous), so the snapshot pane switches away from the deleted task's session; with no task left, the pane resets to its placeholder.
A file-system error surfaces as an error `Alert`.
The dialog is built through `Alerts.withContent` so the pane grows to its content's preferred height (`Alerts`' `DialogPane` sizing workaround): otherwise JavaFX sizes the pane from the header alone and clips the message plus the checkboxes below it.
The same holds for the group and corrupt-file confirmations, which use `Alerts.wrapping` for their multi-line messages.
The dialog is resizable for the same reason, and deliberately carries no `ScrollPane`: everything it asks about must be readable without scrolling.

Tags: windows, linux

Covers:
- req~task-delete~2

Needs: impl

### Hover row actions
`dsn~task-row-hover-actions~7`

A task row's actions sit in an **action gutter** at its right end, shown only while the mouse is on the row (`visibleProperty` bound to the cell's `hoverProperty`) — there is no separate Switch button.
The gutter is laid over the row rather than into it (`~7`, Carl, 2026-09-16): an unmanaged `HBox` the row's `layoutChildren` places just left of the row's always-visible trailing icons (refactoring count, links, note), so hovering never reflows or re-truncates the title; it spans the row's full height and has no frame: its background (`task-action-gutter`) fades from transparent into the row's own colour over 24 px and stays that colour behind the buttons, so the title and second line fade out under it instead of being cut; a hovered task row takes `-color-accent-subtle` across its whole width (a selected one keeps its selection colour), named `-cs-row-bg` on the cell so the fade matches it.
Left to right: **one toggle** — pause on an active task (suspend, with its confirmation), play on a suspended or done task (tooltip "Resume and switch"; the regular switch resurrects) — its glyph in the accent colour as the row's main action; `Add link…`; the refactoring view (`dsn~refactoring-web-view~1`, only when the task has a remote and a Claude section); a thin vertical divider; and delete (confirmed) at the far right, away from the toggle a row is reached for.
Until `~6` play and pause were two buttons in a strip that pushed the title aside; an active task is now switched to by double-click or the context menu's `Switch`, which remain the keyboard and mouse paths for that.
Icons are Material Design SVG paths rendered by `SvgNode` (MADR 0010) — unicode symbol glyphs render as empty boxes in JavaFX on Windows. An invisible button is unreachable — becoming visible requires the very hover that reaches it.
They reuse the exact handlers of the status-label toggle (`dsn~task-status-cycle~2`, including the suspend confirmation) and the context menu's delete (`dsn~task-delete~6`, including its confirmation). The buttons are not focus-traversable — the context menu remains the keyboard path; double-clicking the row still switches.
Pressing play (and the menu's `Switch`) selects the row **and** focuses the list (changed in `~6`): the buttons take no focus themselves, so afterwards the keyboard sat wherever it happened to be — or was taken by the mirror's attach for the newly selected task (`dsn~terminal-pane~14`, which no longer grabs an unasked-for focus) — and the arrow keys did not move on from the row just switched to.
A double-click on the row is a click as well, so it still hands the keyboard to the mirror afterwards.
Because the buttons take no focus, clicking one right after editing the task file would act on the pre-edit task (the editor's focus-loss auto-save never fired): switch, status change, and delete therefore flush the editor and re-parse the task from disk first (`MainWindow.freshTask`; unparseable content falls back to the row's last good state).

Tags: windows, linux

Covers:
- req~task-list-window~1
- req~task-status-toggle~2
- req~task-delete~2

Needs: impl, utest

### Task from PR: window + primed Claude
`dsn~task-from-pr~6`

Toolbar `Add from PR…`: the URL dialog first searches all tasks' `browser.urls` — a hit selects the row (pending-selection mechanism).
Otherwise the remote is chosen (`MainWindow.chooseRemote`): the sole candidate — settings `remotes` ∪ the tasks' distinct remotes — is used without a prompt; several offer an **editable combo** (pick an existing one, or type a new one); none falls back to a text prompt. Then `Main.createTaskFromPr` runs on the executor: the title comes from `PrTitleLookup` (local authenticated `gh pr view --json title`, system-tool approach of MADR 0003; fallback `PR #<n> (<owner>/<repo>)` from the URL), the session is the remote's usual one (that of its first task, else `0`), and `ClaudeWindowLauncher` creates the window (resurrect commands; `new-session` fallback) and starts Claude.
The initial prompt travels **as** a queued message (`MessageSender.send`, `dsn~message-queue-send~5`): `ClaudeWindowLauncher` brings Claude's TUI up (server-side `run-shell sleep`), then the prompt goes over ssh **stdin** into a tmux buffer and is pasted bracketed with the separate delayed Enter — it may span lines and carry any quotes, nothing prompt-related appears in an argv (where Windows ssh.exe mangles double quotes) — and any `[image: …]`/`[file: …]` attachment markers in it (a live task's description may carry them, `dsn~task-create-ui~15`) are uploaded and rewritten to their remote paths exactly like in a queued message.
A paste that lands while Claude's TUI is still (re)starting — right after the model switch, typically — is silently dropped, so three seconds after the send the launcher captures the pane (`capture-pane -J`, with scrollback) and, when the prompt's first marker-free line is not there, pastes the prompt again, up to three times (without the mode commands, which already took).
The task file carries title, remote, and the PR as the `browser.urls` entry, and is written before the remote work so the row shows its creation progress from the start (`dsn~task-create-progress~5`); the tmux session + window id are written back the moment the window exists, ahead of the Claude start, and the new row is selected once the watcher delivers it. — unless the keyboard focus sits in one of the queue pane's text boxes (`MainWindow.selectCreatedTask`): the creation runs asynchronously, so the user is typically typing a message for the task still in view by the time it finishes, and the selection change would swap the queue pane over and clear that half-typed message.

Tags: windows, linux

Covers:
- req~task-from-pr~1

Needs: impl, utest

### Claude's folder trust dialog is answered on launch
`dsn~claude-trust-dialog~1`

A directory Claude Code has never run in — the fresh workspaces root of a category created from a URL (`dsn~category-from-url~4`) — greets it with the folder trust dialog ("Yes, I trust this folder"), whose default answer is "No, exit".
Left alone, the first Enter of the mode commands quits Claude and the prompt lands in the shell.
So `ClaudeWindowLauncher.launch` captures the pane after the start command and, when the trust option's label is showing, sends Down + Enter (`trustCommand`) followed by a server-side sleep for the TUI start, before the prompt goes out.
A pane without the dialog is left alone — the check costs one capture per launch.

Tags: windows, linux

Covers:
- req~task-from-pr~1
- req~category-from-url~3

Needs: impl, utest

### Add task via unified title/PR dialog
`dsn~task-create-ui~15`

The toolbar add menu's `Add task…` item (`dsn~task-list-toolbar~3`) — and a category's `Add task…` row, passing its group — open `MainWindow.addTask(group)`: one dialog with a single field whose content decides the mode, the label under it live-reflecting it (`Title` vs `from PR/MR URL`, `PrTitleLookup.isPrOrMrUrl`).
The field is a small multi-line area, not a single-line `TextField`: a live task's input is a free-form multi-sentence description (`dsn~task-create-live~8`), and with a single-line field every plain Enter fired the dialog's default button — submitting a half-typed description mid-sentence.
Enter therefore inserts a newline (the area consumes it before the default button sees it); three plain Enters in a row — or three Ctrl+Enters, which fired the button on the first press until `~11` — trigger the default button, and Shift+Enter inserts a newline too:
the queue boxes' compose chords, installed by the shared `QueuePane.installComposeKeys` (`dsn~message-queue-ui~26`) so the dialog and the queue advance in sync per `req~compose-key-conventions~5`; a muted hint under the field names the keys.
The field also takes the queue boxes' attachment affordances (`QueuePane.installAttachments`, `dsn~message-queue-ui~26`): files dragged from the OS and pasted clipboard images are stored under the local attachments dir and referenced as `[file: …]`/`[image: …]` markers in the description — the live-task prompt delivery uploads them and rewrites the markers to their remote paths like any queued message (`dsn~task-from-pr~6`), so Claude can read an attached spec right from the initial prompt; in the task file's title and Notes the markers keep pointing at the local copies.
`Ctrl+T` opens the same dialog from anywhere in the main window (a capture-phase scene filter like `Ctrl+F`'s, `dsn~task-find~9`, so the terminal pane does not swallow it as ^T).
A **Category** combo box at the top of the dialog picks the group the task lands in, pre-selected with the group it was opened for; choosing another re-evaluates the hints, the enabled buttons and the default button below for that group.
The add-menu item and `Ctrl+T` target the category in view (`MainWindow.selectedGroup`): the group of the current selection while it is on screen — a selected group header's own name, a selected task's folder — else the group of the topmost visible row, else the root — so adding a task while a category is in view files it into that category (with that group's workdir defaults) rather than always the root.
A list scrolled away from the selection, or with none, pre-selects the category shown at its top, not the one that sorts first (`~15`, 2026-09-18).
The per-group affordances still pass their own group explicitly.
The field is pre-filled from the clipboard when it holds text, so a copied PR link or title is one chord away.
Any http(s) URL **mentioned inside** the description (rather than pasted alone, which routes to the PR flow) is appended to the new task file's `browser.urls` as well (`TaskFileParser.addUrlsFrom`, `dsn~task-url-collect~1`) — for the plain and the live flow alike, so an issue link, a PR, or any other address is there without a second `Add link…` round.
Under the field sit the model and effort pickers (`dsn~claude-mode-select~3`), pre-selected with the last pick (a fresh install starts on "as is"): they reach the fresh chat as `/model`/`/effort` ahead of the context prompt, for the live-task and the PR flow alike (`Add plain task` starts no chat, so they are ignored).
The dialog offers an explicit choice of four buttons, in this order: `Add plain task`, `Add remote Claude`, `Add local Claude`, and `Cancel`.
`Add remote Claude` is enabled whenever a remote resolves: the group's `CONTEXTSWITCHER.md` `remote`, else the app's first configured remote (settings `remotes`), the same fallback `openGroupConfig` uses to pre-fill a new CS config — so a folder without a CS config still offers it.
`Add local Claude` is enabled for a **local category** — a group whose CS config sets a `workspacesRoot`/`workdir` but **no** `remote` (the lightweight, no-tmux half of a local/remote group pair, `dsn~task-create-local~4`).
The default (Ctrl+Enter) button follows the category the task lands in: a local category defaults to `Add local Claude`, a group with its own `remote:` to `Add remote Claude`, and a group with neither (including the root) to `Add plain task` — the settings.yaml fallback remote keeps the remote button enabled but no longer makes it the default, since it is a fallback, not a statement about this category.
A hint under the field states the remote and its source (group default vs configured remote) and the local directory, or why either button is disabled. The editor is flushed first, so a `remote:` just typed into an open CS config counts.
A PR URL always routes to the PR flow (`dsn~task-from-pr~6`) regardless of the button. Otherwise `Add remote Claude` creates a live task (`dsn~task-create-live~8`), `Add local Claude` a file-only title task plus a local Claude session (`dsn~task-create-local~4`), and `Add plain task` a file-only title task in `group` (empty = root): `TaskFileParser.newTaskFileName` slugs `group/title` into the relative file path, `MainWindow` de-duplicates with `-2`/`-3`…, `newTaskContent` renders minimal frontmatter (seeded from the group defaults, `dsn~group-config-apply~2`) plus a Notes body, `TaskFileAccess.save` creates the group subfolder when needed, and the new row is selected once the watcher delivers it and opened in the editor lane.
Each slug is capped at 60 chars, cut at a `-` word boundary (trailing `-`/`.` stripped): a live task's input is a free-form multi-sentence **description** (`dsn~task-create-live~8` derives the short title from it later), and an uncapped slug blows the Windows 260-char MAX_PATH under `%USERPROFILE%\.contextswitcher\tasks\` — the file name is only the task id, not the displayed title.
Submitting the dialog (any of the three add buttons) moves the keyboard focus to the task list on the left, so the arrow keys and the switch chord act on the new task instead of on the toolbar button or `Add task…` row the dialog was opened from — the same move the row's play button makes (`dsn~task-row-hover-actions~7`). The focus request is deferred to the next pulse: the PR flow opens its host dialog from within this one, and the owner window claims the focus back as that closes.
`TaskRepository` publishes the observable set of subfolder names — including empty folders — so an empty folder still renders as a group.
Every hand-filled category offers it twice (`~14`, Carl, 2026-09-16): an always-visible plus icon at the right end of its header, and a slim, muted `Add task…` row (`TaskListCell.AddTaskRow`, a flat button with a plus icon, indented to the task titles) closing its tasks while it is expanded — both open the dialog for that group so the task lands in it (its per-group workdir defaults then apply).
They replaced the header's hover-only plus and the `Add…` row that only an empty group showed, so adding to a category never depends on hovering or on the category being empty; like the header, the row is a drop target for the category (`dsn~task-move-dnd~6`). An auto category has neither (`dsn~auto-category-placeholder-row~2`).

Tags: windows, linux

Covers:
- req~task-create~1
- req~compose-key-conventions~5

Needs: impl, utest

### URLs mentioned in free text become browser tabs
`dsn~task-url-collect~1`

Any http(s) URL mentioned anywhere in a task's description (the Add-task dialog's field, `dsn~task-create-ui~15`) or in a message queued for its chat (`dsn~message-queue-ui~26`) is appended to the task file's `browser.urls` — not only a GitHub PR link, so a pasted issue link, wiki page, or any other address shows up as a tab too, without a separate `Add link…` round.
`Task.urlsIn` scans free text with a general `https?://\S+` pattern and strips the trailing sentence punctuation (`.,;:!?)]}'"`) a URL commonly sits in front of, e.g. "see https://example.org/x." keeps the period out of the link.
`TaskFileParser.addUrlsFrom` runs the scan and appends every match through the existing `addBrowserUrl` (deduplicated, first occurrence wins, sorted-position insertion) — the same textual, comment-preserving rewrite the Add-link dialog and the PR-mention extraction already used.
Wired at both textual sources: `MainWindow.createTitleTask`/`Main`'s live-task creation run it over the description before the file is first written, and `QueuePane.commitAddBox` runs it (via a new `onMessageQueued` callback into `Main.rewriteTaskFile`) right after a message is added to the queue, so a URL typed into an already-open chat's queue gets the same treatment as one typed while creating the task.

Tags: windows, linux

Covers:
- req~task-create~1
- req~message-queue~2

Needs: impl, utest

### Task creation shows its progress on the task's own row
`dsn~task-create-progress~5`

Creating a task from the `Add task…` dialog is not instant — the live and PR flows do ssh round-trips (tmux window, Claude start, `gh` title lookup) before anything appears in the list — so the status bar carries the same "doing X …" → outcome shape as the other async single-shot actions.
`Main.createLiveTask` and `Main.createTaskFromPr` announce "Creating task …" on the FX thread before handing off to the executor, and report "Task created." or "Task creation failed." from every completion path (window launch failed, PR title lookup failed, file save failed) — next to the existing error alert, so the bar never stays stuck on "Creating …".
The plain/local flow (`MainWindow.createTitleTask`) only writes a file, so it reports the outcome alone.

The status bar is not the main channel, though: both async flows write the **task file first** — everything they know up front, without the `tmux:` section the launcher has yet to produce — and only then start the remote work.
The row is therefore in the list from the first second, and a failed remote leaves a plain task carrying the typed description instead of nothing at all (the failure alert and the retry pre-fill stay as they are).
The window is written back as early as it exists: `ClaudeWindowLauncher.launch` hands its id to an `onWindow` consumer the moment `new-window` returns — before Claude is started and the prompt sent, the slow half of a launch — and `Main.recordCreatedWindow` inserts it with `TaskFileParser.withTmuxSection` right then, reloading the editor lane if it shows the file (the pre-tmux buffer must not auto-save over the new section).
The watcher's reload re-fires the preview of the selected row, so the terminal mirror attaches to the fresh window and the session can be **watched coming up** instead of the pane reading "No tmux configured for this task" for the whole launch.
Until that window exists there is nothing to mirror, and the freshly selected task would stare at that same placeholder: `Main.creationStep` therefore holds each still-windowless creation's step (`Main.creationSteps`, FX thread) and `previewTask` shows it in the terminal lane in the placeholder's place, so the pane says what the launch is doing from the first second.
The entry is dropped the moment the window is recorded — the mirror itself takes over from there — and on the creation's end.
From that moment the creation is in `Main.windowedCreations` and its remaining steps ("Starting Claude …", "Sending the prompt …") go to the row's progress bar **only**: `TerminalPane.showMessage` ends the running connection, so standing in for the mirror again would tear the just-attached terminal down and the pane would sit on "Sending the prompt …" for the rest of the launch instead of showing Claude coming up.
`Main.finishCreation` then only clears the row's progress and reports the outcome.

While the remote half runs, the row's second line is a `ProgressBar` plus the step currently running: "Preparing the workspace …" (the `mkdir -p` of a group's workspaces root), then "Creating the tmux window …", "Starting Claude …", then "Switching the model …" and "Switching the effort …" (each only when picked — one step per slash command, so the row names the round-trip that is slow), "Sending the prompt …" — reported by `ClaudeWindowLauncher.launch`'s `onStep` consumer, held in `MainWindow.creations` (`creationProgress`/`creationDone`, FX thread), and rendered by `TaskListCell.Creation`.
The bar is **time-driven**, not step-driven: the steps differ far too much in length (the Claude start alone carries a server-side `sleep 5`) for equal parts to mean anything, so it fills over the worst case 20 s of a remote creation and stops at 99 % — a slow remote never shows a full bar on an unfinished task.
A 500 ms `Timeline` advances every in-flight creation and stops with the last one.
`Creation` holds its step and progress as **properties** and the row binds bar and label to them: a `ListView.refresh` twice a second flickers the whole tree for as long as a task is being created, whereas a bound bar repaints itself alone (the same reason `updateRunningStatuses` refuses to refresh on an unchanged poll).
Only two events rebuild rows — the first tick of a creation, where the row has to grow its bar, and its end, where the row goes back to its normal second line.

Creations may **overlap**: each runs on its own virtual thread from the app's action executor and touches nothing shared (the file name is de-duplicated on the FX thread, and each tmux window is created independently), so the user need not wait for one to finish before adding the next.
The status bar is the one shared resource, and a single message line would let a fast creation's "Task created." hide a slow one still running: `Main` therefore counts the in-flight creations (FX thread only) and reports "Creating N tasks …" while several run, and "Task created. (N still creating …)" when one finishes ahead of the others.

Tags: windows, linux

Covers:
- req~task-create~1

Needs: impl, utest

### A task row's title is one line
`dsn~task-row-single-line-title~1`

A live task's `title:` is the typed description until Claude publishes its short `@cs_title` (`dsn~claude-title-sync~3`), and that description is routinely several lines long.
A `Label` renders embedded newlines, so the freshly created row stood two or three lines tall among its one-line neighbours for the whole launch — exactly where the eye is, since the row also carries the creation bar.
`TaskListCell.singleLine` therefore collapses every line break (with the whitespace around it) to a single space before the title reaches the row, and installs the untouched text as the label's tooltip so nothing typed is lost from view.

Tags: windows, linux

Covers:
- req~task-create~1

Needs: impl, utest

### Retry after a failed creation keeps the failed text
`dsn~task-create-retry-prefill~1`

A failed creation must not cost the user a hand-written description: every creation error path — file save (plain, PR, and live flow), tmux window launch, PR title lookup — flags the last Add-task submission as failed (`MainWindow.noteTaskCreationFailed`; `Main`'s async flows call it on the FX thread next to their error alerts).
The next `Add task…` dialog compares the current clipboard with its content at submit time (re-read on submit, so copying text inside the dialog does not defeat the check): unchanged means the user is retrying, and the field pre-fills with the failed submission's text — including any edits made over the original clipboard pre-fill — instead of the clipboard.
A changed clipboard signals new input and pre-fills from the clipboard as before (`dsn~task-create-ui~15`); the failed flag is consumed by the next dialog either way, so a later unrelated dialog is not affected.

Tags: windows, linux

Covers:
- req~task-create~1

Needs: impl

### Add a live task (tmux window + Claude) on a group's remote
`dsn~task-create-live~8`

The `Add task…` dialog's `Add remote Claude` button (enabled whenever a remote resolves — the group's `CONTEXTSWITCHER.md` `remote`, else the app's first configured remote) creates a live task instead of a file-only one, mirroring the PR flow (`dsn~task-from-pr~6`) without the PR/browser parts. Without a group CS config only the remote is imagined; the `workspacesRoot`/`workdir`/`repo` defaults stay empty (Claude starts in the remote's default directory with the plain context prompt).
The dialog input is the task **description**, not a polished title: the prompt (`Main.liveTaskPrompt`) carries it verbatim between `-----` fence lines (delivery over ssh stdin into a tmux buffer, `dsn~task-from-pr~6`, so quotes and newlines survive — no stripping) and first asks Claude to derive a short task title and publish it as the `@cs_title` window option, which is synced back into the task file (`dsn~claude-title-sync~3`).
`Main.createLiveTask` writes the task file on the FX thread first (`dsn~task-create-progress~5`) and then runs off it: it `mkdir -p`s the working directory on the remote (so a fresh `workspacesRoot` exists — Claude then makes the per-task worktree under it), creates a fresh tmux window in the remote's usual session (`usualSession` — the session of the remote's first tmux task, else `0`), started in the group's `workspacesRoot` (or the fixed `workdir`, resolved by `GroupConfig` — no task suffix) via `ClaudeWindowLauncher.launch(…, cwd)`, and launches Claude with that prompt.
When the group uses a shared `workspacesRoot` (`GroupConfig.bootstrapWorktree`), a prompt clause tells Claude to create the task's git worktree from the group's primary clone — the `group`-named subdirectory of the workspace root — and work in it, naming the group's GitHub `repo` when configured (when not, nothing is asked: the clone's origin already knows the URL); a fixed `workdir` (an existing checkout) keeps the plain prompt.
The instruction part is deliberately terse — conventions live in the workspace root's `CLAUDE.md` (auto-loaded) — so Claude starts on the task instead of a setup conversation.
A caller may hand `liveTaskPrompt` an `appendix`: reference material appended after the description's closing fence, used by the category-from-URL flow for the workspace-root `CLAUDE.md` template (`dsn~claude-md-templates~1`).
It is a separate parameter rather than part of the description because the description also becomes the task's `title:`, its file name and the tmux window name, all of which a multi-kilobyte multi-line value would wreck.
The task file carries `remote`, `claude.cwd`, the description as the initial `title:` **and** under `# Notes` (so nothing is lost when the title is replaced), de-duplicated like the plain flow; the `tmux` target is written back the moment the window exists, ahead of the Claude start.
The new row **is** auto-selected — synchronously, right after the file is written, so the row is selected as it appears: a creation that runs for a quarter minute has to be visible while it runs, so the user sees the row's progress, the same step in the terminal lane (`dsn~task-create-progress~5`) and then the session coming up in the mirror, instead of an unselected row somewhere in the list.
Through `MainWindow.selectTask`, **not** the PR flow's `selectCreatedTask`: that one steps aside for a queue box being typed in (`dsn~task-from-pr~6`), which is right for a creation landing minutes later but not for the dialog the user just submitted — whenever the closing dialog handed the focus back to a queue box, the guard dropped the selection and the new row stayed unselected.
The editor lane opens the just-written file with the selection; the title-adoption rename that follows seconds later carries it along (`MainWindow.taskRenamed`), and the `tmux:` write back reloads it (`reloadEditorIfShowing`), so a clean editor cannot auto-save over either.
The file name starts as `<ISO date>-<slugged description>` — dated like the per-task worktrees, capped per `dsn~task-create-ui~15` — and is renamed to the dated **adopted title** once `@cs_title` arrives (`dsn~claude-title-sync~3`), so the placeholder slug never outlives the session's first minutes.

Tags: windows, linux

Covers:
- req~task-create~1

Needs: impl, utest

### Add a local task (a Claude session) in a local category
`dsn~task-create-local~4`

The `Add task…` dialog's `Add local Claude` button is the local counterpart of `Add remote Claude` (`dsn~task-create-live~8`) for a **local category**: a group whose `CONTEXTSWITCHER.md` sets a `workspacesRoot`/`workdir` but no `remote` — the lightweight, no-tmux half of a local/remote group pair, the shape `TaskFileParser.newTaskContent` already writes as the task's local `folder:` (`dsn~group-config-apply~2`).
It is enabled exactly for such a group and is then the dialog's default button; a group carrying a `remote:` offers only the remote flow (its directory lives on the remote, not on this machine).

The task file is the **plain** one (`MainWindow.createTitleTask`, `dsn~task-create-ui~15`): the description as title, `folder:` seeded from the group defaults, selected and opened in the editor lane.
Off Windows the tmux window the session runs in is then written back into it (`Main.writeLocalWindowBack`: `tmux:` with session `0` and the window id, plus `claude.cwd`) — without a `remote:`, which is exactly what marks the session as local and lets the pane mirror it (`dsn~terminal-local-mirror~2`).
On Windows the same call writes `claude.cwd` alone: there is no tmux window, and the cwd is what finds the transcript again for a later resume (`dsn~terminal-owned-session~3`).
Nothing else is: a local session is not polled, so there is no `@cs_title` to sync and none of the live flow's id bookkeeping.
The description-as-title is shortened by the app itself instead (`dsn~task-create-local-title~1`).

`Main.startLocalClaude` then runs `LocalClaudeLauncher` off the FX thread, with the status bar's "Starting …"/outcome shape.
A failure names its **reason** in the status bar (the launcher returns the attempt's stderr, else its exit code) rather than pointing at the log: a packaged app has no console to look at while it is running.
The argv actually launched is logged at info for the same reason.
The directory is `Files.createDirectories`d first — a category's workspaces root may not exist yet, and both launchers refuse a missing directory — and the typed description travels as Claude's initial prompt, the model picked in the dialog as `--model <alias>`; the effort has no CLI flag and is not passed (unlike the remote flow, which sends both as slash commands, `dsn~claude-mode-select~3`).

On Windows the session is hosted by the **app itself**, in the terminal pane's ConPTY (`dsn~terminal-owned-session~3`): `cmd /k claude [--model <alias>] [description]` with `claude.cwd` as its working directory, so it can be typed into.

Elsewhere `wt` does not exist, so the session is created in the **local tmux server** instead — the same place a remote task's Claude lives, and no terminal emulator is opened: `tmux new-window -P -F '#{window_id}' -t 0: -c <dir> -n <last path segment> claude [--model <alias>] [description]`, falling back to `tmux new-session -d -P -F '#{window_id}' -s 0 …` when there is no session `0` yet.
The prompt is a plain argv element there (no shell re-parses it), so it keeps its quotes, semicolons and newlines.
`-P -F '#{window_id}'` makes tmux print the new window's id; that id is what the task file records, so the session is mirrored in the app's terminal pane instead of only being reachable by `tmux attach -t 0` in some other terminal (`dsn~terminal-local-mirror~2`).
An id tmux did not print (nothing starting with `@`) is not recorded — no `tmux:` section is better than one pointing at a window that does not exist.
Off Windows the status bar's success message names the attach command (`tmux attach -t 0`), since the session is also usable outside the app, and the dialog's hint under the field says the same up front; on Windows it only names the directory, the session being the app's own.

Tags: windows, linux

Covers:
- req~task-create~1

Needs: impl, utest

### A local Claude task gets a short title
`dsn~task-create-local-title~1`

A local Claude task (`dsn~task-create-local~4`) starts with the typed description as its `title:`, and a description is routinely a paragraph — far more than a row shows before it ellipsizes.
Unlike a remote task, nothing replaces it later: the local prompt asks for no `@cs_title`, and the local tmux window is named after its directory, so the title is shortened by the app and no tmux is involved.
`Main.startLocalClaude` hands the new task to `InitialTitleAdopter.adopt` on the FX thread — on Windows (the app-owned session), elsewhere (the local tmux session), and for the category-from-URL flow's local branch alike.
When the title reads as a description — more than one line, more than 60 characters or more than 8 words (`TaskTitles.looksLikeDescription`) — a `TaskTitleSummarizer` derives one off the FX thread.
`ClaudeCliSummarizer` runs `claude -p --model haiku --tools "" --setting-sources "" --no-session-persistence --output-format text <instruction>` in the temp directory, with the description on stdin, through `cmd /c` on Windows and by resolved path elsewhere (MADR 0035).
`FirstWordsSummarizer` — the first sentence, at most 8 words — answers when `claude` is missing, fails or times out after 45 s (`FallbackSummarizer`).
Every answer is cleaned by `TaskTitles.sanitize`: the first non-blank line, without enclosing quotes or emphasis and trailing punctuation, cut at a word boundary to 60 characters.
Back on the FX thread the title is written only while the file's title still equals the description it was derived from, so a rename or a configuration edit made meanwhile wins; it is written once, and never again.
The description moves to the end of the body under `# Notes` (`TaskFileParser.withNotesAppended`), the file keeps its name (the app-owned session is keyed by the task id), and an editor lane showing the file reloads.

Tags: windows, linux

Covers:
- req~task-create~1

Needs: impl, utest

### Fork a task's Claude conversation into a new task
`dsn~task-fork~1`

The terminal bar's "Fork…" button (`TerminalPane.jumpToBottomBar`, id `fork-task-button`) forks the previewed task, next to the pane's other buttons. `TerminalPane` does not own tasks — like `diffOpener` — so `MainWindow` decides the button's enabled-ness (`renderPrHeader`, since it already runs on every previewed-task change) and wires its click (`TerminalPane.setOnFork`/`setForkAvailability`). Enabled only when the task has a `remote`, a `tmux` window and a non-blank `claude.sessionId` — `claude --resume <id>` has nothing to resume without one — else disabled with a tooltip naming what is missing.

The click opens `MainWindow.openForkDialog`: title "Fork &lt;source title&gt;", a `TextArea` ("What should the fork do?"), the model/effort `ComboBox`es built exactly as the Add-task dialog's (`QueuePane.modeBox`, pre-filled from `MainWindow.lastMode`), and **Back** (cancel, creates nothing) / **Fork** (default, disabled while the field is blank). The field holds a message for a Claude chat, so it gets the Add-task dialog's compose chords (`QueuePane.installComposeKeys`) — a shared behavior needing a UI test at each call site (`dsn~message-queue-ui~26`'s rationale). The dialog closes the instant Fork is clicked or the chord fires; creation runs in the background through `MainWindow.TaskForker` (implemented by `Main.forkTask`, next to `createLiveTask`), and the picked mode is remembered like the Add-task dialog's.

`Main.forkTask` resolves the same category as the source task (its id's group folder) and that group's `repo`/`bootstrapWorktree` from its `CONTEXTSWITCHER.md`, like `startRemoteWindow`. It uses the source's own `remote`, `claude.cwd` as the new window's working directory (Claude stores sessions per directory, so `--resume <id>` must run where the source session ran) and the remote's usual tmux session (`usualSession`). The task file is written first, like `createLiveTask`: title/slug from the instruction, `remote`, `claude.cwd`, the instruction under `# Notes` plus a "Forked from &lt;source title&gt; (`&lt;source task id&gt;`)" line; the launcher's row progress steps (`dsn~task-create-progress~5`), the prompt recorded via `QueueFile.saveSent` and the post-create session-id sync (`dsn~tmux-sync~7`) all run as they do there. Unlike `createLiveTask`, the new row is **not** selected — the user stays on the source task, since a fork is meant to run unattended alongside it — and start/outcome are reported through the status bar ("Forking &lt;instruction's first line&gt; …" → "Forked …"/"Forking … failed.") instead of the terminal lane.

`ClaudeWindowLauncher` gained a `forkFrom` parameter (`launch(…, @Nullable String forkFrom)`, `startClaudeCommand(windowId, auto, @Nullable forkFrom)`): non-null, it starts `claude --resume <forkFrom> --fork-session` (plus `--dangerously-skip-permissions` when `claudeAuto`) instead of a plain `claude`, so the fresh session continues the source conversation as a fork — the source session itself is untouched. `Main.forkPrompt` (next to `liveTaskPrompt`, sharing its `@cs_title` clause and worktree-bootstrap clause) tells Claude it is a fork of the source task's conversation and to first check the recorded `claude.workspace()` — or, when none was recorded, `claude.cwd()` — since the workspace may have moved on since the remembered conversation, then do the instruction, which travels verbatim between `-----` fence lines like a live task's description.

Tags: windows, linux

Covers:
- req~task-fork~1
- req~compose-key-conventions~5

Needs: impl, utest

### Open a window on play for a remote-only task
`dsn~remote-window-choice~6`

Pressing play on a task that carries a `remote:` but no `tmux:` section has nothing to focus yet, so `MainWindow.switchTask` intercepts it (`task.remote() != null && task.tmux() == null`) and asks — a confirmation alert with **tmux** and **claude** buttons (plus Cancel) — whether to open a plain shell or a Claude session on the remote.
That alert also carries the **model and effort pickers** (`dsn~claude-mode-select~3`), pre-filled from the last pick and remembered on a **claude** choice, so the session starts at the model the task needs instead of whatever the remote CLI defaults to — "I always want to set model and effort, because it differs from task to task" (field report 2026-09-12).
The **tmux** choice ignores them: a plain shell has no model to pick.
`MainWindow.askRemoteWindow` is the dialog, static and shared, because both entry points that create a session show it — the same widget, so there is no second copy of the choice to keep in agreement.
The `Claude` button next to `IDEA` (`dsn~start-claude-button~2`) deliberately asks **nothing**: it resumes an existing session that already runs at some model and effort, and offering to override them would work against a repair whose whole point is getting that conversation back.
The intercept is only on the play path: `openInIntellij` reuses the shared switch core (`switchResolved`), so opening a remote-only task in IntelliJ is never sidetracked into the window dialog.
The **terminal pane** offers the same two buttons under its "No tmux configured for this task." placeholder (`TerminalPane.showStartWindowMessage`, added in `~2`), for the same tasks (a `remote:`, no window, and no creation step still running — a creation still on its way shows its progress instead).
That placeholder is where a task creation that died halfway is actually seen, and before this the only way on from it was to guess that play — of a task that visibly has nothing to play — would offer the choice.
Its **claude** button opens the dialog above rather than firing straight away, and its **tmux** button keeps firing straight away; the pickers stay out of the placeholder itself, which is a message with two buttons in the middle of the terminal lane, not a form.
Opened that way the dialog shows **no tmux button** (`askRemoteWindow`'s `offerTmux`, false from `Main`'s window starter): the choice has already been made by the button that opened it, and a second **tmux** next to the pickers would only undo it — "tmux is not required here, because we start claude" (field report 2026-09-12).
It also asks with the Claude wording ("Start a Claude session for … on <host>?") rather than play's "has a remote but no tmux window yet", which is not the question being asked there.
A cancelled dialog starts nothing and re-draws the placeholder (`MainWindow.refreshPreview`), so the two buttons — dead for a round-trip that never began — can be pressed again.
Both buttons go dead for the round-trip, and the row shows the **same progress bar an Add-task creation shows** (`dsn~task-create-progress~5`): `Main.startRemoteWindow` reports "Creating the tmux window …", then for the claude choice the launcher's own steps ("Starting Claude …", "Switching the model …", "Switching the effort …", "Sending the prompt …"), and ends through `finishCreation` — which clears the bar, says "Task created." or raises the failure alert, exactly as the Add-task flow does.
Disabled buttons alone were not feedback for ten seconds of ssh: "I clicked 'Claude' on this task and it just stalled. I was expecting the 'usual' progress bar" (field report 2026-09-12, on a creation that had in fact succeeded while the screenshot was taken).
The window written back replaces the placeholder, and a failure re-draws it (`MainWindow.refreshPreview`) so the buttons live again.
The host may be the **category's** (`~3`): `Main.taskRemote` reads the task's own `remote:` first and falls back to the `remote:` of its `CONTEXTSWITCHER.md`, both for offering the buttons and for running the choice.
That resolved host is what the dialog **names**, passed in as `askRemoteWindow`'s `remote` parameter: the dialog used to print `task.remote()`, so a task borrowing its category's host asked "Open one on null?" (field report 2026-09-12). A null there — no host anywhere — reads "the remote", like the IntelliJ project-path prompt's.
A task written by hand, or one whose creation died before it recorded a host, therefore reaches the same two buttons instead of a dead end that asks the user to add a frontmatter line first — "claude" is meant to mean "tmux + claude", not "tmux + claude, once you have configured a host" (field report 2026-09-12).
The task's own `remote:` always wins, so a task deliberately moved to another host is never pulled back to its category's.
A host borrowed this way is written into the task file alongside the new window (`withScalar`, in the same save): the window really is on that host now, and every later action — the mirror, the focus, the queue — addresses a task by its `remote:`.
Only a task whose category names no host either keeps the bare message: `Main.startRemoteWindow` still needs one.
On a choice, `Main.startRemoteWindow` runs off the FX thread. The working directory resolves from the **task's own** frontmatter first — an explicit `workdir:` (a fixed checkout, plain prompt) wins over a `workspacesRoot:` (Claude bootstraps its own worktree under it) — and falls back to the group's `CONTEXTSWITCHER.md` defaults (`GroupConfig.resolveWorkdir`/`bootstrapWorktree`); it is `mkdir -p`ed so a fresh root exists. The window joins the remote's usual session (`usualSession` — the session of the remote's first tmux task, else `0`).
The **claude** choice launches Claude with the live-task context prompt (`liveTaskPrompt` — the task's `# Notes` body, else its title, as the description; the worktree clause when a `workspacesRoot` resolves) via `ClaudeWindowLauncher.launch(…, cwd, mode)` — the picked model and effort reach the fresh session as `/model`/`/effort` ahead of that prompt; the **tmux** choice creates a plain window via `TmuxResurrect.createWindow(…, cwd)`.
`Main.writeRemoteWindowBack` (FX thread) then writes the new window id back — a fresh `tmux:` section (`TaskFileParser.withTmuxSection`; an existing `window:` line is rewritten instead) and, for the Claude choice, a `claude:` `cwd` section (`withClaudeSection`, never overwriting an existing one) — selects the task, and switches to it so the window is focused and the mirror attaches; the Claude choice also schedules the post-create sync so the published `sessionId` is backfilled. The next play sees a `tmux:` section and focuses normally.

Tags: windows, linux

Covers:
- req~focus-remote-tmux-window~1

Needs: impl, utest

### Auto mode: `--dangerously-skip-permissions` on launch and resume
`dsn~claude-auto-permissions~1`

With `claudeAuto: true` in `settings.yaml` (`dsn~app-settings~5`, opt-in — absent or anything but boolean `true` means off), every `claude` invocation ContextSwitcher types into a tmux window carries `--dangerously-skip-permissions`: the fresh start (`ClaudeWindowLauncher.startClaudeCommand` — live-task and PR flows) and the resurrect resume (`TmuxResurrect.resumeCommand`).
The flag is Claude Code's own bypass-permissions mode (equivalent to `--permission-mode bypassPermissions`); the first such session per remote shows Claude's one-time acceptance dialog, afterwards sessions start unattended.
Claude refuses the flag when running as root — a non-issue for the per-user ssh remotes ContextSwitcher targets.

Tags: windows, linux

Covers:
- req~claude-auto-permissions~1

Needs: impl, utest

### Task title synced back from the Claude session
`dsn~claude-title-sync~3`

`TmuxTitlePoller` polls the `@cs_title` tmux window user option of every window on the task hosts (same host set, cadence, and single-daemon-scheduler pattern as the `@cs_pr` poller).
`Main.adoptPublishedTitles` (FX thread) matches each `host windowId -> title` entry to a task by remote + tmux window and replaces the frontmatter `title:` textually (`TaskFileParser.withTitle`) when it differs; the original description keeps living in the task body, written there at creation (`dsn~task-create-live~8`).
An adopted title also renames: the tmux window (like a manual rename, `dsn~task-rename-title~2`) and the task **file** — the initial name is only a slug of the description, so it becomes `<group>/<date>-<slugged title>` (`TaskFileParser.adoptedFileName`: the group folder and the old name's leading ISO date survive, else the adoption day fills in; `-2`/`-3`… dedupe like creation).
The file rename goes through `TaskFileAccess.move` (the message-queue file follows, `dsn~task-move-dnd~6`), and `MainWindow.taskRenamed` keeps a selected (or pending) row selected and an open editor lane on the file under the new id.
It also swaps the entry in place via `TaskRepository.renamed` (same for the drag'n'drop move): the watcher reports a rename as an unrelated delete plus a create, and applying those in order blinks the task out of the list and back — a visible flicker — while the in-place replacement under the new id makes both watch events no-ops.
Afterwards the option is unset on the remote (`tmux set -w -u @cs_title`, off the FX thread) whether or not the title differed — a **one-shot mailbox**: a stale option must never fight a later manual rename, and Claude can publish a new title again at any time.

Tags: windows, linux

Covers:
- req~claude-title-sync~1

Needs: impl, utest

### Settings editor popup
`dsn~settings-editor~4`

A **cog icon button** at the right end of the list toolbar (`MainWindow.settingsButton`, a Material Design `COG` glyph via `SvgNode`, MADR 0010 — icon-only, `Styles.SMALL`/`BUTTON_ICON`/`FLAT`, tooltip "Settings"; the last item of the toolbar `dsn~task-list-toolbar~3`, after the add menu) opens a resizable dialog (`setResizable(true)` — JavaFX dialogs are fixed-size by default).
The form scrolls in a viewport of `FieldForm.viewportHeight()`, which keeps the whole dialog, chrome included, within 75 % of the primary screen's height (`0.75 × height − 260`, at least 120 px); the task/category configuration form (`dsn~task-field-form~4`) uses the same rule (`~3`→`~4`: the viewport took the full height less 260 px, so a long form opened taller than the screen).
A lone gear in the status bar had been easy to miss and read as a twin of the sync-tmux button, which used a cog glyph (`COG_SYNC`); `COG` is now the one plain cog in the app, and sync-tmux uses `SYNC`.
The dialog is parented to the main window (`Dialog.initOwner`, `MainWindow.dialogOwner`): an ownerless JavaFX `Dialog` is application-modal — it grays the whole UI — but is its own top-level window, so with the main window pinned to every virtual desktop (`showOnAllDesktops`) the orphan opened on the primary desktop while the user was on another, leaving the app apparently frozen with no dialog in sight. The raw-fallback editor (`editSettingsRaw`) is parented the same way.
The form is **generated from `SettingsCatalog`** (MADR 0015) — one control per known `settings.yaml` option (label, help, type), grouped into a section per option group — by an own small renderer, not a third-party form framework (FormsFX was tried and dropped, MADR 0015): a `TextField` for the tasks directory, WebSocket port, and WebSocket token; `CheckBox`es for `hints`, `claudeAuto`, and `showOnAllDesktops`; a `ComboBox` for an `ENUM` option's fixed `choices` (the `theme`, `dsn~theme-select~8`); a multi-line `TextArea` (one entry per line) for `remotes`; and a purpose-built `TagListEditor` for the tag palette — a row per tag pairing a name `TextField` with a `ColorPicker`, plus "Add tag" and a per-row remove (a tag is always a name + color). Each row places the label on its own line above a full-width control (a checkbox sits at the right edge instead of stretching) and highlights on hover; the option's help is the row tooltip. The form sits in a `ScrollPane`.
The **raw `settings.yaml` text is the data source of truth**: the dialog reads it via `Main.readSettingsFile`, and the form never re-dumps the whole file. On Save (and when toggling to raw), only the keys whose values changed are patched back into the raw text by `YamlPatch.set` (which replaces a single top-level key's block in place, leaving comments, key order, and keys the app does not know about untouched); a cleared tasks directory or a non-numeric port blocks with an alert.
A **View raw** button (a `LEFT` dialog button) shows that same canonical text in a monospace `TextArea` and back (**Form view**); switching to raw patches the changed keys in first, and switching back reparses the text (`AppSettings.parse` — malformed text keeps raw view with an alert) and rebuilds the form.
Save writes the canonical text via `Main.writeSettingsFile` (error surfaced as an alert without dismissing the dialog); the tag palette refreshes live (`refreshTagPalette`), other settings need a restart, which the header notes.
When the file on disk is too malformed to parse (`AppSettings.parse` throws) the dialog opens straight in a raw-only fallback editor on the offending text.
Pressing **F1** in either raw editor opens the settings field reference (`AppSettings.settingsReference()`, a commented template of every key the parser reads, kept next to the parser and guarded by a keys-match unit test) in the same non-modal reference popup the editor lane's field help uses (`dsn~task-field-help~1`).
`YamlPatch`'s single-key patching is covered by `YamlPatchTest`.

Tags: windows, linux

Covers:
- req~settings-editor~1

Needs: impl, utest

### Theme selection
`dsn~theme-select~8`

`AppSettings.theme` (a String, default `everforest`; normalized to one of `AppSettings.THEMES` — `system`/`light`/`dark` plus the three Everforest values of `dsn~everforest-theme~2` — in the record's compact constructor, so a hand-edited or unknown value falls back to `everforest`) drives which AtlantaFX theme the whole UI is drawn with.
`Themes.apply(pref)` (in the `ui` package) sets the JavaFX user-agent stylesheet: `NordLight` for `light`, `NordDark` for `dark`, and for anything else — `system`, a bare `everforest`, an unknown value — the one matching the OS; `Themes.systemPrefersDark()` reads the Windows registry `Personalize\AppsUseLightTheme` (`0` = dark) via a `reg query`, and every non-Windows OS falls back to light (a later port adds its own probe). The app's own `main.css` (added to every window) layers over the active theme and its color variables (`-color-bg-subtle`, `-color-border-muted`, `-color-accent-emphasis`, …) resolve against it, so the toolbar, panel headers, and search field recolor with the theme without extra work.
`Main.start` calls `Themes.apply(settings.theme())` at startup (before building the scene), and `MainWindow.show` calls `Themes.refresh()` once the window is on screen: JavaFX ignores a user-agent stylesheet it already holds (`StyleManager` returns early on the same URL and checksum), so with the ShellFX shell the window came up in JavaFX's own greys on every start although the stylesheet was reported "as requested", and a theme switch in the settings dialog restored it — `refresh` makes that switch itself, JavaFX's default and then the theme again, before the next pulse renders (`~4`→`~5`, field report 2026-09-13).
`Themes.checkApplied` logs the root's first background fill, so a recurrence names the colour the window was painted with.
That fill turned out not to tell the two apart (field report 2026-09-16, "theme not loaded" and "second run worked"): the ShellFX root is transparent in a themed and an unthemed window alike, and both starts logged the same twelve scene stylesheets and the user-agent URL "as requested" — while `StyleManager` keeps no cache for user-agent sheets, so both refreshes had re-read the theme file from disk (`~7`→`~8`).
What an unthemed window lacks is the theme's **colour variables**, so that is what is measured now: `Themes.variablesResolve` styles a probe `Region` in a scene of its own with `main.css`'s `.theme-probe` rule (`-color-bg-default`) and reports whether it got a background.
`Themes.verify` runs it when the window is shown and again 20 s later, past the FX-thread stalls of a busy start; when the variables do not resolve it logs a warning naming the stylesheet JavaFX holds, forces `refresh` once more and logs whether that repaired them — the restart and the settings-dialog switch, the only repairs seen so far, both came well after start-up.
And JavaFX's own CSS log (`javafx.css`, `java.util.logging`) is forwarded into tinylog at debug instead of reaching only the run loop's console, so a stylesheet JavaFX could not load or a variable it could not resolve is in the log file of the start it happened in — at debug, because a themed start already produces about ninety such records (value conversions in library stylesheets and in Modena's, which the refresh's switch through JavaFX's default evaluates too).
That recurrence came (field report 2026-09-14, a fill of Everforest's `#2d353b` logged at window-shown and a Modena-grey window on screen): ShellFX sets the user-agent stylesheet **itself** — every top-level window presenter hands its theme's URL to `Application.setUserAgentStylesheet` in `postInitialize`, which on a busy start runs after both refreshes.
So the `Theme` ShellFX is given (`ShellFxHost.AppTheme`) is its Nord of the current lightness for the chrome it styles, with `getUserAgentStylesheet` returning `Themes.stylesheet()` — whatever `apply` last resolved — so ShellFX's own reset installs the app's theme whenever it fires (`~5`→`~6`).
`AppTheme` also answers `name`, `equals` and `hashCode` as that Nord constant: ShellFX adds its per-theme sheets (`material-nord-dark.css`, `core-nord-dark.css`, …) only for a theme in its own constant set, and without them `-color-bg-extra` is undefined, so every menu popup in the shell drew no background and on Windows let clicks through to the window behind (`~6`→`~7`, field report 2026-09-14; `ContextMenuBackgroundUiTest`). The settings editor applies it **live** on Save (`Themes.apply(AppSettings.parse(content).theme())`, right after `refreshTagPalette`), so a light/dark switch restyles every open window at once — the one setting besides the tag palette that needs no restart.
In the settings form the option renders as a `ComboBox` seeded with `SettingsCatalog`'s `ENUM` `choices` (`AppSettings.THEMES`) and read back in `readForm`.
Every SvgNode glyph takes its fill from CSS: SvgNode's colour is a styleable property (`-fx-color`), `.svg-node` in `main.css` sets it to the theme's `-color-fg-muted`, and narrower classes set it where the colour means something (`status-glyph`, `pr-<state>`, `update-available`), so a live theme switch recolours every glyph with the rest of the UI.
No colour is set from Java anywhere in the UI: muted, danger and warning text use AtlantaFX's own classes (`Styles.TEXT_MUTED`, `Styles.DANGER`, `Styles.WARNING`), everything else a class in `main.css` built on the theme's variables — the one inline colour left is a tag chip's background, which is data the user picked.
`MainWindow` adds `main.css` to every window as it opens (`Window.getWindows()`, and again when a window gets its scene), so dialogs follow the same rules — before, a dialog had none of them, and the settings dialog's *Add tag* glyph rendered invisible (`~3`→`~4`: `Themes.iconColor`, the `themed-icon` class and `MainWindow.refreshIconColors` are gone; field report 2026-09-13, the hover icons of a task row nearly invisible under Nord dark).
Because the toolbar's own background is `-color-bg-subtle`, the toolbar buttons' flat hover is set to `-color-accent-muted` — `-color-accent-subtle` nearly equals `-color-bg-subtle` in Nord light (so it vanished there), while `-color-accent-muted` contrasts with the bar on both themes.
`apply` records the resolved mode in `Themes.dark` and derives everything else from it, and the palette in `Themes.everforest`, so a value's *palette* (Nord or Everforest) and its *mode* (light or dark) are two independent reads.
The terminal mirror follows too, through a palette of its own rather than the CSS variables — `dsn~terminal-theme~2`.


The concatenation lives in a **stable** file, `~/.contextswitcher/themes/everforest-<variant>.css`, rewritten only when its content differs from what is already there (so an upgrade to either stylesheet takes effect and an ordinary start touches nothing).
It used to be a fresh `createTempFile` per start, and that is what made the app come up in JavaFX's **own Modena** every other launch: a light, unstyled window while `Themes.dark` was correctly `true` — measurable in a screenshot as Modena greys around an Everforest-dark terminal pane.
`setUserAgentStylesheet` falls back to Modena, silently, for a stylesheet JavaFX cannot load, and the `Theme everforest-dark (dark): file:///…` line added on the way named the file as readable and non-empty at hand-over.
So the failing read was JavaFX's own, on a file written milliseconds earlier — which on Windows is the shape of a scanner holding a new file: `Files.isReadable` and `size` are metadata and pass while a content read does not.
The generated content is therefore read **back** before its URL is handed over — the same operation JavaFX is about to perform — and only then cached; a mismatch falls back to the plain base theme of the right lightness, logged.
After the first run the file is old news to everything on the machine, which is the actual fix; the read-back is what turns a recurrence into a dark window with a warning instead of a white one in silence.
Tags: windows, linux

Covers:
- req~theme-select~1

Needs: impl, utest

### Everforest recolouring of the AtlantaFX theme
`dsn~everforest-theme~2`

The `theme` values `everforest`, `everforest-light` and `everforest-dark` draw the UI in the [Everforest](https://github.com/sainnhe/everforest) palette, medium contrast — the bare name follows the OS like `system` does, the suffixed ones force a variant ([decision 0025](../decisions/0025-everforest-ui-as-an-atlantafx-variable-override.md), which supersedes the UI half of MADR 0024).

An AtlantaFX theme resolves **every** colour through the 113 lookup variables of its stylesheet's `.root` block: outside that block its 172 KB hold no literal colour at all.
So a second `.root` block, appended after the theme's own, recolours the entire UI without touching AtlantaFX's SASS sources — CSS gives the last declaration of equal specificity, and the base theme supplies structure only.
`Themes.everforest(base, dark)` concatenates the base stylesheet with `everforest-light.css`/`everforest-dark.css` (resources beside `main.css`) into a temp file — JavaFX takes one URL, not two stylesheets — and hands that to `Application.setUserAgentStylesheet`, caching one file per variant so a live switch does not rebuild it.
It must be the **user-agent** stylesheet rather than a per-scene one: only the main window adds a scene stylesheet, so a layered override would leave every dialog in the base palette.
The `base` stays the Nord theme of the matching mode, so a variable the override forgets falls back to a value of the right lightness; any failure logs and falls back to the plain base theme, because a broken recolouring must not cost the user their window.

`scripts/everforest-atlantafx.py` generates the two stylesheets and is the file to edit — they are derived data.
It builds AtlantaFX's ramp shape (index 0 lightest, 5 the seed, 9 darkest) from Everforest's own named steps for the neutrals and from four seeds for accent/success/warning/danger: **green** as the accent (Everforest's signature hue), aqua for success so it never reads as the accent, yellow, and red.
The light variant travels further down its ramps than Nord does (slope 0.75 against 0.55) and reads its `fg`/`emphasis` two steps deeper: Everforest's light hues are bright enough that Nord's indices produced a green fill too pale to carry the cream `fg-emphasis` text.
The generator therefore ends in a WCAG check — `fg-default` on the ground, `fg-emphasis` on each emphasis fill, and each `X-fg` on the ground, all at AA 4.5:1 — and fails rather than writing a palette that would ship a contrast regression.

Tags: windows, linux

Covers:
- req~theme-select~1

Needs: impl, utest

### Readline cursor chords in the text inputs
`dsn~readline-keys~1`

`AppSettings.readlineKeys` (a boolean, default `false`; only an explicit `true` counts — the chords are opt-in, since Ctrl+A stops being select-all) turns the bash/emacs cursor chords on in every text input of the app.
`ReadlineKeys` (in the `ui` package) holds the chord table and one **capture-phase `KEY_PRESSED` scene filter** per window.
A filter, not a per-control handler: it sees the key before the focused control's own behavior *and* before the scene's accelerators, which is what it takes to override built-ins like Ctrl+A.
It is registered **unconditionally** and re-reads a static flag per keystroke (`ReadlineKeys.setEnabled`), so `Main.start` seeds it from the settings and the settings-save lambda flips it live — no re-installation, no restart, and the "off" state costs one boolean read per key press.
`ReadlineKeys.installEverywhere` (called once from `Main.start`) installs on every window that exists and, through a listener on `Window.getWindows()`, on every dialog opened later — a window enters that list when it is shown, and showing needs a scene, so one hook covers the settings, add-task, and link dialogs without each remembering.
`MainWindow.show` additionally calls `ReadlineKeys.install(scene)` **before** registering the Ctrl+F find filter (`dsn~task-find~9`): scene filters run in registration order, so inside a text input Ctrl+F moves the caret, while every other focus (list, terminal, editor) still opens the find bar. `install` is idempotent — a marker in `Scene.getProperties()` keeps the two entry points from doubling up.

The filter walks up from `Scene.getFocusOwner()` to the nearest `TextInputControl` **or** `RichTextArea` (a control's focus may sit on an inner node) and does nothing when there is none — so the embedded terminal keeps every control key, and the remote shell's own readline is untouched.

| Chord | readline | `TextInputControl` | `RichTextArea` |
| --- | --- | --- | --- |
| Ctrl+A / Ctrl+E | beginning/end-of-line | own line arithmetic (`lineStart`/`lineEnd`) | `MOVE_TO_LINE_START` / `_END` |
| Ctrl+B / Ctrl+F | backward/forward-char | `backward()` / `forward()` | `MOVE_LEFT` / `MOVE_RIGHT` |
| Alt+B / Alt+F | backward/forward-word | `previousWord()` / `endOfNextWord()` | `MOVE_WORD_PREVIOUS` / `MOVE_WORD_NEXT_END` |
| Ctrl+D / Ctrl+H | delete/backward-delete-char | `deleteNextChar()` / `deletePreviousChar()` | `DELETE` / `BACKSPACE` |
| Ctrl+K / Ctrl+U | kill-line / unix-line-discard | `killToLineEnd` / `killToLineStart` | — / `DELETE_PARAGRAPH_START` |
| Ctrl+W / Alt+D | unix-word-rubout / kill-word | `killPreviousWord` / `killNextWord` | `DELETE_WORD_PREVIOUS` / `DELETE_WORD_NEXT_END` |

The line chords compute their own bounds on **logical** lines: `TextInputControl.home()`/`end()` jump to the start/end of the whole text (Ctrl+Home territory), not of the caret's line.
Ctrl+K standing at the end of a line removes the line break itself, so repeated Ctrl+K eats downwards like it does in a shell's multi-line buffer.
Ctrl+W is whitespace-delimited (bash's `unix-word-rubout` — `kopp.dev` goes in one), while Alt+D is punctuation-aware, the exact counterpart of Alt+F.
Deliberately unbound: Ctrl+P/Ctrl+N (in bash they walk the *history*, which a text box has none of) and Ctrl+Y (there is no kill ring — what a kill chord removed comes back with Ctrl+Z); Ctrl+K has no `RichTextArea` function tag and so is the one chord the notes editor does not get.
In the settings form the option renders as a `CheckBox` (General group) and is read back in `readForm`.

Tags: windows, linux

Covers:
- req~readline-keys~1

Needs: impl, utest

### Window pinned to all virtual desktops
`dsn~window-desktop-pin~2`

`AppSettings.showOnAllDesktops` (a boolean, default `false`; only an explicit boolean `true` counts — the pin is opt-in) makes `Main.applyDesktopPin` re-pin the main window to all Windows virtual desktops — at startup right after `window.show(stage)`, and again right after a successful settings save (the save lambda parses the just-written text; unparseable raw-mode text skips the pin until the next start). Off the FX thread, guarded by an `os.name` check so non-Windows never spawns the script; the outcome is reported to the status bar as well as the log, so a failing COM call is visible in the UI.
`WindowsDesktopPin` does the pinning through `powershell -EncodedCommand` (the `WindowsVirtualDesktopFocus` pattern): the script resolves the window from the JVM's PID (`Get-Process` `MainWindowHandle`, retried up to ~5 s while the stage is still appearing), then reaches through the immersive shell's `IServiceProvider` to `IApplicationViewCollection::GetViewForHwnd` and `IVirtualDesktopPinnedApps::PinView` — the same call Task View's "Show this window on all desktops" makes.
The pinned-apps *service* is a distinct CLSID (`B5A399E7-1C87-46B8-88E9-FC5747B171BD`) — v1 shipped a mistyped value here, which failed `QueryService` and the whole pin; the view-collection service reuses its own IID.
Unlike the desktop-switch interface (`dsn~category-desktop-focus~4`), these IIDs (`1841C6D7…`, `4CE81583…`, view IID `372E1D3B…`) have been stable since Windows 10 1607 (verified against MScholtes/VirtualDesktop's 24H2 declarations), so no per-build fallback is declared; a moved interface or missing `powershell` fails best-effort with the detail in status bar and log.
An already-pinned view (`IsViewPinned`) is left alone rather than re-pinned.
In the settings form the option renders as a `CheckBox` (General group) and is read back in `readForm`.

Tags: windows

Covers:
- req~window-desktop-pin~2

Needs: impl, utest

### Per-desktop window geometry via the active-desktop poll
`dsn~window-position-per-desktop~2`

`WindowPositions` (config package) stores one geometry per desktop *name* in `<configDir>/window-positions.properties` (`name = x,y,width,height`, whole pixels) — a plain file-backed map with no JavaFX dependency, best-effort on I/O errors, malformed entries reading as absent (MADR 0021: the app remembers its own position; PowerToys FancyZones has no virtual-desktop awareness and cannot do this).
Tracking is active exactly while `showOnAllDesktops` is on and the OS is Windows (`Main.positionTrackingEnabled`, refreshed on a settings save like the pin itself): an unpinned window only exists on one desktop, so there is nothing to position, and the desktop read is Windows-only.
It rides the existing 2 s active-desktop poll: `scheduleActiveDesktopWatch`'s tick now also fires while tracking is on, and `pushActiveDesktop` additionally calls `Main.applyDesktopPosition(name)` on the FX thread after each successful read.
On a desktop *change* that method saves the current stage geometry under the previous desktop's name, then applies the new desktop's stored geometry.
Independent of tracking, the reserved key `WindowPositions.STARTUP` (`!startup`) holds the geometry at last exit: `Main.start` applies it before the stage shows — on every OS, pin on or off — so the app opens in place instead of at the fixed default; when tracking is on, the first poll read then applies the current desktop's own entry on top.
Restores go through the shared `Main.applyStoredGeometry`: a stored geometry intersecting no current screen (`Screen.getScreensForRectangle` empty — the monitor configuration changed) is removed instead of applied.
`Main.stop` persists the closing geometry under the startup key always, and additionally under the closing desktop's name while tracking is on — a move without a later desktop switch still survives the restart.
Unnamed desktops (name null) have no key: nothing is saved or restored there beyond the startup entry. A maximized window is neither saved nor moved.
Known ceilings: the poll means the window follows a desktop switch up to 2 s late, and a window moved within that window of time is attributed to the previous desktop; both self-correct on the next switch. A virtual desktop literally named `!startup` would collide with the reserved key.

Tags: windows, linux

Covers:
- req~window-position-per-desktop~1
- req~window-geometry-restore~1

Needs: impl, utest

### Group config via header context menu
`dsn~group-config-create~9`

A group header's right-click menu offers `Add CS config…` when the folder has no `CONTEXTSWITCHER.md`, otherwise `Edit CS config…`, and a primary-button click on the header does the same; the `Add task…` row carries no config button.
The header carries no cog icon anymore (`~9`, Carl, 2026-09-16): the category config opens in its own tab of the editor lane on the right, which is where it is reached, so the hover-only cog only duplicated it. Both open the file in the editor lane; the add variant first writes a mostly-commented skeleton whose frontmatter documents the default keys (`remote`, `workspacesRoot`, `repo`), then refreshes the list.
Whenever a category config opens (header click, `Add category…`, context menu), the task preview is dropped — the terminal shows its select-a-task placeholder and the queue lane empties — and the group's header row is selected when it already exists (right after `Add category…` the file watcher may not have delivered it yet), so the lanes never keep showing a previously selected, unrelated task next to the category config; a `GroupHeader` selection made any other way (e.g. clicking the header of an already-configured group) clears the preview through the list's selection listener as well. The `remote` line is pre-filled (uncommented) with the first configured remote from settings.yaml when one exists, so the common case needs no edit; the skeleton explains that `workspacesRoot` is where a task's Claude session starts (Claude managing each task's own directory under it). `TaskRepository.isTaskFile` excludes `CONTEXTSWITCHER.md` (like `TEMPLATE.md`), so the file never appears as a task or error row. Header clicks act only on the primary mouse button — a right-click just opens the menu.

Tags: windows, linux

Covers:
- req~group-config-file~1

Needs: impl, utest

### Pinned categories first
`dsn~pinned-categories~1`

A category's `CONTEXTSWITCHER.md` carries `pinned: true` (`GroupConfig.pinned`, parsed leniently like every other key — anything but `true` is unpinned).
`MainWindow.rebuildRows` copies the group buckets out of their `TreeMap` and stably sorts them by `!pinned`, so pinned categories head the list and both blocks stay alphabetical; the sort reads `groupDefaults`, which is memoized per rebuild (`dsn~group-config-cache~1`), so pinning costs no extra file reads.
Label buckets (`dsn~task-label-grouping~3`) are not re-ordered — a label has no config file to pin.
The group header's right-click menu carries a `Pinned` `CheckMenuItem`; toggling it writes (or removes) the `pinned:` line via `TaskFileParser.withPinned` — the same comment-preserving textual write as the group tags, creating the config from the skeleton first when the folder has none — and rebuilds the rows, since a category config is not a task file and the watcher-driven refresh would otherwise not re-order anything.
Unpinning removes the key rather than writing `pinned: false`, so an unpinned category carries no leftover line.

Tags: windows, linux

Covers:
- req~pinned-categories~1

Needs: impl, utest

### Pinned tasks first
`dsn~pinned-tasks~2`

A task's frontmatter carries `pinned: true` (`Task.pinned`, parsed leniently like every other key — anything but `true` is unpinned), written and removed by the same `TaskFileParser.withPinned` the categories use (`dsn~pinned-categories~1`): the key means the same in a task file, and lands after `title:` there.
`TaskOrder` ranks pinned before unpinned right after the error-row/status split, so every sort mode lifts a pinned task to the top of **its status block** rather than of the whole list — a pinned done task must not push active work down, and inside the Done section pinning still orders.
A pinned row leads its title with a small muted pin glyph (`MDIInterface.PIN` through the shared `statusGlyph`, id `task-pin-icon`) in the same title line the queue badge uses, so the reason a row sits at the top of its block is visible on the row and not only in its menu.
The row's right-click menu carries a `Pinned` `CheckMenuItem`; toggling it rewrites the task file through `MainWindow.rewriteFrontmatter` (which re-opens an editor showing that file), and the repository watcher's reload re-sorts the list the way a status flip does — no explicit rebuild.
Unpinning removes the key rather than writing `pinned: false`, so an unpinned task carries no leftover line.

Tags: windows, linux

Covers:
- req~pinned-tasks~2

Needs: impl, utest

### Group defaults applied on task creation
`dsn~group-config-apply~2`

Creating a task in a group (the `Add task…` dialog, or a category's `Add task…` row) reads that folder's `CONTEXTSWITCHER.md` via `TaskFileParser.parseGroupConfig` (tolerant — a missing, empty, or half-edited config yields `GroupConfig.EMPTY`, never a parse error) and seeds the new file's frontmatter: a `remote` inherited uncommented, and the resolved working directory (`GroupConfig.resolveWorkdir` — the fixed `workdir` when set, otherwise `workspacesRoot` verbatim, no task suffix; Claude manages the per-task subdirectory under it) written uncommented under the key that matches the group's shape.
A **remote** group (a `remote` is set) writes it as `claude.cwd` — the Claude session's directory.
A **local** group (no `remote` — the lightweight, no-tmux category) writes it as `folder` — the local directory the switch action focuses/opens in Explorer (`dsn~explorer-folder-focus~3`), since without a remote there is no Claude session to root.
Absent keys keep the commented example lines, so an untouched group creates the same minimal task as before. Root-level tasks (no group) inherit nothing.

Tags: windows, linux

Covers:
- req~group-config-file~1

Needs: impl, utest

`dsn~group-config-cache~1`

`MainWindow.groupDefaults` memoizes the parsed `CONTEXTSWITCHER.md` per group in a map that is cleared at the start of every `rebuildRows`. Every reader — the tag filter, the row and header "Tags" submenus (which collect the effective tags of *all* tasks per rendered cell), the desktop filter, the remote/repo/folder lookups — otherwise hit the disk once per call, and one list refresh became tasks² `exists`+read+parse calls on the FX thread: a 62 s freeze with 46 tasks on a slow Windows drive (logged by `dsn~fx-stall-log~1`), triggered by any list change (a Qodo sync's queue write, a tmux reconcile).
Freshness is unchanged: every create/modify/delete of a config reaches `rebuildRows` through the repository watcher (`dsn~task-repository-watching~6`), which drops the cache before the rows are rebuilt.

Tags: windows, linux

Covers:
- req~group-config-file~1

Needs: impl

### Markdown editor lane
`dsn~richtext-markdown-editor~9`

The selected task file split in two panes of the main window (`dsn~shell-layout~2`), each a JavaFX incubator `RichTextArea` with a `CodeTextModel` — **Notes** holds the Markdown body after the closing fence, the **Configuration** pane's *Raw YAML* view the YAML frontmatter without its `---` fences (the same file for a category's `CONTEXTSWITCHER.md`).
Opening a task file leaves the docks untouched — whichever pane is in front stays in front and shows the new file — while opening a category config puts Configuration in front, since a category is opened to edit its keys (`~7`→`~8`: the two were tabs of the editor lane's own `TabPane`; `~8`→`~9`: a task switch no longer pulled Notes in front of a Configuration pane the user was working in).
The file stays one Markdown file with frontmatter (MADR 0002): `MainWindow.splitFrontmatter` cuts it the way the parser does (`\r\n`→`\n`, BOM dropped, `\n---` closes), `joinFrontmatter` puts the fences back around a non-blank configuration for every save and dirty check, so the split is a view, not a storage format — no migration, and every textual mutator and the git merge keep working on the file as is.
The editor **auto-saves**: losing focus (and switching to another file) writes the file when its text differs from the loaded content — unchanged focus round-trips do not churn the watcher; Ctrl+S stays as the manual trigger. A **Revert** button reloads the file from disk, discarding unsaved edits; fine-grained undo/redo within the session is the `RichTextArea` built-in (Ctrl+Z / Ctrl+Y).
The Revert button is not focus-traversable: clicking it must not move focus out of the editor, or the focus-loss auto-save would write the edits to disk first and turn the revert into a no-op.
A guard skips reloading the editor when the selected file is already open, so the watcher round-trip does not stomp the editing session.
`TaskFileAccess` exposes two readers: `load` returns a human-readable `Cannot read …` string on failure — for the editor lane, which shows it as content — while `read` returns the real content or `null`. Every *transform-and-save* caller (status/session-id/tag/title/intellij frontmatter rewrites) uses `read` and aborts on `null`: applying a mutator to `load`'s error placeholder and writing it back overwrote a task file with its own error text (unparseable, `must start with '---' fence`), triggered when the file vanished mid-write — e.g. renamed by a concurrent import between the post-create sync's read and save.
The auto-save itself carries a **fence guard** (`MainWindow.droppedFrontmatterFence`): it is the only writer that persists arbitrary editor text rather than a fence-guarded textual mutator, so it refuses to write when the joined text would drop the opening `---` frontmatter fence off content that had one — the same unparseable `must start with '---' fence` corruption, reached by clearing the Raw YAML view (a blank configuration joins to bare notes). The file label reports the skipped save; any non-blank configuration keeps the fence, so legitimate saves are untouched.
The blank line between the closing fence and the body is **structure, not content**: every task file carries one (the template writes it), and keeping it in the notes editor opened every task on an empty first line — field report 2026-09-13, "the empty line on top of the notes is strange".
`splitFrontmatter` drops exactly one leading newline and `joinFrontmatter` writes it back, so a load and a save leave the file byte-identical; a *second* blank line is the user's and survives.
A file that happens to lack the blank line gains one on its first save, which is the shape the template and every writer in the app already produce. (`~6`→`~7`)

Tags: windows, linux

Covers:
- req~task-file-editing~1

Needs: impl, utest

### Editor field help (F1)
`dsn~task-field-help~1`

Pressing **F1** in the editor lane opens a non-modal popup listing every field the open file accepts, as a commented, copy-and-pastable template with placeholders — so the user can select what they need, Ctrl+C, and paste into the editor (a `Copy all` button copies the whole reference without dismissing the popup, which stays open beside the editor).
The reference text is the same content the app already seeds/generates, so the help never drifts from what the parser reads: a **task file** shows `TaskRepository.templateReference()` (the bundled `TEMPLATE.md`, `dsn~task-template-file~1`), a **category config** (`CONTEXTSWITCHER.md`) shows `TaskFileParser.groupConfigSkeleton` with hints on. The popup is a read-only monospace `TextArea` (selectable/copyable, not editable); the editor's type label carries a tooltip pointing at F1.

Tags: windows, linux

Covers:
- req~task-file-editing~1

Needs: impl

### Editor configuration form
`dsn~task-field-form~4`

A **configure icon button** in the Notes pane header (left of *Revert*, `MDIInterface.TUNE`) puts the **Configuration** pane in front — reopening it when closed, raising its window when popped out — which shows the frontmatter of the open file as a generated key/value form (`ConfigFormPane`, generated and written back by `ConfigForm`), so a task or a category is set up by filling fields instead of hand-writing YAML — the raw YAML stays reachable, as the form's fallback rather than the only way in.
The form is generated from `FrontmatterCatalog` — one control per key a user hand-edits, grouped into sections — the way the settings dialog is generated from `SettingsCatalog` (`dsn~settings-editor~4`); both render through the shared `FieldForm` (bold section header, label above a full-width control, hover highlight, the field's help as the row tooltip).
A task file gets the `TASK` catalog, a category's `CONTEXTSWITCHER.md` the `CATEGORY` one.
Text fields are strings, a checkbox is a flag, a drop-down the fixed sets (`status`), and a multi-line field a list, one entry per line (`tags`, `folders`, `browser.urls`).

Every control is pre-filled from the open file (`Frontmatter.parse` of the editor buffer, so unsaved edits are kept), and **Apply** writes back only the keys that actually changed, each through `Frontmatter.set` — a `YamlPatch` on the frontmatter block alone.
So comments, key order, machine-suffixed variants (`folders-windows`) and keys the catalog does not list survive a form edit, and the Markdown body is never touched.
Unlike the add-only form it replaces, an emptied field **removes** its key (an empty field means "not set", not `key: ''`), which is what makes the form an editor rather than an append box.
Keys the app writes for itself (`claude.workspace`, `claude.commit`, `suspended`, `storedTabs`) are not in the catalog and so are neither shown nor touched.

Three keys carry a shape the form hides:
`intellij` is enabled by the key's presence alone, so the section gets an *Open in IntelliJ* checkbox above its optional path (an enabled section with no children is written as the bare `{}` form);
`desktop` is written as the plain name, or as the nested `{name, completeControl}` form when *Clear that desktop on suspend* is ticked (`dsn~complete-control-desktop~1`);
`browser.urls` entries are edited as `url — title` lines and written back as a plain scalar or a `{url, title}` map (`dsn~browser-url-title~1`).
The `folders` list subsumes the single-directory `folder:` spelling a local category seeds — writing the one removes the other, so a file never names two sets of folders.

**Raw YAML** swaps the form for the frontmatter as YAML, in the frontmatter editor of `dsn~richtext-markdown-editor~9` — the fallback for anything the form does not cover; **F1** there opens the field reference (`dsn~task-field-help~1`).
Turning it on applies the form's pending changes first (an invalid form keeps the form view), turning it off rebuilds the form from the buffer, and **Reload** rebuilds it dropping unapplied changes.
A file without a `---` fence has nothing to configure, and the pane says so instead of showing a form.
**Pop out** moves the pane into a window of its own, which docks back (`dsn~shell-layout~2`).
Like *Revert*, the configure button and the pane's buttons are not focus-traversable, so using them does not trigger the editor's focus-loss auto-save; an apply is written into the editor buffer and saved right away (`~2`→`~3`: the form was a modal dialog with *View raw* and *Save*).
The pane's toolbar controls are icon buttons like every other toolbar's — `CODE_BRACES` (*Raw YAML*), `CHECK` (*Apply*), `RELOAD` (*Reload*), `OPEN_IN_NEW` (*Pop out*) — named by their tooltips, with ids for the UI test (`~3`→`~4`: they were text buttons).

Tags: windows, linux

Covers:
- req~task-file-editing~1

Needs: impl, utest

### Attachment preview on hover
`dsn~attachment-image-hover~2`

Hovering an attachment marker (`[image: …]` or `[file: …]`, the markers the queue boxes and the description field insert) in the editor lane pops the referenced picture up next to the pointer as a thumbnail (scaled into 480×480, ratio preserved), so a screenshot path is recognisable without opening the file.
`RichTextArea.getTextPosition(screenX, screenY)` maps the pointer to a text position on every mouse move; `Attachments.pathAt(line, offset)` returns the marker path covering that offset (pure logic, unit-tested — an unparseable path counts as no marker).
Re-entering the same marker leaves the popup in place (no flicker while the pointer travels along the marker text); leaving the editor, scrolling, and any key press hide it.
Anything that does not load as an image — a missing file, a `[file: …]` marker for a zip, a hand-written path — shows nothing rather than an error.

The **queue pane's text boxes** carry the same per-marker preview: the add box, every queued message box, the Add-task dialog's description field (all three via `QueuePane.installAttachments`) and the read-only **last sent** box (`dsn~last-sent-message~5`), whose recorded text keeps the local markers the send rewrote for the chat.
A plain `TextArea` maps the pointer to a text index through its skin (`TextAreaSkin.getIndex(x, y)`), which feeds the same `Attachments.pathAt` lookup — so a text with several attachments shows the hovered picture, not all of them stacked.
An uploaded attachment's **remote path** (`…/.contextswitcher/attachments/<name>`, what the send rewrote a marker to — pasted back from the terminal, say) previews too: the upload keeps the file name, so `Attachments.pathAt(line, offset, attachmentsDir)` maps it to `<attachmentsDir>/<name>` without any stored mapping.
Editing the text, leaving the box and scrolling hide the popup.

The **terminal** previews the same way: hovering a file link (`dsn~terminal-file-links~1`) pops up the thumbnail of the file it opens on this machine; scrolling and leaving the link hide it.

Tags: windows, linux

Covers:
- req~task-file-editing~1
- req~message-queue~2
- req~terminal-live-mirror~1

Needs: impl, utest

### Copy an attachment's path
`dsn~attachment-copy-path~1`

Right-clicking an attachment marker — wherever `dsn~attachment-image-hover~2` previews one: the editor lane, the queue boxes, the Add-task description field and the last sent box — opens a menu with **Copy path**, which puts the marker's local path on the clipboard, so the file can be handed to another program without selecting the path by hand.
The marker under the pointer is found the same way the hover finds it; a right-click on plain text shows the control's own menu as before.

Tags: windows, linux

Covers:
- req~task-file-editing~1
- req~message-queue~2

Needs: impl, utest

### Markdown syntax highlighting
`dsn~markdown-syntax-highlighting~3`

A `SyntaxDecorator` per tab's `CodeTextModel` styles the configuration tab as YAML (keys, comments — a leading `#` line is a comment, not a heading) and the notes tab as Markdown (headings, bullet markers, quotes, inline code) via a pure line-based tokenizer; the tab decides the language, so no fence detection is needed.

Tags: windows, linux

Covers:
- req~task-file-editing~1

Needs: impl, utest

### A fresh install says which tools are missing
`dsn~startup-tool-check~1`

On the fresh-machine path — the same empty or missing task directory the setup wizard keys on (`dsn~setup-wizard~8`, `dsn~task-git-clone-setup~2`) — `RequiredTools.missing` scans `PATH` for `tmux`, `ssh` and `git`, and a warning alert names those that are not there.
`tmux` runs local and remote Claude sessions, `ssh` reaches every remote, `git` syncs the task files; a missing one would otherwise only surface at the first click that needs it, as a failure with no obvious cause.
Once, not on every start: the tools are installed once, and a per-launch check would be a permanent nag for a one-time problem.
Windows is skipped — `ssh` ships with it and local sessions there are hosted by the app itself (`dsn~terminal-owned-session~3`), so `tmux` is not part of that install.
The scan tests each directory for an executable file of that name, so it costs no child processes; a blank entry — POSIX's "current directory" — is not searched.
Searched are the `PATH` entries **and** a list of well-known directories (`/usr/bin`, `/bin`, `/usr/local/bin`, `/opt/homebrew/bin`, `/run/current-system/sw/bin`, `~/.nix-profile/bin`, `~/.local/bin`): a GUI-launched app inherits the desktop session's environment rather than the login shell's, so an installed tool can be off the app's own `PATH` — reporting it as missing right after the user installed it is worse than not checking at all.
`RequiredTools.resolve` returns the absolute path it found (else the bare name), and the local Claude launcher uses it for `tmux` for the same reason: the session must start even when the app's `PATH` is thin.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### A button opens the log
`dsn~open-log-button~1`

A small icon button at the right end of the status bar opens the newest `~/.contextswitcher/logs/*.log` through `java.awt.Desktop.open`.
It sits where the failures are read: a status-bar line names the reason, the log carries the context around it, and a packaged app has no console to fall back on.
The directory is fixed under `user.home` (tinylog writes it there, not under the config dir).
Anything that goes wrong — no log yet, no application registered for it — puts the **path** in the status bar, so the file is still findable by hand.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl

### The status bar stays one line
`dsn~status-bar-one-line~2`

Every text `SwitchStatusBar` puts on its label — a switch's task title, a `message`, a hover text — is folded onto a single line first (whitespace runs collapsed to one space, trimmed).
A message often carries text the app did not write: a failed command's stderr, a queued multi-line prompt.
Left as-is, its line breaks grow the label, and with it the bottom bar, over a large part of the window.

A line too long for the bar is cut by JavaFX, not by the code: the `HBox` shrinks the label — by far the widest child, so the action chips next to it keep their size — and `Label` ellipsizes its text by default.
The whole line therefore goes into the label's tooltip as well, since an ellipsized URL or error message says nothing.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### Main window
`dsn~main-window~2`

`MainWindow` shows a flat `ListView` of tasks: root-level tasks first (sorted: active, suspended, done, then title), then one collapsible group header per subfolder followed by its tasks sorted the same way, a switch control per row, and a status area rendering one chip per action (color-coded state; tooltip shows the failure detail). Parse-error entries render as red rows showing their relative file path and problem, grouped the same way as loaded tasks. A flat list (not a tree) is used so every row shares the same left and right edge regardless of grouping.

Tags: windows, linux

Covers:
- req~task-list-window~1
- req~task-parse-error-handling~1
- req~per-action-feedback~1
- req~task-folder-grouping~1

Needs: impl

### Docked panes in a ShellFX shell
`dsn~shell-layout~2`

The main window hosts its panes in a ShellFX shell (`ShellFxHost`, MADR 0032): **Tasks**, **Terminal**, **Notes**, **Configuration** and **Queue** are dock tabs, laid out by default like the three lanes before them — tasks 0.28, terminal 0.36, notes over the queue 0.62/0.38, configuration a second tab beside notes.
A tab can be dragged into another pane's tabs or split beside it; a dock that loses its last tab closes, so the panes share whatever docks are left.
Closing a tab — from its context menu — hides its pane, and the panes menu at the right end of the main toolbar shows it again, in its last dock if that still exists.
The dock tabs show no ×: one stray click beside a tab title closed a pane, so `main.css` hides the close button under the style class every dock carries (`ShellFxHost.DOCK_STYLE`, added to the docks ShellFX creates on a drop by `tidyDocks`) — hidden rather than `Tab.setClosable(false)`, which the context menu's close entries depend on.
Every pane's own toolbar — the task list's, the Notes and Configuration headers, the Terminal pane's band, the Queue's top bar — has the task toolbar's height and background: `.list-toolbar` and `.panel-header` share one fixed height and one two-layer paint in `main.css`.
The Terminal pane has no title line of its own: `TerminalPane` builds its band around the mirrored pane's title (right-click anywhere on the band for *Select the mirrored task*, `dsn~terminal-mirrored-task~1`), and `MainWindow` adds the Claude and IntelliJ buttons at its right end (`addToolbarControls`) and the PR rows below it (`setSubHeader`) (`~1`→`~2`).
The main toolbar above the docks holds the app-wide controls (energy saver, browser status, update, settings); the task list toolbar keeps what acts on the list, and the status bar sits below the docks.
The Configuration pane can pop out into a window of its own and dock back (**Dock back**, closing the window, or dragging its title over the main window).
`ShellFxHost.Hosted` hands `MainWindow` the one way the editor brings a pane forward: showing Configuration on request (`dsn~task-field-form~4`).
The shell needs JavaFX's `HeaderBar` preview API, so `-Djavafx.enablePreview=true` is in the app's, the packaged app's and `uiTest`'s JVM arguments; ShellFX is a pinned `2.0.0-SNAPSHOT`, and `ShellFxHost` works around its known bugs in place.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl

### The app has an icon
`dsn~app-icon~4`

The stage carries the browser extension's target-rings mark at 16/32/64/128 px, so the title bar and the taskbar button show it, and the Settings dialog shows the same mark at 48 px beside its header text.
`AppIcon` loads the extension's `icon-128.png`, which `processResources` copies into the app jar — one raster for the whole product, and no second drawing to keep in sync.
Loading rather than drawing is what makes the window icon appear at all: `WindowStage.findBestImage` accepts only byte-based pixels (`BYTE_RGB`, `BYTE_BGRA_PRE`, `BYTE_GRAY`) and silently ignores the rest, so the `INT_ARGB_PRE` image a canvas snapshot produces was dropped and the window kept the toolkit's default icon — twice (2026-09-09, 2026-09-12).
One mark for the app and its extension is what makes it recognizable as the same tool as the browser toolbar button.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### The window title names the selected category
`dsn~window-title-category~3`

The stage title is `<category> | <n> waiting | <n> working | ContextSwitcher` for the selected row's category, each segment dropped when it is empty or zero — so a root-level task with nothing running leaves the plain `ContextSwitcher`.
The counts are over that category's tasks: `waiting` is the same "needs the user" set the awaits-input filter uses (`dsn~awaits-input-filter~1`), `working` the orange running dot.
The app name goes last because a taskbar button or window switcher truncates the end: the category and its counts are what distinguish two ContextSwitcher windows, the app name is not.
Selecting a category header, a task, or a corrupt row switches the title's category; a label header leaves it alone (it sits inside the category already shown).
A virtual-desktop switch under the active-desktop filter (`dsn~active-desktop-filter~6`) switches it too, to the category of the task that desktop re-selects (`dsn~desktop-last-task-selection~4`) — even while that selection waits for the queue box to lose the keyboard.
A desktop with no task to select names itself: its desktop name stands in for the category, so the title never keeps the previous desktop's category.
The counts follow the status poller (`dsn~task-running-indicator~7`) and every row rebuild, so the title stays as current as the dots.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### A click in a task's pane selects its row
`dsn~pane-click-selects-task~1`

The terminal mirror, the file editor, and the message queue all follow the previewed task (`MainWindow.previewedTask`, set from the list selection).
That selection can drift away from what the panes show: a rebuild clears the selection as a deliberate no-op (`null` is ignored so transient rebuild clears survive), and "Add task…" moves keyboard focus off the list onto the toolbar button while the new task is created in the background — so the user ends up working in a pane with no matching row highlighted ("I don't know which task it was").
A `MOUSE_PRESSED` event filter on each of the three pane roots therefore re-selects the previewed task's row (`MainWindow.selectPreviewedTask` → `selectTask`) on any click inside them — including on a button belonging to the task, since the capture-phase filter fires for descendant clicks and never consumes, so the terminal canvas and the panes' own buttons still receive the click.
Focus is left in the clicked pane (`selectTask` only sets the selection, it does not focus the list), so typing continues to reach the terminal; the off-focus selection accent (`dsn~main-window~2`) makes the re-selected row visible without stealing focus.
Selecting a row that is already selected fires nothing (the list's listener only reacts to a *changed* selection), so a click in the terminal never re-attaches the mirror.
A `GroupHeader`/`LabelHeader` or a corrupt `TaskEntry.Failed` row carries no task, so `previewedTask` is null and the click is a no-op — no stale row is selected.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### Scrolling a row into view
`dsn~task-list-scroll-into-view~1`

Every place that has to reveal a task row — the pane-click re-selection (`dsn~pane-click-selects-task~1`), the find bar's Enter and Escape (`dsn~task-find~9`), and the after-rebuild pending selection (`dsn~task-move-dnd~6`) — goes through `MainWindow.scrollRowIntoView` instead of `ListView.scrollTo`.
It reads the list's `VirtualFlow` and does nothing when the row lies strictly between the first and the last visible cell: those two are the ones the viewport may clip, and the topmost one also sits under the pinned section header (`dsn~sticky-group-header~1`), so only the rows in between are genuinely on screen.
Clicking into an editor or queue input therefore leaves the list exactly where it was, rather than jerking the already-visible row to an edge.
A row that does have to be revealed is scrolled so it lands about a third of a viewport down (`scrollToTop(index - visibleRows / 3)`) — clear of the pinned header, and with rows above and below it, so the user can scroll in both directions from where they landed instead of being parked against an edge.
Before the first layout there is no flow (or no cells) to measure, and the plain `ListView.scrollTo` is used.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### Delete a corrupt task file from its error row
`dsn~corrupt-task-delete~2`

A parse-error row (`TaskEntry.Failed`) carries no `Task`, so the normal row actions and the kill dialog (`dsn~task-delete~6`, keyed on a live `Task` and its cleanup targets) do not apply. `TaskListCell.errorRow` therefore renders a hover trash button — and a matching "Delete file…" context menu — that calls back into `MainWindow.deleteCorruptFile(fileName)` with the failed file's name.
The menu leads with "Copy file path", which puts the file's absolute path (`tasksDir` resolved against the failed file's name) on the clipboard — the row names the file but not where it lives, and repairing a broken frontmatter by hand needs that path in an editor or shell. That method confirms (the delete is irreversible), deletes the file via `TaskFileAccess.delete`, and clears the editor lane when it was showing the deleted file; the watcher then drops the red row. A broken frontmatter file (e.g. one corrupted into the unparseable `must start with '---' fence` state) has nothing worth keeping, so no remote cleanup is offered — this is a plain file delete.

Tags: windows, linux

Covers:
- req~task-parse-error-handling~1

Needs: impl

### Folder group headers
`dsn~task-folder-grouping-ui~8`

`MainWindow` flattens the entry list into an observable list of rows: ungrouped `TaskEntry` rows first, then, per subfolder, a `TaskListCell.GroupHeader(name, expanded)` record followed — when expanded — by that folder's `TaskEntry` rows. `TaskListCell` is a `ListCell<Object>` dispatching on `TaskEntry.Loaded` / `TaskEntry.Failed` / `GroupHeader`. Clicking the header's disclosure arrow (its click is consumed before the cell handler) toggles the folder in a `collapsedGroups` set and re-flattens; clicking the rest of the header opens the group's `CONTEXTSWITCHER.md` in the editor (`dsn~group-config-create~9`). The set is kept across list rebuilds, so a background file-watch update never re-collapses a group the user opened. Each row's width is bound to the list's width so a long title ellipsizes rather than pushing the status label and action icons out of view.
While a filter narrows the view (`dsn~task-find~9` and friends), a group with no matching task is normally hidden — but a folder that has never held any task at all (freshly created via "Add category…", `dsn~task-create-ui~15`) is exempt: `rebuildRows` computes the set of group names that have at least one entry and only hides a narrowed-and-empty group when it is in that set, so a brand-new empty category stays visible in the left list — with its "Add task…" row — right after creation instead of only reappearing once every filter is cleared or the app restarts.
A typed **search** is the exception to that exemption (`~6`→`~7`, field report 2026-09-16: two empty JabRef categories with their "Add…" rows under a PR-URL query — "they can never hit, and results should only show categories with hit results"): a query looks for a task, so a category without one is skipped by the task-less-folder pass while `searching`, like it is under the active-desktop filter (`dsn~active-desktop-filter~6`), and the result lists only categories holding hits.
So that "Add category…" still shows what it made, it clears the query (`closeFind`) before opening the new config, and "Add task…" clears it once a task was created — its *Cancel* keeps the query (`~7`→`~8`, 2026-09-16); the tag, awaits-input, running and desktop filters keep the exemption as before.
The visible selection survives rebuilds too: replacing the rows clears the ListView selection, so the selected task's id is captured as the pending selection and re-applied after the rebuild — a background change to any task file (a title adoption renaming a file, a PR write-back), a collapse toggle, or a filter change never silently drops the highlight while the terminal/editor/queue lanes keep showing the task; if the selected task itself was renamed, the not-found pending id is what `taskRenamed` re-points at the new id.
While a filter narrows the view the selection is only preserved when the rebuilt rows still show that task — and that test is by **task id**, not by row equality: a task being worked on has its file rewritten under it (session id, `@cs_status`, commit), so the rebuilt row is a different `TaskEntry.Loaded` record for the same task, and comparing rows dropped the highlight of exactly the running task the user was watching (field report 2026-09-10).
A group whose own `CONTEXTSWITCHER.md` sets a `remote` shows a muted terminal glyph right after its name (tooltip names the host), so remote-Claude categories tell apart from local ones at a glance; the settings.yaml fallback remote deliberately does not count — the glyph marks the group's explicit choice, mirroring the local-group notion of `dsn~group-config-apply~2`.

Tags: windows, linux

Covers:
- req~task-folder-grouping~1

Needs: impl, utest

### Category workspace path on the header
`dsn~category-header-path~1`

A category header has a second line naming, once, the directory its task workspaces live under: `MainWindow.groupPath` — the category config's `workspacesRoot`, else its fixed `workdir`, else nothing (no second line) — carried as `GroupHeader.path` and rendered muted and small, cut at the front when narrow (the end of a path tells categories apart), the full path in its tooltip.
Its task rows then name only their own directory below it: `TaskListCell.configSummary` passes the task's workspace (`wd …`) and IntelliJ project path through `WorkspacePaths.belowRoot`, which strips the header's path and a separator when the path lies strictly below it (either separator, trailing ones ignored) and leaves any other path whole.
Carl, 2026-09-16: "Repo path sits once in the category header (in second line). From Workspace path should only show the name of directory below the path showing in category repo path."

Tags: windows, linux

Covers:
- req~task-folder-grouping~1

Needs: impl, utest

### Sticky section header
`dsn~sticky-group-header~1`

While the list is scrolled into a section, that section's header stays pinned to the list's top edge, so a long category never leaves the user guessing which one they are reading.
`MainWindow.installStickyHeader` wraps the `ListView` in a `StackPane` and floats a second `TaskListCell` — built by the same cell factory and attached to the same list via `updateListView` — over its top-left corner.
Pointing that cell at a row index with `updateIndex` renders exactly what the real row renders, so the pinned header carries the live category's remote glyph, tags, and context menu, and a click on it toggles the group like a click on the row it stands in for; no second rendering path exists to drift.
The pinned index is the nearest `GroupHeader`/`LabelHeader`/`DoneHeader` at or above the topmost fully visible row (`VirtualFlow.getFirstVisibleCellWithinViewPort`), and is dropped when that header *is* the topmost row — it is then on screen in its own right, and a pinned copy would double it.
Repaints are driven by the vertical scroll bar's value (every scroll source moves it), the list's height, and row rebuilds; an unchanged index repaints nothing, so scrolling within one section costs no cell rebuild.
The `StackPane` is `pickOnBounds(false)`, the pinned cell is sized to its preferred height, and its width stops short of the vertical scroll bar, so only the pinned strip takes clicks and the rest of the list — scroll bar included — stays reachable.
The overlay `StackPane` carries the `task-list` style class as well: `main.css` scopes the row rules to `.task-list .list-cell`, a descendant selector the pinned cell escapes by hanging off the `StackPane` beside the list rather than inside it — without the class the pinned header has no section background at all and the rows scroll visibly through it.

Tags: windows, linux

Covers:
- req~task-folder-grouping~1

Needs: impl, utest

### Completed tasks in a collapsed bottom section
`dsn~done-section~1`

`rebuildRows` diverts every `TaskEntry.Loaded` whose status is `done` out of its group (and out of the ungrouped block) into one shared list, rendered — below all folder groups — as a `TaskListCell.DoneHeader(count, expanded)` followed, when expanded, by the done rows (sorted by `ORDER`). Error rows are never diverted (a parse failure must stay visible in place). The section is pinned to the very bottom and starts **collapsed** (`doneSectionCollapsed` defaults true, toggled by clicking the header — mirroring the group-collapse click, a separate flag rather than a `collapsedGroups` entry so it has the opposite default and cannot collide with a folder named "Done"). The header and each done task row carry a grayer background so finished work reads as de-emphasised — a `done-cell` style class on the `ListCell` (painted `-color-bg-inset`), not the inner row: the row's width is bound narrower than the cell to reserve scrollbar room, so a background on it would leave gaps at both edges (unnoticed in light mode, obvious in dark), whereas the cell reaches border to border. The category (`group-header-cell`) and label headers use the same cell-level trick with `-color-bg-subtle`. A group left with only done tasks still renders as an (empty) group header from the `folders` set.

Tags: windows, linux

Covers:
- req~task-folder-grouping~1

Needs: impl

### Task-list toolbar
`dsn~task-list-toolbar~3`

`MainWindow.toolBar` builds a `ToolBar` placed at the top of the left `VBox`, above the find bar and the list (it was previously the whole window's top bar) — so every list-wide action sits over the list it acts on.
The controls, left to right: the back/forward history `Button`s (`ARROW_LEFT`/`ARROW_RIGHT`, `dsn~task-history-navigation~2`); an add `MenuButton` (`PLUS`) whose items open `Add task…`/`Add category…`; the filter `MenuButton` (`FILTER_VARIANT`) holding the three narrowing filters as `CheckMenuItem`s (`dsn~awaits-input-filter~1`, `dsn~active-desktop-filter~6`, `dsn~running-tasks-filter~1`) plus, after a separator, the tag filter as a `Tags` submenu (`Menu`, `dsn~task-tag-filter~2`); the sort `MenuButton` (`SORT_VARIANT`, `dsn~task-sort-modes~2`); the group `MenuButton` (`FORMAT_LIST_GROUP`, one `CheckMenuItem` per grouping, `dsn~task-label-grouping~3`, `dsn~pr-status-grouping~1`, `dsn~nested-grouping~1`); the energy-saver `ToggleButton` (`LEAF`, `dsn~energy-saver~1`); the restart-to-update `Button` (`UPDATE`, always shown, blue once an update exists, with a badge counting the pending changes, its tooltip listing the pending news of `dsn~whats-new-upstream~7`, `dsn~restart-to-update~11`); a "more" `MenuButton` (`DOTS_VERTICAL`) whose items — each a `CustomMenuItem` label carrying a tooltip that explains the action, since a plain `MenuItem` takes none — are open-tasks-directory, sync-tmux-windows (`dsn~tmux-sync~7`), restart-running-tasks (`dsn~tmux-restart-running~1`), and refresh-now (`dsn~energy-saver~1`); and the settings gear (`COG`, `settingsButton`, `dsn~settings-editor~4`) at the right end.
The four occasional actions used to be icon buttons of their own; with a fifteenth control on the way the bar outgrew the narrow list pane, so they share one menu (a ribbon library was considered and dropped: unmaintained since 2018, and a tabbed band costs more list height than it saves width).
Every control is icon-only via the shared `iconButton`/`iconMenuButton`/`iconToggle` helpers (`SvgNode`, MADR 0010 — `Styles.SMALL`/`BUTTON_ICON`/`FLAT`, muted fill, a tooltip naming it), so the whole bar stays narrow over a list that can be squeezed to `minWidth`.
The filter button's text badges the count of active narrowing dimensions — each of the three toggles plus a non-empty tag selection — blank when none, since an icon alone cannot show that a filter is on.
The single-shot items (restart, sync) disable themselves for their ssh round-trip and re-enable on completion per the async single-shot convention; the icon carries no progress text, so the disable is the only in-flight cue.
The toolbar (`.list-toolbar`) shares one font-relative fixed height with every pane toolbar (`.panel-header`) in `main.css`, so all of them line up in height and paint alike (`dsn~shell-layout~2`); the ⋮ menu sits at its right end, pushed there by a growing spacer (`~2`→`~3`). All three control types share one `main.css` block (font-relative `em` padding, flat/transparent background with a subtle hover) so a `Button`, `ToggleButton`, and `MenuButton` in the bar look and size identically — AtlantaFX otherwise pads them differently in a toolbar and, lacking a `.toggle-button.flat` rule, gives the toggle a filled background with an accent hover border. Each icon `MenuButton`'s child `.label` padding is zeroed and its drop-down arrow region collapsed so it is no wider than the plain icon buttons (the menu still opens on a click of the glyph).
`main.css` also reserves the check-mark column of every `CheckMenuItem`/`RadioMenuItem` unconditionally (a fixed `.check`/`.radio` size, invisible while unticked): AtlantaFX sizes the mark only under `:checked`, so ticking an item widened just that row's left column while JavaFX kept the label offset it had computed when the popup was laid out — the ticked (or just-unticked) item's label then sat indented against its neighbours until the menu was rebuilt.
UI wiring, not separately unit-tested (house convention); the add menu carries id `add-menu` so the TestFX add-task tests can reach its item, and `FilterMenuAlignmentUiTest` pins the label column against a check toggle.

Tags: windows, linux

Covers:
- req~task-list-toolbar~2

Needs: impl, utest

### Task sort modes
`dsn~task-sort-modes~2`

`TaskOrder` (an enum: `ALPHABETICAL`, `LAST_UPDATE`, `ACTION_NEEDED`) builds the comparator `MainWindow.rebuildRows` sorts every block with (ungrouped, per group, Done), replacing the fixed `ORDER` of `dsn~main-window~2` (not bumped — the base order is unchanged, the selected mode only refines it).
Every mode keeps error rows first, sorts by `TaskStatus` ordinal (active, suspended, done) next, and then puts the pinned tasks of each block first (`dsn~pinned-tasks~2`); the mode decides the order within those blocks, with the case-insensitive title as the final tie-breaker: `ALPHABETICAL` adds nothing, `LAST_UPDATE` sorts by the task file's modification time descending (memoized per rebuild, so at most one `Files.getLastModifiedTime` per entry and rebuild; an unreadable file counts as 0 and sorts last), `ACTION_NEEDED` ranks the live `@cs_status` (`dsn~task-running-indicator~7`): `limit`, then `attention`, then `waiting`, then `working`, then status-less.
The status and file-time lookups are passed in as functions, so the ordering logic stays pure and unit-tested.
The task-list toolbar (`dsn~task-list-toolbar~3`) carries an icon-only `MenuButton` (`SORT_VARIANT`) with one radio item per mode; the choice is UI state kept across restarts (`Preferences`, like the last import host) — unlike the narrowing filters, a sort order is how the user reads the list, not a transient view.
While `ACTION_NEEDED` is active, `updateRunningStatuses` rebuilds the rows instead of merely repainting them — a status change reorders rows, just as it changes row membership for the awaits-input filter.

Tags: windows, linux

Covers:
- req~task-sort-modes~1

Needs: impl, utest

### Group by labels
`dsn~task-label-grouping~3`

The "Labels" item of the task-list toolbar's group menu (`dsn~task-list-toolbar~3`; persisted as `MainWindow.Grouping` under the `grouping` preference, the old `groupByLabels` boolean read once as its default) switches `MainWindow.rebuildRows` from folder buckets to label buckets: each loaded task files under every tag of `effectiveTags(task)` (`dsn~task-tag-filter~2`) — a task with several tags appears once per tag — while untagged tasks and error rows stay in the top ungrouped block and the Done section keeps gathering completed work.
A label bucket renders as a `TaskListCell.LabelHeader(name, expanded, count)` row: the tag's palette-colored chip plus a task count; clicking anywhere on it toggles collapse (reusing `collapsedGroups`, so a label shares collapse state with a same-named folder — acceptable, the two views never show together), and selecting it clears the preview lanes like a folder header.
While the label view is on, each task row leads its second line (before the tag chips and the config subtitle) with its category name in muted small text plus a `·` separator — the folder is invisible through the label headers, so the row itself answers where the task lives; a root-level task shows nothing extra, and in folder view nothing changes.
The cell reads "not folder view" through a `BooleanSupplier` passed by `MainWindow` (so the PR-status view of `dsn~pr-status-grouping~1` shows the category too), and the menu's `rebuildRows` re-renders every row, so the category appears and disappears with the view.
A label header is deliberately not a drop target and has no context menu: there is no folder or config file behind it, and dropping a task cannot mean "add this tag" while drops elsewhere mean "move to".
Folder-only affordances — empty-folder headers and the "Add task…" row — are skipped in label view: a label exists only through the tasks carrying it.
UI wiring, not separately unit-tested (house convention).

Tags: windows, linux

Covers:
- req~task-label-grouping~3

Needs: impl

### Group by PR status
`dsn~pr-status-grouping~1`

The "PR status" item of the group menu reuses the label view of `dsn~task-label-grouping~3` (`LabelHeader` rows, the category on each task row, no folder affordances), with buckets from `PrStatusGroup.bucket` for each of the task's PRs `prInfoByUrl` has resolved; a task without one stays in the ungrouped block.
`PrStateLookup` fetches `isInMergeQueue` and `reviewDecision` in the existing batched query, so the grouping costs no extra API request.
Merged, closed, and merge queue win; otherwise a repository in which any known PR carries a `status:` label (`PrStatusGroup.labelRepos`, JabRef's review workflow) groups by that label (the first one, `no status` when the PR has none, `draft` for a draft), and every other repository by GitHub's state: `draft`, `changes requested`, or `ready`.
The buckets sort in workflow order (`PrStatusGroup.BY_WORKFLOW`), not alphabetically; `updatePrStates` rebuilds the rows instead of just repainting them while this view is on, since a new state moves a task between buckets.
Knobless on purpose: which of the two schemes applies is read from the labels themselves, not configured per category.

Tags: windows, linux

Covers:
- req~pr-status-grouping~1

Needs: impl, utest

### Nested grouping
`dsn~nested-grouping~1`

The group menu's items are `CheckMenuItem`s over an `EnumSet<MainWindow.Grouping>` (persisted comma-separated under the `grouping` preference, a single stored name reading as itself); unticking the last one re-ticks it.
The first ticked grouping in enum order (folder, labels, PR status) builds the top level exactly as `dsn~task-label-grouping~3`/`dsn~pr-status-grouping~1` describe; `MainWindow.addNestedRows` then splits each expanded top-level group by the remaining ones recursively: tasks without a key at a level (`groupKeys`) first, then one `LabelHeader` per key, indented by its depth, a task with several keys appearing under each.
A nested header's collapse key is its path (parent key, `U+001F`, name), so the same label under two categories collapses independently; a find force-expands nested headers like top-level ones.
The category line on task rows shows whenever folder is not ticked, and PR-state changes rebuild the rows whenever PR status is ticked at any level.

Tags: windows, linux

Covers:
- req~nested-grouping~1

Needs: impl, utest

### Find bar
`dsn~task-find~9`

A find bar sits permanently above the task list: a search field — a bordered `.search-field` box wrapping a leading magnifier glyph, a borderless inner `TextField`, and a trailing clear ✕ (the look of GemsFX's `SearchTextField`, built natively rather than pulling that library) — followed by a match count. A capture-phase `Ctrl+F` filter on the scene focuses the field and selects its text, so a fresh query overwrites the previous one. The ✕ is shown only while the field has text (bound to the text property) and clears it in place, keeping focus in the field — unlike Escape, which clears and returns focus to the list.
The filter must run in the capture phase rather than as a scene accelerator: an accelerator fires only after the focused node's own handlers, so the terminal pane's control-key forwarding (`dsn~terminal-pane~14`) consumed `Ctrl+F` as `^F` and the find bar never opened while the terminal had focus.
The terminal consequently loses `^F` (forward-char) — an accepted trade. Typing filters live: `MainWindow.rebuildRows` drops every entry for which `TaskSearch.matches(entry, query)` is false and, while a query is active, force-expands all groups and the Done section and hides groups with no match, so every hit is visible without extra clicks. `TaskSearch` is a pure, unit-tested helper matching the query case-insensitively as a substring over the entry's searchable text: for a loaded task its id, title, remote, note, folder, tags, browser URLs (and their titles), the whole configuration (Claude cwd/workspace/session, tmux session/window, terminal tab title, IntelliJ project/remote/IDE, chat) and the Markdown note body — the URLs make a task findable by its PR link, the note body by anything written in it; trailing `/` is dropped from the query, so a URL copied with a trailing slash still finds the task storing it without one; for a failed entry its file name and parse error.
A task whose own fields miss the query is then matched against its chat messages — the queued ones and every sent one, the last plus the recorded history (`dsn~message-queue-store~2`, `dsn~last-sent-message~5`) — via the `Supplier<String>` overload of `TaskSearch.matches`, which `MainWindow.messageTextFor` fills by reading those `.queues/` files.
As a fallback rather than part of the searchable text: the queue lives outside the task file, and reading it for every row on every keystroke would put file I/O on the typing path — this way only the tasks that did not already match are read.
The matching itself runs off the FX thread: `applySearch` bumps a generation counter and hands the query with a snapshot of the entries to a single-thread worker, which posts the matching ids back via `Platform.runLater`; `rebuildRows` then only consults that set (`searchHits`).
The worker posts twice: first the hits on the task's own fields, which are in memory and show at once, then — after the chat-message fallback read the queue files of the remaining tasks — those hits plus the message-only ones; meanwhile the match label adds "· searching messages…".
A single post at the end made a PR-number search wait on every queue-file read although the URL holding it was in memory all along (field report 2026-09-22: about ten seconds).
A newer keystroke bumps the generation, so the running query stops at its next entry and a stale result is dropped when it arrives — typing never waits for a search in flight (field report 2026-09-12: the field stalled while the queue files were read per keystroke).
A *changed* query empties the list at once and sets the match label — and the emptied list's placeholder, instead of "No tasks match the current filters." — to "Searching…" until its hits land: keeping the previous rows on screen — on the first keystroke, the full list — read as a search that does nothing (field report 2026-09-14).
Emptying the rows clears the list's selection, which `rebuildRows` does not preserve under a search, so `applySearch` remembers the selected task first and re-selects it once the hits land — when it is one of them and nothing else was selected meanwhile.
A change to the entries re-runs the current query the same way, so a task edited or created while searching is matched against the new content; since the query text is unchanged, the rebuild shows the previous hits until that result lands, so a file write does not blank the list.
Enter selects and scrolls to the first matching task (and focuses the list); Escape (or the clear button) clears the query, returns focus to the list, and scrolls the selected row back into view — in the restored full list the found task usually sits outside the viewport. A blank query matches everything, so an empty bar shows the full list.
A running query **suspends the narrowing content filters** — tag, awaits-input and running-tasks (`dsn~task-tag-filter~2`, `dsn~awaits-input-filter~1`, `dsn~running-tasks-filter~1`): `rebuildRows` reads each of them as `!searching && <flag>`, so while the find bar has text the query alone decides which *tasks* show.
Find is an explicit "where is this task", those filters are ambient state the user set at some other moment, and ANDing the two made the bar answer "0 matches" for a task that plainly exists (field report 2026-09-09: a PR URL found nothing because its category lives on another virtual desktop while the desktop filter was on).
The active-desktop filter is the exception and keeps narrowing (`dsn~search-hits-off-desktop~1`): suspending it moved the list to another desktop behind the user's back, which reads as the app switching context on its own (field report 2026-09-10).
The filters stay ticked and take effect again the moment the query is cleared.
Clearing the query keeps whatever task is selected — the match the user found and focused stays selected, rather than the list reverting to the task that was selected before the search: `rebuildRows` does not preserve a selection the active search hides, so no stale pre-search id lingers to override the found task once the filter is lifted.

Tags: windows, linux

Covers:
- req~task-find~3

Needs: impl, utest

### Category scope chip in the find field
`dsn~category-search~3`

There is one filter field, the list's find bar (`dsn~task-find~9`); a category is searched by scoping that field to it (`~3`, Carl, 2026-09-16 — `~2` put a second search field in a row under a selected category header).
Pressing a category header's **name** (`TaskListCell` label `group-name-label`, hand cursor, tooltip "Search in <name>") calls `MainWindow.scopeToCategory`: the name appears as a chip inside the find field, between the magnifier and the text, the prompt becomes "Find in <name>…", and the keyboard moves into the field, so typing searches that category. The rest of the header still opens the category config (`dsn~group-config-create~9`); the press is consumed, and acts on press rather than click for the arrow's reason (`dsn~collapse-on-press~1`).
While scoped, `rebuildRows` keeps only the category's entries (ungrouped tasks, other categories and other categories' done tasks leave the list) and force-expands it; the scoped category stays even when nothing in it matches. A query then narrows it like any find — the same `TaskSearch` matching on the search worker, the queue-message fallback included — and the find bar's match count counts its hits.
The chip's × (`find-scope-remove`, tooltip "Search all categories"), or Backspace at the start of the field, removes the scope; the typed query stays and searches every category again. Escape clears the query as before and keeps the scope.
Nothing about the scope appears or moves on hover. GemsFX's `TagsField` was considered for the chip, but it commits typed text into tags on Enter, while here Enter selects the first match and the typed text is always the query — so the chip is a plain `HBox` (`find-scope-chip`) inside the existing search box.
The rebuild is deferred with `Platform.runLater`, since the press that scopes happens inside a cell the rebuild replaces.

Tags: windows, linux

Covers:
- req~task-find~3

Needs: impl, utest

### Jump to category
`dsn~category-jump~1`

`Ctrl+J` (a capture-phase scene filter like `Ctrl+T`'s, `dsn~task-create-ui~15`) opens a popup over the task list: a field and the category names containing its text, case-insensitive.
The categories on the active virtual desktop (`desktop:`, or the fallback desktop, `dsn~fallback-desktop~1`) come first under "This desktop (<name>)", the rest under "Other desktops"; while the active desktop is unknown (non-Windows, unnamed) the names come without headings.
The popup reads the active desktop afresh in the background and regroups when the read arrives, since the window only keeps it current while a desktop watch runs.
Up/Down move over the names (headings are skipped), Enter or a click selects the category's header row, expanding it (`MainWindow.selectCategory`) and lifting a find scope to another category (`dsn~category-search~3`); Escape or a click outside closes it.
A category a list filter hides is not selected; the status bar says so.

Tags: windows, linux

Covers:
- req~task-find~3

Needs: impl, utest

### Current category highlight
`dsn~current-category-highlight~1`

The header of the category the selected row belongs to carries the selection's own colours — the `-color-cell-bg-selected` background and the accent left edge — through a `group-header-current` style class `TaskListCell` adds to the header cell (`MainWindow.isCurrentGroup`).
So the highlighted task always names the context it sits in, without scrolling up to look; a header that is both current and selected lands on the same colours, since the rule follows the `:selected` ones with the same style-class count.
`MainWindow.markCurrentGroup` records the selected task's group (the selected header's own name when a header is selected, `""` for an ungrouped task or a label bucket, which has no category header) and, only when it changed, calls `ListView.refresh()` — the rows are unchanged, so the cells are re-rendered rather than rebuilt.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### Task history navigation
`dsn~task-history-navigation~2`

`MainWindow` keeps a `List<String> history` of visited task ids with an `historyIndex` cursor, the shape a browser's back stack has.
The task-list selection listener calls `recordHistory(id)` for every loaded task it sees: an id equal to the one at the cursor is ignored — a row rebuild re-selects the same task, and the step `navigateHistory` itself took lands on the cursor it just moved, so no separate "currently navigating" flag is needed — any other id truncates the forward branch and appends, capped at `HISTORY_LIMIT` (50) entries and not persisted across restarts.
`navigateHistory(±1)` first drops every entry whose task no longer exists in `entries` (deleted or renamed since the visit), adjusting the cursor for each removal below it, then moves the cursor one step and hands the id to `selectTask` — which already reveals a row a collapsed group or a find query hides.
Back is therefore never a no-op that looks like a dead button; `updateHistoryButtons` disables each arrow at its end of the trail.
A visit replaced within `TRANSIENT_VISIT_NANOS` (1 s) of being appended is no visit: the next task takes its entry instead of being appended after it.
A desktop switch re-selects that desktop's last task (`dsn~desktop-last-task-selection~4`) and the browser window it activates reports its tab a moment later (`dsn~browser-tab-selects-task~5`); Back then landed on the task that had only flashed, not on the one worked on before (field report 2026-09-13).
Only an append stamps the time and `navigateHistory` clears it, so the replaced entry is always the newest one and a step taken with the arrows is never overwritten.

The two icon buttons (`ARROW_LEFT`, `ARROW_RIGHT`) sit at the **left** end of the list toolbar (`dsn~task-list-toolbar~3`), before the add menu, where a browser puts them.
`Alt+Left` / `Alt+Right` are registered as a capture-phase scene filter next to the `Ctrl+F` one, for the same reason (`dsn~task-find~9`): as accelerators the terminal pane's key forwarding (`dsn~terminal-pane~14`) would send them on as an escape sequence first.
Neither chord is claimed by tmux, bash/readline or a Linux desktop by default, so the app taking them window-wide costs nothing elsewhere.

Tags: windows, linux

Covers:
- req~task-history-navigation~1

Needs: impl, utest

### Tag filter and chips
`dsn~task-tag-filter~2`

The task-list toolbar's filter menu (`dsn~task-list-toolbar~3`) carries a "Tags" submenu (`Menu`, `TAG_MULTIPLE`) built from the `settings.yaml` tag palette (`AppSettings.TagDef`): one `CheckMenuItem` per tag plus a "Clear filter" item; a non-empty selection counts towards the filter button's badge, and the submenu is disabled when no tag exists anywhere. Toggling an item updates an in-memory `activeTags` set (reset on launch — never persisted) and rebuilds the rows.
Each tag menu item — in the toolbar filter menu and in the row/group "Tags" submenus — shows the tag as its palette-colored chip (the shared `TagChips` rendering, identical to the row chips) instead of plain text, so every tag menu carries the same color cue as the list.
`MainWindow.rebuildRows` treats a non-empty `activeTags` like an active search: it force-expands all groups and the Done section, hides empty groups, and drops every entry not matching — a loaded task must satisfy `TaskTags.matchesAll(effectiveTags(task), activeTags)` (AND) in addition to any `TaskSearch` query; failed rows are hidden while a tag filter is active (a deliberately narrowed view).
`effectiveTags(task)` is the task's own tags plus its group's inherited tags (`GroupConfig.tags` from the group's `CONTEXTSWITCHER.md`), so filtering by a group tag shows the whole group.
Each task row renders its (effective) tags as small rounded colored chips on the row's second line (before the config subtitle, so the title keeps its own line and never competes with the fixed-width chips for space in a narrow pane; palette color, a muted gray for a color-less or unknown tag). The chips shown are `TaskTags.visible(effectiveTags(task), activeTags)` — all tags with no filter, only the selected ones while filtering. The group header shows the group's own tags as chips too.
A task is (un)tagged without editing YAML through a "Tags" submenu of `CheckMenuItem`s on the row context menu, written via `TaskFileParser.withTags` through the same fresh-from-disk rewrite path as the other row actions.
The pure filter/visibility logic is `TaskTags` (`dsn~task-tag-model~2`), unit-tested there; this item's UI wiring is not separately unit-tested (house convention).

Tags: windows, linux

Covers:
- req~task-tag-filter~1

Needs: impl

### Awaits-input filter
`dsn~awaits-input-filter~1`

The task-list toolbar's filter menu (`dsn~task-list-toolbar~3`) leads with an "Awaits input only" `CheckMenuItem`. Ticking it flips an in-memory `awaitsInputOnly` flag (reset off on launch — never persisted) and rebuilds the rows.
`MainWindow.rebuildRows` treats a set `awaitsInputOnly` like an active search or tag filter — a narrowed view: it force-expands all groups and the Done section, hides empty groups, and drops every entry that is not `awaitsInput`.
`awaitsInput(entry)` is true only for a `TaskEntry.Loaded` whose task is `ACTIVE` and whose `runningStatusFor(task)` (the live `@cs_status`, `dsn~task-running-indicator~7`) is `waiting`, `attention` or `limit`; so done, suspended, `working`, and status-less rows are all excluded, and the Done section vanishes while the filter is on.
The item's text carries the current awaiting count (`Awaits input only (N)`), refreshed from `rebuildRows` and, so it stays live while the filter is off, from `updateRunningStatuses` — which, while the filter is on, calls `rebuildRows` instead of a plain `taskList.refresh()` because a status change alters row membership, not just the dot color. The filter button badges the number of narrowing filters that are active.
The filter/count logic reuses the running-status map, so no new polling or state is introduced; this UI wiring is not separately unit-tested (house convention, as with the tag filter).

Tags: windows, linux

Covers:
- req~awaits-input-filter~1

Needs: impl

### Active-desktop filter
`dsn~active-desktop-filter~6`

A "Show active desktop only" `CheckMenuItem` sits in the task-list toolbar's filter menu (`dsn~task-list-toolbar~3`), below "Awaits input only" and "Show running tasks only" — the two filters that need no desktop poll come first, and the desktop filter carries its own sub-option under it. Ticking it flips an in-memory `activeDesktopOnly` flag (reset off on launch) and rebuilds the rows, and calls `Main` to start/stop watching the active desktop.
`Main` reads the active desktop's name via `WindowsVirtualDesktopFocus.current()`, through `powershell` as a base64 `-EncodedCommand`.
The active desktop's GUID comes from the shell itself first: `IVirtualDesktopManagerInternal::GetCurrentDesktop` + `IVirtualDesktop::GetID`, through the same pinned Windows 11 24H2 vtable the direct switch uses (`dsn~category-desktop-focus~4`) — the registry's `CurrentVirtualDesktop` is only Explorer's lazily written mirror and goes stale after programmatic switches (including our own direct COM switch), so trusting it first narrowed the list to the *wrong* desktop (field report 2026-07-29: on "JabRef", config `jabref`, no match — the mirror still held the previous desktop).
On a build whose interface moved, `QueryService` fails cleanly (mismatched IID, never a wrong dispatch) and the script falls back to that registry value.
Where Explorer keeps that mirror depends on the Windows version, so `Get-CurrentBytes` reads both places: `CurrentVirtualDesktop` next to `VirtualDesktopIDs` (Windows 11) first, then `…\Explorer\SessionInfo\{session}\VirtualDesktops\CurrentVirtualDesktop` (Windows 10, session number from the PowerShell process's own `$PID`).
Windows 10 has no value in the Windows 11 location *and* an older shell interface than the pinned 24H2 one, so reading only the first place left every Windows 10 read falling through to the desktop-0 assumption below — the filter permanently believed the user was on the first desktop whatever they switched to (field report 2026-07-29: Windows 10 22H2, desktop switches via WindowsVirtualDesktopHelper, menu stuck on "main").
A value missing in *both* places is still not a failure — Explorer writes it only on the first desktop switch of a session, so the script then assumes the first GUID in `VirtualDesktopIDs` (the same desktop-0 assumption the focus script's walk makes).
The GUID's label is read from `…\Desktops\{GUID}\Name`; matching against the categories' `desktop:` is case-insensitive.
`current()` distinguishes a read that reached the registry from one that did not (`ActiveDesktop(read, name)`): a successful read yields the name, or null on an unrenamed desktop; a mechanism failure (non-Windows, no `powershell`, timeout) yields `read = false` and `Main.pushDesktop` then pushes nothing, so a transient failure keeps the last known desktop instead of silently un-narrowing the filtered list.
On turn-on `Main` starts a **persistent watcher** (`WindowsVirtualDesktopFocus.watch`, one start attempt per app run): the same read in a long-running `powershell` process that compiles the COM shim once and then re-checks in-process every 250 ms, printing a `desktop:<name>` line (UTF-8, auto-flushed) whenever the name changes.
An externally triggered switch (e.g. a WindowsVirtualDesktopHelper hotkey) thus reaches the list within the check interval — instead of after up to the poll interval plus `powershell` start plus `Add-Type` compile, seconds during which the list silently still shows the *previous* desktop's tasks with no way to tell whether the switch was noticed (field report 2026-08-07).
The `Add-Type` sits before the loop because a second add of the same types throws — inside the loop it would silently demote every later tick to the registry read.
A watcher tick that cannot read at all prints nothing (the same keep-last-known contract); non-protocol stdout lines are ignored.
While the watcher runs, the `scheduleAtFixedRate` poll (`ACTIVE_DESKTOP_POLL_SECONDS`, dispatching **only while the filter is on**, off the action pool so the scheduler thread never blocks on `powershell`) skips its one-shot read; it is the fallback wherever the watcher cannot start or later dies — with no restart attempts, since a box without `powershell` would respawn-fail every tick.
Each successful read is pushed to `MainWindow.updateActiveDesktop`, which caches it and, when it changed and the filter is on, rebuilds so the list follows desktop switches; the check item's text shows the active desktop's name once known, and "(unknown)" while the filter is on but the desktop cannot be determined — a bare checked item would silently promise a narrowing that is not happening.
`MainWindow.rebuildRows` treats a set `activeDesktopOnly` **with a known active desktop** as a narrowed view (force-expand groups + Done, hide empty groups) and drops every entry whose group's `desktop:` — read straight from the `CONTEXTSWITCHER.md`, memoized per rebuild to at most one read per group — does not case-insensitively equal the active desktop; ungrouped tasks and no-`desktop:` groups have no match and fall out, and Done tasks are filtered the same way.
Unlike everywhere else, the filter does **not** resolve a missing `desktop:` through `groupDesktop`'s fallback (`dsn~fallback-desktop~1`): with the default `misc` fallback no category would name no desktop any more, leaving the sub-option below with nothing to show. Such a category is instead matched directly against the fallback name, so it does appear while the fallback desktop is the active one.
That last exclusion is what the **"Also show categories without a desktop"** `CheckMenuItem` under it relaxes: ticked, it flips an in-memory `noDesktopCategoriesToo` flag (reset off on launch) and the shared `MainWindow.onActiveDesktop` test — used by both the per-entry and the task-less-folder pass — keeps a group that names no desktop at all, the ungrouped block included (no `CONTEXTSWITCHER.md`, so no `desktop:`, and a task in no category is on no desktop either).
Categories that belong nowhere in particular — tooling, scratch, anything not yet assigned — are otherwise reachable on no desktop at all, which reads as "the filter ate my tasks".
The item is disabled while the desktop filter is off (on its own it changes nothing) and does not count towards the filter button's badge — it widens the view rather than narrowing it. When the active desktop is unknown the flag narrows nothing (a harmless no-op — e.g. on Linux).
A category whose `CONTEXTSWITCHER.md` does not load is kept regardless, so its header warning stays visible (`dsn~group-config-parse-error~1`).
The same test applies to the **task-less folders** added as groups after that per-entry pass (`dsn~task-create-ui~15`, so a fresh category is not invisible): they are exempt from the content filters because a category without tasks has nothing to filter out, but this filter narrows by a property of the *category*, which an empty one has just as much (field report 2026-07-29: `cloudref`, `desktop: jabref` and no task file yet, sat in the list while the ContextSwitcher desktop was active).
`FilterAndCombineUiTest` also pins the sub-option: with the desktop filter on, a no-`desktop:` category is hidden, and ticking "Also show categories without a desktop" brings it back without bringing back the categories of *other* desktops.
A find query does not suspend this filter, unlike the content filters (`dsn~task-find~9`); the hits it hides are counted and reported instead (`dsn~search-hits-off-desktop~1`).
No row of another desktop can therefore reach the list while the filter is on, and the earlier `unfilterForeignDesktop` — which unticked the filter when such a row was selected out of a suspended-filter search — is gone with the state it repaired.
The filter composes with the other toolbar filters as an AND — with "Show running tasks only" also on, the list is the running tasks *of the active desktop* (`FilterAndCombineUiTest`); `current()`'s parsing, the script's fallback, and the watcher's compile-once shape and line protocol are unit-tested.

Tags: windows

Covers:
- req~active-desktop-filter~2

Needs: impl, utest

### Search hits outside the active desktop
`dsn~search-hits-off-desktop~1`

The active-desktop filter (`dsn~active-desktop-filter~6`) keeps narrowing while a find query runs, so a hit in a category of another desktop never appears in the list — the list stays on the desktop the user is working on instead of quietly following the query elsewhere (field report 2026-09-10: a search jumped the view to another desktop's category, and the implicit context switch was worse than the missing hit).
So the hit is not lost, `MainWindow.rebuildRows` counts the entries the query matched but the desktop filter dropped into `hitsOffDesktop`, and the find bar's match label appends "· N on other desktops" to its count (blank when the count is 0 — no filter, no query, or every hit is here).
When *every* hit is elsewhere the list is empty, and its placeholder says "No matches on this desktop — N on other desktops." (`dsn~desktop-filter-empty-state~2`) instead of the generic filter line.
Reaching those hits stays a manual act: unticking the desktop filter, or switching to the desktop in question. Deliberately no auto-widening and no offered shortcut yet — the point of this design is to find out how often the count is non-zero in daily use before adding machinery for it.
The label carries the `find-match-count` style class so the UI test can read it.

Tags: windows

Covers:
- req~active-desktop-filter~2
- req~task-find~3

Needs: impl, utest

### Filtered-out empty state
`dsn~desktop-filter-empty-state~2`

When a rebuild produces no rows while a narrowing filter is active, `MainWindow.rebuildRows` swaps the list's placeholder: the stock "No tasks yet — copy TEMPLATE.md…" text would be a lie, since tasks exist and the filters hide them all (field report 2026-08-23: desktop filter on, a desktop without categories, the app claiming there are no tasks).
With the desktop filter as the *sole* narrowing (a known active desktop, no search/tag/awaits-input/running filter), the placeholder names the desktop ("No categories on desktop "X".") and offers an `Add category for desktop "X"…` `Hyperlink`, which runs the normal `Add category…` dialog (`dsn~category-create-ui~2`) and pre-fills the created config's `desktop:` line — real (uncommented) via a `desktop` parameter on `TaskFileParser.groupConfigSkeleton` instead of the `# desktop: <group>` example — so the new category appears on the desktop in view without a manual edit; the dialog header names the pre-set desktop.
A search whose hits all sit on other desktops (`dsn~search-hits-off-desktop~1`) gets its own line — "No matches on this desktop — N on other desktops." — rather than a bare "nothing matches" that reads as "the task is gone".
Any other filter combination (search, tags, awaits-input, running — with or without the desktop filter) gets a plain "No tasks match the current filters." line (field report 2026-08-24: desktop filter on plus a non-matching "Find task" search offered to create a category for a desktop that had plenty).
An empty task directory keeps the stock placeholder.
The skeleton's `desktop` pre-fill is unit-tested; the placeholder swap itself is UI wiring (house convention, as with the other toolbar filters).

Tags: windows

Covers:
- req~active-desktop-filter~2

Needs: impl, utest

### Last selected task per desktop
`dsn~desktop-last-task-selection~4`

`MainWindow` maps desktop name → last selected task id in a preferences child node (`lastTaskByDesktop`, so the memory outlives a restart): the task list's selection listener records every selected `TaskEntry.Loaded` under the currently active desktop (nothing is recorded while the active desktop is unknown — non-Windows, unnamed, or not yet read).
`updateActiveDesktop` — only while the filter is on — reads the remembered id *before* its rebuild, whose own selection changes would otherwise be recorded under the desktop just switched to and overwrite it, and re-selects the task afterwards via `selectTask`, which also scrolls the row into view; the selection change then drives the terminal, editor, and queue panes as any click would.
Only an id the narrowed rows actually show is handed to `selectTask` — a deleted or otherwise filtered-out id would sit in `pendingSelectionId` forever and block the selection preservation of every later rebuild — and a desktop with no usable remembered id falls back to the first task of the narrowed list.


The re-selection is skipped while the keyboard sits in one of the queue pane's text boxes (`MainWindow.queueFieldFocused`, the guard `selectCreatedTask` has carried since `dsn~task-from-pr~6`).
A selection change swaps the queue pane to the new task, and `QueuePane.showTask` **commits the half-typed message as the previous task's draft** before clearing the box — so a desktop switch arriving mid-sentence reads as "I typed something in the queue field, focus was stolen to the task list - and my typed letters lost" (field report 2026-09-12; the letters were not lost, they were filed under the task the user had left).
The list still follows the desktop — only the selection waits, and the next switch or a click applies it.
A desktop switch is an *ambient* event, unlike the click or keypress that every other selection comes from, so it is the one that must yield to someone typing.
Tags: windows

Covers:
- req~desktop-last-task-selection~4

Needs: impl, utest

### Running-tasks filter
`dsn~running-tasks-filter~1`

A "Show running tasks only" `CheckMenuItem` sits in the task-list toolbar's filter menu (`dsn~task-list-toolbar~3`), below "Show active desktop only".
Ticking it flips an in-memory `runningTasksOnly` flag (reset off on launch — never persisted) and rebuilds the rows.
`MainWindow.rebuildRows` treats a set `runningTasksOnly` like the other toolbar filters — a narrowed view: it force-expands all groups and the Done section and hides empty groups — and drops every `TaskEntry.Loaded` whose status is not `ACTIVE`, so paused and done tasks vanish and the Done section disappears with them.
Parse-failure rows (`TaskEntry.Failed`) stay visible: they carry no status and a broken file must be seen.
The filter reuses the status already parsed from the task file, so no new state or polling is introduced; this UI wiring is not separately unit-tested (house convention, as with the other toolbar filters).

Tags: windows, linux

Covers:
- req~running-tasks-filter~1

Needs: impl

### Filter persistence
`dsn~filter-persistence~1`

The toolbar filters are kept in the same `Preferences` node as the sort mode and the label grouping (`MainWindow.PREFERENCES`, `dsn~task-sort-modes~2`): `filterAwaitsInput`, `filterRunningTasks`, `filterActiveDesktop`, `filterNoDesktopCategories` (booleans) and `filterTags` (the selected tag names, newline-joined — a tag name may carry spaces, never a newline).
Each `CheckMenuItem`'s listener writes its key as it flips, so the store follows the UI without a save step; the flags are read back into their fields at construction, so the very first `rebuildRows` is already narrowed.
This deliberately overrides the "reset off on launch — never persisted" sentences of `dsn~task-tag-filter~2`, `dsn~awaits-input-filter~1`, `dsn~active-desktop-filter~6` and `dsn~running-tasks-filter~1` without bumping them (like `dsn~tag-auto-color~2`).
Each item is `setSelected(<restored flag>)` **after** its listener is installed, so the restore runs exactly the code path a user tick runs — the badge count, the rebuild, and, for the desktop filter, `Main.watchActiveDesktop(true)`, which starts the desktop watch a restored filter needs to narrow at all (`dsn~active-desktop-filter~6`). Until that first read arrives the desktop is unknown and the filter narrows nothing, as it does on any other unknown desktop.
The tag selection is restored in `populateTagFilterMenu` rather than in the field initializer, because the selectable names only become known as the palette and the task files load: each populate re-applies the stored names intersected (case-insensitively) with the selectable ones, so a tag that is not offered yet at the first build still comes back, while a tag that no longer exists anywhere drops out (`dsn~tag-selection-union~2`) — the store stays the newer truth because every toggle writes it.
The UI tests clear these keys after the filter classes (`UiTestSupport.clearStoredFilters`, hence the package-private key constants): a filter one test leaves on would otherwise narrow the next test's list, since the `uiTest` prefs store outlives the app restart.
`FilterPersistenceUiTest` pins the round trip across a real app restart (the TestFX extension relaunches the app per test method, as in `dsn~claude-mode-select~3`).

Tags: windows, linux

Covers:
- req~filter-persistence~1

Needs: impl, utest

### Tag auto color
`dsn~tag-auto-color~2`

A tag without a configured palette color renders in a stable, name-derived color instead of a fixed gray: `TaskTags.autoColor(name)` picks from a fixed set of distinct hues by the lowercased name's hash, so the same tag always gets the same color (this deliberately overrides the muted-gray fallbacks described in `dsn~task-tag-filter~2` and `dsn~task-tag-model~2` without bumping them).
`TagChips` applies it whenever the palette yields no (parseable) color, so rows, group headers, and all tag menus agree.
Creating a category neither adds a palette entry nor tags the category with its own name (`~1`→`~2`): a category is not a tag.

Tags: windows, linux

Covers:
- req~task-tags~3

Needs: impl, utest

### Tag selection union
`dsn~tag-selection-union~2`

Every tag picker — the toolbar filter menu and the row/group "Tags" toggle submenus — offers the union of the configured palette and the tags already in use, not just the palette (this deliberately extends the palette-only menus described in `dsn~task-tag-filter~2` without bumping it, like `dsn~tag-auto-color~2`).
The pure list logic is `TaskTags.selectable(configured, inUse)`: the union de-duplicated case-insensitively with the configured spelling winning, the whole list sorted alphabetically (case-insensitive) — one predictable order, no configured-first block (changed in `~2`).
`MainWindow.selectableTagNames` feeds it the palette names plus every loaded task's `effectiveTags` (own and group-inherited), and replaces the palette as the source for both the filter menu (`populateTagFilterMenu`) and the submenu supplier handed to `TaskListCell`; a tag without a palette color falls back to its auto color (`dsn~tag-auto-color~2`).
Because the offered tags now follow the task files, a task-list change repopulates the filter menu too (the `entries` listener), and selections for tags that no longer exist anywhere — configured or in use — are dropped there (case-insensitively), no longer only on a settings edit; the filter button is disabled with a hint only when no tag exists at all.

Tags: windows, linux

Covers:
- req~task-tags~3

Needs: impl, utest

### Recently used tags lead the tag filter
`dsn~tag-filter-recent~1`

The toolbar "Tags" submenu (`dsn~task-tag-filter~2`) lists the up to five tags most recently ticked first, newest on top, then a separator, then the remaining selectable tags in their usual order (`dsn~tag-selection-union~2`), so a long palette does not bury the tags actually filtered by.
Ticking a tag moves it to the front of that list (`TaskTags.withRecent`, case-insensitive, capped at `TaskTags.RECENT_LIMIT`); unticking and "Clear filter" leave it unchanged.
The list is kept in the `filterRecentTags` preference (newline-joined, like `filterTags` in `dsn~filter-persistence~1`), and a remembered tag that is no longer selectable is simply not shown.

Tags: windows, linux

Covers:
- req~task-tag-filter~1

Needs: impl, utest

### Message queue below the editor
`dsn~message-queue-ui~26`

`QueuePane` (the **Queue** pane, docked below Notes by default, `dsn~shell-layout~2`) shows the selected task's queued messages as a stack of wrapped `TextArea` cards, ordered top-down by how urgently each part wants the eye (changed in `~15`): the last sent message (`dsn~last-sent-message~5`), the status line, the queued messages oldest-first — so the **top** card is the next to send (changed in `~18`; the newest message ends up next to the add box that produced it) — and the always-empty **add** box directly under the last card — inside the scrolling stack, not pinned to the pane's bottom edge, where a short queue would leave it stranded at the far end of an empty pane (committed as a new message when focus leaves it non-blank, or via `Ctrl+Enter` which keeps the caret in the emptied add box).
A committed add-box message is trimmed of surrounding whitespace (`String.strip()`) before it enters the queue, and any http(s) URL it mentions is also added to the task's `browser.urls` (`dsn~task-url-collect~1`).
The add box also commits on three plain Enters in a row at the end of the box — a message followed by two empty lines: `KEY_PRESSED` fires before the third Enter's newline lands, so two newlines sitting immediately in front of a caret **at the box's end** mark the submit and are deleted before committing (the end check, added in `~12`, keeps a caret moved up behind an existing blank line from submitting after only two Enters), while three Enters into an all-blank box do nothing.
In any box (add box or card) `Shift+Enter` inserts a newline (chat-app muscle memory) — a plain `TextArea` does not bind it, so a capture-phase `KEY_PRESSED` filter does `replaceSelection("\n")` and consumes the event, which also keeps it from ever committing.
The boxes' chords (Shift+Enter newline, triple Ctrl+Enter act, triple-Enter commit) are installed by the static `QueuePane.installComposeKeys`, shared with the Add-task dialog's description field (`dsn~task-create-ui~15`) per `req~compose-key-conventions~5` — one implementation, so the compose keys cannot drift apart.
Card edit boxes use it with a null `commit` (changed in `~20`): the triple-Enter half is left out, since blank lines are content while editing, and their chord saves the edit and fires that card's own send button — with the add box's fallback message when the task has no tmux window to send to.
Ctrl+Enter pressed **three times in a row** queues the message and sends it (added in `~16`, and it takes all three presses since `~18` — the first press used to queue on its own, which put a card in the queue for a chord that was only on its way): the add box passes `installComposeKeys` a chord action that fires its own send button, so the keyboard reaches exactly the send the mouse does — including its disabled state on a task without `remote`+`tmux`, where the chord does nothing.
Each press *before the last* greens the box a shade stronger (`queue-chord-1`/`-2` in `main.css`, painted on the `.content` like `.queue-last-sent` and built from `-color-success-subtle`/`-muted`, so Nord and Everforest each land on their own green); the firing press clears the shade before it acts (changed in `~23`, dropping the ~0.9 s "it went out" flash), because that press empties the add box, and a green box left standing colours the box the *next* message is typed into rather than marking the message that went out.
Any other key or a click into the box drops the run, so a Ctrl+Enter minutes later never completes a chord started before it.
The run counts key presses, not the box's text — a modifier's own key press is ignored (Ctrl goes down before every Ctrl+Enter), and the commit the last press triggers rebuilds the add row, taking the box out of the scene and back in, so the run deliberately does not reset on focus either.
When the send button is disabled the chord says so in the status line ("Queued — this task has no tmux window to send to.", added in `~19`; "Saved — …" from a card edit box): the message is safe in the queue either way, but a green flash over a card that quietly stayed behind reads as a chord that does nothing but add a box.
All rows share one skeleton (handle/send column, text, delete) with inapplicable elements kept invisible in the layout, so every text box starts at the same x.
Every box (add box and cards alike) carries a resize grip along its bottom edge — a thin `Region` with an `S_RESIZE` cursor whose drag sets the box's `prefHeight` (screen-y deltas, since the grip moves with the drag; floored at one row) — so a long message gets the room a browser's `<textarea>` gives it.
The height is per box and not persisted: it belongs to the message being written, and a task switch starts from the default row counts again (a rebuild within the task keeps the areas, `~26`).
Each card auto-saves edits on focus loss (editor-lane convention) or on its own triple `Ctrl+Enter` chord (which then sends it, changed in `~20`), shows a ✕ delete button while hovered, and is reordered by dragging its ≡ handle onto another card (string-payload drag'n'drop like the task-row move).
The handle carries the card's **position in the delayed send order** as a negative-circled number (`CircledCount`, the glyphs of `dsn~message-queue-count-badge~1`), and only an **armed** card gets one (changed in `~22`): `➊` is the armed card that goes out first, and the tooltip spells the number out — a stack of look-alike cards otherwise leaves the order to be guessed, which matters most for the delayed send (`dsn~message-queue-delayed-send~4`), where nothing is pasted right away to confirm what went.
An unarmed card keeps a bare `≡` handle: nothing sends it on its own, so a number on it promised a turn it would never get.
Every card carries a send button on its left (a mirrored ➤ pointing left, towards the terminal the message goes to) — disabled with a tooltip when the task has no `remote`+`tmux` — which delivers exactly that card's message via `dsn~message-queue-send~5` on the background executor, so any message can jump the queue; on success it leaves the queue through the same `QueuePane.dropFromQueue` as the delayed send (changed in `~21`) — re-located by content, so an in-flight edit or reorder cannot remove the wrong card, and written straight to the task's queue file when the pane has meanwhile switched to another task, which previously left a delivered message queued forever.
A failure keeps it and shows the error in the pane's status line.
While the send is in flight that card's text box is read-only alongside its disabled send button (added in `~24`): the message is already on its way, and an edit landing during the paste would be lost silently — the chat would have the old text and the queue file the new one.
A failure makes the box editable again, so the message can be fixed and retried; a success removes the card anyway.
`Ctrl+V` with an image on the clipboard stores it as a PNG under `<configDir>/attachments/` (FX `PixelReader` → `BufferedImage` → `ImageIO`, no `javafx.swing` bridge) and inserts its `[image: <local path>]` marker at the caret; plain text pastes fall through to the `TextArea`.
An image whose alpha is 0 on every pixel — how the Windows clipboard hands over 32-bit screenshots — is stored opaque (`Attachments.opaqueIfFullyTransparent`); stored as-is it showed blank in every viewer and to Claude (field report 2026-09-15).
Files dragged from the OS onto any text box (add box or card) are copied under `<configDir>/attachments/` — timestamp-prefixed, so equal names cannot collide locally or in the flat remote drop directory, and sanitized (everything outside `[A-Za-z0-9._-]` becomes `_`), because the remote path lands verbatim in the chat message text, where a space would split the path — and inserted as `[file: <local path>]` markers at the caret; the handlers are event **filters** accepting only file drags, so the card-reorder drag (string payload) and the `TextArea`'s own text-drag handling are unaffected, and directories are skipped.
Re-showing the task already shown (a background task-list rebuild re-applying the selection, the preview refresh after a switch/suspend) never commits or rebuilds the boxes — it would turn a half-typed add box into a queued message and steal the keyboard focus mid-typing.
Every other rebuild of the cards (a sent or dropped message, a review-comment sync, a reorder) **reuses each message's `TextArea`** (`~25`→`~26`): a card area used to be created anew per rebuild, so the one being typed in left the scene for good and the next pulse cleared the focus — the next Tab then started from the window's first control, the toolbar (field report 2026-09-16: typing in a queued message, "the cursor got dragged away … I pressed Tab and then that no-browser thing got focussed"); the add box never had that problem, being one node taken out and put back within the same pulse, and a card's area is now the same — `card` takes the area last shown for its message (matched by the message it was created for, or by its current text) and only builds one for a message without, its compose chord reaching the rebuild's send button through a node property — so focus, caret, selection, height and a half-typed edit all survive a background rebuild.
`showTask` then only takes over the fresh task data (the pane is the sole writer of the queue files, so its in-memory queue stays authoritative), refreshes the Qodo-sync button, and rebuilds the cards — restoring the add box's focus and caret — only when the fresh data changed the sendability (`remote`+`tmux` appeared or vanished, e.g. a resume or suspend).
A task whose **file was renamed** is the same task too, under a new id (added in `~25`): `MainWindow.taskRenamed` tells the pane (`QueuePane.taskRenamed`, wired by `Main` as `setOnTaskRenamed`) *before* it re-selects the row, and the next `showTask` recognises the new id and takes that same path — nothing committed, nothing rebuilt, the caret where it was.
The in-memory queue is authoritative and `save()` writes it under the id `this.task` now carries, which is the new one, and `QueueFile.rename` has already moved the file there; the pending id is consumed by whichever `showTask` comes next, so it can never make a later genuine switch look like the same task.
Without it, an adopted published title (`dsn~claude-title-sync~3`) — which renames a task's file seconds after it was created, while the user is still typing the first message for it — committed the half-typed add box under the **old** id and loaded the new id's empty queue: the box went empty with no card to show for it, and the message sat in a queue file no task points at (field report 2026-09-13, "i just typed and then the box was empty, but no task switched").
Pure logic lives in `QueueFile`/`Attachments`/`QueueSendCommands` (unit-tested); the pane's UI wiring is not separately unit-tested (house convention).

Tags: windows, linux

Covers:
- req~message-queue~2
- req~compose-key-conventions~5

Needs: impl, utest

### Message queue storage
`dsn~message-queue-store~2`

A task's queue is a YAML string list in `<tasksDir>/.queues/<task id, `/`→`__`>.yaml` (`QueueFile`), written on every mutation and loaded on task selection; index 0 is the next message to send.
YAML because messages are multi-line Markdown that may contain any ad-hoc delimiter, and SnakeYAML is already a dependency.
The `.queues/` directory is dot-prefixed so the repository scanner skips it like `.git` (`TaskRepository.isProjectGroup` ignores every dot-directory): it is not scanned, watched, or shown as a category, so a queue file is never parsed as a broken task — yet, living **inside** the task directory, it rides the task-dir git backup (`dsn~task-git-backup~5`), so queued messages sync across machines with the tasks.
The close-time git sync commits `.queues/` with everything else (`git add -A`), so a queue-only change needs no trigger of its own.
An empty queue deletes the file; a missing or corrupt file loads as an empty queue rather than failing the pane.
On first launch after the move, queue state at the old `<configDir>/queues/` and `<configDir>/qodo/` locations is migrated into `.queues/` and `.queues/qodo/` once — only when the new location does not yet exist (last-writer-wins if two machines upgrade independently).

Tags: windows, linux

Covers:
- req~message-queue~2

Needs: impl, utest

### Queued-message count badge on the task row
`dsn~message-queue-count-badge~1`

Each task row shows, right after its title, a small negative-circled number of how many messages are queued for it (`dsn~message-queue-store~2`): `➊`…`➓` for 1–10, `⓫`…`⓴` for 11–20, capped at 20 (`CircledCount`, a pure Unicode-glyph mapping), with the exact count in the badge's tooltip.
A task with an empty queue shows nothing, so the badge only ever marks a real backlog.
The title comes first and ellipsizes ahead of the fixed-width badge — the name matters more than the count.
`TaskListCell` reads the count fresh per render via a `MainWindow` callback (`QueueFile.load(…).size()` — the queue files are tiny and local); the status poller refreshes the list only when a remote status changes, so `QueuePane` calls back into `MainWindow.refreshTaskRows` on every queue write, and the badge then tracks enqueues and sends even while the task sits idle.

Tags: windows, linux

Covers:
- req~message-queue~2

Needs: impl, utest

### Last sent message below the queue
`dsn~last-sent-message~5`

A successful queued send records the delivered text as the task's **last sent message** (`QueueFile.saveSent` — keyed by the task the send was for, so a selection change while the send is in flight still records the right task) and shows it in a read-only wrapped `TextArea` at the queue pane's **top**, above the status line and the queue (changed in `~3` — see `dsn~message-queue-ui~26`): a muted "Last sent" caption, a subtly different background (`.queue-last-sent .content` in `main.css` — AtlantaFX paints a text area's background on its `.content` child, so an inline style would not take), collapsed while the task has no recorded send.
The box scrolls internally when the message exceeds its three rows.
The earlier sent messages get an identical read-only box each, stacked **above** the last one inside a `ScrollPane` whose `prefViewportHeight` is bound to the last-sent box's height: the pane shows exactly the last send as before, and scrolling up inside it walks back through the history (oldest at the top).
The caption says "Last sent (scroll up for older)" while there is history, so the hidden messages are discoverable; the older boxes carry the attachment-marker preview and the same Resend/Copy menu (changed in `~5`; before, only the last box had it).
`QueuePane.showSent` refills the whole stack from disk after every send (the files are tiny and local, like the queue) and parks the viewport at the bottom via `Platform.runLater`.
Storage is a plain-text sidecar `<tasksDir>/.queues/<task id, `/`→`__`>.sent.txt` beside the queue file (one message — no list format needed), plus `<task id>.sent-history.yaml`, the same YAML string list the queue uses, holding every sent message oldest-first and capped at the newest 50 (a recall aid, not an archive, and it rides the git backup); `QueueFile.saveSent(queuesDir, taskId, text)` writes both: inside `.queues/` it is invisible to the repository scanner and rides the task-dir git backup (`dsn~message-queue-store~2`), so it survives restarts and syncs across machines; the recording pings the same `onQueueChanged` hook, the sole backup trigger under the watcher-ignored `.queues/`.
`QueueFile.rename` moves both sidecars together with the queue on a task rename or move.
A live task's initial launch prompt (`dsn~task-create-live~8`) and a PR task's context prompt (`dsn~task-from-pr~6`) are recorded the same way when the task file is written.
It is the task file's own description, but the paste into the just-started session is the one send that can fail silently — Claude's TUI may not be up yet — and the recorded copy carries the attachment markers and the prompt's framing, so the queue pane offers the whole text to copy and re-send instead of retyping it.
Every sent box's right-click menu carries a **Resend** entry (and a **Copy**, since the custom menu replaces the TextArea's default one): Resend delivers that box's recorded text again with the currently picked mode, touching no queue entry.

Tags: windows, linux

Covers:
- req~last-sent-message~2

Needs: impl, utest

### Quick message buttons
`dsn~quick-message-buttons~7`

Directly below the last-sent box sits a row of small buttons, one per **quick message**: a click copies that text into the queue's add box — appended after a blank line when the box already holds something — scrolls the box into view, focuses it and puts the caret at its end; nothing is sent.
The add box sits under the last queued card, so with a long queue it is below the fold — a text landing out of sight read as a click that did nothing.
It exists for the handful of replies one retypes all day — "Go" above all.
Sending at once was the first version; it left no room to add a URL or more context before the message went out, so the text now lands where one keeps typing and is queued or sent by the add box's usual chords and buttons.
A button's label is the message's first line, ellipsized past 24 characters, with the full text in its tooltip; its right-click menu carries a **Remove**.
When the buttons do not all fit, `‹` and `›` either side of them page through the row, one width at a time, starting at a whole button; each is disabled at its end, both while everything fits.
A long list otherwise ran off the pane's edge, taking the `…` with it.
The trailing `…` button opens the **Quick messages** editor: the messages as a list on the left, the selected one's full text in a `TextArea` on the right, **Add** and **Remove** below the list, **Save** and **Cancel** in the dialog.
Dragging a list row onto another moves it there (below the last row: to the end), and the button row follows the list's order — so the reply one reaches for most can sit first.
A one-field prompt could only append, so a message one wanted to reword or drop had to be retyped or hunted down in the button's context menu; the list edits the whole set in one place, multi-line messages included.
**Save** replaces the row with what the list holds, dropping the entries left empty.
The row is disabled while no task is shown; a task without a tmux window still gets the text, since queueing needs no window.
The `Add task…` dialog (`dsn~task-create-ui~15`) shows the same row above its description field (`QueuePane.quickRow`), copying into that field instead: a task description starts from the same recurring phrases a message does.
Editing or removing a message from the dialog's row updates the queue pane's row as well — it is one list.

The list is shared by all tasks — a quick reply is not task-specific — and stored as a YAML string list in `<tasksDir>/.queues/quick-messages.yaml` (`QueueFile.quickFile`, the same `load`/`save` the queue uses), so it rides the task-dir git backup and syncs across machines (`dsn~message-queue-store~2`).
An empty list means "no file yet" — `QueueFile.save` deletes the file with the last entry — so a fresh install, and a row the user emptied, both come up offering `Go`.

Tags: windows, linux

Covers:
- req~message-queue~2

Needs: impl, utest

### Marking a queued message read
`dsn~message-queue-read-mark~1`

Every queued message carries a tick box **below its 🗑 delete button** — same right-hand column, both meaning "I am done with this one", the tick reversibly.
Ticking it moves the message out of the stack into a collapsed `TitledPane` **"Read (N)"** at the bottom of the pane, where it stays a full card: still editable, still sendable, still deletable, and unticking brings it back up.
This is what makes a review-comment queue (`dsn~qodo-agent-prompt-queue~11`) workable — a review that raises twenty comments would otherwise bury the messages one actually wants to send under the ones already dealt with.
The section's expanded state is kept across rebuilds and opens automatically when a message is ticked, so the box does not fold shut under the user's hands and one sees where the message went.
The marks are a YAML string list beside the queue (`QueueFile.readFile`, `<tasksDir>/.queues/<task id, `/`→`__`>.read.yaml`, so it syncs and follows a task rename like the queue and last-sent files, `dsn~message-queue-store~2`), pruned on every queue write to the messages still queued — a message that was sent, deleted, or edited into a different text drops its mark, and an empty set deletes the file.

Tags: windows, linux

Covers:
- req~message-queue~2

Needs: impl, utest

### Delayed send when the chat is done working
`dsn~message-queue-delayed-send~4`

Below each card's ➤ send button (and below the add box's) sits a **delayed next** toggle — a clock icon: instead of pasting the message now, into a chat that is mid-run where it queues behind whatever Claude is doing and is easily missed, it arms the message, and the status poll (`dsn~task-running-indicator~7`) delivers it on the first tick that reports the task's window `waiting` or `done`.
The add box's toggle commits what is typed first, like its send button, and keeps the caret in the emptied box.
Untick to disarm.
Several messages can be armed at once: they form a **delayed queue** that goes out one message per idle turn, in the order the armed cards' send-order numbers show (`➊` first) rather than the order the clocks were ticked, so a chat that falls idle never gets a burst.
Ticking a clock moves that card **directly below the last armed card** (changed in `~3`), so it takes the next number and the armed cards form one contiguous block in the order they were ticked; when nothing is armed yet the card stays where it is.
Dragging an armed card afterwards reorders the block and renumbers it, since the numbers are the cards' positions.
A delivered message blocks its task until the poll reports the window busy again — the tick right after a paste can still carry the pre-paste `waiting`, and without that block the whole queue would go out at once; a delivery that *fails* lifts the block instead, since nothing was pasted and the chat will not become busy on its own.
The poll also checks a blocked task whose delayed queue is already empty, so its block lifts when the chat goes busy — a message armed later on the idle chat otherwise never went out (field report 2026-09-14).
`QueuePane.save` is the single place that keeps an armed queue honest: it drops entries whose message left the queue (sent by hand, deleted, edited into a different text) and re-sorts the rest into card order after a reorder.
A `working` or `limit` window keeps waiting — the poll's own stuck-status safety net (a silent `working` window read as `waiting`) is what keeps a missed `Stop` hook from arming forever.
`QueuePane` holds the armed messages per task id, so they survive switching to another task — the point of the button is to arm the follow-up and walk away — and the send then runs while a different task is shown, looking the task up fresh by id and dropping the delivered message from its own queue file directly (the pane holds no other task's queue in memory).
The armed queues also survive an app restart (changed in `~4`): every change is written to `armed-messages.yaml` in the local config directory, a YAML map of task id to armed texts, read back when the pane is created.
It is deliberately **not** beside the queue files in the synced `.queues/`: two machines sharing the tasks directory would both deliver the same message.
A task rename moves its armed queue to the new id; an armed task that is not (or no longer) in the repository keeps its messages armed but sends nothing.
An armed message that is edited stays armed with its new text; deleting it disarms; a failed delivery leaves it queued and disarmed, with the error in the pane's status line, rather than retrying against a chat that may be broken — the rest of that task's delayed queue tries again on the next idle tick.

Tags: windows, linux

Covers:
- req~message-queue~2

Needs: impl, utest

### Sending to a suspended task resumes it first
`dsn~message-queue-resume-send~2`

A suspended task's `tmux:` section names only the session (its `window:` line was dropped, `dsn~task-suspend~6`), so a plain send would paste into whatever window that session currently shows.
Instead, the send button and the delayed-send toggle on a suspended task **arm** the message (`dsn~message-queue-delayed-send~4`) and resume the task through `MainWindow.resumeTask` — the regular resume: status back to `active`, switch, resurrect with `claude --resume`.
The armed message goes out on the first poll tick that reports the recreated window idle; for that, `QueuePane.sendDelayed` re-reads the armed task by id from the repository (the resurrect wrote the new window id into the file; the armed `Task` still has none) and skips a task without a window id.
A freshly resumed session publishes `waiting` from its `SessionStart` hook (README, remote setup) — before that hook existed, a resumed window reported no status until its first `Stop`, and the armed message would have waited for a turn that never came.
Clicking into a message field of a suspended task — the add box or any queued message's text area — resumes it on the spot, before anything is typed: writing a message is the decision to work on that task again, and starting the resurrect then rather than at the send hides the half minute the window needs to come up.
The focus path goes through the same `MainWindow.resumeTask`, which re-reads the task file, so clicking between fields of an already-resumed task writes its status once more and resurrects nothing.

Tags: windows, linux

Covers:
- req~message-queue~2
- req~task-suspend-resume~2

Needs: impl

### Review comments queued from PR reviews
`dsn~qodo-agent-prompt-queue~11`

A task's queue is also fed from its PR's open review comments — Qodo Merge's code suggestions **and** what human reviewers wrote — automatically in the background and on demand via a **Review comments sync** button.
Qodo Merge (`qodo-free-for-open-source-projects[bot]`) posts each code suggestion as an inline PR review comment whose body carries a `<details><summary><strong>Agent Prompt</strong></summary>` section wrapping a fenced, ready-to-run prompt.
A human reviewer just writes prose, so their comment becomes `Review comment by @<login> on <path>:<line> (<comment URL>):` followed by the comment text (`QodoReviewLookup.reviewMessage`) — qodo's agent prompts carry their own context, a bare "use a switch here" reaches the chat with no anchor at all, and the permalink lets the agent re-read the thread's current state instead of trusting the snapshot in the message.
`QodoReviewLookup` fetches the PR's **review threads** (`gh api graphql --paginate` with the `reviewThreads` query, parsed with Jackson — no jq expression, and the query passes owner/repo/number as GraphQL *variables* so the argv carries no double quote, the Windows trap of `dsn~pr-state-indicator~3`), and returns each comment's URL and its message(s) together with whether it is still **active**: its thread is neither `isResolved` nor `isOutdated`.
Comments by **the user themselves** are skipped — those are their own replies, not feedback to act on; the query asks GitHub for `viewer{ login }` (who the `gh` login is), falling back to the PR's author when the response carries no viewer, so a PR one only reviews works too.
Other bots' comments are skipped as well: the query asks for `author{ login __typename }`, and `__typename == "Bot"` outside qodo means a CI or coverage bot whose comment is not a review instruction.
In **every** thread, qodo's as much as a human's, everything up to the user's **last own reply** is skipped (`lastReplyBy`) — they have seen those comments and answered them, and re-queuing would ask them to do the work a second time.
What a reviewer wrote *after* that reply is new: skipping the thread whole swallowed every follow-up, so a reviewer answering the user's answer never reached the queue at all.
Qodo threads were once exempt from the reply rule — a "will fix" reply does not make a suggestion done — but `isResolved`/`isOutdated` cannot carry that on their own: qodo never resolves its own threads, and a suggestion addressed by *adding* code (a test, a requirement item) leaves the flagged line where it is, so it never goes outdated either.
The answered suggestion then stayed active for the life of the PR and came back into the queue on every sync, while a reply of the user's is exactly the signal that they dealt with it — the same signal a human thread has always been read by.
Both flags live on the thread and neither is exposed by the REST review-comments endpoint — a suggestion someone resolves by hand without touching the code never goes outdated, so filtering on the REST comment's `position` alone let such a prompt look active and re-queued it on every sync.
`promptsFor` — the current-suggestions view — maps only the active comments' prompts, in review order, to the URL of the comment each came from.
The bot's login is matched in both spellings, REST's `qodo-free-for-open-source-projects[bot]` and GraphQL's bare `qodo-free-for-open-source-projects`.
Comments on a **closed or merged** PR are dropped wholesale — the query asks for the pull request's `state`, and anything but `OPEN` contributes nothing: that PR is done with, so its threads are no work to do however active they still look, and the sync's remove half then clears what it had queued from it.
The same query also reads the PR's **conversation** — its top-level comments — and harvests it the same way, after the threads of every page.
Reviewers write there as much as in the diff (a top-level "we need to document the submodule checkout" never reached the queue at all), and qodo repeats its findings there in a "Code Review by Qodo" summary comment, which sometimes carries one the inline round left out.
The conversation is not the connection `--paginate` pages, so every page repeats it verbatim; it is taken from the first page and harvested once.
Its comments are always **active**: a top-level comment has no thread to resolve and no diff position to go outdated, so it stands until the user deals with it or writes below it — the last-own-reply rule applies to the conversation exactly as it does to a thread.
In the summary comment every finding is wrapped in a Markdown blockquote and its `<summary>` reads `Agent prompt` without the `<strong>`, so the blockquote markers are stripped and the pattern accepts both spellings; the preamble sentence qodo puts above the summary copy of a prompt ("The issue below was found during a code review…") is dropped, and `promptKey` (the prompt with whitespace runs collapsed) then recognises the inline and summary copies of one finding as the same text.
A finding that qodo also raised inline is skipped in the summary whatever became of its thread — queuing the summary copy would otherwise resurrect every suggestion the user has already answered, since the reply rule silences the inline one but not the summary.
On a fetch failure `comments`/`promptsFor` return `null`, never an empty list, so a network blip is never mistaken for "everything is resolved".
An **unreadable** response counts as a failure too: `parse` returns `null` rather than an empty list, because "we cannot read what the PR carries" reaching the sync as "the PR carries nothing" reports `+0 added` and looks exactly like a clean PR.

`QueuePane` is the single writer of the queue file and of a per-task **review-sourced** sidecar (`QodoImported`, a YAML `prompt: comment url` map at `<tasksDir>/.queues/qodo/<task id, `/`→`__`>.yaml`, stored beside the queues so it syncs too — `dsn~message-queue-store~2`) recording which message texts the review sync has placed in that queue and which review comment each came from.
A sidecar in the older string-list format still loads; those prompts simply carry no URL until the next sync re-learns it.
Every queued message the sidecar knows a URL for gets an **open-in-browser** button in its row (below the send button, the same `ExtensionServer` focus-or-open path as the PR icon of `dsn~pr-state-indicator~3`), so one can jump to what was actually flagged and see whether the comment is still there — the "is this queued message outdated?" check the sync itself cannot answer.
A message with no known URL (hand-typed, or edited so it no longer matches) gets no button and no slot for it — an icon button is no wider than the send button above it, so the rows stay aligned either way, while a kept slot would stretch the two-row add box.
Right-clicking the button offers **Copy URL** (the bare URL, like the PR icon's menu entry) — the fallback for when the Firefox extension is not connected and the focus-or-open path can do nothing.
Reconciliation is a pure function (`QodoReconcile`): given the queue, the qodo-sourced set, and qodo's current active prompts, it adds each active prompt not already sourced (so a prompt the user sent or deleted is not re-added — their choice stands) and, only when `removeResolved`, drops queued messages that are qodo-sourced **and** no longer active — the "done" ones; a queued message outside the sourced set (hand-typed, or a qodo prompt the user edited so it no longer matches verbatim) is never removed, so a sync cannot clobber a draft.

`QodoReviewPoller` runs the automatic feed on a long fixed-delay daemon scheduler, the same system-tool approach as the PR-state poll (`dsn~pr-state-indicator~3`).
To stay a good GitHub citizen each tick makes only a *cheap* `gh pr view <url> --json updatedAt` call first and fetches the heavier review comments **only when the PR moved**, so a PR nobody touched costs one light call and no comment fetch; activity opens a short follow-up window (`FOLLOWUP_POLLS` = 5 ticks) since a qodo review lands minutes after the push it reviews, then it goes quiet again.
The gate reads `updatedAt` and not the head SHA it read before: a push is not the only thing worth fetching for — a human reviewer writing on a branch nobody pushes to leaves the head exactly where it was, so their comment never opened the window and never reached the queue, which is the automatic half of this feature silently doing nothing for every human review.
`updatedAt` moves on a push *and* on a review comment (verified against a PR whose newest event was an inline comment), so one call covers both; it also moves on labels and other noise, which costs at worst a comment fetch that finds nothing new.
It harvests **every** PR of a task, not only the first: a task can carry a code PR and a docs PR, each reviewed by qodo on its own.
The timestamp and window count live in memory per **PR URL** (single scheduler thread, no locking), pruned to the currently referenced PRs each poll — keying them per task instead would let one PR's activity swallow the other's window; on startup the empty map opens one window per PR so anything posted while the app was off is still caught.
The poller keeps no disk state: it hands the active prompts and their comment URLs to the FX thread, where `QueuePane.addQodoPrompts` runs the add-only reconcile.
The **Review comments sync** button (in the pane, enabled only when the task has a PR) fetches the current open review comments off the FX thread and runs the full reconcile (add **and** remove), reporting `Review comments: +N added, −M removed` in the status line.
It tries the task's PRs top to bottom and the **first one with open comments wins**; a PR that is reachable but has none does not end the search, and only if every fetch fails does it report the failure and leave the queue untouched.
Reconciling against several PRs at once is deliberately unsupported: the remove half of the reconcile is per-PR, so prompts from a second PR would read as "resolved" and be dropped — this both migrates a PR reviewed before the feature existed and refreshes the queue after a push without waiting for the poll.
The sidecar follows a task rename like `QueueFile.rename`.

Tags: windows, linux

Covers:
- req~message-queue~2

Needs: impl, utest

### Energy saver
`dsn~energy-saver~1`

`EnergySaver` is a process-wide gate (a static flag — the app has one window and one set of pollers; threading a flag through six poller constructors buys nothing) that every poller consults before its periodic tick: `TmuxStatusPoller`, `TmuxPrPoller`, `TmuxTitlePoller`, `PrStatePoller`, `QodoReviewPoller` and `RefactoringMinerPoller` each schedule a `tick()` that calls their `poll()` only while the gate is open, and `Main`'s periodic reconcile (`dsn~tmux-sync~7`) and active-desktop read (`dsn~active-desktop-filter~6`) skip their tick the same way.
The gate stops the *scheduled* work only: every poller keeps a `pollNow()` that runs one poll on its own thread past the gate, so an explicitly requested refresh always works.
The embedded terminal is untouched — it is a live pty stream, not a poll, and the whole point is to keep working in the selected task's session.
`Main` installs two refresh actions on the gate: the cheap one (the status poll alone) for a task change, and one that calls `pollNow()` on every poller for the manual refresh.
`MainWindow`'s selection listener runs the first while the saver is on — no tick would otherwise arrive for the task the user just opened — and the toolbar's refresh `Button` (`REFRESH`) runs the second; the toolbar's `ToggleButton` (`LEAF`) flips the gate and stores it in the preferences like the grouping toggle (`dsn~task-label-grouping~3`), restored after its listener is attached so a stored `true` sets the gate and not just the button.
The refresh button is always visible: running every poll once is useful with the saver off too, and hiding it would be one more piece of conditional toolbar.

Tags: windows, linux

Covers:
- req~energy-saver~1

Needs: impl, utest

### Refresh progress in the info center
`dsn~refresh-progress~2`

"Refresh now" (`dsn~energy-saver~1`) used to fire every poll and return, so nothing showed whether it was still running or had finished.
Every `pollNow()` now returns the `CompletableFuture` of its poll, and `Main.pollEverythingNow` hands the named futures (task statuses, PR states, published PRs, task titles, Qodo reviews, refactorings, auto PRs, sync groups — the pollers that exist) to `MainWindow.showRefreshProgress`.
That posts a GemsFX info-center notification (MADR 0034) "Refreshing…" whose summary names the polls still waiting, updated as each completes; after the last one it is replaced by "Refreshed" with the poll count and duration, or by a warning "Refresh incomplete" naming the polls that threw (each logged).
The replacement is a new notification rather than a retitle, because the info center slides in only when one is added — a retitle after its auto-hide would go unseen.
The previous refresh's notifications are cleared when a new one starts, and the menu item stays disabled until the last poll finishes (async single-shot convention).
The `InfoCenterPane` takes the dock host's place in the shell's workspace slot (`ShellFxHost.wrapInPlace`), so notifications overlay the docks from the top right — not the scene root: ShellFX casts the parent of its window view to its own `WindowPane` when a child window opens, and a wrapped root threw a `ClassCastException` there (`~1`→`~2`, field report 2026-09-14); `main.css` repaints the cards with the theme's `-color-*` variables instead of GemsFX's translucent grey and literal yellow.

Tags: windows, linux

Covers:
- req~energy-saver~1

Needs: impl, utest

### A poll never replays what a sleeping machine missed
`dsn~poll-no-wake-backlog~1`

Every periodic job in the app is scheduled with `scheduleWithFixedDelay`, never `scheduleAtFixedRate`.
A fixed rate keeps a schedule: a run that could not happen is still owed, so `ScheduledThreadPoolExecutor` runs the whole backlog back to back as fast as the pool allows once the machine wakes.
That is not a theoretical difference here — a laptop asleep from 03:35 to 08:36 (field report 2026-09-12) left the periodic reconcile (`dsn~tmux-sync~7`) owing 300 ticks, and each reconcile costs one `list-windows` plus one `capture-pane` per window over the six global ssh slots of `dsn~ssh-command-runner~6`.
The app spent the next eleven minutes after wake running ~9 `ssh` processes a second, draining a backlog of work whose answers were all superseded by the next item in the same backlog.
Everything else needing ssh queued behind it: the terminal mirror's attach for the task the user selected waited seventeen seconds for a slot and read "Connecting to koppor@devbox old-group …" the whole time, which is indistinguishable from a hung remote.
A fixed delay measures from the *end* of the previous run, so a machine that was away simply resumes: one tick, then the interval.
The interval is then approximate rather than exact, which no poll here cares about.

The energy-saver gate (`dsn~energy-saver~1`) does not help with this and is not meant to: it skips a tick's *work*, but a skipped fixed-rate tick is still owed and still replayed, and the gate is off while the user is on power anyway.
The pollers of `dsn~energy-saver~1` were already fixed-delay; the four schedules in `Main` — memory log, update check, periodic reconcile, active-desktop read — were not.

Tags: windows, linux

Covers:
- req~energy-saver~1

Needs: impl

### The collapse arrow acts on the press
`dsn~collapse-on-press~1`

A category header's `▾`/`▸` toggles on **`MOUSE_PRESSED`**, not on a click.

JavaFX synthesizes `MOUSE_CLICKED` only when the press and the release land on the same node, and that Label lives inside a `ListCell`'s *graphic* — which `ListView.refresh()` throws away and rebuilds.
The running-status poll calls `refresh()` on every tick whose snapshot differs (`dsn~task-running-indicator~7`), so with live Claude sessions flipping between `working`, `waiting` and `attention` it fires constantly.
A human press and release are a hundred milliseconds apart; when a rebuild lands between them the Label the press hit no longer exists, no click is synthesized, and the arrow ignores the attempt.
Field report 2026-09-12: "For the expansion icon at categories, I need to click it twice".

A toggle is press-shaped anyway — it has no drag-off-to-cancel semantics to preserve — and the press also consumes the event, so the cell's own click handler does not open the group's config underneath it.
The same exposure applies in principle to every control built into a row graphic; the row's *cell-level* handlers are not affected, since the `ListCell` itself survives a refresh and only its graphic is replaced.

Not reproducible from a UI test on a developer machine that has ContextSwitcher itself running: the app pins its window on all desktops (`dsn~window-desktop-pin~2`), the robot's clicks land on whichever window is in front, and every robot-driven UI test fails for that environmental reason rather than for a defect.
The mechanism is read off the code and the poll cadence instead, and `MainWindow.toggleGroup` logs each toggle so a recurrence can be checked against the user's own log: a first click that reaches the handler and does nothing is a different bug from one that never arrives.

Tags: windows, linux

Covers:
- req~task-folder-grouping~1

Needs: impl

### A standing filter still lets a category collapse
`dsn~collapse-survives-a-filter~1`

A find force-expands every group and the Done section (`dsn~task-find~9`, `dsn~task-tag-filter~2`): the point of a query, or of picking a tag, is to see every match without hunting behind collapsed headers.
That expansion was driven by `narrowed`, which also covers the three **standing** filters — awaits input, running tasks, active desktop.
Those are not a search; they are how the user reads their list all day.
So while any of them was on, collapsing a category did nothing: the click flipped `collapsedGroups` and the rebuild overrode it on the very next pass, with no sign that anything had been registered.

Field report 2026-09-12 — "collapsing click doesn' work" — with `filter/Active/Desktop` stored as `true`, so the list had been narrowed since the previous session and every category had been stuck open ever since.

`rebuildRows` therefore expands on `finding` (`searching || filtering`) rather than on `narrowed`; the rest of `narrowed` keeps its other jobs, including hiding a group a filter emptied.
Same shape as `dsn~selection-survives-a-filter~2`: a rule written for a query, applied to every filter.

Tags: windows, linux

Covers:
- req~task-folder-grouping~1

Needs: impl, utest

### A filter never costs a task its selection
`dsn~selection-survives-a-filter~2`

Every row rebuild replaces the list's items, which clears the `ListView` selection, so `MainWindow.rebuildRows` turns the task on screen into the pending selection first and re-applies it afterwards (`dsn~browser-tab-selects-task~5`).
That preservation skipped a task the rebuilt rows do not contain, and the reason given was a **search**: a query that hides the selected task must not leave its id pending, or clearing the query would jump back to the pre-search task instead of the one the user just found.
The guard, however, read `narrowed` — search *and* the tag, awaits-input, running and active-desktop filters.
So a filter that hid the selected task for a moment dropped its selection for good: the row came back, the highlight did not, and no later rebuild had anything left to restore.

Field report 2026-09-12, on a task being created with a filter on: "the highlight the task on the left while creation still does not work — the terminal content is right though".
Both halves are this: a newly created task is not running and has no desktop *yet*, so a narrowing filter hides its row for a second or two, and the terminal kept showing it because a **cleared** selection is deliberately a no-op for the panes (they have to survive the clear that every rebuild performs).
That is the one state where the list and the panes can disagree, and it is exactly what the user saw.

The guard now reads `searching`, which is the condition its own rationale was ever about; every other filter keeps the task pending, and it takes its highlight back as soon as it has a row again.

A second half of the same story: replacing the rows makes JavaFX's own selection model **emit a selection**, of a neighbouring row, before the restore a few milliseconds later puts the real one back.
The selection listener treated that as a user selection and ran everything it runs: the preview (so the terminal mirror pointed at the other task's window), the auto-switch on selection, since removed (so the other task's browser tabs were switched to — the status bar read "… : browser ✗" for a task nobody selected), and `rememberLastTask`, which stamped it as the desktop's last task for a later desktop switch to act on.
Field report 2026-09-12, seconds after creating a task, with no `selectTask` anywhere in the log: an auto-sync's file write rebuilt the rows and the wrong task's switch ran.
`MainWindow.replacingRows` is true only while `visibleRows.setAll` runs, and the listener returns at once while it is: the restore right after is the selection that counts, this one is noise (logged at debug).

Returning from the listener suppresses the replacement's *effects*, not the selection itself, and the selection left standing is the rest of this story.
It becomes the `onScreenId` the **next** rebuild preserves, and the restore then selects a row that is already selected — a no-op, which fires no event, so the listener never runs and the panes are never told.
The list highlights one task while the terminal, the notes and the configuration pane show another, indefinitely: every later rebuild reads the same drifted selection back and agrees with itself.
Field report 2026-09-14 — "i clicked it, terminal was right, then I cleared the search, something flickered on the left - and now the terminal does not match the content" — with the log showing the same ignored, non-null selection twice fourteen seconds apart (`Ignoring the selection a row replacement emitted: Loaded[… pr-status-labels-missing …]`) and no `previewTask` between them.
`rebuildRows` therefore ends on `MainWindow.syncPanesWithSelection`, after every restore path including the two that return early: if the list has a task row selected and it is not the task the panes show, the two are made to agree.
The panes' task wins when it still has a row — it is the one the user chose, and the drift is arbitrary — and otherwise the highlight wins and the panes follow (`applySelection`, the selection listener's body, extracted so it can run for a selection that reached the list without an event of its own).
Clearing the drifted selection instead was the first fix and the wrong one: the drift is frequently *correct* — a rename replaces the selected row with the renamed one at the same index, before `taskRenamed` re-targets `previewedTask` — and clearing it left the list with no highlight at all, which `SelectTaskRevealsRowUiTest.anUnreachablePendingIdStillLeavesTheShownTaskSelected` caught.

Not pinned by a test: whether `setAll` drifts the selection to another row or clears it is JavaFX's own business, and three shapes of the reported sequence (a filter hiding the selected row, a rename, a rename under a filter) all cleared it in the harness while the reported app drifted.
The two debug lines — `The list had drifted to … ; taking the highlight back to …` and `The panes showed … while the list selected … ; the panes follow` — name the branch on a recurrence.

A third fallback closes the case where **both** of the above are unresolvable at the same rebuild: the task the *panes* are showing (`previewedTask`, which `taskRenamed` and `refreshPreview` re-target, so it survives a rename) takes the highlight when it has a row.
Field report 2026-09-12, with the log line naming both misses at once — `was …-regarding-the-wizard-… (pending jabref/…-restore-welcome-tab-appearance, narrowed true, row -1)`: the on-screen id was a task whose adopted title had just renamed its file, and the pending id was a browser-tab report for a task on another desktop that the active-desktop filter will never show.
That fallback is a safety net rather than a behaviour: if the panes show a task and the list has nothing selected, the two disagree and the list is wrong.
It is deliberately **not** pinned by a test — the interleaving needs a browser-tab report landing between the rename and the next rebuild, which the harness cannot produce; the diagnostic line carries `previewed` so a recurrence says whether it fired.

The tests that missed this asserted `getSelectedItem()` — the model — which was right all along.
`SelectTaskRevealsRowUiTest.paintedSelected` asserts what the user looks at instead: the selection *index* points at the row, and the cell rendering it reports itself selected.
A rebuild that ends with nothing selected while a task was on screen logs `Rebuild left nothing selected` with the pending id, the narrowing state and the row index, so the next report of a vanished highlight names its branch instead of needing this session's bisection.

Tags: windows, linux

Covers:
- req~task-list-window~1

Needs: impl, utest

### FX thread stall watchdog
`dsn~fx-stall-log~1`

`FxStallWatchdog` runs on a daemon thread started first thing in `Main.start` with the FX thread's reference: once a second it posts a no-op through `Platform.runLater` and waits for it; a probe still pending after one second logs a warning with the FX thread's stack trace (`Thread.getStackTrace`, top 40 frames — the top names the blocking call, the bottom is always the Glass event loop), and a second warning with the total stall length once the probe finally runs.
One second is above any honest layout or CSS pass and below what a user notices as "not responding".
A nested event loop (a modal dialog) still services `runLater`, so an open dialog is not a stall; `IllegalStateException` from a toolkit that has shut down ends the loop, and `Main.stop` interrupts it.

Tags: windows, linux

Covers:
- req~freeze-diagnostics~2

Needs: impl, utest

### An uncaught exception reaches the user
`dsn~uncaught-exception-report~1`

`Main.start` installs a *default* uncaught-exception handler (`Thread.setDefaultUncaughtExceptionHandler`), not one on the FX thread alone: the action executor hands out virtual threads, which are unnamed and have no thread group to fall back on, so a job dying there printed `Exception in thread ""` to a stderr the packaged app drops.
The handler logs the throwable with its stack trace and puts `Internal error: <exception>` in the status bar, the same place every other background outcome is reported.
The status-bar write runs through `Platform.runLater` and swallows its own exceptions — one escaping there would land back in the handler and post another.
Before the window exists there is only the log.
For the same reason the desktop pin (`dsn~window-desktop-pin~2`) is `execute`d rather than `submit`ted: a `submit` parks the exception in a `Future` nobody reads, and no handler ever sees it.

Tags: windows, linux

Covers:
- req~freeze-diagnostics~2

Needs: impl

### Startup work off the FX thread
`dsn~startup-background~1`

Two pieces of `Main.start` leave the FX thread.
The task-directory scan, watcher registration and startup git pull run as one background job on the action executor (in that order — the pull stays behind the watcher start so the files it merges in are seen; the scan itself parses off-thread per `dsn~task-repository-watching~6` and entries stream into the list as they parse).
And a `class-warmup` daemon thread started first thing `Class.forName`-loads the heavyweight UI classes (the JediTermFX widget, the incubator `RichTextArea`, `TerminalPane`, `MainWindow`): their classpath scanning cost over a second on a cold Windows drive and showed up as an FX stall inside the `TerminalPane` constructor (`dsn~fx-stall-log~1`); loading them concurrently, the FX thread later finds them loaded, or waits only for the one still in flight.
The synchronous remainder of startup is deliberate: settings must exist before anything is built, the clone offer is a modal decision the scan depends on, and the stored-geometry read must precede `show`.

Tags: windows, linux

Covers:
- req~startup-responsiveness~1

Needs: impl

### Memory log line
`dsn~memory-log~1`

`MemoryLog.line()` formats one line from the platform MXBeans, no library: heap used/committed and non-heap used (`MemoryMXBean`), the process's committed virtual memory and the machine's free physical RAM (`com.sun.management.OperatingSystemMXBean` — the virtual size is the figure that grows in *any* native leak, pty4j, JavaFX or thread stacks alike, and the free-RAM figure separates "we leaked" from "the machine was full"), and the live thread count (`ThreadMXBean`; every thread costs native stack memory).
`Main` schedules it on the existing single-thread daemon scheduler, every 10 minutes at info level, first line immediately — a session must log at least one baseline.
The extended OS bean lives in `jdk.management`, which the jpackage image's `java.se` does **not** include, so it is added to `addModules`; on a JVM without it the line simply loses those two figures (`instanceof` probe).

Tags: windows, linux

Covers:
- req~memory-diagnostics~1

Needs: impl, utest

### Restart to update
`dsn~restart-to-update~11`

`AppUpdate` (a process-wide holder like `EnergySaver` — one window, one exit, so no callback threaded through the 30-parameter `MainWindow` constructor) carries the whole feature.
`Main.scheduleUpdateCheck` ticks every 5 minutes (first tick after 5 minutes, so a launch never competes with a `fetch`; was 30 in `~1`, shortened so the upstream news of `dsn~whats-new-upstream~7` arrives while it is fresh), skips while the energy saver is on, and hands the work to the action pool — the shared single-thread scheduler must not wait on the network.
The check itself is system `git` through `LocalCommandRunner` (MADR 0003's spirit, the `TaskGitBackup` route): `git -C <repo> fetch --quiet`, then `git -C <repo> rev-list --count HEAD..@{u}`.
The repository is the nearest ancestor of the working directory holding a `.git` (a directory in a clone, a file in a worktree); an app unzipped somewhere has none and the check never runs.
Every failure — no network, no upstream, no git, unparseable output — counts as *up to date*: the check is an offer, not an error the user must dismiss.

The toolbar's `UPDATE` button is always there (it was hidden until an update existed, through `~4`: revealing it shifted every icon beside it, and a toolbar that hops is worse than one gray glyph too many).
A positive count only recolours it (`MainWindow.showUpdateAvailable`) and says so once in the status bar.
The commit count in its tooltip tracks the current poll: every positive check replaces it, while the recolour and the status-bar message happen once (through `~5` the count stayed at whatever the first positive poll had reported, drifting from the pending bullets refreshed on every poll).
A small red badge over the glyph's lower right corner, like a phone's notification count (`99+` past 99; the badge overflows the glyph's fixed 16-pixel box, so no toolbar icon moves when it appears), counts the **pending changes** of `dsn~whats-new-upstream~7` — the number the click's window carries in its title — not the commits.
This reverses `~7`/`~8`, which put the commit count there on purpose: the two diverge for good (a merge commit carries no changelog bullet) and for a while (a local changelog edit is a pending change without a commit), so a badge of 7 opened "1 pending change" and read as a bug.
Commits are what a restart is about, and code without a bullet is still code, so the commit count stays labelled in the tooltip's restart sentence.
With nothing pending but commits behind — a merge-only update — the badge is hidden (never a `0`), while the glyph stays blue and the tooltip still offers the restart with the commit count; while an update waits, the badge stays until the restart.
The glyph is painted in the app's own blue (the `update-available` class, `-cs-brand-blue` in `main.css`, the mark's `#0077BB`) — the muted icon gray of its neighbours means nothing is waiting.
Clicking it is **not** the restart (it was, in `~2`): the count behind it is up to five minutes old, so the click opens the "What's new" window of `dsn~whats-new-upstream~7` on a fresh look — a `ProgressBar` and "Checking remote …" in its button row while `Main.checkRemoteNow` runs `AppUpdate.commitsBehind` (the same `fetch` plus `rev-list`) and re-projects the news on the action pool.
The count starts at the commit the app **runs**, not the checkout's current `HEAD`: `Main.scheduleUpdateCheck` records it once at startup (`AppUpdate.rememberRunningCommit`, `git rev-parse HEAD`), and `commitsBehind` counts `<running commit>..@{u}` (`HEAD..@{u}` only while none is recorded).
Through `~9` it counted from `HEAD`, so a `git pull` outside the app — or another session fast-forwarding the checkout — made the old build read as up to date and took *Restart to update* away while that build kept running (field report 2026-09-13).
`MainWindow.newsChecked` then replaces the body with what the fetch found, retitles the window to the new range and hides the bar.
*Restart to update* stays live during that fetch (`~10`→`~11`: it was disabled until the answer, so a slow or hanging remote held the restart hostage); the run loop pulls on its own, so the fetch only decides what the window lists.
While the app is behind it does **not** announce the bullets: *Later* or closing the window leaves them pending — in the window's next look, the tooltip and the badge — and *Restart to update* announces them just before leaving (`~9`→`~10`: the fetch announced them, so a dismissed window came back empty).
It answers even when the fetch found nothing new (the body then says so), because a bar nobody ends reads as a hang; a window closed meanwhile is simply not filled in.
When that fetch finds the app not behind (`commitsBehind` 0), there is nothing to restart into: *Restart to update* is removed and *Later* reads *Close* (added in `~8`; through `~7` the restart stayed offered and merely re-ran the build of the same commit), and having seen the bullets is what announces them.
The restart is that second press, never automatic — the same `AppUpdate.requestRestart()`: a flag plus `Platform.exit()`, so `Main.stop` still runs (task git sync, window geometry) and `Launcher` — back from `launch` with the toolkit down — exits with **55**.
That code is the whole protocol with `scripts/run-loop.sh` / `.cmd` (`just run-loop`), which loops `git pull --no-rebase` → `gradlew :app:jpackageImage` → run: 55 means "rebuild and start me again", every other code ends the loop, so a normal quit stays a quit.
Rebuilding outside the app is what makes this safe — the process that is being replaced is gone before its image is overwritten.

Tags: windows, linux

Covers:
- req~restart-to-update~2

Needs: impl, utest

### Nothing that breaks a start while a task is being created
`dsn~busy-while-creating~2`

While any task creation of `dsn~task-create-progress~5` runs (`Main`'s in-flight count above zero), the bar below the terminal (`dsn~terminal-jump-to-bottom~1`) and *Restart to update* in the What's new window (`dsn~restart-to-update~11`) are disabled.
The terminal shows the fresh session while the launcher still starts Claude and pastes the prompt: *Clear input* or *Accept suggestion* typed into it then corrupts the prompt, and a restart kills the launch halfway.
Both bind their `disable` to the count, so they come back the moment the last creation finishes, success or failure; the restart no longer waits for "Checking remote …" as well (`~1`→`~2`, `dsn~restart-to-update~11`).
The whole bar is disabled rather than each button, whose own disabling (placeholder, local session, round-trip) stays untouched — including for a task other than the one being created, a deliberate simplification.

Tags: windows, linux

Covers:
- req~task-create~1
- req~restart-to-update~2

Needs: impl

### Restart or exit, by how the app was started
`dsn~restart-label-by-launch~1`

Only a run loop acts on exit code 55; started through `just run-debug`, `gradlew run` or the packaged executable by hand, the same press just quits, so a button reading *Restart to update* promised what it could not do (field report 2026-09-12: under `just pull-run-debug`, Gradle turned the 55 into a red BUILD FAILED).
`scripts/run-loop.sh` / `.cmd` therefore export `CONTEXTSWITCHER_RUN_LOOP=1` before starting the app, and `AppUpdate.startedByLoop` reads it once at startup.
The environment is the only thing a wrapper can vary per start: a Gradle property is fixed at build time and the packaged launcher never goes through Gradle, and a `-D` system property would sit in jpackage's `java-options`, baked into the image and the same for every launch.

`AppUpdate.actionLabel` turns that boolean into the update window's action button — *Restart to update* when looped, *Exit to update* otherwise — and `AppUpdate.actionHint` into a muted line above the window's buttons and the restart sentence of the toolbar tooltip: looped, the app quits and `just run-loop` pulls, rebuilds and starts it again; otherwise it quits, does not come back by itself, and `just run-loop` is named as the way to get the update — the same words the `run` task in `app/build.gradle.kts` prints on exit 55.
The line goes with the button: when there is nothing to restart into, both are gone.
Only the wording changes: `AppUpdate.requestRestart` exits with 55 either way — a request, and whether anything acts on it is the wrapper's business.

Tags: windows, linux

Covers:
- req~restart-to-update~2

Needs: impl, utest

### What's new while the app runs
`dsn~whats-new-upstream~7`

Pending news is a projection that needs no commit: `WhatsNew.pending` diffs changelog *text* against the **last announced copy** of `CHANGELOG.md`, kept on disk as `~/.contextswitcher/whats-new-announced.md` next to the launch script's `whats-new-last-commit` — a bullet (day, heading, first line and continuation lines) present in a source but not in the copy is pending; `WhatsNew.announce` replaces the copy with every source seen.
The copy on disk is what makes "nothing missed, nothing twice" hold across restarts: three changes landing while the app is closed are three bullets in the update tooltip at the next start, and a bullet announced once is old for both triggers.
Without a copy yet (first run) the current changelog becomes it silently — the app must not greet a fresh install with the whole changelog.
Two triggers feed it and share the copy, so whichever sees a bullet first shows it and the other stays quiet:
the local `CHANGELOG.md` of the checkout the app runs out of (`AppUpdate.repositoryRoot`) is watched — its parent directory through a `WatchService` filtered to the file, `TaskRepository`'s pattern, `ENTRY_CREATE` as well as `ENTRY_MODIFY` since an editor may write through a temp file and a rename, settled for two seconds because a session writes several times in a row — which sees a parallel session's edit seconds after the write, minutes before its commit, and a landing `git pull`; and the five-minute fetch tick of `dsn~restart-to-update~11`, which once behind reads `git blame --line-porcelain @{u} -- CHANGELOG.md` — the case where nobody edits locally and a plain push from elsewhere still surfaces.
Both sources stay in the projection (`Main.newsSources`, the latest text per trigger), so upstream bullets arriving while a local edit is still pending neither hide it nor get hidden by it; a bullet in both counts once.
Was, in `~1`, `git blame` over `announced..@{u}` on the tick only: a working-tree edit has no commit and was invisible, and a committed one was up to five minutes late.
Both sources are blamed (`WhatsNew.blame`; the local one on the working tree), so every bullet names who wrote it (`Item.by`) and the window groups *Changes by <author>* per other author, then *Changes by me — remotely*, then *Changes by me*.
A bullet is *me* when its author mail is `git config user.email` or it is not committed yet; *me — remotely* is that same mail on a bullet only the fetched upstream holds, pushed from another machine or checkout.
A bullet in both sources takes the local attribution.
Was, in `~4`, *mine* for every local bullet and *by others* for every fetched one, so a bullet pushed from elsewhere read *by others* in the app and *by me* in the launch-time popup of `scripts/WhatsNew.java`, whose `collect` groups by the same blame.

The signal never steals focus: there is no news button of its own (`~3` had a `BELL_RING` button with one dot per pending bullet as its text; `~2` had the dots without an icon) — `MainWindow.showPendingNews` puts the pending bullets into the tooltip of the `UPDATE` button of `dsn~restart-to-update~11`, whose blue already says that something waits.
The tooltip is the same heading the window carries plus the bullets' first lines, each prefixed with its author (`WhatsNew.shortBy`, cut to 12 characters) — often all the user needs — followed, once the running app is behind, by *restart to update*; with neither it just offers the check.
The heading names the basis its count uses: `What's new — N pending changes since you last looked — now at <short sha> (<date> <time>)`, the upstream from `Main.newsUpstream` (`AppUpdate.describe` of `@{u}`); when it is the running commit (the news is a local edit nobody pushed) or git cannot say, the upstream is left off rather than faked.
Through `~5` it read `since <running sha> — now at <upstream sha>`, naming the running commit as the start while the count was measured against the announced copy, which has no commit: four bullets since the running commit, one of them unread, read as "1 pending change since" that commit.
A local uncommitted edit is news without being behind, so it fills the tooltip without turning the glyph blue.
Clicking the button opens `MainWindow.showWhatsNew` under that same heading, on a fetch (`dsn~restart-to-update~11`) — the non-modal `Stage` owned by the main one, body `WhatsNew.view` (`**bold**`, `` `code` ``, clickable URLs through the app's URL opener), *Later* (close) and *Restart to update* (`AppUpdate.requestRestart`) below.
What announces — the copy is replaced and the tooltip and badge drop the bullets — is *Restart to update*, pressed while an update waits, or the window's look itself when there is nothing to restart into; dismissing the window while an update waits keeps them pending (`~6`→`~7`: opening the window announced them, so after *Later* the next click showed nothing).
The announce is a direct file write, not a task on the action pool, because the restart runs it just before `Main.stop` shuts that pool down.
The heading's `now at` compares the upstream with the running commit of `dsn~restart-to-update~11`, not the checkout's current `HEAD`.
Every git or file failure yields nothing pending — the news is an offer, as the update check is.
`WhatsNewTest` pins both projections: the blame-based one on a synthetic changelog and blame, the pending one on a copy in a temp directory (two sources, once each, old after announcing, nothing without a copy).

Tags: windows, linux

Covers:
- req~restart-to-update~2

Needs: impl, utest

### The running commit in the status bar
`dsn~running-commit~3`

`Main.scheduleUpdateCheck` asks the same checkout (`AppUpdate.repositoryRoot`) for its `HEAD` once at startup — `git show --no-patch --date=format:%Y-%m-%d %H:%M --format=%h (%cd) HEAD`, local and instant, so the shared scheduler thread may run it — and `MainWindow.showCommit` puts the answer in a muted `Label` at the right end of the bottom bar, left of the open-log button.
The corner furthest from the status messages: the line is a permanent fact, not a message, and `SwitchStatusBar` replaces its own children on every switch.
An app started from a zip has no checkout and no `HEAD` to show, and the label simply stays empty.
A double-click on the label copies the bare sha — `MainWindow.shaOf`, the line up to its first space, without the date a paste into a bug report does not want — and the status bar says which sha went to the clipboard.
The copy is the double-click itself rather than a selection to press Ctrl+C on: pasting the sha somewhere is the only thing anyone does with this line, and a `Label` would have to become a text control to be selectable at all.

Tags: windows, linux

Covers:
- req~restart-to-update~2
- req~running-commit~1

Needs: impl, utest
