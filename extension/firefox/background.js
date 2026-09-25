// ContextSwitcher Firefox extension: connects to the app's loopback WebSocket
// server and executes tab commands (focus-or-open, close, list windows/tabs,
// close window), collecting a task's tabs in a named tab group. Reconnects with exponential backoff, so browser and app may
// start in any order.
// [impl->dsn~firefox-extension-mv2~4]
// [impl->dsn~browser-tab-group~3]

const BACKOFF_INITIAL_MS = 1000;
const BACKOFF_MAX_MS = 30000;

let socket = null;
let reconnectTimer = null;
let backoffMs = BACKOFF_INITIAL_MS;

// The connection state, on the icon and in its tooltip: red = nothing to
// connect with, orange = every "cannot connect" reason there is (app stopped,
// wrong token, rejected origin — indistinguishable from here), grey = talking
// to the app, blue = talking to the app about a page some task lists (set per
// tab by showTabCount), so a glance says whether there is anything to see.
// Deliberately not the badge: that carries the per-tab task count
// and is invisible at zero, which is when the state matters most.
// [impl->dsn~extension-state-icon~3]
const STATES = {
  "no-token": { icon: "icon-red.svg", title: "ContextSwitcher: no token configured" },
  disconnected: { icon: "icon-orange.svg", title: "ContextSwitcher: not connected to the app" },
  connected: { icon: "icon-gray.svg", title: "ContextSwitcher: connected" },
  // [impl->dsn~extension-enable-toggle~1]
  disabled: { icon: "icon-disabled.svg", title: "ContextSwitcher: disabled" },
};
// Null until the first state is painted, so the very first setState is
// never mistaken for a no-op repaint.
let connectionState = null;

function setState(state) {
  if (state === connectionState) {
    return; // every reconnect attempt calls this; only a change is work
  }
  connectionState = state;
  browser.browserAction.setIcon({ path: STATES[state].icon });
  // No tabId: while connected, a page the app has answered for keeps the
  // per-tab tooltip naming its count, which says more about that tab.
  browser.browserAction.setTitle({ title: STATES[state].title });
  sweepTabs(state);
}

// Every transition repaints the per-tab icons showTabCount set, or a tab would
// keep the colour of a state long gone. A count nobody is left to refresh is
// a lie about the tab, so the badge and the per-tab tooltip go with the
// connection — the tooltip to the state text, which is what that tab has to
// say now.
// [impl->dsn~extension-state-icon~3]
async function sweepTabs(state) {
  for (const tab of await browser.tabs.query({})) {
    browser.browserAction.setIcon({ tabId: tab.id, path: STATES[state].icon });
    if (state !== "connected") {
      browser.browserAction.setBadgeText({ tabId: tab.id, text: "" });
      browser.browserAction.setTitle({ tabId: tab.id, title: STATES[state].title });
    }
  }
}

// Bumped by every connect() and every settings change. The settings are read
// asynchronously, so a connect() that finds a newer number after the read lost
// a race — to a parallel start-up call, or to the popup's switch landing
// mid-read — and must not dial with what it read: that socket would outlive
// the switch. [impl->dsn~extension-enable-toggle~1]
let generation = 0;

async function loadSettings() {
  return browser.storage.local.get({ port: 17872, token: "", enabled: true });
}

async function connect() {
  reconnectTimer = null;
  const mine = ++generation;
  const { port, token, enabled } = await loadSettings();
  if (mine !== generation || socket) {
    return;
  }
  if (!enabled) {
    // Switched off in the popup: stay silent until it is switched on again,
    // which the storage listener turns into a reconnect.
    // [impl->dsn~extension-enable-toggle~1]
    setState("disabled");
    return;
  }
  if (!token) {
    // Not configured yet: connecting would only be rejected by the app.
    // Saving the options triggers the storage listener, which retries.
    setState("no-token");
    return;
  }
  setState("disconnected");
  const ws = new WebSocket(`ws://127.0.0.1:${port}`);
  socket = ws;

  ws.onopen = () => {
    setState("connected");
    backoffMs = BACKOFF_INITIAL_MS;
    ws.send(JSON.stringify({ type: "hello", token, client: "firefox" }));
    // The task list may have changed while the app was away, so the badge of
    // the tab in view is re-asked for rather than left at its stale number.
    reportCurrentTab();
  };

  ws.onmessage = async (event) => {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch (e) {
      console.warn("ContextSwitcher: unparseable message", event.data);
      return;
    }
    const reply = await handleCommand(message);
    if (reply && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(reply));
    }
  };

  // onclose also fires when the connection attempt itself fails.
  ws.onclose = async () => {
    socket = null;
    // A close the popup's switch caused is no connection problem.
    setState((await loadSettings()).enabled ? "disconnected" : "disabled");
    // The answer belonged to a task list that is now out of reach; the popup
    // says "not running" rather than showing what was true a moment ago.
    answered.url = null;
    answered.tasks = [];
    if (tabCountListener) {
      tabCountListener();
    }
    scheduleReconnect();
  };
}

