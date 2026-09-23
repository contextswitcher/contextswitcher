---
status: proposed
date: 2026-09-07
decision-makers: Oliver Kopp
---

# sshj for SSH on Android

## Context and Problem Statement

MADR [0003](0003-system-ssh-for-remote-access.md) spawns the system `ssh` binary; Android has none.
The Android app (MADR 0026) needs an in-process SSH client for remote commands (tmux discovery, queue send, `capture-pane` snapshots) and later a shell channel for a live terminal.
This is a *scoped exception*: the desktop keeps system ssh, 0003 stays accepted there.

## Considered Options

* sshj (Apache-2.0)
* ConnectBot sshlib (Apache-2.0, Android-native)
* Apache MINA sshd (Apache-2.0)
* JSch (mwiede fork, BSD)

## Decision Outcome

Chosen option: "sshj", because it is the maintained mainstream Java client with an explicit Android configuration, Apache-licensed (proprietary-compatible, MADR license rule), and covers both exec channels now and PTY/shell channels for the later live terminal.
Status `proposed` until a spike has run a real `tmux` command against a real remote from a device; ConnectBot's sshlib is the fallback if sshj misbehaves on Android.

### Consequences

* Good, because one `SshCommandRunner` interface with two implementations keeps all 42 call sites unchanged.
* Bad, because key management (generation, storage in Android Keystore, host-key verification UX) becomes app code — exactly what 0003 avoided on the desktop.
* Bad, because ProxyJump/ControlMaster conveniences from `~/.ssh/config` are gone on the phone; direct reachability (VPN/Tailscale) is the user's problem.
