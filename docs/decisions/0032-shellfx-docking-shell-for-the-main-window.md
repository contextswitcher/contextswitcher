---
status: accepted
date: 2026-09-13
decision-makers: Carl Christian Snethlage
---

# ShellFX Docking Shell for the Main Window

## Context and Problem Statement

The main window was a fixed three-lane `SplitPane`: the task list on the left, the terminal in the middle, the editor over the message queue on the right, and the task configuration a tab of the editor plus a modal form dialog.
Nothing could be moved, hidden or placed side by side differently, and the configuration form blocked the window it edited.
An opt-in exploration (`1d4c7f30`, `-Dcontextswitcher.shellfx=true`) hosted the same lanes as dockable tabs in [ShellFX](https://github.com/techsenger/shellfx) and turned the configuration into a pane.
Should that shell become the only main window?

## Decision Drivers

* Panes the user can rearrange, hide and bring back, instead of one fixed layout.
* The task configuration as a non-modal pane beside the notes, not a dialog on top of them.
* One layout to maintain and test — two main windows double every layout-dependent UI path.
* The project's dependency rules: a released, permissively licensed library is the norm.

## Considered Options

* ShellFX docking shell, as the only layout
* Keep the `SplitPane` layout, drop the exploration
* Keep both, the shell behind the flag

Other docking libraries were not evaluated: the ShellFX exploration was already running with the app's real lanes.

## Decision Outcome

Chosen option: "ShellFX docking shell, as the only layout", because it is the only option that gives rearrangeable panes and a non-modal configuration without a second main window to keep working.
The `-Dcontextswitcher.shellfx` flag, the `SplitPane` layout, the editor's configuration tab and the configuration dialog are removed; `ConfigForm` keeps the form's generation and merge logic for `ConfigFormPane`.

### Consequences

* Good, because panes can be regrouped, closed and reopened, and the configuration can pop out into a window of its own.
* Good, because there is one main window again: every UI test and every field report is about the layout users run.
* Bad, because ShellFX exists only as `2.0.0-SNAPSHOT`: the build pins one timestamped build from techsenger's repsy repository, its transitive techsenger dependencies still float, and a later snapshot can break the build.
* Bad, because ShellFX's window needs JavaFX's `HeaderBar` preview API: `-Djavafx.enablePreview=true` is in the app's, the packaged app's and the UI tests' JVM arguments.
* Bad, because `ShellFxHost` carries workarounds for six snapshot bugs (named in its comments), each to be re-checked on an upgrade.
* Neutral, because ShellFX depends on `tabpanepro`, licensed GPL-2.0 with Classpath Exception; the exception permits using it as an ordinary library, as OpenJDK's own licence does, so it is accepted beside the weak-copyleft licences `CLAUDE.md` already allows (decision 2026-09-13).

### Confirmation

`MainWindow.show` always builds the shell, and `gradlew :app:uiTest` runs every UI test against it.

## Pros and Cons of the Options

### ShellFX docking shell, as the only layout

* Good, because it already worked in the exploration with the lanes unchanged — `ShellFxHost` is a thin adapter around them.
* Bad, because of the snapshot, preview-API and licence costs listed above.

### Keep the `SplitPane` layout, drop the exploration

* Good, because it needs no preview API and no snapshot dependency.
* Bad, because the layout stays fixed and the configuration stays a modal dialog.

### Keep both, the shell behind the flag

* Good, because the stable layout stays the default while ShellFX matures.
* Bad, because the two windows diverge: the exploration already had layout-only paths (the toolbar split, the configuration pane) that the flag-off window never exercised, and the UI tests covered only one of them.
