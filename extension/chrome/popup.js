// The toolbar icon's popup: the tasks listing the page in view. Clicking a
// task selects it and raises ContextSwitcher; the ▶ next to it runs the full
// switch. Both go out as the `contextswitcher://` links the app's own URL
// scheme already handles.
// [impl->dsn~browser-tab-task-popup~1]

const browser = globalThis.browser ?? globalThis.chrome;

// An MV3 service worker has no page the popup can reach (MV2's
// `extension.getBackgroundPage()`), so the state comes over a runtime message
// and a late answer arrives as a `tab-count-updated` broadcast.
async function state() {
  return browser.runtime.sendMessage({ type: "popup-state" });
}

// `contextswitcher://<kind>/<task id>`. Ids carry `/` for the group folder,
// so the separators stay and only the segments are encoded.
function link(kind, id) {
  return `contextswitcher://${kind}/` + id.split("/").map(encodeURIComponent).join("/");
}

// Named `send`, not `open`: a top-level `function open` in a classic
// script would replace `window.open`.
function send(url) {
  browser.runtime.sendMessage({ type: "open-link", url });
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
  // Not `state`: that would shadow the state() function and make every call throw.
  const stateLine = document.getElementById("state");
  list.replaceChildren();
  note.textContent = "";
  stateLine.textContent = "";

  let answer;
  try {
    answer = await state();
  } catch (e) {
    // The service worker was asleep and the wake-up raced this message; the
    // start it just did answers the next ask.
    heading.textContent = "ContextSwitcher";
    note.textContent = "Starting up\u2026";
    setTimeout(render, 1000);
    return;
  }
  stateLine.textContent = STATE_TEXT[answer.state];
  const tasks = answer.tasks;
  if (tasks === null || tasks === undefined) {
    // No answer for this URL yet: the re-report the state request triggers
    // will bring one, and the broadcast re-renders when it lands.
    heading.textContent = "This page";
    // Not connected needs no second sentence: the state line below says so.
    note.textContent = answer.connected ? "Asking ContextSwitcher…" : "";
    return;
  }
  heading.textContent = tasks.length === 1 ? "This page is in 1 task" : `This page is in ${tasks.length} tasks`;
  if (tasks.length === 0) {
    note.textContent = "No task lists this address.";
    return;
  }
  list.append(...tasks.map(row));
}

// Fresh numbers while the popup is open: the worker broadcasts when an answer
// lands. The listener dies with the popup, so there is nothing to unregister.
browser.runtime.onMessage.addListener((message) => {
  if (message.type === "tab-count-updated") {
    render();
  }
});

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
