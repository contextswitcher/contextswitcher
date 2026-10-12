# Changelog

All notable changes to ContextSwitcher are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions are release dates ([CalVer](https://calver.org/), `YYYY-MM-DD`), tagged `v<date>` on GitHub once the day is over.
The topmost section is the day in progress — its link points at `main` until that tag exists.

## [2026-10-12] - 2026-10-12

### Added

- The setup wizard installs Claude Code's status reporting hooks on the session host, keeping the hooks and settings already there. With them, a session reports its status, its id and the directory it works in, so tasks get their status dot and deleting a task offers to remove its worktree and transcript. Before, a host set up through the wizard reported none of this.

### Fixed

- The Add-task dialog shows its buttons ("Add remote Claude", "Add local Claude", "Add plain task") and hints in full instead of cutting them off with "…"; the hints wrap and the dialog is as wide as its buttons need.
- The setup wizard's "Test connection" button, the tools page's "Install" and "Recheck", and the field captions are no longer cut off on a narrow page: the field next to a button gives way and long captions wrap.
- On a host whose login shell is fish, a task whose title contains an apostrophe ("can't …") could not get its tmux window: fish rejected the command as having unbalanced quotes.
- A remote directory written with `~` (such as `~/workspaces/…`) works: before, the app looked for a directory literally named `~` there, so creating a window or looking up a workspace's pull request failed.
- Closing the app no longer logs an error from a pull-request check that was still scheduled.
- The pull-request lookup no longer logs a warning every few minutes when a task's directory has no pull request or is not a git checkout.
- When creating a task's tmux window fails, the error alert and the task's terminal area show the remote's error message instead of only "(see log)"; before, the terminal area kept saying "Creating the tmux window …" after the failure.

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

[2026-10-12]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-11...main
[2026-10-11]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-10...v2026-10-11
[2026-10-10]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-01...v2026-10-10
[2026-10-01]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-28...v2026-10-01
[2026-09-28]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-27...v2026-09-28
[2026-09-27]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-23...v2026-09-27
[2026-09-23]: https://github.com/contextswitcher/contextswitcher/commits/v2026-09-23
