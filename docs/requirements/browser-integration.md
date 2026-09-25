# Browser Integration

Related idea issues: [#21 Start after the applications](https://github.com/contextswitcher/contextswitcher-private/issues/21), [#22 Focus without closing](https://github.com/contextswitcher/contextswitcher-private/issues/22).

Design decision: [0004 — WebSocket on loopback for browser extension transport](../decisions/0004-websocket-loopback-for-browser-extension-transport.md).

## Requirements

### Focus browser tab
`req~focus-browser-tab~2`

Activating a task with a `browser` section focuses the existing Firefox tab showing each of the task's URLs (raising its window); where no such tab exists, the URL is opened in a new tab.
The first configured URL is the one left focused.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Startup-order independence
`req~extension-reconnect~1`

Browser and ContextSwitcher may be started in any order and restarted independently; the connection between extension and application re-establishes itself automatically.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Browser tab selects task
`req~browser-tab-selects-task~2`

Activating a Firefox tab whose address belongs to a task — one of the task's `browser.urls`, or a page under it — selects that task in ContextSwitcher, the reverse direction of `req~focus-browser-tab~2`.
For a task that is not suspended, that is all that happens: the application is not raised and no switch is run, since the user is working in the browser and the selection is only there when they look back at the app.
A **suspended** task is resumed as well, exactly as its row's play button resumes it (`req~task-suspend-resume~2`): its tmux window is recreated and its Claude session resumed, so opening the task's pull request lands on the same live context a running task offers instead of a dead row.
A tab belonging to no task leaves the selection alone.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Task tabs in a tab group
`req~browser-tab-group~3`

Every tab ContextSwitcher focuses or opens for a task is collected in a Firefox tab group of its own, so a task's pages sit together in the tab bar instead of scattered between unrelated tabs.
The group is named after the task's first pull request, merge request or issue (`#17148`, `!42`), so it matches what the user already calls the task; a task with none falls back to its tmux window number, and one running no remote tmux window either to `l:<task id>`.
Inside the group the tabs stand in the order the task lists its URLs, and only the first URL is activated — the rest open behind it, so a switch never pulls the user away from the task's primary page.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### No second tab for an open page
`req~browser-no-duplicate-tab~1`

Opening a page in a new tab while another tab already shows exactly that address closes the new tab again and focuses the existing one instead, so the existing tab — the one already sitting in its task's tab group — is not joined by a duplicate.
A tab opened in the background is closed without pulling the user to the existing one.
A tab that navigates to such a page after its first page is left alone, since closing it would lose its history.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Tab shows how many tasks list it
`req~browser-tab-context-count~1`

The extension's toolbar icon tells, for the page in view, how many tasks list its address — the number itself, not merely "some task": the same page is routinely a URL of several tasks, and which of them is the one worth switching to is a question the count is the first half of.
A page no task lists carries no number.
Opening the icon's popup re-asks, so a task edited in the application does not leave a stale number behind.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Tab names its tasks and acts on one
`req~browser-tab-task-popup~1`

The number the icon carries raises the question the icon must also answer: *which* tasks.
Its popup names them, best match first, and each is actionable without leaving the browser — one click selects the task and brings ContextSwitcher to the front, a second control switches to it outright, the two things the task's row in the application offers.
A page no task lists says so; an application that is not running says that instead of a stale list.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Connection is visible
`req~extension-connection-visible~1`

The application shows at a glance whether a browser extension is connected, and which browser it belongs to.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Extension state is visible on the toolbar icon
`req~extension-state-visible~1`

The mirror image of `req~extension-connection-visible~1`: the extension's toolbar icon says whether the extension is talking to the application, and when it is not, whether that is because nothing is configured yet or because the connection does not come up.
The distinction is the one the user can act on — the first is answered in the options page, the second by starting the application (or by checking the token it rejects).
A silent extension is the failure this exists for: a temporary add-on loses its `browser.storage.local` across a Firefox restart, and a token that quietly evaporated looks exactly like an application that is not listening.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Extension can be switched off
`req~extension-can-be-disabled~1`

The extension can be switched off and on again from its toolbar popup, the way Tampermonkey's popup does it, without uninstalling it or losing its token.
Switched off, it leaves the browser alone: no connection to the application, no tab reports, no tabs closed as duplicates — which is what parks the second of two installed browser extensions, or a browser that should not take part for a while.
The toolbar icon shows that it is off, so a silent extension is never mistaken for a broken one.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Extension authentication
`req~extension-token-auth~1`

Only a client presenting the user's configured secret token (entered once in the extension's options page) may issue or receive tab commands.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Browser choice
`req~browser-choice~2`

