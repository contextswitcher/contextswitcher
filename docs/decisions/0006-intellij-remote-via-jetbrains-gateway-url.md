---
status: accepted
date: 2026-07-12
decision-makers: Oliver Kopp
---

# IntelliJ Remote via JetBrains Gateway URL

## Context and Problem Statement

Switching to a task must open the task's project folder in IntelliJ running against a remote machine (JetBrains Remote Development).
How does ContextSwitcher trigger this?

## Considered Options

* Launch a `jetbrains-gateway://connect#…` URL (host, projectPath, optionally IDE version)
* JetBrains Toolbox CLI
* Run the full IDE on the remote machine, display via X forwarding / RDP

## Decision Outcome

Chosen option: "`jetbrains-gateway://` URL", because it is the documented client-side entry point into JetBrains Remote Development, requires no extra daemon, and launching a URL is trivial from JavaFX (`HostServices.showDocument`).

The exact URL parameter set is validated in a timeboxed spike (M1 work package); its outcome is recorded as a `dsn~` item and, if needed, an amendment here.

**Amendment (2026-07-13, spike [#28](https://github.com/contextswitcher/contextswitcher-private/issues/28) outcome):** the parameter set matches the format JetBrains documents for generated Gateway links ([Connect and work with JetBrains Gateway](https://www.jetbrains.com/help/idea/remote-development-a.html)): `jetbrains-gateway://connect#host=…&port=…&user=…&type=ssh&projectPath=…&idePath=…&deploy=false`. ContextSwitcher emits `host`, optional `user` (split from a `user@host` task host), `type=ssh`, `projectPath`, and — only when the task pins an IDE build — `idePath` with `deploy=false`; without `idePath`, Gateway prompts for the IDE on first connect. Parameters live in the URL fragment and are percent-encoded (spaces as `%20`). See `dsn~gateway-url-action~2`.

**Amendment (2026-07-13, manual E2E):** `port` is **not** optional in practice — Gateway 2026.1 rejects a URL without it (`Invalid ssh link parameters: doesn't contain port` in idea.log, shown as "Cannot Connect — There was an error in the connection provider"). ContextSwitcher therefore always emits `port=22`; non-standard ssh ports are out of v0.1 scope. The same validation also requires `idePath` (`doesn't contain idePath`, shown as "Cannot Execute Command — No connection handle was returned") — the documented "Gateway asks for the IDE" flow does not exist for links in 2026.1. When the task does not pin `intellij.ide`, ContextSwitcher discovers the newest backend installed on the remote (`~/.cache/JetBrains/RemoteDev/dist/`) via ssh and pins that. See `dsn~gateway-url-action~6`.

### Consequences

* Good, because ContextSwitcher stays decoupled from JetBrains internals — it only builds a URL.
* Bad, because JetBrains Gateway may open a **new** window instead of focusing an already-open project. Accepted limitation for v0.1.
* Bad, because URL parameters are not a stable public contract; a JetBrains Gateway update may require adjusting the spike results.

## Pros and Cons of the Options

### Toolbox CLI

* Good, because scriptable.
* Bad, because it adds a dependency on Toolbox being installed and its CLI being on PATH; still no focus-existing-window guarantee.

### IDE on remote via X/RDP

* Good, because true single-instance focus semantics.
* Bad, because poor experience on Windows clients and contrary to the JetBrains Remote Development direction the maintainer wants to try.
