/*
 * TrulyApply extension service worker: holds the connection to your TrulyApply account and makes every
 * API call, so the token never reaches web pages.
 */
"use strict";

async function settings() {
  return chrome.storage.local.get({ baseUrl: "", token: "", email: "" });
}

async function api(path, options = {}) {
  const { baseUrl, token } = await settings();
  if (!baseUrl) throw new Error("Connect the extension to your " + chrome.runtime.getManifest().name + " account first.");
  const headers = Object.assign({}, options.headers || {});
  if (token) headers.Authorization = "Bearer " + token;
  if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
  let response;
  try {
    response = await fetch(baseUrl + "/api" + path, Object.assign({}, options, { headers }));
  } catch (e) {
    throw new Error("Couldn't reach " + baseUrl + ". If it's on Render's free plan it may be waking up: try again in a minute.");
  }
  if (response.status === 401 && token) {
    await chrome.storage.local.set({ token: "", email: "" });
  }
  if (!response.ok) {
    let message = "Something went wrong (" + response.status + ")";
    try { message = (await response.json()).error || message; } catch (e) { /* not JSON */ }
    throw new Error(message);
  }
  return response;
}

async function pdfAsBase64(path) {
  const response = await api(path);
  const bytes = new Uint8Array(await response.arrayBuffer());
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

const handlers = {
  async pair({ baseUrl, code }) {
    await chrome.storage.local.set({ baseUrl, token: "", email: "" });
    const r = await (await api("/ext/pair", { method: "POST", body: JSON.stringify({ code }) })).json();
    await chrome.storage.local.set({ token: r.token, email: r.email });
    return { email: r.email };
  },
  async status() {
    const s = await settings();
    return { baseUrl: s.baseUrl, email: s.email, connected: !!s.token };
  },
  async disconnect() {
    await chrome.storage.local.set({ token: "", email: "" });
    return {};
  },
  async autofillData({ url, jobId }) {
    const q = "url=" + encodeURIComponent(url || "") + (jobId ? "&jobId=" + encodeURIComponent(jobId) : "");
    const data = await (await api("/ext/autofill?" + q)).json();
    if (data.application) {
      try {
        data.resume = { base64: await pdfAsBase64(data.application.resumePath), fileName: data.application.resumeFileName };
      } catch (e) {
        data.resumeError = e.message;
      }
    }
    return data;
  },
  async save(job) {
    return (await api("/ext/save", { method: "POST", body: JSON.stringify(job) })).json();
  },
  async applied({ jobId }) {
    return (await api("/ext/applied", { method: "POST", body: JSON.stringify({ jobId }) })).json();
  },
};

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  // Only the extension's own pages (the popup) talk to the worker; web pages and content scripts can't.
  const ownPage = "chrome-extension://" + chrome.runtime.id + "/";
  if (sender.id !== chrome.runtime.id || !(sender.url || "").startsWith(ownPage)) return false;
  const handler = handlers[message && message.type];
  if (!handler) return false;
  handler(message.payload || {})
    .then((result) => sendResponse({ ok: true, result }))
    .catch((e) => sendResponse({ ok: false, error: e.message || String(e) }));
  return true; // async response
});