function scheduleReconnect() {
  if (reconnectTimer !== null) {
    return;
  }
  reconnectTimer = setTimeout(connect, backoffMs);
  backoffMs = Math.min(backoffMs * 2, BACKOFF_MAX_MS);
}

async function handleCommand(message) {
  if (message.type === "focus-url") {
    return resultOf(message.id, () => focusUrl(
      message.url, message.openIfMissing, message.windowTitle, message.group, message.background));
  }
  if (message.type === "close-url") {
    return resultOf(message.id, () => closeUrl(message.url));
  }
  if (message.type === "list-tabs") {
    return resultOf(message.id, listTabs);
  }
  if (message.type === "active-tab") {
    return resultOf(message.id, activeTab);
  }
  if (message.type === "close-window") {
    return resultOf(message.id, () => closeWindow(message.windowId));
  }
  if (message.type === "tab-count") {
    // Unsolicited answer to a tab report: no correlation id, no reply.
    showTabCount(message.url, message.tasks);
    return null;
  }
  console.warn("ContextSwitcher: unknown command", message.type);
  return null;
}

async function resultOf(id, command) {
  try {
    const { ok, detail, title, windows } = await command();
    const reply = { type: "result", id, ok, detail };
    // The window title lets the app raise the right Firefox window (its own
    // windows.update can't cross the OS foreground lock); only sent when a
    // focused tab is known.
    if (title) {
      reply.title = title;
    }
    // The window list a list-tabs answers with.
    if (windows) {
      reply.windows = windows;
    }
    return reply;
  } catch (e) {
    return { type: "result", id, ok: false, detail: String(e) };
  }
}

// Exact-URL match; tabs.query({url}) is not used because it treats the URL as
// a match pattern (fragments stripped, *? special).
async function findTabs(url) {
  const tabs = await browser.tabs.query({});
  return tabs.filter((tab) => tab.url === url);
}

// True when tabUrl is url itself or a page under it — a sub-path, fragment, or
// query. The boundary characters (/, #, ?) stop a longer sibling from matching
// (e.g. .../pull/15785 must not match the tab .../pull/157850).
function isSameOrSubUrl(tabUrl, url) {
  return tabUrl === url
    || tabUrl.startsWith(url + "/")
    || tabUrl.startsWith(url + "#")
    || tabUrl.startsWith(url + "?");
}

// Puts the tab into the tab group named `name` (creating the group when no
// group carries that title yet) and moves it to the group's end. The app
// sends a task's URLs in its own order, so appending each one reproduces
// that order in the tab bar — for a tab that was already open elsewhere too.
// Tab groups exist from Firefox 139; an older browser has no API and simply
// leaves the tab where it is.
// [impl->dsn~browser-tab-group~3]
async function groupTab(tabId, name) {
  if (!name || !browser.tabs.group || !browser.tabGroups) {
    return;
  }
  try {
    const groups = await browser.tabGroups.query({});
    const existing = groups.find((g) => g.title === name);
    const tab = await browser.tabs.get(tabId);
    let groupId = existing ? existing.id : undefined;
    if (tab.groupId !== groupId) {
      groupId = await browser.tabs.group(
        existing ? { tabIds: [tabId], groupId: existing.id } : { tabIds: [tabId] });
    }
    if (!existing) {
      // A group made for this tab holds nothing to order it against.
      await browser.tabGroups.update(groupId, { title: name });
      return;
    }
    // Append: move behind the last tab the group occupies (a tab already in
    // the group is moved too — that is what re-orders it into task order).
    const grouped = await browser.tabs.get(tabId);
    const siblings = (await browser.tabs.query({ windowId: grouped.windowId }))
      .filter((t) => t.groupId === groupId);
    const last = Math.max(...siblings.map((t) => t.index));
    if (siblings.length > 0 && grouped.index !== last) {
      await browser.tabs.move(tabId, { index: last });
    }
  } catch (e) {
    // Grouping is cosmetic: never fail the focus over it.
    console.warn("ContextSwitcher: could not group tab", e);
  }
}