ContextSwitcher drives Firefox or Chrome.
The extension needs no configuration for this: it connects out to the application and names itself, so either browser's extension — or both — may be installed, and the one whose extension is connected is the one the application drives, windows included.
The `browser` key in `settings.yaml` (`firefox`/`chrome`, unset or unknown meaning Firefox) is what decides when **no** extension is connected — which is exactly the case that has to start a browser rather than talk to one.
The setting must not win over a live connection: an extension executing a tab command is by definition the browser holding that tab, and raising the window of some other browser instead is how the user ends up looking at a leftover window of the browser they stopped using.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

### Complete-control desktop
`req~complete-control-desktop~1`

A category whose `desktop:` uses the nested form with the flag set — `desktop: {name: <desktop>, completeControl: true}` (the plain scalar form stays valid and means no complete control) — runs its virtual desktop under complete control:
suspending (or completing) a task of the category stores the tab URLs of every Firefox window sitting on that desktop into the task's file and closes those windows, so the desktop is clean when the user later switches to it manually.
Windows shown on **all** desktops are exempt from the capture.
Resuming the task reopens the stored URLs (and clears them from the file); a stored URL whose tab is still open is kept — focused, not closed and reopened.

Tags: windows, linux

Covers:
- feat~browser-context~1

Needs: dsn

## Design

### Extension server and protocol
`dsn~extension-server-protocol~4`

`ExtensionServer` runs a WebSocket server bound to `127.0.0.1` on the configured port. Messages are JSON: extension sends `{"type":"hello","token":…,"client":…}` after connecting; the app sends `{"type":"focus-url","id":<uuid>,"url":…,"openIfMissing":true}`, `{"type":"close-url","id":<uuid>,"url":…}`, `{"type":"list-tabs","id":<uuid>}`, `{"type":"active-tab","id":<uuid>}` (`dsn~browser-teardown-quiet-tabs~1`), or `{"type":"close-window","id":<uuid>,"windowId":…}` — `focus-url` optionally carries `"windowTitle":…`, the caption of the window the tab is to end up in — created there when the URL has no tab, moved there when it has one elsewhere (`dsn~pr-open-on-category-desktop~3`); the extension answers `{"type":"result","id":…,"ok":…,"detail":…}` — for `list-tabs` additionally carrying `windows`, an array of `{id, title, urls}` (one entry per normal browser window; `title` is the caption-forming active-tab title, `id` is valid for a following `close-window`). Requests correlate by `id` and time out after 5 seconds; no connected extension fails a request immediately.
The server doubles as the single-instance IPC for deep links (`dsn~deep-link-forward~1`): a second app instance sends one `{"type":"deeplink","token":…,"url":…}` message instead of `hello`; the server validates the token and acknowledges by closing the connection normally (1000).

Tags: windows, linux

Covers:
- req~focus-browser-tab~2
- req~extension-token-auth~1
- req~task-suspend-resume~2
- req~complete-control-desktop~1
- req~deep-link-url~1

Needs: impl, utest

### Token and origin check
`dsn~extension-origin-check~3`

The server accepts a connection only if its `Origin` header starts with `moz-extension://` or `chrome-extension://` (a browser extension) or is absent entirely (a plain local client — the deep-link forwarder; a browser **page** always presents its page origin and is thus still rejected), and every action additionally requires the configured `wsToken` (`hello` for the extension, per-message for `deeplink`). Non-conforming connections are closed.

Tags: windows, linux

Covers:
- req~extension-token-auth~1

Needs: impl, utest

### Firefox extension
`dsn~firefox-extension-mv2~4`

