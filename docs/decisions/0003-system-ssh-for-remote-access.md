---
status: accepted
date: 2026-07-12
decision-makers: Oliver Kopp
---

# System ssh for Remote Access

## Context and Problem Statement

ContextSwitcher runs commands on remote machines (focus a tmux window, later attach an interactive tmux session for the chat mirror).
How does the Java application talk SSH?

## Decision Drivers

* The maintainer's SSH setup (aliases, keys, agent, `ProxyJump`, `ControlMaster`) lives in `~/.ssh/config` and must keep working unchanged.
* No credential or host-key management code in the app if avoidable.
* Windows 10+ ships OpenSSH (`ssh.exe`) by default.
* The v0.2 chat mirror needs a real TTY (`ssh -t … tmux attach`), which pairs naturally with a PTY-spawned process.

## Considered Options

* Spawn system `ssh.exe` via `ProcessBuilder` (non-interactive) and pty4j (interactive)
* sshj (Java SSH library)
* JSch / Apache MINA sshd (Java SSH libraries)

## Decision Outcome

Chosen option: "Spawn system `ssh.exe`", because it reuses the user's entire OpenSSH configuration and authentication state for free, keeps zero security-sensitive code in the app, and serves both the non-interactive case (`ProcessBuilder`) and the interactive terminal case (pty4j + ConPTY) with the same mechanism.

### Consequences

* Good, because anything that works in the user's terminal works in ContextSwitcher — debugging is "copy the logged argv and run it".
* Good, because `ControlMaster` connection reuse makes repeated switch actions fast without any Java-side session pooling.
* Bad, because there is a process-spawn overhead per command (mitigated by `ControlMaster`).
* Bad, because error reporting is exit-code + stderr parsing instead of typed exceptions; every invocation is logged with full argv, exit code, and stderr to compensate.

## Pros and Cons of the Options

### sshj / JSch / MINA sshd

* Good, because in-process sessions with typed APIs.
* Bad, because they do not read `~/.ssh/config` (or only partially), so aliases, `ProxyJump`, agent and `ControlMaster` setups break.
* Bad, because the app would own key/host-key handling — avoidable attack surface and support burden.