async function focusUrl(url, openIfMissing, windowTitle, group, background) {
  const tabs = await browser.tabs.query({});
  // Prefer an exact tab; otherwise focus a tab under the same URL before
  // opening a duplicate — a GitHub PR is routinely open on a /files or
  // /commits sub-path, or carries a #discussion fragment.
  const exact = tabs.find((tab) => tab.url === url);
  const tab = exact || tabs.find((tab) => tab.url && isSameOrSubUrl(tab.url, url));
  if (tab) {
    // Grouping may move the tab into the group's window, so the window to
    // raise is read back afterwards.
    await groupTab(tab.id, group);
    let found = await browser.tabs.get(tab.id);
    // A named window means the app has already switched to the task
    // category's virtual desktop and raised the Firefox window living there.
    // A tab sitting in some *other* window is moved into that one rather than
    // raised where it is: raising it makes Windows follow it to its desktop,
    // which dropped the user off their category's desktop.
    const target = await windowIdFor(windowTitle);
    let moved = false;
    if (target !== undefined && target !== found.windowId) {
      try {
        await browser.tabs.move(found.id, { windowId: target, index: -1 });
        found = await browser.tabs.get(tab.id);
        moved = true;
      } catch (e) {
        // A tab that refuses to move (pinned, a private window) is still
        // worth focusing where it is — never fail the click over the desktop.
        console.warn("ContextSwitcher: could not move tab to the target window", e);
      }
    }
    if (!background) {
      await browser.tabs.update(found.id, { active: true });
      await browser.windows.update(found.windowId, { focused: true });
    }
    // The tab title is now the window's active-tab title, which the app uses
    // to raise this exact window (there may be many Firefox windows).
    return {
      ok: true,
      detail: (exact ? "focused existing tab" : "focused related tab")
        + (moved ? " (moved to this desktop)" : ""),
      title: background ? undefined : found.title,
    };
  }
  if (openIfMissing) {
    // The app names the window it just raised on the target desktop (its OS
    // caption); without one Firefox picks its most recently focused window.
    const windowId = await windowIdFor(windowTitle);
    const created = await browser.tabs.create({ url, active: !background, windowId });
    await groupTab(created.id, group);
    if (background) {
      // Nothing to raise, and no title to wait for.
      return { ok: true, detail: "opened new tab" };
    }
    const grouped = await browser.tabs.get(created.id);
    await browser.windows.update(grouped.windowId, { focused: true });
    // Wait (bounded) for the page title to settle so the app can title-match
    // and raise this new tab's window too; a page too slow to finish inside
    // the budget simply opens in the background (no title returned).
    const title = await waitForTabTitle(created.id, 3000);
    return { ok: true, detail: "opened new tab", title };
  }
  return { ok: false, detail: "no tab with that URL" };
}

// The id of the normal window whose title matches the OS caption (both are
// the active tab's title, matched by containment like the app's complete-
// control capture), or undefined when nothing matches — letting tabs.create
// fall back to Firefox's own choice.
async function windowIdFor(caption) {
  if (!caption) {
    return undefined;
  }
  const all = await browser.windows.getAll({ windowTypes: ["normal"] });
  const match = all.find((w) => w.title && (caption.includes(w.title) || w.title.includes(caption)));
  return match ? match.id : undefined;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// The tab's title once loading completes with a non-empty title, or undefined
// on timeout / if the tab vanished. A settled title matches the window caption
// stably (a still-loading title keeps changing and would race the app's raise).
async function waitForTabTitle(tabId, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    let tab;
    try {
      tab = await browser.tabs.get(tabId);
    } catch (e) {
      return undefined; // tab was closed
    }
    if (tab.status === "complete" && tab.title) {
      return tab.title;
    }
    await sleep(150);
  }
  return undefined;
}