The extension (Manifest V2, persistent background script, `tabs` permission) connects to `ws://127.0.0.1:<port>` with exponential-backoff reconnection; while no token is configured it does not connect at all (the app would only reject it). On `focus-url` it queries all tabs for an exact URL match, and failing that a tab under the same URL (`isSameOrSubUrl`: the requested URL followed by `/`, `#`, or `?` — the boundary stops a longer sibling like `.../pull/157850` matching `.../pull/15785`), so a GitHub PR already open on a `/files` or `/commits` sub-path is focused rather than duplicated; it activates the tab and focuses its window, and only when no tab matches at all does it create one (`openIfMissing`) — in the window whose title matches the request's `windowTitle` by containment when one is given and matches, else in Firefox's most recent window. A focus reply carries the tab's `title` so the app can raise that exact window past the OS foreground lock (many Firefox windows share one process, so only a title match picks the right one); a newly created tab is awaited (bounded) until its title settles and returns that, so its window is raised too. On `close-url` it closes all **exactly** matching tabs (none is still a success — the goal state is "no such tab"); close stays exact so a sub-path tab is never collateral. On `list-tabs` it reports every normal window's id, title, and tab URLs (`windows.getAll({populate: true})`); on `close-window` it closes the window by that id with all its tabs. Each command reports its outcome in a `result` message. Port and token are configured in an options page (`browser.storage.local`); saving (re)connects immediately with the new values.

Tags: windows, linux

Covers:
- req~focus-browser-tab~2
- req~extension-reconnect~1
- req~task-suspend-resume~2
- req~complete-control-desktop~1

Needs: impl

### Chrome extension
`dsn~chrome-extension-mv3~1`

`extension/chrome/` is the Manifest V3 twin of `dsn~firefox-extension-mv2~4`: the same protocol, the same commands, the same reconnect loop, and — below a compat block at the top of each file — deliberately the same code, kept as two copies rather than one shared source, since Chrome dropped MV2 and a file serving both would be more branching than logic.
Four things genuinely differ.
The background script is an **event-driven service worker**, which Chrome kills after roughly 30 s idle and which would take the WebSocket with it: a heartbeat calling an extension API every 20 s holds a live connection open (calling any API resets the idle timer, and the application sends nothing periodic to lean on), and a 30 s `chrome.alarms` wakes the worker and reconnects if it was killed anyway.
`browser_action` is `action`, and the icon is a PNG set rasterized from the same `icon.svg` — Chrome renders no SVG icon.
Chrome's `windows.Window` carries no `title`, so the caption the application matches windows by is read off the populated tab list's active tab, which is what the caption is made of either way.
The popup cannot reach into the worker (MV3 has no `extension.getBackgroundPage()`), so its state, its refresh on a late `tab-count`, and its `contextswitcher://` links all go over `runtime` messages; a message arriving while the worker is asleep races its start and reads as "Starting up — reopen this popup" rather than as an empty task list.
Chrome installs the directory itself (`chrome://extensions` → Developer mode → Load unpacked, permanent and unsigned), so the packaged `.zip` is only for handing the extension around.

Tags: windows, linux

Covers:
- req~focus-browser-tab~2
- req~extension-reconnect~1
- req~browser-tab-selects-task~2
- req~browser-tab-context-count~1
- req~browser-tab-task-popup~1
- req~browser-tab-group~3
- req~task-suspend-resume~2
- req~complete-control-desktop~1
- req~browser-choice~2

Needs: impl

### Browser choice
`dsn~browser-choice~2`

`Browser` (`firefox`/`chrome`) is a `settings.yaml` key parsed by `AppSettings` and offered as a drop-down by `SettingsCatalog`; an absent or unknown value reads as Firefox.
It is the *fallback* — `dsn~drive-the-connected-browser~1` decides what is actually driven.
It carries the four things the native side needs and the extension cannot supply: the Win32 window class, the process image name, the executable, and the new-window flag.
`Main` holds it in a volatile field refreshed on a settings save, like `fallbackDesktop`, and hands it to `BrowserWindowFocus` and `BrowserDesktopWindows`, whose PowerShell templates carry `__CLASS__`/`__PROCESS__` placeholders instead of a hard-coded `MozillaWindowClass`; the status-bar wording follows the choice too.
The class name alone is **not** the browser: `Chrome_WidgetWin_1` is every Chromium-based window on the machine — VS Code, Slack, any Electron app — and a complete-control suspend closes what it captures, so a window counts only when its class *and* its owning process name match.
The check is applied to Firefox as well, where it costs nothing and rules out Thunderbird, which shares `MozillaWindowClass`.

