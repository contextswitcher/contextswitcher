---
status: accepted
date: 2026-09-10
decision-makers: Oliver Kopp
---

# One Mirror Client per tmux Session, Verified by Its Own Select

## Context and Problem Statement

The terminal pane mirrors a task's tmux window through one grouped session per **base session** (`cs-mirror-<session>`, `dsn~terminal-pane~14`): every task of `old-group` is shown by re-pointing that single attached client with `select-window`.
The client's current window is therefore shared state across all those tasks, and nothing on screen says which window it is on.

Field report 2026-09-09: a browser tab of PR 17071 selected the *other* task that lists the same PR, the mirror followed, and the pane showed the tab-theme session while the list, the notes and the header showed "Fix stale status labels workflow".
The wrong-task selection was a bug of its own (`dsn~browser-tab-selects-task~5`, fixed), but it exposed the structural half: **any** stray write to that client shows one task's Claude session under another task's name, and the pane reports success while doing it.
A pane that quietly shows the wrong session is worse than one that says it cannot show anything — the terminal is where messages are typed.

## Considered Options

* **Keep one client per session, verified by its own select** — the `select-window` carries a chained `display-message` that prints the window the mirror really ended on
* **One mirror session per window** (`cs-mirror-<session>-<window>`, grouped onto the base) — grouped sessions have independent current windows, so no task can move another's view
* **Keep one client per session, unverified** — the wrong-task selection is fixed; the shared client only made it visible
* **Poll the mirror's current window** on the existing status tick and correct on drift

## Decision Outcome

Chosen option: **one client per session, verified by its own select**.

The verify is free: `tmux select-window -t <mirror>:<window> \; display-message -p -t <mirror> '#{window_id}'` is one command, one ssh slot, the same round-trip the select already spent — so the pane confirms *what it is showing* instead of an exit code, and a select that succeeds onto another window re-attaches through the repair path (`TmuxMirrorCommands.repairMirrorCommand`) instead of lying.
This supersedes the rationale in `dsn~terminal-pane~14` that a `display-message` verify is too expensive: that weighed a *separate* round-trip, which this is not.

One mirror session per window is the only option that makes the drift impossible rather than detectable, and it was rejected on switching cost: the pty is keyed by (remote, session), so per-window sessions turn every task switch inside a group — the common one, a group holds dozens of tasks — from a ~0.5 s `select-window` into a full `ssh -t` attach with a blank pane.
The pane is switched all day; paying that on every switch to close a hole that a free check reports is the wrong trade.
Leaving it unverified was rejected because the fixed selection bug is not the only way the client can move (a stranded mirror already moves it, `dsn~terminal-pane~14`), and the failure is silent by construction.
Polling was rejected as machinery for what the select already answers: the pane re-selects on every re-show anyway, so drift is caught within one confirmation interval without a new protocol on the status poller.

### Consequences

* Good, because the mismatch is caught at the moment the pane would start showing the wrong session, with no extra ssh slot on the switch path.
* Good, because it keeps the fast in-group switch that the shared client exists for.
* Bad, because the shared client remains shared: two tasks of one session cannot be viewed at once, and a stray writer is still *possible* — it is now reported and repaired rather than prevented.
* Bad, because the check trusts tmux's own answer; an empty answer is read as "cannot tell" and the select is accepted, so a tmux that prints nothing keeps the old behaviour.

## More Information

`dsn~terminal-pane~14` (mirror, select fast path, stranded-mirror repair), `dsn~browser-tab-selects-task~5` (the tie-break that caused the field report), MADR 0007 (JediTermFX).
