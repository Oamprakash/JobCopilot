/*
 * Field matching for Apply Copilot's autofill: decides what a form field asks for from its label, name, id,
 * placeholder and similar hints, and finds the tailored answer for a free-text question.
 * Plain script (no imports) so it can be injected into pages; also loads in Node for tests.
 */
var JobCopilotMatch = (function () {
  "use strict";

  /** Turns hints like "legalNameSection_firstName" or "urls[LinkedIn]" into "legal name section first name urls linkedin". */
  function words(text) {
    return String(text || "")
      .replace(/([a-z])([A-Z])/g, "$1 $2")
      .replace(/[_\-\[\]().:*?/\\#]+/g, " ")
      .toLowerCase()
      .replace(/\s+/g, " ")
      .trim();
  }

  // Order matters: specific fields first, so "LinkedIn profile URL" isn't taken for a website and
  // "Company name" isn't taken for the applicant's name.
  var rules = [
    ["coverLetter", /\bcover ?letter\b|\bmotivation(al)? letter\b/],
    ["linkedin", /\blinked ?in\b/],
    ["github", /\bgit ?hub\b/],
    ["website", /\b(website|portfolio|personal (site|url|page)|blog|other (url|link)s?|urls other)\b/],
    ["email", /\be ?mail\b/],
    ["phone", /\b(phone|mobile|cell|telephone|contact number|whatsapp)\b/],
    ["firstName", /\b(first|given|fore) ?name\b|\bfname\b/],
    ["lastName", /\b(last|family|sur) ?name\b|\bsurname\b|\blname\b/],
    ["currentCompany", /\b(current|present|latest|most recent) (company|employer|organi[sz]ation)\b|\bcompany name\b|^org$|\bemployer\b/],
    ["currentTitle", /\b(current|present|latest|most recent) (job )?(title|role|position|designation)\b|^job title$|^title$/],
    ["location", /\b(location|current city|city|where are you (based|located)|town)\b/],
    ["fullName", /\b(full|legal|your) name\b|^name$|\bsystemfield name\b|^candidate name$/],
  ];

  // Labels that look like a field above but are really questions to leave for the user.
  var notAFieldWe = /\b(relocat|willing|sponsor|visa|salary|ctc|compensation|notice|referr|reference|hear about|how did you|pronoun|gender|race|ethnic|veteran|disab|school|university|college|degree|manager|recruiter)\w*/;

  /** The profile field a form field asks for, or null. */
  function classify(hints) {
    var w = words(hints);
    if (!w) return null;
    if (/\bcover ?letter\b/.test(w)) return "coverLetter";
    if (notAFieldWe.test(w)) return null;
    for (var i = 0; i < rules.length; i++) {
      if (rules[i][1].test(w)) return rules[i][0];
    }
    return null;
  }

  /** True when a file input is for the resume / CV (not a cover letter or other attachment). */
  function isResumeUpload(hints) {
    var w = words(hints);
    return /\b(resume|résumé|cv|curriculum vitae)\b/.test(w) && !/\bcover\b/.test(w);
  }

  var stop = { a: 1, an: 1, the: 1, to: 1, of: 1, in: 1, for: 1, and: 1, or: 1, is: 1, are: 1, you: 1, your: 1, do: 1, what: 1, why: 1, how: 1, this: 1, with: 1, at: 1, on: 1, we: 1, our: 1, us: 1, me: 1, please: 1, describe: 1, tell: 1, about: 1 };

  function keywords(text) {
    return words(text).split(" ").filter(function (t) { return t.length > 1 && !stop[t]; });
  }

  /** How alike two questions are, 0..1 (Dice coefficient over their key words). */
  function similarity(a, b) {
    var x = keywords(a), y = keywords(b);
    if (!x.length || !y.length) return 0;
    var setY = {};
    y.forEach(function (t) { setY[t] = 1; });
    var shared = 0, seen = {};
    x.forEach(function (t) { if (setY[t] && !seen[t]) { shared++; seen[t] = 1; } });
    var ux = Object.keys(x.reduce(function (m, t) { m[t] = 1; return m; }, {})).length;
    return (2 * shared) / (ux + y.length);
  }

  /** The tailored answer whose question best matches a field's label, if it's a close enough match. */
  function pickAnswer(label, answers, threshold) {
    var best = null, bestScore = 0, min = threshold == null ? 0.6 : threshold;
    (answers || []).forEach(function (a) {
      var s = similarity(label, a.question);
      if (s > bestScore) { best = a; bestScore = s; }
    });
    return bestScore >= min ? best : null;
  }

  return { words: words, classify: classify, isResumeUpload: isResumeUpload, similarity: similarity, pickAnswer: pickAnswer };
})();

if (typeof module !== "undefined" && module.exports) module.exports = JobCopilotMatch;
