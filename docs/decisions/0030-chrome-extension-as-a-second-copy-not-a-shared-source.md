---
status: accepted
date: 2026-09-10
decision-makers: Oliver Kopp
---

# Chrome Extension as a Second Copy, Not a Shared Source

## Context and Problem Statement

Firefox has become unreliable against GitHub on Oliver's machine, so Chrome must be usable as the task browser.
MADR 0004 already promised this would be "the same server, repackaged extension".
It is not quite that: Chrome dropped Manifest V2, so the Chrome extension is MV3 with an event-driven service worker, and a handful of APIs the Firefox extension uses differ.
The two extensions are then ~430 lines each with maybe forty lines of real difference.
How are they kept from drifting apart?

## Decision Drivers

* The Firefox extension works and is in daily use — the port must not risk it.
* Two copies of a file that must stay in sync is the classic way to ship a bug in the one nobody looked at.
* One-man-show project: machinery only pays for itself if it is smaller than what it replaces.
* Chrome's "Load unpacked" wants a plain directory; anything shared has to be materialized into it.

## Considered Options

* Two directories, two full copies, kept diffable by convention
* One shared `background.js` with runtime branching, copied into both directories by a Gradle task
* A build step (bundler, template expansion) generating both from one source

## Decision Outcome

Chosen option: "two directories, two full copies", because the alternatives buy sync at the price of a build step between editing the extension and loading it — and the whole appeal of a local extension is that you edit a file and press reload.
The copies are kept honest by structure rather than tooling: every platform difference lives in a compat block at the top of each `background.js` (the `browser`/`chrome` namespace, `browserAction`/`action`, the window-title read), so `diff extension/firefox/background.js extension/chrome/background.js` stays short enough to read, and both files say so at the top.

The four differences that are not cosmetic are recorded in `dsn~chrome-extension-mv3~1`: the service worker's ~30 s idle death (a 20 s API-call heartbeat holds a live socket open, a 30 s alarm resurrects a killed worker), PNG icons instead of the SVG, the missing `windows.Window.title`, and a popup that reaches the worker over `runtime` messages instead of `getBackgroundPage()`.

The app side needed almost nothing: the extension dials in, so the transport is browser-agnostic and only the `Origin` allowlist grew a second prefix.
What did need configuring is the half that cannot dial in — the Windows helpers that raise, launch, and close browser windows by window class — hence `browser: firefox|chrome` in `settings.yaml` (`dsn~browser-choice~2`).
Amended 2026-09-10: that setting turned out to be the wrong *first* answer, only the right fallback.
The `hello` already names the browser, so the helpers follow whichever extension is connected and consult the setting only when none is (`dsn~drive-the-connected-browser~1`) — a Chrome extension driving the tabs while `browser:` still said `firefox` raised a leftover Firefox window instead of the Chrome one holding the tab.

### Consequences

* Good, because editing either extension is edit-and-reload, with no build step in between.
* Good, because the Firefox extension was not touched at all, so the working one cannot regress.
* Good, because Chrome's install is strictly simpler than Firefox's: Load unpacked is permanent and needs no signing.
* Bad, because a fix to shared logic must be applied twice, and nothing enforces it.
  A `ponytail:` comment marks the ceiling; the upgrade path is a shared file plus a Gradle copy once the diff stops being readable.
* Bad, because the service-worker keepalive is a documented-but-fragile Chrome behaviour (calling an API resets the idle timer). The alarm under it means the worst case is a reconnect, not a dead extension.

## Pros and Cons of the Options

### Shared `background.js` copied in by Gradle

* Good, because one file to fix.
* Bad, because loading the extension then needs a Gradle run first — the reload loop that makes local extension work pleasant is gone.
* Bad, because the runtime branching has to cover MV2-vs-MV3 lifetime, not just API names, and that is the part least amenable to an `if`.

### A bundler

* Good, because it would also give minification and a real Web Store build.
* Bad, because it adds a Node toolchain to a Java project for two files of a few hundred lines.

Revisit if a third browser appears, or if the two `background.js` files ever diverge past a readable diff.