Tags: windows, linux

Covers:
- req~browser-choice~2

Needs: impl, utest

### Drive the browser that is connected
`dsn~drive-the-connected-browser~1`

`ExtensionServer` keeps every authenticated connection in a map from socket to the `client` name its `hello` carried, next to the single `client` socket that a later `hello` takes over.
`connectedBrowser()` reads that name for the current client through `Browser.named` — the strict lookup, which answers null for a name it does not know rather than falling back to Firefox the way the tolerant `Browser.of` does for a hand-edited settings file: on this side, not knowing must stay not knowing so the configured value takes over.
`Main.drivenBrowser()` is `connectedBrowser()` falling back to the setting, and it is what `BrowserWindowFocus`, `BrowserDesktopWindows`, and the status-bar wording are built from — read per call, since the connection comes and goes.

Two browsers may hold an authenticated socket at once, so a close hands the client role back rather than clearing it: `onClose` drops the socket from the map and, when it was the client, promotes any connection that is still open.
Without that, a second extension connecting and then leaving took the first one — still there, still able to execute — down with it, and the application reported no extension at all.
The pending requests of the closed socket fail either way; they were sent to it.

Tags: windows, linux

Covers:
- req~browser-choice~2

Needs: impl, utest

### Prefer the chosen browser
`dsn~prefer-chosen-browser~1`

With both extensions connected, the last `hello` used to take the client role, so which browser drove depended on which one reconnected last.
Right-clicking the toolbar globe offers Firefox and Chrome as radio items, the chosen one selected and a browser without a connected extension marked `(not connected)`; `ExtensionServer.connectedBrowsers()` answers which.
Choosing one saves it as `browser` in `settings.yaml` (`YamlPatch`, like the tag palette) and hands it to `ExtensionServer.prefer`, as startup and a settings save do too.
While the preferred browser's extension is connected, it holds the client role: a `hello` from the other browser does not take it, `prefer` promotes the open socket of the chosen browser at once, and a close hands the role to the chosen browser first.
While it is not connected, the other browser's extension drives as before (`dsn~drive-the-connected-browser~1`), and the setting still decides what is launched.
One port serves both browsers, so no second port is needed to switch.

Tags: windows, linux

Covers:
- req~browser-choice~2

Needs: impl, utest

### Extension connection indicator
`dsn~extension-connection-indicator~2`

`ExtensionServer.setConnectionHandler` reports the current `connectedBrowser()` on registration and again on every `hello` that takes the client role and every close that gives it up, so the indicator is right whichever of browser and application started first.
`MainWindow.showBrowserExtension` renders it left of the settings gear as a globe glyph — struck through (`web-off`) while nothing is connected — with the browser's name in the tooltip.
It is a button, and its click is a play narrowed to the browser section: `Main.switchTo` with `only = {browser}` runs `BrowserFocusAction` for the selected task and nothing else, with the same status-bar chips a play shows — the one-click "bring this task's pages up" that switching everything else was too much for.
The task is the one the list is on (`previewedTask`, re-read from disk like play does); with no task selected the status bar says so rather than the click doing nothing.
Colour is deliberately not used; the whole toolbar is monochrome, and the two glyphs differ in shape.

Tags: windows, linux

Covers:
- req~extension-connection-visible~1
- req~focus-browser-tab~2

Needs: impl

### Extension state icon
`dsn~extension-state-icon~3`

