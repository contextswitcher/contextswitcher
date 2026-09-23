// Options page: port + token, stored in browser.storage.local. Saving
// triggers the background script's storage listener, which reconnects.
// [impl->dsn~firefox-extension-mv2~4]

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
