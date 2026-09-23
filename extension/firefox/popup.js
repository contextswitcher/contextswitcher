// The toolbar icon's popup: the tasks listing the page in view. Clicking a
// task selects it and raises ContextSwitcher; the ▶ next to it runs the full
// switch. Both go out as the `contextswitcher://` links the app's own URL
// scheme already handles.
// [impl->dsn~browser-tab-task-popup~1]

// A persistent background page (MV2) hands the popup its state directly —
// no message passing. Null in a private window, where no background page is
// shared with the popup.
const bg = browser.extension.getBackgroundPage();

// `contextswitcher://<kind>/<task id>`. Ids carry `/` for the group folder,
// so the separators stay and only the segments are encoded.
function link(kind, id) {
  return `contextswitcher://${kind}/` + id.split("/").map(encodeURIComponent).join("/");
}

// Named `send`, not `open`: a top-level `function open` in a classic
// script would replace `window.open`.
function send(url) {
  bg.openLink(url);
  window.close();
}

// The icon's colour, in words. [impl->dsn~extension-state-icon~3]
const STATE_TEXT = {
  "no-token": "No token configured \u2014 set one in the options.",
  disconnected: "Not connected to ContextSwitcher.",
  connected: "Connected to ContextSwitcher.",
  disabled: "Disabled \u2014 ContextSwitcher leaves this browser alone.",
};

function row(task) {
  const select = document.createElement("button");
  select.className = "task";
  select.title = "Select this task and raise ContextSwitcher";
  select.textContent = task.title;
  if (task.status !== "active") {
    const status = document.createElement("span");
    status.className = "status";
    status.textContent = task.status;
    select.appendChild(status);
  }
  select.addEventListener("click", () => send(link("task", task.id)));

  const run = document.createElement("button");
  run.className = "switch";
  run.title = "Switch to this task";
  run.textContent = "▶";
  run.addEventListener("click", () => send(link("switch", task.id)));

  const item = document.createElement("li");
  item.append(select, run);
  return item;
}

async function render() {
  const heading = document.getElementById("heading");
  const list = document.getElementById("tasks");
  const note = document.getElementById("note");
  const state = document.getElementById("state");
  list.replaceChildren();
  note.textContent = "";
  state.textContent = "";

  if (!bg) {
    heading.textContent = "ContextSwitcher";
    note.textContent = "Not available in a private window.";
    return;
  }
  state.textContent = STATE_TEXT[bg.stateOfConnection()];
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  const tasks = bg.tasksForUrl(tab && tab.url);
  if (tasks === null) {
    // No answer for this URL yet: the re-report render() triggers will bring
    // one, and the listener re-renders when it lands.
    heading.textContent = "This page";
    // Not connected needs no second sentence: the state line below says so.
    note.textContent = bg.isConnected() ? "Asking ContextSwitcher…" : "";
    return;
  }
  heading.textContent = tasks.length === 1 ? "This page is in 1 task" : `This page is in ${tasks.length} tasks`;
  if (tasks.length === 0) {
    note.textContent = "No task lists this address.";
    return;
  }
  list.append(...tasks.map(row));
}

if (bg) {
  // Fresh numbers while the popup is open: ask again, and re-render on the
  // answer. Dropped on close so the background page does not call into a
  // document that is gone.
  bg.setTabCountListener(render);
  window.addEventListener("unload", () => bg.setTabCountListener(null));
  bg.reportCurrentTab();
}
// The on/off switch, as Tampermonkey has one. Saving the flag reaches the
// background's storage listener, which drops or dials the connection; the
// icon fading or coming back is the feedback, so the popup just closes.
// [impl->dsn~extension-enable-toggle~1]
async function showToggle() {
  const { enabled } = await browser.storage.local.get({ enabled: true });
  const toggle = document.getElementById("toggle");
  toggle.textContent = enabled ? "\u2713 Enabled" : "\u2717 Disabled";
  toggle.title = enabled ? "Stop ContextSwitcher in this browser" : "Let ContextSwitcher work in this browser again";
  toggle.onclick = async () => {
    await browser.storage.local.set({ enabled: !enabled });
    window.close();
  };
}
showToggle();

// The token is what a fresh install lacks, so its page is one click away.
document.getElementById("options").addEventListener("click", () => {
  browser.runtime.openOptionsPage();
  window.close();
});
render();
