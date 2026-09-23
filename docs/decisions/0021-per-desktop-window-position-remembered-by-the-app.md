---
status: accepted
date: 2026-07-29
decision-makers: Oliver Kopp
---

# Remember the Window Position per Virtual Desktop in the App, Not via PowerToys FancyZones

## Context and Problem Statement

With the window pinned to all virtual desktops (`showOnAllDesktops`, MADR-less, `dsn~window-desktop-pin~2`), the same window sits at the same place on every desktop.
Different contexts arrange their browser and apps differently, so the ContextSwitcher window should sit where it fits *that* desktop's layout — a different position per desktop.
Oliver raised two candidates: lean on PowerToys FancyZones somehow, or have the app remember its own position per desktop, the way git gui records its geometry in its config.
git gui is also the cautionary tale: a remembered position on a monitor that is no longer there parks the window out of reach.

## Considered Options

* **The app remembers its geometry per desktop** — keyed by desktop name, stored in the config dir, restored when the active desktop changes.
* **PowerToys FancyZones** — let the user's zone layout place the window.
* **Do nothing** — the user drags the window after each desktop switch.

## Decision Outcome

Chosen option: **the app remembers its geometry per desktop**, because FancyZones cannot do the job at all and doing nothing is the tedium being solved.

FancyZones has no virtual-desktop awareness: zones are per-*monitor* layouts, a window is assigned to a zone once (by shift-dragging it there), and nothing repositions a window when the active virtual desktop changes — the pinned window would keep one zone across all desktops, which is exactly the status quo.
It is also an optional external install this app cannot depend on.

So the app does what git gui does, minus its known failure: `WindowPositions` stores `name = x,y,width,height` per desktop in `<configDir>/window-positions.properties`; the existing 2 s active-desktop poll (built for the "show active desktop only" filter) reports desktop changes, on each change the geometry the user left behind is saved under the previous desktop's name and the new desktop's remembered geometry is applied; a stored geometry that intersects no current screen is discarded instead of applied, so a changed monitor configuration resets the position.
Tracking runs exactly while `showOnAllDesktops` is on — an unpinned window exists on only one desktop, and the poll should not spawn `powershell` every 2 s for a feature that cannot show.

### Consequences

* Good, because each desktop keeps its own window placement across switches *and* restarts, with no external tool involved.
* Good, because the off-screen check makes a monitor change reset the position instead of reproducing git gui's out-of-reach window.
* Good, because it reuses the existing desktop poll and pin setting — no new poller, no new setting, no new dependency.
* Neutral, because desktops are keyed by *name*: unnamed desktops get no memory, and renaming a desktop forgets its position.
* Bad, because the poll-driven follow lags a desktop switch by up to 2 s, and the `powershell` read now runs whenever the pin is on, not only while the filter is in use.
* Bad, because it is Windows-only in effect, like the pin and the poll it rides on.

## Pros and Cons of the Options

### PowerToys FancyZones

* Good, because zone layouts are a polished, user-maintained placement system.
* Bad, because it has no per-virtual-desktop concept: one window keeps one zone, so the pinned window would still sit at the same place on every desktop — the feature simply cannot be built on it.
* Bad, because it is an optional external install (and per-user configuration) the app cannot assume.

### Do nothing

* Good, because zero code.
* Bad, because it is the manual re-dragging after every switch that prompted the request.
