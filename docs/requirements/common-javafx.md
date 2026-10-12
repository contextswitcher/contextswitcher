# Common JavaFX requirements

Rules every part of the desktop UI follows, whatever feature it belongs to — standard JavaFX practice the app holds itself to, written down so a new dialog or window does not quietly break them.
Feature-specific UI behavior stays in [ui.md](ui.md).

## Requirements

### Dialogs belong to their window
`req~owned-dialogs~1`

Every dialog the app opens — add task, confirmations, error messages, the settings — appears over the window it concerns, on the monitor that window is on, stays above that window, and blocks only it.

Tags: windows, linux

Covers:
- feat~task-context-switching~1

Needs: dsn

## Design

### Every dialog gets an owner
`dsn~owned-dialogs~1`

A JavaFX `Dialog` or `Alert` without `initOwner` is centered on the **primary** screen, so with the app on a second monitor *Add task* and every error alert opened on the other one (field report 2026-10-12, PowerToys not involved).
`initOwner` is the standard remedy, and it is set before `show` at each place a dialog is built:

- `Alerts.plain(owner, type, message, buttons…)` replaces `new Alert(…)`; `Alerts.wrapping` and `Alerts.withContent` take the owner as their first parameter too, so an alert cannot be built without deciding it.
- A hand-built `Dialog`/`TextInputDialog` goes through `Alerts.owned(owner, new Dialog<>())`, or calls `initOwner` right after construction.
- The owner is the window the dialog is about: `MainWindow.dialogOwner()` for the main window's dialogs and `Main`'s, `Alerts.windowOf(node)` for a pane's (the terminal pane, the config form, the host table), the session's `TerminalSession.View.dialogOwner()` for a terminal session's, and the main window for the settings dialog's alerts.
- Null only where no window exists yet: the first-start wizard and what it triggers (`Main.cloneTaskRepo`, `Main.applyWizardSettings`); a wizard re-run from the menu is owned by the main window (`SetupWizard.show`'s `owner`).

An owned dialog also gets JavaFX's window-modal behavior and no taskbar entry of its own.
A window-list listener moving ownerless dialogs after the fact was tried first and dropped: it worked around the missing owner instead of setting it, guessed the dialog's size before JavaFX had measured it, and gave neither modality nor stacking.

Tags: windows, linux

Covers:
- req~owned-dialogs~1

Needs: impl
