import type { Profile } from "./api";

/**
 * Carries the profile read during the home page's fit check into onboarding, so a new user doesn't upload the
 * same resume twice. Kept in this browser only, for a few hours (the sign-in link often opens in a new tab, so
 * per-tab storage would lose it), and removed as soon as onboarding uses or discards it.
 */
const KEY = "ta.fitcheck.profile";
const MAX_AGE_MS = 3 * 60 * 60 * 1000;

export type Handoff = { profile: Profile; removed: string[]; fileName: string };

export function saveHandoff(h: Handoff) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ ...h, savedAt: Date.now() }));
  } catch {
    // Blocked storage: onboarding simply asks for the resume again.
  }
}

export function takeHandoff(): Handoff | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const h = JSON.parse(raw) as Handoff & { savedAt?: number };
    if (!h.savedAt || Date.now() - h.savedAt > MAX_AGE_MS || !h.profile) {
      localStorage.removeItem(KEY);
      return null;
    }
    return { profile: h.profile, removed: h.removed ?? [], fileName: h.fileName };
  } catch {
    return null;
  }
}

export function clearHandoff() {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // Nothing to clear.
  }
}
