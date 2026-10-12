"use strict";

const $ = (id) => document.getElementById(id);
let tab = null;
let data = null; // autofill data for the current page
let savedJobId = null;

function send(type, payload) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage({ type, payload }, (response) => {
      if (chrome.runtime.lastError) return reject(new Error(chrome.runtime.lastError.message));
      if (!response) return reject(new Error("No response from the extension"));
      response.ok ? resolve(response.result) : reject(new Error(response.error));
    });
  });
}

function say(text, tone) {
  const m = $("message");
  m.hidden = !text;
  m.textContent = text || "";
  m.className = tone || "";
}

function busy(button, label) {
  const original = button.textContent;
  button.disabled = true;
  button.textContent = label;
  return () => { button.disabled = false; button.textContent = original; };
}

function normalizeBase(raw) {
  let url = (raw || "").trim().replace(/\/+$/, "");
  if (!/^https?:\/\//.test(url)) url = "https://" + url;
  const parsed = new URL(url);
  const local = parsed.hostname === "localhost" || parsed.hostname === "127.0.0.1";
  if (parsed.protocol !== "https:" && !local) throw new Error("Use the https:// address of your Apply Copilot app.");
  return parsed.origin;
}

async function showConnect(status) {
  $("connect").hidden = false;
  $("main").hidden = true;
  $("who").textContent = "";
  if (status && status.baseUrl) $("baseUrl").value = status.baseUrl;
}

function describe() {
  const info = $("pageInfo");
  $("applied").hidden = true;
  if (!data) { info.textContent = ""; return; }
  if (data.application) {
    const a = data.application;
    info.textContent = "Using your " + (a.status === "approved" ? "approved" : "draft") + " application for " + a.title + " at " + a.company +
      (data.resume ? " (resume attached)" : "") + (data.trackerStage ? " · " + data.trackerStage : "");
    $("applied").hidden = false;
  } else {
    info.textContent = data.recent && data.recent.length
      ? "This page doesn't match a tailored application. Pick one below, or fill just your details."
      : "No tailored application yet: this fills your details only. Tailor one in the app for answers and a resume.";
  }
  const pick = $("pickApp");
  if (data.recent && data.recent.length && !$("pick").dataset.ready) {
    pick.innerHTML = "";
    const none = document.createElement("option");
    none.value = "";
    none.textContent = "Only my details";
    pick.appendChild(none);
    data.recent.forEach((r) => {
      const o = document.createElement("option");
      o.value = r.jobId;
      o.textContent = r.title + " · " + r.company + (r.status === "approved" ? "" : " (draft)");
      pick.appendChild(o);
    });
    $("pick").dataset.ready = "1";
  }
  $("pick").hidden = !(data.recent && data.recent.length);
  if (data.application) pick.value = data.application.jobId;
}

async function load(jobId) {
  try {
    data = await send("autofillData", { url: tab.url, jobId });
    describe();
    if (data.resumeError) say("Couldn't fetch your tailored resume: " + data.resumeError, "bad");
  } catch (e) {
    data = null;
    describe();
    say(e.message, "bad");
    const status = await send("status");
    if (!status.connected) showConnect(status);
  }
}

async function init() {
  [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const status = await send("status");
  if (!status.connected) return showConnect(status);
  $("connect").hidden = true;
  $("main").hidden = false;
  $("who").textContent = status.email;
  if (!tab || !/^https?:/.test(tab.url || "")) {
    $("pageInfo").textContent = "Open a job page or application form, then use these buttons.";
    $("fill").disabled = true;
    $("save").disabled = true;
    return;
  }
  await load();
}

$("pair").addEventListener("click", async () => {
  const done = busy($("pair"), "Connecting…");
  say("");
  try {
    const base = normalizeBase($("baseUrl").value);
    // Permission for just your app's address, asked for once.
    const granted = await chrome.permissions.request({ origins: [base + "/*"] });
    if (!granted) throw new Error("The extension needs permission to reach " + base + ".");
    const r = await send("pair", { baseUrl: base, code: $("code").value });
    say("Connected as " + r.email + ".", "good");
    $("code").value = "";
    await init();
  } catch (e) {
    say(e.message, "bad");
  } finally {
    done();
  }
});

$("pickApp").addEventListener("change", (e) => load(e.target.value || undefined));

$("fill").addEventListener("click", async () => {
  const done = busy($("fill"), "Filling…");
  say("");
  try {
    if (!data) await load();
    if (!data) return;
    const payload = Object.assign({}, data);
    if ($("pickApp").value === "" && !$("pick").hidden) { payload.application = null; payload.resume = null; }
    const target = { tabId: tab.id, allFrames: true };
    await chrome.scripting.executeScript({ target, files: ["lib/match.js", "content/fill.js"] });
    const results = await chrome.scripting.executeScript({
      target,
      func: (d) => (window.__jobcopilotFill ? window.__jobcopilotFill(d) : null),
      args: [payload],
    });
    const totals = results.map((r) => r.result).filter(Boolean);
    const filled = totals.reduce((n, r) => n + r.filled, 0);
    const resume = totals.some((r) => r.resumeAttached);
    if (filled === 0) {
      say("I didn't find empty fields I recognise here. If the form is on another step or opens after a click, open it and try again.");
    } else {
      say("Filled " + filled + " field" + (filled === 1 ? "" : "s") + (resume ? " and attached your resume" : "") +
        ". Check everything and answer the rest, then click Submit yourself.", "good");
    }
  } catch (e) {
    say(/Cannot access|cannot be scripted/i.test(e.message) ? "This page can't be filled by extensions." : e.message, "bad");
  } finally {
    done();
  }
});

$("save").addEventListener("click", async () => {
  const done = busy($("save"), "Saving…");
  say("");
  try {
    const [r] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["content/extract.js"] });
    const job = r && r.result;
    if (!job || !job.title) throw new Error("I couldn't find a job title on this page.");
    const saved = await send("save", job);
    savedJobId = saved.jobId;
    $("applied").hidden = false;
    say((saved.alreadyTracked ? "Already in your tracker" : "Saved to your tracker") + ": " + job.title + (job.company ? " at " + job.company : "") + ".", "good");
  } catch (e) {
    say(e.message, "bad");
  } finally {
    done();
  }
});

$("applied").addEventListener("click", async () => {
  const jobId = (data && data.application && data.application.jobId) || savedJobId;
  if (!jobId) return say("Save this job first, then mark it as applied.");
  const done = busy($("applied"), "Saving…");
  try {
    await send("applied", { jobId });
    say("Marked as applied. You'll get a reminder to follow up in a week.", "good");
  } catch (e) {
    say(e.message, "bad");
  } finally {
    done();
  }
});

$("openTracker").addEventListener("click", async (e) => {
  e.preventDefault();
  const status = await send("status");
  if (status.baseUrl) chrome.tabs.create({ url: status.baseUrl + "/tracker" });
});

$("disconnect").addEventListener("click", async (e) => {
  e.preventDefault();
  await send("disconnect");
  data = null;
  say("Disconnected. Connect again with a new code from your profile.");
  showConnect(await send("status"));
});

init().catch((e) => say(e.message, "bad"));