async function closeUrl(url) {
  const tabs = await findTabs(url);
  if (tabs.length > 0) {
    await browser.tabs.remove(tabs.map((tab) => tab.id));
  }
  return { ok: true, detail: `closed ${tabs.length} tab(s)` };
}

// Every normal browser window with its tabs' URLs. The window's title is what
// the OS caption is made of (the active tab's title), so the app can match a
// window it sees on a virtual desktop to an id it can close.
async function listTabs() {
  const all = await browser.windows.getAll({ populate: true });
  const windows = all
    .filter((w) => w.type === "normal")
    .map((w) => ({
      id: w.id,
      title: w.title || "",
      urls: (w.tabs || []).map((tab) => tab.url || ""),
    }));
  return { ok: true, detail: `${windows.length} window(s)`, windows };
}

// The tab in view, for the app's re-sync after a suspend: its closes activated
// neighbour tabs whose reports the app dropped. Counts as reported, so the
// dedup keeps working. [impl->dsn~browser-teardown-quiet-tabs~1]
async function activeTab() {
  const [tab] = await browser.tabs.query({ active: true, lastFocusedWindow: true });
  const url = (tab && tab.url) || "";
  lastReportedUrl = url || null;
  return { ok: true, detail: url };
}

async function closeWindow(windowId) {
  await browser.windows.remove(windowId);
  return { ok: true, detail: `closed window ${windowId}` };
}

// The user activating a tab is reported to the app, which selects the task
// owning that URL (no reply expected). Deduplicated: onUpdated fires several
// times per navigation, and re-reporting the same URL is pure noise.
// [impl->dsn~browser-tab-selects-task~5]
let lastReportedUrl = null;

function reportActiveTab(tab) {
  if (!tab || !tab.url || tab.url === lastReportedUrl) {
    return;
  }
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    return;
  }
  lastReportedUrl = tab.url;
  socket.send(JSON.stringify({ type: "tab-activated", url: tab.url }));
}

async function reportTab(tabId) {
  try {
    reportActiveTab(await browser.tabs.get(tabId));
  } catch (e) {
    // Tab vanished between the event and the lookup.
  }
}

browser.tabs.onActivated.addListener(({ tabId }) => reportTab(tabId));

// The app answers every report with a `tab-count`: the tasks listing that
// URL, best match first. Their number goes on the toolbar icon as its badge,
// so the icon says whether the page in view belongs to a task and to how
// many; the popup lists the tasks themselves.
// [impl->dsn~browser-tab-context-count~2]
const answered = { url: null, tasks: [] };

// The popup, while it is open, so a late answer reaches the list on screen.
// Set through a function rather than assigned: a top-level `let` is no
// property of the background page's window, so the popup cannot reach it.
// [impl->dsn~browser-tab-task-popup~1]
let tabCountListener = null;

function setTabCountListener(listener) {
  tabCountListener = listener;
}

async function showTabCount(url, tasks) {
  answered.url = url;
  answered.tasks = tasks || [];
  const count = answered.tasks.length;
  const tabs = await browser.tabs.query({});
  // Applied by URL, not by tab id: the report carries no id, and two tabs on
  // the same page deserve the same badge anyway.
  for (const tab of tabs.filter((t) => t.url === url)) {
    browser.browserAction.setBadgeText({ tabId: tab.id, text: count > 0 ? String(count) : "" });
    browser.browserAction.setIcon({ tabId: tab.id, path: count > 0 ? "icon.svg" : STATES.connected.icon });
    browser.browserAction.setTitle({
      tabId: tab.id,
      title: count > 0
        ? `ContextSwitcher \u2014 this page is in ${count} task${count === 1 ? "" : "s"}`
        : "ContextSwitcher \u2014 this page is in no task",
    });
  }
  if (tabCountListener) {
    tabCountListener();
  }
}

// The popup's view of the answer: the task list for `url`, or null when the
// app has not answered for that URL (yet). Only the tab in view can be shown,
// so one answer is kept rather than a map that grows all session.
// [impl->dsn~browser-tab-task-popup~1]
function tasksForUrl(url) {
  return url && url === answered.url ? answered.tasks : null;
}