One `setState()` in each extension's `background.js` paints the toolbar icon in one of three colours from the Paul Tol vibrant palette `icon.svg` already uses and puts the same state into the icon's tooltip (`setTitle`): red `#CC3311` "no token configured", orange `#EE7733` "not connected to the app", grey `#BBBBBB` "connected".
While connected, `showTabCount` paints a tab whose page some task lists blue `#0077BB` instead, so the colour alone tells a page with something to find from one without — the badge number is small, and grey against blue reads at a glance.
Orange is deliberately every reason a connection does not come up at once — a stopped application, a token the server rejects (`dsn~extension-origin-check~3`), a refused origin all close the socket identically, and from the extension's side they are indistinguishable, so a fourth state would promise a distinction it cannot make.
It is called from the four places the state changes: after the settings are read at startup (no token, or about to dial), on the socket's `open`, on its `close` — which the WebSocket specification guarantees follows an `error`, so there is no separate error handler — and, through the reconnect it already triggers, from the options-saved storage listener.
The popup says the same three states in words below the task list, so a click explains the colour rather than only showing it; the "ContextSwitcher is not running" note it used to carry is now that line.
An **Options** button beside that line opens the options page (`runtime.openOptionsPage()`), since the red and orange states are the ones a token typed in there fixes.
The badge is deliberately left alone: it carries the per-tab task count (`dsn~browser-tab-context-count~2`) and is invisible at zero, which is exactly when the state matters most.
The state tooltip is set without a `tabId`, so while connected a tab the application has answered a count for keeps the per-tab wording of that count, which says more about that tab.
Every transition repaints the per-tab icons in the new state's colour, or a tab would keep a colour from a state long gone; leaving `connected` also sweeps the other per-tab overrides away — badge text cleared, tooltip set to the state text — since a count nobody is left to refresh is a lie about the tab.
`setState` returns early when the state is unchanged, so the sweep runs on the transition and not on every reconnect attempt behind it.
Firefox swaps in copies of `icon.svg` — the same three circles with a different fill — via `setIcon({path})`, keeping the "no generator script, no rasters to keep in sync" of `dsn~browser-tab-context-count~2`.
Chrome renders no SVG, and a colour variant of its PNG set would be eight more binaries to keep in sync, so it draws the three circles into an `OffscreenCanvas` and passes `setIcon({imageData})` — which an MV3 service worker can do, and which is what `AppIcon` already does on the application side for the same reason.
This is the mirror image of `dsn~extension-connection-indicator~2`, which is the *application* showing whether an extension is connected; neither is a duplicate of the other, and the two together say which end is missing.

Tags: windows, linux

Covers:
- req~extension-state-visible~1

Needs: impl

### Extension enable toggle
`dsn~extension-enable-toggle~1`

An `enabled` flag (default true) sits next to port and token in `storage.local`, so it survives a browser restart and the storage listener that already reconnects on an options save also reacts to it.
The popup's first row below the task list shows it as "✓ Enabled" / "✗ Disabled" and flips it on a click, then closes — the icon changing is the feedback.
`connect()` checks the flag before the token and, when off, paints a fourth icon state `disabled` and returns without dialing or scheduling a retry; switching back on reconnects through the storage listener.
The socket's `close` handler reads the flag too, so a close the switch caused paints `disabled` rather than orange "not connected".
The flag is read asynchronously, so every `connect()` and every storage change bumps a counter, and a `connect()` that finds it moved after the read gives up instead of dialing — otherwise parallel start-up calls, or the switch landing mid-read, leave a socket open that the switch never closes.
The icon is the grey rings faded to 40 % opacity, like a disabled toolbar button — Firefox via `icon-disabled.svg`, Chrome by fading the drawn canvas — and the popup's state line reads "Disabled".
`closeIfDuplicate` returns early while disabled; tab reports need an open socket and stop by themselves.

Tags: windows, linux

Covers:
- req~extension-can-be-disabled~1

Needs: impl

### Browser focus action
`dsn~browser-focus-action~4`

`BrowserFocusAction` submits a `focus-url` request for every one of the task's browser URLs to the extension server, in the order the task lists them and with only the first one in the foreground (`dsn~browser-tab-group~3`), and maps the correlated results (or timeout / no-extension-connected) to the action status shown to the user.
The chip detail is the first URL's outcome, with `(+n more)` for the others; any failing URL fails the action.
A task carrying `storedTabs:` (what a complete-control suspend left behind) first reopens each stored URL as focus-or-open — an already-open tab is thereby kept, never closed and reopened, which is the whole of the overlap rule.
On a complete-control category the first stored URL is probed (`openIfMissing: false`) and, when gone, routed through `Main.openOnDesktop` — switch to the category's desktop, raise a Firefox window living there or launch one carrying the URL — so the restored tabs land on the desktop they were stored from; the remaining URLs then focus-or-open into that raised window.
The `storedTabs:` key is cleared from the task file only when every reopen succeeded; on any failure it stays for the next resume (the chip says so).
Ceiling: a window freshly launched by the preparer may still be starting while the remaining URLs open, in which case they land in Firefox's most recent window instead.

