---
status: accepted
date: 2026-07-21
decision-makers: Oliver Kopp
---

# Deep-Link Registration via Per-OS Scripts and Single-Instance IPC via the Extension Server

## Context and Problem Statement

`contextswitcher://task/<id>` links in notes and browsers ([#47](https://github.com/contextswitcher/contextswitcher-private/issues/47)) must reach the app: the OS needs a registered protocol handler, and a click while the app is already running must land in that instance instead of opening a second one.
MADR 0012 deferred this to "when an installer exists" for a stable executable path — but clicking a link today yields Firefox's "unknown protocol" error, so the feature should not wait for installer machinery.

## Considered Options

For registration:

* Per-OS registration scripts shipped next to the app image (Windows `reg add` under HKCU, Linux `.desktop` + `xdg-mime`)
* jpackage `--type exe` installer with WiX (registers during install)
* Self-registration on app startup (the app writes the registry / `.desktop` entry itself)

For single-instance IPC:

* A `deeplink` message on the existing loopback WebSocket extension server (port 17872, MADR 0004)
* A separate lock-file / dedicated localhost socket protocol
* OS mechanisms (Windows named mutex + WM_COPYDATA, D-Bus activation)

## Decision Outcome

Chosen: **registration scripts in `scripts/`, packaged into the app-image zip; IPC as a one-shot `deeplink` message on the extension server**.

The scripts default to the `ContextSwitcher` app image beside them (the zip layout), so "unzip, run script once" replaces an installer; moving the image means re-running the script — an acceptable cost for testing builds, and an installer can later run the same registration during install.
Self-registration on startup was rejected: silently editing `HKCU\Software\Classes` on every launch is surprising, and dev-time Gradle launches would register a transient classpath command.

For IPC, the extension server already is a loopback singleton owned by the running instance — the issue's own sketch.
A second (deep-link) launch connects as a WebSocket client, sends `{"type":"deeplink","token":…,"url":…}`, and treats a normal close (1000) as "delivered", then exits; connection failure means "no instance running" and the process starts the app normally, handling the URL after startup.
The server's origin check loosens from "must be `moz-extension://`" to "must be `moz-extension://` **or absent**": a native local client sends no `Origin` header, while a browser page always presents its page origin and stays rejected; the token remains required for every action, so the security posture (MADR 0004) is unchanged.
A dedicated IPC channel or OS mechanisms would duplicate an already-running, already-authenticated loopback listener with platform-specific code.

### Consequences

* Good, because links work with the zip distribution now — no installer prerequisite (revises the MADR 0012 deferral).
* Good, because forwarding reuses the existing server, protocol JSON, and token: no new port, dependency, or platform code.
* Bad, because the registered absolute path goes stale when the app image moves; the script prints what it registered and is idempotent to re-run.
* Bad, because a deep-link cold start pays a full app start (JVM); acceptable — it is the "app not running yet" case only.
* Neutral, because dev-time Gradle launches can test the handler by passing the URL as a program argument without any registration.
