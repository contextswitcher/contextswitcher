# Changelog

All notable changes to ContextSwitcher are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions are release dates ([CalVer](https://calver.org/), `YYYY-MM-DD`), tagged `v<date>` on GitHub once the day is over.
The topmost section is the day in progress — its link points at `main` until that tag exists.

## [2026-09-23] - 2026-09-23

### Added

- Initial release.
- A task's context menu offers "Copy link" for its `contextswitcher://task/…` deep link, e.g. to link it from OneNote.

### Fixed

- "Review comments sync" no longer queues qodo findings with a stack of repeated "The issue below was found during a code review…" lines: the preamble is stripped again now that qodo writes its headings without `##`.

[2026-09-23]: https://github.com/contextswitcher/contextswitcher/commits/v2026-09-23
