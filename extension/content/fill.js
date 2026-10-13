/*
 * Fills the application form in this frame from Apply Copilot data. Injected on demand by the extension popup
 * (after lib/match.js). Fills empty fields only, highlights what it filled, and never clicks or submits anything.
 */
(function () {
  "use strict";
  if (window.__jobcopilotFill) return;

  var M = window.JobCopilotMatch;
  var HIGHLIGHT = "0 0 0 2px #1f4fe0";

  function visible(el) {
    if (el.disabled) return false;
    // File inputs are usually hidden behind a styled "Attach" button, so they count even when hidden.
    if (el.type === "file") return true;
    if (el.readOnly) return false;
    var style = window.getComputedStyle(el);
    if (style.visibility === "hidden" || style.display === "none") return false;
    return el.offsetParent !== null || el.getClientRects().length > 0;
  }

  function textOf(el) {
    return el ? (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim() : "";
  }

  /** Everything that says what a field is for: its label(s), aria text, placeholder, name, id and nearby question text. */
  function hintsFor(el) {
    var parts = [];
    if (el.id) {
      try {
        document.querySelectorAll('label[for="' + CSS.escape(el.id) + '"]').forEach(function (l) { parts.push(textOf(l)); });
      } catch (e) { /* odd ids */ }
    }
    var wrapping = el.closest("label");
    if (wrapping) parts.push(textOf(wrapping));
    parts.push(el.getAttribute("aria-label") || "");
    var labelledBy = el.getAttribute("aria-labelledby");
    if (labelledBy) labelledBy.split(/\s+/).forEach(function (id) { parts.push(textOf(document.getElementById(id))); });
    parts.push(el.getAttribute("placeholder") || "", el.name || "", el.id || "", el.getAttribute("data-automation-id") || "", el.getAttribute("autocomplete") || "");
    if (parts.join("").replace(/\s/g, "").length < 3 || el.type === "file" || el.tagName === "TEXTAREA") {
      // ATS forms often put the question in a sibling element of a wrapper: Greenhouse .field, Lever .application-question,
      // Ashby and Workday wrappers. Take the nearest wrapper's own label-ish text.
      var box = el.closest(".field, .application-question, .application-field, [data-automation-id^='formField'], fieldset, li, [class*='question'], [class*='Field']");
      if (box) {
        var label = box.querySelector("label, legend, .application-label, [class*='label'], [class*='Label']");
        parts.push(textOf(label || box).slice(0, 300));
      }
    }
    return parts.filter(Boolean).join(" ");
  }

  /** Sets a value the way typing would, so React/Vue-controlled inputs (Ashby, Workday, new Greenhouse) keep it. */
  function setValue(el, value) {
    var proto = el.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, "value").set;
    el.focus();
    setter.call(el, value);
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    el.dispatchEvent(new Event("blur", { bubbles: true }));
  }

  function mark(el) {
    el.style.boxShadow = HIGHLIGHT;
    el.setAttribute("data-jobcopilot-filled", "1");
  }

  function base64ToFile(b64, name) {
    var bin = atob(b64), bytes = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new File([bytes], name, { type: "application/pdf" });
  }

  function attach(input, file) {
    try {
      var dt = new DataTransfer();
      dt.items.add(file);
      input.files = dt.files;
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
      return input.files && input.files.length === 1;
    } catch (e) {
      return false;
    }
  }

  function valueFor(key, data) {
    var p = data.profile || {};
    switch (key) {
      case "coverLetter": return data.application ? data.application.coverLetter : "";
      case "currentTitle": return p.currentTitle;
      case "currentCompany": return p.currentCompany;
      default: return p[key];
    }
  }

  function toast(message) {
    if (window.top !== window) return;
    var box = document.createElement("div");
    box.setAttribute("role", "status");
    box.textContent = message;
    box.style.cssText = "position:fixed;z-index:2147483647;right:16px;bottom:16px;max-width:360px;padding:12px 16px;border-radius:10px;" +
      "background:#14213d;color:#fff;font:14px/1.45 system-ui,sans-serif;box-shadow:0 8px 24px rgba(0,0,0,.25)";
    document.body.appendChild(box);
    setTimeout(function () { box.remove(); }, 9000);
  }

  window.__jobcopilotFill = function (data) {
    var filled = 0, resumeAttached = false, skipped = 0;
    var answers = (data.application && data.application.answers) || [];
    var resume = data.resume ? base64ToFile(data.resume.base64, data.resume.fileName) : null;

    document.querySelectorAll("input, textarea").forEach(function (el) {
      var type = (el.type || "text").toLowerCase();
      if (["hidden", "submit", "button", "checkbox", "radio", "password", "search", "image", "reset", "range", "color", "date"].indexOf(type) >= 0) return;
      if (!visible(el)) return;
      var hints = hintsFor(el);

      if (type === "file") {
        if (resume && !resumeAttached && (!el.files || el.files.length === 0) && M.isResumeUpload(hints)) {
          if (attach(el, resume)) { resumeAttached = true; filled++; mark(el.parentElement || el); }
        }
        return;
      }
      if (el.value && el.value.trim()) { skipped++; return; } // never overwrite what's there

      var key = M.classify(hints);
      var value = null;
      if (key === "coverLetter") {
        if (el.tagName === "TEXTAREA") value = valueFor(key, data);
      } else if (key) {
        value = valueFor(key, data);
      } else {
        var answer = M.pickAnswer(hints, answers);
        if (answer) value = answer.answer;
      }
      if (value) {
        setValue(el, String(value));
        mark(el);
        filled++;
      }
    });

    if (filled > 0) {
      toast(chrome.runtime.getManifest().name + " filled " + filled + " field" + (filled === 1 ? "" : "s") + (resumeAttached ? ", including your resume" : "") +
        ". Check each one, answer the rest, then click Submit yourself.");
    }
    return { filled: filled, resumeAttached: resumeAttached, skipped: skipped, frame: location.host };
  };
})();
