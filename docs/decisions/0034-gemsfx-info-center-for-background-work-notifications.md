---
status: accepted
date: 2026-09-14
decision-makers: Oliver Kopp
---

# GemsFX Info Center for Background-Work Notifications

## Context and Problem Statement

Background work started by the user — "Refresh now" running every poller, and later sync rounds, restarts, auto-suspend — ran without visible progress or outcome.
The status bar is one line shared with the task switch's action chips, so a second long-running report either overwrites the switch or is overwritten by it.
Oliver asked for a task indicator on "Refresh now" and named GemsFX's info center (2026-09-14), after it had been skipped once as "a new dependency for what the status bar covers".
Where do notifications about background work go?

## Considered Options

* GemsFX `InfoCenterPane` wrapping the main window's shell
* The existing status bar line (`SwitchStatusBar.message`/`retitle`)
* ControlsFX `Notifications` popups
* A home-grown overlay (a `StackPane` card sliding in)

## Decision Outcome

Chosen option: "GemsFX `InfoCenterPane`", because it gives stacked, grouped, updatable notifications (title and summary are properties) that slide in, auto-hide, and stay out of the status bar's way, maintained by DLSC, Apache-2.0, and already the style reference for the find field.

### Consequences

* Good, because progress and outcome of background work get their own surface; the status bar keeps reporting the task switch.
* Good, because later background actions (sync groups, restart running tasks, auto-suspend) can post into the same center.
* Bad, because GemsFX is a large multi-control library and pulls ikonli (three icon packs), jsvg, pickerfx and validatorfx (all Apache-2.0) for one control.
* Bad, because its default look (translucent grey, hard-coded warning yellow) must be overridden in `main.css` to follow the theme.
* Neutral, because the pane wraps the ShellFX scene root; ShellFX keeps working (UI tests boot through it), but a ShellFX update that assumes its own root class would break here first.

## Pros and Cons of the Options

### The existing status bar line

* Good, because no dependency.
* Bad, because one line: a refresh report and a running switch overwrite each other, and a finished outcome vanishes with the next message.

### ControlsFX `Notifications` popups

* Good, because small API, one call per popup.
* Bad, because each popup is fire-and-forget: its text cannot be updated once shown, so progress means stacking popup after popup.
* Bad, because it is a new dependency all the same.

### A home-grown overlay

* Good, because no dependency and exactly the look wanted.
* Bad, because stacking, slide-in, auto-hide with hover-pause and grouping are all to be written and tested.
