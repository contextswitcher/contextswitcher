---
status: accepted
date: 2026-07-13
decision-makers: Oliver Kopp
---

# Local Windows Terminal Focus via UI Automation

## Context and Problem Statement

A task's Claude session may run in a local **Windows Terminal** tab instead of a remote tmux window. Switching to such a task must bring the correct Windows Terminal tab to the foreground — even across multiple terminal windows and virtual desktops. How does ContextSwitcher focus a specific, already-running Windows Terminal tab?

## Considered Options

* **UI Automation** (Microsoft UIA): find the terminal window's tab element by title, `Select` it, foreground the window — driven from PowerShell (`System.Windows.Automation`).
* **Windows Terminal CLI** (`wt.exe -w <name> focus-tab -t <n>`).
* **Win32 window focus only** (`EnumWindows` + `SetForegroundWindow` by window title, via FFM).

## Decision Outcome

Chosen option: **UI Automation, driven through `powershell`**, because it is the only option that focuses an individual **tab** (not just a window) reliably, works for any launch method, and enumerates windows across all virtual desktops. Shelling out to `powershell` keeps the pattern identical to the system-`ssh` approach (MADR 0003) — no native COM/FFM code, no extra dependency. The whole UIA script is passed as a base64 `-EncodedCommand`, so there is no shell quoting to get wrong, no temp file, and no execution-policy prompt.

The tab is identified by a **fixed tab title** the user pins via Windows Terminal "Rename Tab" (recorded as `terminal.tabTitle`); the auto-title Claude sets is dynamic and often duplicated across tabs, so it is unsuitable as a key. Foreground stealing is worked around with the standard restore + Alt-tap + `SetForegroundWindow`/`BringWindowToTop` sequence. Validated live: `Select()` and `SetForegroundWindow` succeed on a real Windows Terminal tab.

### Consequences

* Good, because per-tab focus works across multiple terminal windows, and activating a window on another virtual desktop switches Windows to that desktop (useful for the planned desktop-per-task feature).
* Good, because it stays consistent with the "drive a system tool via a child process" architecture — no native bindings.
* Bad, because it is Windows-specific; a Linux terminal analog will need its own `SwitchAction` behind the same interface.
* Bad, because it depends on Windows Terminal's UIA tree (control types, `CASCADIA_HOSTING_WINDOW_CLASS`); a future Terminal rewrite could require adjusting the script.
* Bad, because each focus spawns a `powershell` process that JIT-compiles a small C# helper (~a few hundred ms). Acceptable for a user-initiated switch.

## Pros and Cons of the Options

### Windows Terminal CLI (`wt.exe`)

* Good, because no UIA; a documented CLI.
* Bad, because it is built to *launch* content; focusing an existing arbitrary tab is not a first-class, reliable operation, and it depends on the user launching each session in a specifically named window.

### Win32 focus only (FFM)

* Good, because no PowerShell dependency; pure in-process native calls.
* Bad, because it can only focus a whole **window**, not a tab — useless when several Claude sessions share one Windows Terminal window as tabs. More code (COM-free but FFM window enumeration) for less capability.
