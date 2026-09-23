---
status: accepted
date: 2026-07-13
decision-makers: Oliver Kopp
---

# Suspend Ends the Task's Live Context

## Context and Problem Statement

Tasks have a not-active-but-not-done state. Until now it was called `paused` and was purely a label: the tmux window (with the Claude session inside) kept running on the remote host, and browser tabs stayed open. What should putting a task aside actually do?

## Considered Options

* Keep pause as a pure status label (no-op)
* Suspend ends the live context: kill the tmux window (and, once the extension exists, close the browser tabs); resume re-creates it via the existing resurrect path
* Detach only: leave the tmux window running, merely unfocus client-side

## Decision Outcome

Chosen option: "Suspend ends the live context", because a set-aside task should stop consuming remote resources and attention, and the resume path already exists: task files capture `claude.cwd` and `claude.sessionId`, and `TmuxResurrect` recreates a dead window with `claude --resume` during a regular switch. The status is renamed `paused` → `suspended` to make the ending semantics explicit; the old spelling is not kept (task files are easy to edit).

Suspending is destructive (processes in the window die), so it requires an explicit confirmation; when the window's live `@cs_status` reports Claude as `working`, the confirmation escalates to an explicit "Force terminate".

**Amendment (2026-07-13, live use):** the blanket confirmation proved to be friction without protection — in the normal case (Claude waiting, `claude:` section present) the kill loses nothing, since resume recreates the window and resumes the session. Suspend is now silent in that case; the dialog remains exactly where it protects something: Claude `working` ("Force terminate") and tasks without a `claude:` section (resume cannot recreate). See `req~task-suspend-resume~2` / `dsn~task-suspend~6`.

### Consequences

* Good, because suspend/resume becomes symmetric with the existing failure path (dead window → resurrect → `claude --resume`).
* Good, because remote hosts do not accumulate idle Claude processes and tmux windows for tasks nobody is working on.
* Bad, because suspend kills whatever runs in the window; only Claude's session is restored on resume — other in-flight processes are not. Mitigated by the confirmation (escalated while Claude is working).
* Bad, because a task without a `claude:` section cannot be resurrected automatically; the confirmation dialog warns about this.

**Amendment (2026-07-14, live use):** resume of a no-claude task focused the bare session and landed on an arbitrary window.
Resurrection now works without a `claude:` section — the task gets a plain window back (default cwd, nothing resumed).
Suspend always removes the `window:` line (after a suspend no window is known); resume recognizes itself by the task's pre-flip **suspended** status and resurrects instead of focusing the bare session, so only *active* window-less tasks keep the deliberate session-level focus.
The dialog for no-claude tasks stays, now warning that nothing running inside can be brought back.
See `dsn~tmux-resurrect~7` / `dsn~task-suspend~6`.

## Pros and Cons of the Options

### Keep pause as a pure status label

* Good, because zero risk of losing work.
* Bad, because the label suggests the task is parked while it silently keeps consuming remote resources.

### Detach only

* Good, because non-destructive.
* Bad, because tmux windows here are detached by nature already — the option is a de-facto no-op and changes nothing on the remote.
