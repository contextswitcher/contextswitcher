# Changelog

All notable changes to ContextSwitcher are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions are release dates ([CalVer](https://calver.org/), `YYYY-MM-DD`), tagged `v<date>` on GitHub once the day is over.
The topmost section is the day in progress — its link points at `main` until that tag exists.

## [2026-10-11] - 2026-10-11

### Added

- The setup wizard's recommended-tools page has a "Recheck" button next to "Install", so a tool installed some other way while the page is open shows as found.

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

[2026-10-11]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-10...main
[2026-10-10]: https://github.com/contextswitcher/contextswitcher/compare/v2026-10-01...v2026-10-10
[2026-10-01]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-28...v2026-10-01
[2026-09-28]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-27...v2026-09-28
[2026-09-27]: https://github.com/contextswitcher/contextswitcher/compare/v2026-09-23...v2026-09-27
[2026-09-23]: https://github.com/contextswitcher/contextswitcher/commits/v2026-09-23
