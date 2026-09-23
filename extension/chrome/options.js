// Options page: port + token, stored in chrome.storage.local. Saving
// triggers the service worker's storage listener, which reconnects.
// [impl->dsn~chrome-extension-mv3~1]

const browser = globalThis.browser ?? globalThis.chrome;

async function restore() {
  const { port, token } = await browser.storage.local.get({ port: 17872, token: "" });
  document.getElementById("port").value = port;
  document.getElementById("token").value = token;
}

async function save() {
  await browser.storage.local.set({
    port: Number(document.getElementById("port").value) || 17872,
    token: document.getElementById("token").value.trim(),
  });
  const status = document.getElementById("status");
  status.textContent = "Saved.";
  setTimeout(() => { status.textContent = ""; }, 2000);
}

document.addEventListener("DOMContentLoaded", restore);
document.getElementById("save").addEventListener("click", save);