function isConnected() {
  return socket !== null && socket.readyState === WebSocket.OPEN;
}

// The popup says the three states in words, so a click explains the colour.
// [impl->dsn~extension-state-icon~3]
function stateOfConnection() {
  return connectionState;
}

// Runs a `contextswitcher://` link in the app — the popup's task rows.
// [impl->dsn~browser-tab-task-popup~1]
function openLink(url) {
  if (isConnected()) {
    socket.send(JSON.stringify({ type: "open-link", url }));
  }
}

// Re-reports the tab in view even when its URL was the last one reported —
// the badge is only ever as fresh as the last answer, and this is what makes
// it fresh again (on reconnect, and whenever the popup opens).
// ponytail: pull, not push — a task edited in the app leaves the badge stale
// until the tab is revisited or the popup opened; push it from the app if
// that turns out to matter.
async function reportCurrentTab() {
  lastReportedUrl = null;
  try {
    const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
    reportActiveTab(tab);
  } catch (e) {
    // No window open yet.
  }
}

// The badge in the icon's own blue, set once for every tab.
browser.browserAction.setBadgeBackgroundColor({ color: "#0077BB" });
browser.browserAction.setBadgeTextColor({ color: "#FFFFFF" });

// A tab opened on a page some other tab already shows is closed again and the
// older tab is focused in its place (and brought into view only when the new
// one was going to be), so opening a task's pull request a second time never
// leaves two copies behind. Only a fresh tab is judged, and only on its first
// web address: a tab that navigates on to a page open elsewhere keeps its back
// history and stays.
// ponytail: exact URL only — a sub-path or fragment of an open page still opens;
// widen to isSameOrSubUrl if that turns out to duplicate too.
// [impl->dsn~browser-no-duplicate-tab~1]
const freshTabs = new Set();

browser.tabs.onCreated.addListener((tab) => freshTabs.add(tab.id));
browser.tabs.onRemoved.addListener((tabId) => freshTabs.delete(tabId));

// True when the tab was a duplicate and has been closed.
async function closeIfDuplicate(tab) {
  if (!freshTabs.has(tab.id) || !/^https?:/.test(tab.url)) {
    return false;
  }
  // A disabled extension leaves the browser alone. [impl->dsn~extension-enable-toggle~1]
  if (!(await loadSettings()).enabled) {
    return false;
  }
  freshTabs.delete(tab.id);
  const older = (await browser.tabs.query({})).find((t) => t.id !== tab.id && t.url === tab.url);
  if (!older) {
    return false;
  }
  try {
    if (tab.active) {
      await browser.tabs.update(older.id, { active: true });
      await browser.windows.update(older.windowId, { focused: true });
    }
    await browser.tabs.remove(tab.id);
    return true;
  } catch (e) {
    // Either tab vanished meanwhile; nothing is left to deduplicate.
    return false;
  }
}

// A navigation inside the active tab (clicking a link, a redirect finishing)
// changes which task the tab belongs to, so it is reported like an activation.
browser.tabs.onUpdated.addListener(async (tabId, changeInfo, tab) => {
  if (!changeInfo.url || await closeIfDuplicate(tab)) {
    return;
  }
  if (tab.active) {
    reportActiveTab(tab);
  }
});

// Switching between Firefox windows fires no onActivated — the active tab of
// the newly focused window is the one the user is now looking at.
browser.windows.onFocusChanged.addListener(async (windowId) => {
  if (windowId === browser.windows.WINDOW_ID_NONE) {
    return;
  }
  try {
    const [tab] = await browser.tabs.query({ active: true, windowId });
    reportActiveTab(tab);
  } catch (e) {
    // Window vanished between the event and the query.
  }
});

// New port/token from the options page: drop the connection and retry
// immediately with the fresh settings.
browser.storage.onChanged.addListener((changes, area) => {
  if (area !== "local") {
    return;
  }
  backoffMs = BACKOFF_INITIAL_MS;
  generation++; // an in-flight connect() read the old settings
  if (socket) {
    socket.close(); // onclose schedules the reconnect
  } else {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    scheduleReconnect();
  }
});

connect();
