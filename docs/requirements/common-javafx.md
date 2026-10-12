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

### Labels are never cut off
`req~full-labels~1`

No button label, radio or checkbox caption, field caption or hint is shown cut off with "…" ("Finish and che…", "Test conne…"); the window is wider, or the text wraps, instead.
Windows draws a wider font than Linux, so this holds there, not only where the layout was tried.

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

### Dialogs make room for their labels
`dsn~full-labels~1`

Three JavaFX/AtlantaFX defaults cut labels, each with its remedy:

- AtlantaFX caps every `.dialog-pane` at 600px from its user-agent sheet, which a `setMaxWidth` in code does not override; a dialog that needs more adds the `uncapped` style class (`main.css`) — the Add-task dialog and the setup wizard, whose icon and four buttons overran it on Windows (field report 2026-10-12, "Finish and che…").
- A dialog's `ButtonBar` gives every button the widest one's width (`ButtonBar.setButtonUniformSize`, on by default), so one long label makes four wide buttons that then no longer fit; a long button opts out (the wizard's *Finish and check up* and *Start right away*).
- A button shrinks below its label when its container is tight; `setMinWidth(Region.USE_PREF_SIZE)` makes the label the minimum, so the container — the button bar, and with it the window — widens instead (every wizard button, the button next to a field via `withButton`, the tool rows' *Install*/*Recheck*).

Text that may be long wraps instead: `setWrapText(true)` with `setMinHeight(Region.USE_PREF_SIZE)`, or a field next to a button gives way (`HBox.setHgrow`).
Each dialog's UI test asserts `UiTestSupport.truncatedTexts(…)` is empty — it compares each label's drawn text with its text, since `Labeled.isTextTruncated()` proved unreliable — so a layout that only fits under Xvfb's narrower font fails on CI rather than on Windows.

Tags: windows, linux

Covers:
- req~full-labels~1

Needs: impl, utest
