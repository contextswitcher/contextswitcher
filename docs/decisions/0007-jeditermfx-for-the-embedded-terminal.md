---
status: accepted
date: 2026-07-14
decision-makers: Oliver Kopp
---

# JediTermFX for the Embedded Terminal (Claude Chat Mirror)

## Context and Problem Statement

v0.2 embeds a terminal pane in the JavaFX app that mirrors the remote tmux window where a Claude session runs (`ssh -t <host> tmux new -A -s <session>`), with automatic reconnection.
Which terminal widget?

Status was `proposed`; the M2 spike ([#34](https://github.com/contextswitcher/contextswitcher-private/issues/34), 2026-07-14) accepted it — outcome below.

## Decision Drivers

* Pure-JavaFX integration preferred (no Swing interop quirks: focus, IME, HiDPI).
* Proven emulator core (VT100/xterm correctness is hard).
* License compatible with a proprietary application.

## Considered Options

* JediTermFX (`com.techsenger.jeditermfx`) — JavaFX port of JetBrains JediTerm
* JetBrains JediTerm (Swing) embedded via `SwingNode`
* xterm.js in a JavaFX WebView

## Decision Outcome

Chosen option: "JediTermFX", because it inherits JediTerm's emulator core (used by all JetBrains IDEs) while being native JavaFX, is dual-licensed LGPLv3/Apache-2.0 (Apache option fits a proprietary app), is on Maven Central, and works with pty4j (ConPTY on Windows) for spawning `ssh.exe`.

Known risk: small project (~70 commits, ports upstream JediTerm commit `8366f2b`, limited maintenance). Mitigations: the Apache license permits forking/patching, and the `TtyConnector` API parallels JediTerm, so the fallback swap is cheap.

Fallback if the spike fails: JetBrains JediTerm via `SwingNode` (battle-tested core; accept Swing interop quirks).

**Spike outcome (2026-07-14, [#34](https://github.com/contextswitcher/contextswitcher-private/issues/34), `TerminalSpike` in `com.contextswitcher.spike`):** accepted.
`JediTermFxWidget` + pty4j 0.13.10 (ConPTY) running `ssh -t koppor@devbox tmux attach` under the classpath-loaded JavaFX 26 / Java 25: the Claude TUI renders, typing reaches the remote, window resize propagates to tmux.
Findings for the real terminal pane ([#35](https://github.com/contextswitcher/contextswitcher-private/issues/35)):
the provider default is black-on-white — override `getDefaultBackground`/`getDefaultForeground` (spike's `DarkSettings`);
`Ctrl+A` does not reach the shell (key-encoding issue in the port — investigate/patch);
the mouse wheel scrolls the local scrollback unless the remote application requests mouse reporting — with `tmux set -g mouse on` the wheel scrolls inside tmux (document as recommended remote config);
copy/paste is `Ctrl+Shift+C`/`Ctrl+Shift+V` by default (`Ctrl+C`/`V` stay terminal signals).

### Consequences

* Good, because single-toolkit UI; terminal pane styles and focuses like every other node.
* Good, because pty4j + system `ssh.exe` keeps the [0003](0003-system-ssh-for-remote-access.md) approach for the interactive case.
* Bad, because we may inherit unfixed upstream-lag bugs and must be prepared to patch locally.
