# ContextSwitcher Chrome extension

The Manifest V3 twin of `../firefox`, for Chrome and other Chromium browsers.
It does the same things and speaks the same protocol: connects to the running
ContextSwitcher app (`ws://127.0.0.1:<wsPort>`) and executes tab commands —
focus-or-open a task URL on switch, close the task's tabs on suspend, list and
close whole windows for complete-control desktops — collects a task's tabs in
the tab group the app names, and reports the tab the user activates so clicking
a task's tab selects that task in the app.
The badge on the toolbar icon is the number of tasks listing the page in view;
clicking the icon names them.

The app needs no setting for the extension itself: it dials in and names its
browser, and the app's Windows helpers raise, capture, and close the windows of
whichever extension is connected. `browser: chrome` in `settings.yaml` decides
only which browser gets *started* when none is connected — set it if Chrome is
your task browser.

Only one extension is driven at a time (the most recent one to connect), so
keep one browser's extension in play. To park the other without uninstalling
it, click its toolbar icon and switch *✓ Enabled* to *✗ Disabled*: its icon
fades and it never connects until switched on again.

## Install

1. `chrome://extensions` → turn on **Developer mode** → **Load unpacked** →
   select this directory.
   Unlike Firefox's temporary add-ons, this survives a browser restart, and no
   signing is involved.
2. Open the extension's options (`chrome://extensions` → ContextSwitcher →
   **Details** → **Extension options**) and enter the `wsPort` and `wsToken`
   values from `%USERPROFILE%\.contextswitcher\settings.yaml`.

`gradlew packageExtension` also zips this directory into
`build/distributions/contextswitcher-chrome-<version>.zip` — the shape the
Chrome Web Store wants, not something Chrome installs directly.

## Differences from the Firefox copy

The two `background.js` files are meant to stay diffable; everything below the
compat block at the top is the same code.
What differs:

* Manifest V3: an event-driven **service worker** instead of a persistent
  background page, `action` instead of `browser_action`, and PNG icons (Chrome
  does not render an SVG icon).
* The worker is killed after ~30 s idle, which would take the WebSocket with
  it. A heartbeat calling an extension API every 20 s holds a live connection
  open, and a 30 s `chrome.alarms` wakes the worker and reconnects if it was
  killed anyway.
* Chrome's `windows.Window` carries no `title`, so the window caption the app
  matches on is read off the populated tab list's active tab.
* The popup cannot reach into the worker (no `extension.getBackgroundPage()`),
  so its state, its refresh, and its links go over `runtime` messages.

## Regenerating the icons

The source is `../firefox/icon.svg` — the same mark, rasterized:

```
for s in 16 32 48 128; do
  convert -background none -density 1200 ../firefox/icon.svg -resize ${s}x${s} icon-$s.png
done
```
