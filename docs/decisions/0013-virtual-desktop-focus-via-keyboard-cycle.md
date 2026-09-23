---
status: superseded by 0019-direct-virtual-desktop-switch-with-hotkey-walk-fallback
date: 2026-07-17
decision-makers: Oliver Kopp
---

# Focus a Named Virtual Desktop via Registry Lookup + Keyboard Cycle

## Context and Problem Statement

A category (project group) can be pinned to a Windows **virtual desktop** — the ones the user names in Task View, e.g. `vs.code`, `Compilation Result` (see `docs/ng-virtual-desktops-genrator-latex-template.png`).
The category header carries a play button that must switch Windows to that desktop and nothing else.
Windows exposes **no supported API** to activate a virtual desktop by name or index.
How does ContextSwitcher switch to a named virtual desktop without a native dependency and without breaking on the next Windows build?

## Considered Options

* **Registry lookup + `Ctrl+Win+Left/Right` keyboard cycle** — read the desktop order and names from the registry, then inject the built-in switch hotkey the right number of times, all through `powershell`.
* **Undocumented `IVirtualDesktopManagerInternal` COM** — the interface the shell itself uses to switch by index (what tools like `MScholtes/VirtualDesktop` wrap).
* **A third-party helper** — bundle `VirtualDesktopAccessor.dll` or shell out to a packaged `VirtualDesktop.exe`.
* **Focus a window known to live on the desktop** — rely on `SetForegroundWindow` pulling its desktop forward (MADR 0008's side effect).

## Decision Outcome

Chosen option: **registry lookup + keyboard cycle, driven through `powershell`**, because it is the only option that switches to a desktop **by name**, needs **no dependency and no undocumented COM**, and keeps working across Windows builds.
Windows records the desktop order in `HKCU:\…\Explorer\VirtualDesktops\VirtualDesktopIDs` (a packed array of 16-byte GUIDs), the active desktop in `CurrentVirtualDesktop`, and each renamed desktop's label under `…\Desktops\{GUID}\Name` — all stable, documented-by-community registry values.
`WindowsVirtualDesktopFocus` resolves the target index by name and the current index, then injects `Ctrl+Win+Left/Right` (the built-in, user-visible switch shortcut) the signed difference of times via `keybd_event`.
Because `Win`-combinations are global hotkeys the shell processes regardless of the foreground window, no foreground-stealing dance is needed — unlike the Explorer/Terminal focus actions.
The whole script is passed as a base64 `-EncodedCommand`, matching the Explorer/Terminal/Firefox focus pattern (MADR 0008): no shell quoting, no temp file, no COM/FFM code, no new dependency.

### Consequences

* Good, because it switches by the **name** the user sees in Task View, survives Windows updates (only registry keys and a shipped hotkey, no build-specific COM vtables), and adds zero dependencies.
* Good, because it stays consistent with "drive a system tool via a child process, script as base64" (MADR 0003, 0008).
* Neutral, because the hotkey walks through the intermediate desktops rather than jumping — a brief animation for a user-initiated click; the minimal-delta walk (using the current index) keeps it to the fewest steps.
* Bad, because it depends on synthetic keyboard input and the `Ctrl+Win+Arrow` shortcut staying at its default binding; a user who has remapped or disabled it would not switch.
* Bad, because it is Windows-specific; a Linux/GNOME analog (`wmctrl -s <index>` on X11, `gdbus` on Wayland) will need its own implementation behind the same seam.
* Bad, because it reads community-reverse-engineered registry layout (the GUID byte order feeding `[Guid]::new([byte[]])`), which is not a documented contract — validated live before relying on it.

## Pros and Cons of the Options

### Undocumented `IVirtualDesktopManagerInternal` COM

* Good, because it switches directly by index — no walking, no animation through intermediate desktops.
* Bad, because the interface IID and vtable layout **change between Windows builds** (10 vs 11, and across 11 feature updates); pinning them is a permanent maintenance treadmill and the exact failure mode this project avoids.
* Bad, because it is far more script (per-build GUIDs, marshalling) for a fragile result.

### Third-party helper (`VirtualDesktopAccessor.dll` / packaged exe)

* Good, because it hides the COM churn behind someone else's maintenance.
* Bad, because it adds a native binary to ship, sign, and license-check (proprietary project), for a single button.
* Bad, because it still rides the same undocumented COM underneath, so it breaks on the same Windows updates — just one layer removed.

### Focus a window on the desktop

* Good, because it reuses the exact foreground mechanism already built (MADR 0008).
* Bad, because it needs a *window* to target, not a desktop name — the user assigns a desktop, not a window, and an empty desktop has nothing to focus.
