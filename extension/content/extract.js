/*
 * Reads the job on the current page for "Save job": the schema.org JobPosting that most job pages publish for
 * search engines, then Open Graph tags, then the page title. Returns a plain object; changes nothing on the page.
 */
(function () {
  "use strict";
  function text(html) {
    // A detached element has no layout, so keep paragraph and list breaks by hand before reading the text.
    var marked = String(html || "")
      .replace(/<li[^>]*>/gi, "\n• ")
      .replace(/<(br|\/p|\/li|\/div|\/h[1-6]|\/ul|\/ol)[^>]*>/gi, "\n");
    var div = document.createElement("div");
    div.innerHTML = marked;
    return (div.textContent || "").replace(/[ \t]+\n/g, "\n").replace(/\n{3,}/g, "\n\n").trim();
  }
  function meta(name) {
    var el = document.querySelector('meta[property="' + name + '"], meta[name="' + name + '"]');
    return el ? el.getAttribute("content") || "" : "";
  }
  function findPosting(node) {
    if (!node || typeof node !== "object") return null;
    if (Array.isArray(node)) { for (var i = 0; i < node.length; i++) { var f = findPosting(node[i]); if (f) return f; } return null; }
    var type = node["@type"];
    if (type === "JobPosting" || (Array.isArray(type) && type.indexOf("JobPosting") >= 0)) return node;
    return findPosting(node["@graph"]);
  }
  var posting = null;
  document.querySelectorAll('script[type="application/ld+json"]').forEach(function (s) {
    if (posting) return;
    try { posting = findPosting(JSON.parse(s.textContent)); } catch (e) { /* not JSON */ }
  });

  var location = "";
  var company = "";
  var title = "";
  var description = "";
  if (posting) {
    title = posting.title || "";
    var org = posting.hiringOrganization;
    company = (org && (org.name || org)) || "";
    var loc = Array.isArray(posting.jobLocation) ? posting.jobLocation[0] : posting.jobLocation;
    var addr = loc && loc.address;
    if (addr) location = [addr.addressLocality, addr.addressRegion, addr.addressCountry && (addr.addressCountry.name || addr.addressCountry)].filter(Boolean).join(", ");
    if (posting.jobLocationType === "TELECOMMUTE") location = location ? location + " (Remote)" : "Remote";
    description = text(posting.description);
  }
  if (!title) title = meta("og:title") || document.title || "";
  if (!company) company = meta("og:site_name") || "";
  if (!description) description = meta("og:description") || meta("description") || "";
  var canonical = document.querySelector('link[rel="canonical"]');
  return {
    url: (canonical && canonical.href && /^https?:/.test(canonical.href)) ? canonical.href : window.location.href,
    title: String(title).replace(/\s+/g, " ").trim().slice(0, 300),
    company: String(company).replace(/\s+/g, " ").trim().slice(0, 200),
    location: String(location).slice(0, 300),
    description: String(description).slice(0, 30000),
    fromSchema: !!posting,
  };
})();
