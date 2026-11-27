---
status: accepted
date: 2026-11-26
decision-makers: Oliver Kopp
---

# Global hotkey through a persistent PowerShell listener

## Context and Problem Statement

In a Microsoft Teams meeting, one key press should switch ContextSwitcher to the task the meeting belongs to, opening its OneNote page, folders and the rest (`req~switch-by-window~1`).
Teams cannot get a title-bar button, so the trigger is a global hotkey, and the task is found by the title of the window in front.
The app has no hotkey infrastructure yet.
How does a JavaFX app on Windows register a system-wide hotkey and learn which window was in front when it was pressed?

`RegisterHotKey` is the Win32 call for it; its `WM_HOTKEY` messages arrive on the message queue of the thread that registered it, so that thread has to run a message loop for as long as the hotkey lives.

## Decision Drivers

* Consistent with how the app already talks to Win32: every desktop integration is a `powershell` script with inline C# (MADR 0008), among them the persistent virtual-desktop watcher that already reads `GetForegroundWindow` (`dsn~active-desktop-filter~6`, `dsn~desktop-change-foreground-log~1`).
* No native code inside the JVM process that could crash it, and no new dependency.
* The hotkey must be freed when the app dies, or the next start finds it taken by its own predecessor.
* A refused key (taken by Windows, as `Win+F` is) must be reported, never silently missing.
* Portable code base: off Windows the feature is absent, not a build or start failure.

## Considered Options

* A persistent `powershell` process running `RegisterHotKey` and a `GetMessage` loop in inline C#
* JNA (already on the classpath, transitively through pty4j) calling `user32` from a dedicated Java thread
* The Foreign Function & Memory API (Java 22+) calling `user32` from a dedicated Java thread
* A JavaFX key handler (`KeyCombination` accelerators)

## Decision Outcome

Chosen option: **a persistent `powershell` listener**, because it is the pattern the desktop watcher already proves out — one long-running process, a line protocol on stdout read by a daemon thread — and it keeps every Win32 call in the scripts where the others live.
The script registers the key with `MOD_NOREPEAT`, prints `registered` or `failed:<error>`, and per press one line with the foreground window's pid, process name and title (`WindowsHotkey`, `dsn~switch-by-window-hotkey~1`).
Its stdin is the lifeline: Java keeps the pipe open, a thread in the script blocks on it, and end-of-file — the app closing it, or the JVM dying — posts `WM_QUIT`, which unregisters the key.

### Consequences

* Good, because nothing native runs in the JVM; a broken script costs the hotkey, not the app.
* Good, because the window description reuses the watcher's way of reading the foreground window, and it is easy to try out by hand (`powershell` plus a simulated key press).
* Good, because the process that receives the press holds the foreground right and can hand it to ContextSwitcher (`AllowSetForegroundWindow`), so raising the app works.
* Neutral, because the default key is not the one first asked for: `Win+F` is Feedback Hub's on Windows 11 and `RegisterHotKey` refuses it (1409), so the default is `Win+Shift+F`, configurable as `switchByWindowHotkey`.
* Bad, because one more `powershell` process runs for the app's lifetime, and the hotkey is live only a second or two after start (process start plus the C# compile).
* Bad, because a changed `switchByWindowHotkey` takes effect only on the next start.

### Confirmation

`WindowsHotkeyTest` pins the script's protocol and quit path; the script was run on Windows 11 build 26300 with a `keybd_event`-simulated `Win+Shift+F`, printing the window in front, skipping ContextSwitcher's own window, and ending on stdin end-of-file.

## Pros and Cons of the Options

### A persistent `powershell` process

* Good, because it matches MADR 0008 and the desktop watcher.
* Good, because a crash or hang stays outside the JVM, and the stdin lifeline frees the key whenever the JVM goes away.
* Bad, because of the extra process and the start-up delay.

### JNA from a dedicated Java thread

* Good, because no extra process and the key is live at once.
* Neutral, because JNA is already present, but only as pty4j's transitive dependency; using it directly makes it a dependency of the app's own code.
* Good, because the key dies with the JVM's thread, with no lifeline needed.
* Bad, because a wrong structure layout or a stray call crashes the whole app, and it is the only place outside pty4j that would call Win32 from Java.

### The Foreign Function & Memory API

* Good, because it is part of the JDK — no dependency at all.
* Bad, because the same in-process crash risk as JNA, plus `--enable-native-access` for the packaged app.
* Bad, because a second way of calling Win32 next to the scripts.

### A JavaFX key handler

* Good, because it is trivial.
* Bad, because it only fires while ContextSwitcher has the focus — the opposite of the use case, where Teams has it.

## More Information

Probe on Windows 11 build 26300 (2026-11-26), `RegisterHotKey` with `MOD_NOREPEAT`: `Win+F`, `Win+Alt+F`, `Ctrl+Win+F` refused with 1409 (`ERROR_HOTKEY_ALREADY_REGISTERED`); `Win+Shift+F`, `Ctrl+Shift+Win+F`, `Alt+Shift+Win+F` registered.
`Ctrl+Alt` combinations are avoided: on a German keyboard they are AltGr.
