import type { AdminEvent } from "./api";

/** Labels for activity types, as the dashboard shows them. */
export const eventLabels: Record<string, string> = {
  signed_in: "Sign-ins",
  resume_uploaded: "Resumes uploaded",
  profile_saved: "Profiles saved",
  preferences_saved: "Searches saved",
  feed_refreshed: "Feed refreshes",
  job_opened: "Job postings opened",
  job_saved: "Jobs saved",
  job_skipped: "Jobs skipped",
  job_restored: "Jobs restored",
  application_tailored: "Applications tailored",
  application_approved: "Applications approved",
  resume_downloaded: "Resume PDFs downloaded",
  apply_clicked: "Clicks to apply",
  extension_connected: "Extensions connected",
  extension_autofill: "Extension autofills",
  job_saved_from_page: "Jobs saved from a page",
  marked_applied: "Marked as applied",
  tracker_stage: "Tracker stage changes",
  digest_sent: "Daily match emails sent",
  digest_unsubscribed: "Daily email unsubscribes",
  interview_prep: "Interview preps",
  feedback_sent: "Feedback sent",
};

export const funnelLabels: [string, string][] = [
  ["signedUp", "Signed up"],
  ["profile", "Saved a profile"],
  ["refreshed", "Refreshed the feed"],
  ["tailored", "Tailored an application"],
  ["approved", "Approved one"],
  ["applyClicked", "Clicked through to apply"],
];

const list = (v: unknown) => (Array.isArray(v) ? v.filter((x) => typeof x === "string").join(", ") : "");

/** One line describing an event in a user's timeline. */
export function describe(e: AdminEvent): string {
  const d = e.data ?? {};
  const job = e.jobTitle ? `${e.jobTitle}${e.company ? ` at ${e.company}` : ""}` : "a job";
  switch (e.type) {
    case "signed_in":
      return "Signed in";
    case "resume_uploaded":
      return `Uploaded a resume (${d.parser === "claude" ? "parsed by Claude" : "rule-based parser"})`;
    case "profile_saved":
      return `Saved profile, version ${d.version ?? "?"}`;
    case "preferences_saved": {
      const roles = list(d.roles) || "no roles";
      const places = list(d.locations) || "anywhere in India";
      const extras = [d.remoteMode && d.remoteMode !== "any" ? String(d.remoteMode) : null, d.needsVisaSponsorship ? "needs visa sponsorship" : null]
        .filter(Boolean)
        .join(", ");
      return `Searched for ${roles} in ${places}${extras ? ` (${extras})` : ""}`;
    }
    case "feed_refreshed":
      return `Refreshed the feed: ${d.shown ?? 0} shown, ${d.passedFilter ?? 0} passed filters of ${d.considered ?? 0} checked`;
    case "job_opened":
      return `Opened the posting for ${job}`;
    case "job_saved":
      return `Saved ${job}`;
    case "job_skipped":
      return `Skipped ${job}`;
    case "job_restored":
      return `Moved ${job} back to New`;
    case "application_tailored":
      return `Tailored an application for ${job}${d.pastedDescription ? " (with a pasted description)" : ""}`;
    case "application_approved":
      return `Approved the application for ${job}`;
    case "resume_downloaded":
      return `Downloaded the resume PDF for ${job}`;
    case "apply_clicked":
      return `Went to the application page for ${job}`;
    case "extension_connected":
      return "Connected the browser extension";
    case "extension_autofill":
      return d.matched ? `Autofilled the form for ${job} with the extension` : "Used the extension's autofill with profile details only";
    case "job_saved_from_page":
      return "Saved a job from a web page with the extension";
    case "marked_applied":
      return `Marked ${job} as applied${d.via === "extension" ? " (extension)" : ""}`;
    case "tracker_stage":
      return d.from ? `Moved ${job} from ${d.from} to ${d.stage}` : `Added ${job} to the tracker as ${d.stage}`;
    case "digest_sent":
      return `Was emailed ${d.jobs ?? "some"} new match${d.jobs === 1 ? "" : "es"}`;
    case "feedback_sent":
      return `Sent feedback (${d.kind === "problem" ? "something broken" : d.kind ?? "other"})`;
    case "digest_unsubscribed":
      return "Unsubscribed from the daily match email";
    case "interview_prep":
      return `Prepared for an interview for ${job} (${d.questions ?? "?"} questions, ${d.generator === "claude" ? "Claude" : "rule-based"})`;
    default:
      return e.type;
  }
}

export function ago(iso: string | null): string {
  if (!iso) return "never";
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)} d ago`;
  return new Date(iso).toLocaleDateString();
}

export const when = (iso: string) => new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
