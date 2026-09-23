# ContextSwitcher Firefox extension

Connects to the running ContextSwitcher app (`ws://127.0.0.1:<wsPort>`) and
executes tab commands: focus-or-open a task URL on switch (into the window
the app names, when it raised one on the task's desktop), close the task's
tabs on suspend, and — for complete-control desktops — list every window's
tabs and close whole windows.
A focused or opened task tab is collected in the tab group the app names
(Firefox 139+; older versions skip the grouping).
In the other direction it reports the tab the user activates, so clicking a
task's tab selects that task in the app.
The app answers each report with the tasks that list that address; their
number appears as the badge on the toolbar icon (no badge: no task lists the
page), and clicking the icon opens a popup naming them.
A task's title there selects it and raises ContextSwitcher, the `▶` beside it
switches to the task.
The popup's *✓ Enabled* row switches the extension off (and on again): while
off, its icon is faded and it neither connects nor touches any tab.

There is a Chrome twin in `../chrome` (MADR 0030) — same protocol, Manifest V3,
and a strictly simpler install.

## Install (testing build, `.xpi`)

`gradlew packageExtension` zips this directory into
`build/distributions/contextswitcher-firefox-<version>.xpi`
(CI uploads it as the `contextswitcher-firefox-xpi` artifact, MADR 0012).
The file is **unsigned**, so release Firefox refuses a permanent install:

* Everywhere: `about:debugging#/runtime/this-firefox` → **Load Temporary
  Add-on…** → select the `.xpi` (vanishes on Firefox restart).
* Permanent: Firefox ESR / Developer Edition / Nightly with
  `xpinstall.signatures.required=false` in `about:config`, then open the
  `.xpi` via `about:addons` → gear → **Install Add-on From File…**.

## Install (development)

1. Open `about:debugging#/runtime/this-firefox` → **Load Temporary Add-on…**
   → select `manifest.json` in this directory. (Temporary add-ons vanish on
   Firefox restart; permanent install needs signing.)
2. Open the extension's options (about:addons → ContextSwitcher →
   Preferences) and enter the `wsPort` and `wsToken` values from
   `%USERPROFILE%\.contextswitcher\settings.yaml`.

The background script reconnects with exponential backoff (1 s doubling up to
30 s), so Firefox and the app may start or restart in any order.