Tags: windows, linux

Covers:
- req~focus-browser-tab~2
- req~complete-control-desktop~1

Needs: impl, utest

### Browser tab selection
`dsn~browser-tab-selects-task~5`

The extension listens on `tabs.onActivated`, `tabs.onUpdated` (a URL change in the active tab) and `windows.onFocusChanged` (switching windows fires no `onActivated`) and sends an unsolicited `{"type":"tab-activated","url":…}` — no correlation id, no reply — deduplicated against the last reported URL, since `onUpdated` fires repeatedly per navigation.
`ExtensionServer` honours the message only on the connection that passed `hello` and hands the URL to `Main.selectTaskForTab`, which picks the task with the best `Task.tabUrlMatch` (2 exact `browser.urls` hit, 1 page under one of them — same `/`, `#`, `?` boundary rule as the extension's `isSameOrSubUrl`, 0 no match) and selects its row.
No match, or no window yet, does nothing.
A match whose status is `suspended` is additionally handed to `MainWindow.resumeTask` — the same `setStatus(task, ACTIVE)` the row's play button and the queue's send-into-a-suspended-chat run (`dsn~task-suspend~6`), so the status write and the resurrecting switch come for free and no confirmation appears.
It is the status *on disk* that decides, and `resumeTask` re-reads it, so a second `tab-activated` for the same tab arriving while the resume is in flight finds the task already `active` and only rewrites the status it already has.
A `done` task is never resumed: completing it ended its context on purpose.
Several tasks can list the same URL — one implementing a PR, one checking why a check of it fails — and then score alike; `Main.rankTabMatches` breaks that tie in favour of the **currently selected** task, so activating the PR's tab keeps the task the user is on instead of switching the app (and the terminal mirror) to the other one, which reads as the app switching tasks by itself (field report 2026-09-09).
A better score still wins over the selection: an exact hit is about *this* tab, a selected task matching only a parent page is not.
A match that is already the selected task (`MainWindow.selectedTaskId`) is not selected again: the tabs of one task share a tab group, so moving between them reports every tab and each matches the same task — re-selecting it would only scroll, repaint and re-reveal its row, flicker for a selection that never changed.
The suspended resume is not part of that skip: a selected but suspended task activated from its tab still resumes.
`MainWindow.selectTask` reveals a row the current view hides before giving up: a collapsed category is expanded and an active find query is cleared (`closeFind`), each followed by the rebuild that applies the pending selection.
A selection nobody can see is no selection — the reporter has no status message or raise to fall back on — and the same reveal serves every other `selectTask` caller (deep link, pane click).
The narrowing filters are left alone: they are a deliberate working set, and a background browser event must not silently widen it; such a task stays pending and is selected once the filter lets it through.
A pending selection no row carries — a filtered-out task, but equally a rename in flight or a created task the watcher has not delivered — no longer costs the user the highlight they can see: `rebuildRows` falls back to re-selecting the task that was on screen, since a row rebuild clears the `ListView` selection and the pending id used to suppress the preservation that puts it back (field report 2026-09-10: leaving the app and coming back left the list with nothing selected until the next click).
The id stays pending regardless and wins as soon as it has a row.

Tags: windows, linux

Covers:
- req~browser-tab-selects-task~2

Needs: impl, utest

### Quiet tabs during a teardown
`dsn~browser-teardown-quiet-tabs~1`

Closing a tab makes the browser activate a neighbour, and the extension reports that like any activation; when the neighbour is another tab of the task being suspended, `selectTaskForTab` resumed the very task the suspend was ending (field report 2026-09-13).
A suspend or delete teardown is therefore a transaction: `Main` counts teardowns in flight, and while the count is non-zero every `tab-activated` is dropped.
When the last one's actions are done, the app sends an `active-tab` request, the extension answers with the URL of the tab in view (`detail`, empty for none) and records it as reported, and the app selects that tab's task — select and badge only, never resume, since the user did not pick the tab.
The count drops only once that answer is in, so a report the last close triggered, still on its way, is dropped too.

Tags: windows, linux

Covers:
- req~task-suspend-resume~2

Needs: impl, utest

### Browser close action
`dsn~browser-close-action~2`

`BrowserCloseAction` runs in the suspend orchestrator and submits one `close-url` request per `browser.urls` entry — unlike focus, closing applies to all of the task's URLs. All URLs are attempted even when one fails; any failure fails the chip. Resume needs no counterpart: it runs as a regular switch, whose focus-or-open reopens the URL.
On a complete-control category (`req~complete-control-desktop~1`) the action additionally captures the desktop first, sequentially inside the one browser chip (a separate concurrent action could close a task URL before the capture listed it): the desktop's unpinned browser window captions (`dsn~browser-desktop-windows~2`) are matched against the extension's `list-tabs` titles — containment in either direction, since the OS caption is the active-tab title plus the browser suffix — the matched windows' `http(s)` tab URLs are written to the task file's `storedTabs:` (deduplicated, in window/tab order), and only then are the matched windows closed via `close-window`.
Store-before-close is load-bearing: a failing store closes nothing, so tabs cannot be lost. An unavailable capture mechanism (non-Windows, no `powershell`) or an empty/unmatched desktop is reported as a detail and the action proceeds with the plain per-URL close; a failing extension call fails the chip.

Tags: windows, linux

Covers:
- req~task-suspend-resume~2
- req~complete-control-desktop~1

Needs: impl, utest

### Complete-control config, storage, and wiring
`dsn~complete-control-desktop~1`

`GroupConfig` gains `completeControl`; `TaskFileParser.parseGroupConfig` accepts `desktop:` as scalar (name only, back-compat) or mapping (`name`, `completeControl`).
The captured URLs live in the task file's top-level `storedTabs:` list — parsed into `Task.storedTabs`, written and removed textually (comment-preserving, like every frontmatter rewrite) by `withStoredTabs`/`withoutStoredTabs`; storing in the task file makes the state survive restarts and travel with the git-backed task sync.
`Main` resolves the flag per suspend/switch through `completeControlDesktop` (category config read fresh, so an edited flag needs no restart) and hands the actions their file access: store and clear both run through the same read-transform-save the resurrect write-back uses.
`TabCommands`/`ExtensionServer` carry the two new commands (`listTabs`, `closeWindow`).

Tags: windows

Covers:
- req~complete-control-desktop~1

Needs: impl, utest

### Tab group per task
`dsn~browser-tab-group~3`

`Task.tabGroup()` derives the group name from the task: the number of the first `browser.urls` entry that is a GitHub pull request or issue (`#<n>`) or a GitLab merge request (`!<n>`) or issue (`#<n>`); else the `tmux:` `window:` value with a leading `@` stripped when what remains is a number (`@339` → `339`), else `l:<task id>` — a named window (`window: claude`) is not unique across sessions and uses the id form like a task with no tmux section at all.
`BrowserFocusAction` passes it with every `focus-url` (`"group"` field, optional; `Main`'s own PR/deep-link focus sends none and leaves those tabs alone), plus `"background": true` on every URL but the first.
The extension puts the focused or newly created tab into the group whose title equals the name, creating the group when none carries that title (`tabs.group` + `tabGroups.update`), and then moves it behind the last tab of that group — a tab that was already in the group is moved as well, which is what re-orders an already-open page into the task's order.
A `background` tab is created inactive, is neither activated nor its window raised, and answers without a title (nothing is to be raised, so nothing waits for the title to settle).
Grouping is cosmetic: a browser without the tab-group API (before Firefox 139) or a failing call leaves the tab where it is and the focus still succeeds.

Tags: windows, linux

Covers:
- req~browser-tab-group~3

Needs: impl, utest

### Duplicate tab closing
`dsn~browser-no-duplicate-tab~1`

Both extensions remember every tab id `tabs.onCreated` reports (dropped on `tabs.onRemoved`).
The first `tabs.onUpdated` URL change of such a tab to an `http`/`https` address consumes the mark; when another tab's URL equals it exactly, the extension activates that tab and focuses its window — only if the new tab is active — and removes the new one.
That runs before the `tab-activated` report of the same event, so the app hears of the surviving tab (through its `onActivated`) and never of the closed one.
Ceilings: exact URL only, so a sub-path or fragment of an open page still opens a tab; a Chrome service worker restart forgets the marks of tabs created before it.

Tags: windows, linux

Covers:
- req~browser-no-duplicate-tab~1

Needs: impl

### Tab context count
`dsn~browser-tab-context-count~2`

The extension carries an SVG toolbar icon (concentric target rings, Paul Tol's vibrant blue `#0077BB`) as `browser_action.default_icon` and as the add-on's `icons`; Firefox scales the one file to every size, so no raster set is generated or kept in sync.
`Main.selectTaskForTab` already collects the tasks matching a reported tab URL and now answers the report with `{"type":"tab-count","url":…,"tasks":[{id, title, status}, …]}` — ranked by `Task.tabUrlMatch`, so the first entry is the task the same method selects — through `ExtensionServer.sendTabCount`: unsolicited, uncorrelated, and silently skipped when no extension is connected, since a badge must never fail a caller.
The extension puts a non-empty list's size into the icon's badge (`browserAction.setBadgeText`, white on the icon's blue) for every tab whose URL is exactly the reported one, and the wording into its tooltip; an empty list clears both.
Only the active tab's icon is ever on screen, so the report the extension already sends on `tabs.onActivated` / `tabs.onUpdated` / `windows.onFocusChanged` is the whole refresh trigger it needs, and one answer is kept rather than a map that grows all session.
Two more make it fresh again where that is not enough: the reconnect (`socket.onopen`) and the popup opening, both re-reporting the tab in view past the last-URL deduplication.
Ceiling: the count is pulled, never pushed — a task edited in the application leaves the badge stale until the tab is revisited or the popup opened.

Tags: windows, linux

Covers:
- req~browser-tab-context-count~1

Needs: impl, utest

### Tab task popup
`dsn~browser-tab-task-popup~1`

`popup.html`/`popup.js` are the icon's `default_popup` and list the tasks of the kept `tab-count` answer, one row each: the title (plus the status when it is not `active`) selects the task, the `▶` beside it switches to it.
Both send `{"type":"open-link","url":"contextswitcher://task|switch/<id>"}` on the already authenticated socket; `ExtensionServer` hands the URL to the very `deepLinkHandler` the URL scheme uses — and, unlike the one-shot `deeplink` of a second app instance, leaves the connection open.
`Main.handleDeepLink` therefore does the raising (`setIconified(false)`, `toFront`, `requestFocus`) and, for `switch`, the full switch, with no new application-side behaviour to specify.
The popup reads the background page directly (`browser.extension.getBackgroundPage()`, which a persistent MV2 background page allows), registers itself through `setTabCountListener` so a late answer re-renders the open list, and drops the registration on unload.
A task id carries `/` for its group folder, so the link encodes the segments and keeps the separators.
No answer for the URL in view reads as "Asking ContextSwitcher…" or, with the socket down, "ContextSwitcher is not running" — never as an empty task list, which would be a lie about the page.
There is deliberately no bare "show the application" button: with no task the application has nothing to show for the page.

Tags: windows, linux

Covers:
- req~browser-tab-task-popup~1

Needs: impl, utest

### Browser windows on a named desktop
`dsn~browser-desktop-windows~2`

`BrowserDesktopWindows` lists the captions of the configured browser's windows sitting on a **named** virtual desktop via one `powershell -EncodedCommand` child process: the desktop's GUID is resolved from the registry (the `WindowsVirtualDesktopFocus` lookup), the top-level windows of the browser's class **and** process name (`dsn~browser-choice~2`) are enumerated, and a window is kept when the *documented* `IVirtualDesktopManager::GetWindowDesktopId` returns that GUID and `IVirtualDesktopPinnedApps::IsViewPinned` (the stable-since-1607 `WindowsDesktopPin` interfaces) says it is not pinned — a window shown on all desktops must survive the capture; if the pin check itself fails, the window counts as unpinned rather than aborting the capture.
Unknown desktop name or no browser window there is a clean empty list; no `powershell` (non-Windows) or a failing script returns null, telling the caller to skip the capture rather than store a wrong empty state.

Tags: windows

Covers:
- req~complete-control-desktop~1

Needs: impl, utest
