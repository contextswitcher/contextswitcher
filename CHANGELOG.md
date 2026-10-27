# Changelog

All notable changes to ContextSwitcher are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions are release dates ([CalVer](https://calver.org/), `YYYY-MM-DD`), tagged `v<date>` on GitHub once the day is over.
The topmost section is the day in progress — its link points at `main` until that tag exists.

## [2026-10-27] - 2026-10-27

### Fixed

- Android: a task whose tmux window could not be created reports the remote's error output in the Add-task dialog, like the desktop, instead of only "Cannot create a tmux window"; a category's `~/…` workspaces root is now created in the home directory rather than a directory literally named `~`.

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

[2026-10-27]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-25...main
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
