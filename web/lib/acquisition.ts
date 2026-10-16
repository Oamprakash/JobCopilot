/**
 * Where a visitor first came from, remembered in this browser until they sign up: the campaign tags in the link
 * (utm_source, utm_medium, utm_campaign), the referring site's host and the landing page's path. Nothing else is
 * kept. Sent once after sign-in (and with a waitlist request) so the admin page can show which channels bring
 * people. Storage can be blocked or cleared; then the signup simply shows as "unknown".
 */
export type Acquisition = { source?: string; medium?: string; campaign?: string; referrer?: string; landing?: string };

const KEY = "ta.acq";
const SENT_KEY = "ta.acq.sent";

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Blocked storage: this visit just isn't attributed.
  }
}

/** Records the first visit's source. Later visits don't replace it, so the channel that first brought someone counts. */
export function captureAcquisition() {
  if (typeof window === "undefined" || read(KEY)) return;
  const params = new URLSearchParams(window.location.search);
  let referrer: string | undefined;
  try {
    const host = document.referrer ? new URL(document.referrer).hostname : "";
    if (host && host !== window.location.hostname) referrer = host;
  } catch {
    // An unreadable referrer counts as none.
  }
  const a: Acquisition = {
    source: params.get("utm_source") ?? params.get("ref") ?? undefined,
    medium: params.get("utm_medium") ?? undefined,
    campaign: params.get("utm_campaign") ?? undefined,
    referrer,
    landing: window.location.pathname,
  };
  write(KEY, JSON.stringify(a));
}

export function storedAcquisition(): Acquisition | undefined {
  const raw = read(KEY);
  if (!raw) return undefined;
  try {
    return JSON.parse(raw) as Acquisition;
  } catch {
    return undefined;
  }
}

/** True once this browser has reported for [userId]. */
export function acquisitionSentFor(userId: string): boolean {
  return read(SENT_KEY) === userId;
}

export function markAcquisitionSent(userId: string) {
  write(SENT_KEY, userId);
}
