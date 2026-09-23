---
status: accepted
date: 2026-07-23
decision-makers: Oliver Kopp
supersedes: 0013-virtual-desktop-focus-via-keyboard-cycle
---

# Jump Directly to a Virtual Desktop via `IVirtualDesktopManagerInternal`, Falling Back to the Hotkey Walk

## Context and Problem Statement

MADR 0013 switches to a named virtual desktop by resolving its index from the registry and then injecting `Ctrl+Win+Left/Right` the index difference of times.
It works, but it *walks*: switching from desktop 1 to desktop 6 animates through 2, 3, 4 and 5, which reads as "the app is scrolling through my desktops" rather than "the app jumped to my desktop".
0013 called this a neutral consequence; in use it is the most visible thing the button does.

Windows still exposes no supported API to activate a desktop, so the question is not "use the API instead" but: which unsupported mechanism, and what happens when it stops working?

## Considered Options

* **Keyboard walk only** — the status quo from MADR 0013.
* **`IVirtualDesktopManagerInternal::SwitchDesktop`, with the walk as fallback** — try the interface the shell itself uses; when it is not the layout we know, walk as before.
* **`IVirtualDesktopManagerInternal` only** — drop the walk once the direct switch works.
* **Documented `IVirtualDesktopManager::MoveWindowToDesktop` + `SetForegroundWindow`** — move a window we own to the target desktop, then foreground it and let Windows follow.
* **A third-party helper** (`VirtualDesktopAccessor.dll`, packaged `VirtualDesktop.exe`) — as in 0013.

## Decision Outcome

Chosen option: **`IVirtualDesktopManagerInternal::SwitchDesktop`, with the keyboard walk as fallback**, because it removes the walk on the builds people actually run while keeping the old mechanism as the floor — the worst case of the new code is the previous behaviour.

`WindowsVirtualDesktopFocus` resolves the target desktop's **GUID** (not only its index) from the same registry data 0013 already read, then asks the immersive shell's `IServiceProvider` (CLSID `C2F03A33-…`) for service `C5E0CDCA-…` as `IVirtualDesktopManagerInternal`, calls `FindDesktop(guid)` and `SwitchDesktop(desktop)`.
All of it stays inside the one base64 `-EncodedCommand` PowerShell script, so there is still no native dependency, no FFM code, and one child process per click.

0013 rejected this interface because its IID and vtable layout change between Windows builds and pinning them is a maintenance treadmill.
That reasoning is sound and unchanged — what changes is that **the treadmill is now optional**.
Microsoft moves the IID whenever it reorders the vtable (that is what the IID is for), so a build we do not know answers `QueryService` with `E_NOINTERFACE`: a clean, detectable miss, never a dispatch into the wrong slot.
The script therefore declares exactly one layout — Windows 11 24H2, IID `53F5CA0B-158F-4124-900C-057158060B27`, slots through `FindDesktop` — and treats every failure as "walk instead".
Nobody has to chase a Windows release for the button to keep working; chasing one only makes it prettier.

Only the two methods that get called carry real signatures; the preceding slots are position-holders, since a COM method is bound by its vtable index and not by its name.
The declaration is checked by a unit test asserting `SwitchDesktop` at slot 6 and `FindDesktop` at slot 11, so a well-meant edit to the placeholders cannot silently call a different shell function.

A successful switch reports which route ran (`focused-direct:` / `focused-walk:` on stdout, surfacing as a `(hotkey walk)` suffix in the status bar), so "Microsoft moved the interface" is visible as a message rather than only as a slower animation.

### Consequences

* Good, because the common case jumps straight to the desktop — no animation through intermediate desktops, no dependence on `Ctrl+Win+Arrow` still being bound.
* Good, because the failure mode is the old, proven behaviour rather than a broken button, and it is self-announcing.
* Good, because it adds no dependency and no native binary: the COM interop is `Add-Type`d C# inside the script that was already being run.
* Neutral, because the walk stays in the codebase forever, i.e. the mechanism 0013 built is retained rather than replaced.
* Bad, because it takes on undocumented COM after all, including a GUID and a method order copied from community reverse engineering (`MScholtes/VirtualDesktop`) that no contract obliges Microsoft to keep.
* Bad, because a new Windows build silently degrades to walking until someone adds the new layout — mitigated by the status-bar route suffix, not eliminated.
* Bad, because it is Windows-specific, like everything else in this area; the Linux/GNOME analog is still future work.

## Pros and Cons of the Options

### Keyboard walk only

* Good, because it depends on nothing but registry values and a shipped hotkey.
* Bad, because it walks — the problem being solved.

### `IVirtualDesktopManagerInternal` only (no fallback)

* Good, because it deletes the walk and with it a second code path to reason about.
* Bad, because the button breaks outright on any build whose interface moved, turning a cosmetic issue into a broken feature. The fallback costs one `if`.

### Documented `IVirtualDesktopManager::MoveWindowToDesktop` + `SetForegroundWindow`

* Good, because `IVirtualDesktopManager` is genuinely documented and its IID is stable, so this would never need per-build maintenance.
* Bad, because it switches desktops as a *side effect* of foregrounding a window rather than by asking: it needs a top-level window owned by our own process moved to the target desktop, and `SetForegroundWindow` is subject to the foreground-lock rules that MADR 0008 already fights elsewhere.
* Bad, because that window is visible — a flash of a helper window on the target desktop — where `SwitchDesktop` is silent.
* Worth revisiting if the internal interface ever starts failing on builds we care about and maintaining layouts becomes tiresome.

### Third-party helper

* Unchanged from MADR 0013: a native binary to ship, sign and license-check, riding the same undocumented COM one layer down.
