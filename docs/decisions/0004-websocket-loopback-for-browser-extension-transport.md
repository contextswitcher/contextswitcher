---
status: accepted
date: 2026-07-12
decision-makers: Oliver Kopp
---

# WebSocket on Loopback for Browser Extension Transport

## Context and Problem Statement

Switching a task must focus (or open) the task's URL in Firefox.
Only a WebExtension can focus an existing tab, so ContextSwitcher ships one.
How do the extension and the JavaFX app communicate?

## Decision Drivers

* Startup-order independence: ContextSwitcher may start before or after Firefox (GitHub issue [#21](https://github.com/contextswitcher/contextswitcher-private/issues/21)).
* Minimal install ceremony on machines that get reinstalled between work bursts.
* Path to other browsers (same protocol, different extension packaging).
* Single-user desktop tool: pragmatic, documented security is acceptable.

## Considered Options

* WebSocket server on `127.0.0.1` inside the app; extension connects out
* Native Messaging (Firefox spawns a host process; stdio with 4-byte-length JSON framing)

## Decision Outcome

Chosen option: "WebSocket server on `127.0.0.1`", because Native Messaging would need a relay anyway: Firefox spawns its own host process, which must then forward messages to the already-running JavaFX app over a second IPC channel — plus a registry-registered manifest per machine. The WebSocket route is one hop, zero registry setup, and the extension's reconnect-with-backoff loop gives startup-order independence for free.

Details: default port 17872 (configurable); the app generates a random token on first run (stored in `settings.yaml`); the user enters port+token once in the extension's options page; the app verifies the token in a `hello` message and checks the `Origin` header is `moz-extension://…`. Messages are JSON with correlation ids (`hello` / `focus-url` / `result`). The extension ships as Manifest V2 with a persistent background script (Firefox supports MV2; MV3 event pages can idle-kill the WebSocket — migration is future work).

### Consequences

* Good, because no registry keys, no manifest paths, no extra host executable.
* Good, because Chrome support later = same server, repackaged extension.
* Bad, because the app holds a listening loopback socket; any local process that knows the token could command tab focus. Accepted for a single-user tool; documented here.
* Bad, because port conflicts are possible (configurable port mitigates).

## Pros and Cons of the Options

### Native Messaging

* Good, because no listening socket and browser-managed process lifetime.
* Good, because the browser can start the host on demand.
* Bad, because host process ≠ the running app — a relay plus second IPC channel is still required.
* Bad, because per-machine registry manifest registration breaks on moved installs.

Revisit if Firefox ever forces MV3-without-persistent-scripts or a zero-listening-socket posture is wanted.
