"use client";

import { useEffect, useState } from "react";

/**
 * Shown while the feed is being rebuilt. The server answers in one request, so the steps below follow the
 * real order the matcher works in, paced by elapsed time; the last step (detailed scoring) is the slow one
 * and stays active until the answer arrives. The bar eases towards the end but never claims to be done.
 */
const STEPS: { label: string; at: number }[] = [
  { label: "Reading today's open jobs", at: 0 },
  { label: "Keeping the ones that fit your roles, places and experience", at: 2 },
  { label: "Ranking them by your skills", at: 5 },
  { label: "Scoring the best ones in detail, with reasons", at: 8 },
];

const TIPS = [
  "Every match comes with the reasons it fits and the skills it asks for that you don't list.",
  "Tailored resumes only use what's in your profile. Nothing is made up.",
  "Save the jobs you like. You can tailor and apply to them later.",
  "Not seeing the right roles? Add more titles or cities under Preferences.",
  "New matches arrive by email each morning, so you don't need to keep checking.",
  "The Chrome extension fills in Greenhouse, Lever, Ashby and Workday forms for you.",
];

function useElapsed(): number {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const started = Date.now();
    const t = setInterval(() => setSeconds(Math.floor((Date.now() - started) / 1000)), 1000);
    return () => clearInterval(t);
  }, []);
  return seconds;
}

function stepAt(seconds: number): number {
  let i = 0;
  STEPS.forEach((s, n) => {
    if (seconds >= s.at) i = n;
  });
  return i;
}

/** Eases from 0 towards 92% over about a minute; the real finish replaces the panel. */
function percent(seconds: number): number {
  return Math.round(92 * (1 - Math.exp(-seconds / 18)));
}

/** Full panel with steps, a tip and placeholder cards: for when there's nothing on screen yet. */
export function MatchingProgress() {
  const seconds = useElapsed();
  const current = stepAt(seconds);
  const tip = TIPS[Math.floor(seconds / 7) % TIPS.length];

  return (
    <div className="stack" style={{ gap: 16 }}>
      <section className="card stack matching" style={{ gap: 14 }} aria-busy="true">
        <div className="row" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
          <h2 style={{ margin: 0 }}>Finding your matches</h2>
          <span className="small muted" aria-hidden="true">{seconds}s</span>
        </div>
        <div className="progress" role="progressbar" aria-label="Finding matches" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent(seconds)}>
          <span style={{ width: `${percent(seconds)}%` }} />
        </div>
        <ol className="matching-steps" aria-live="polite">
          {STEPS.map((s, i) => {
            const state = i < current ? "done" : i === current ? "active" : "todo";
            return (
              <li key={s.label} className={`matching-step ${state}`} aria-current={state === "active" ? "step" : undefined}>
                <span className="matching-dot" aria-hidden="true">{state === "done" ? "✓" : ""}</span>
                <span>{s.label}</span>
              </li>
            );
          })}
        </ol>
        <p className="small muted" style={{ margin: 0 }}>
          {seconds < 25
            ? "This usually takes under a minute."
            : "Still going. There are a lot of new jobs to check today, so this can take a little over a minute."}
        </p>
        <p className="matching-tip small" key={tip}>
          <strong>Tip:</strong> {tip}
        </p>
      </section>
      {[0, 1, 2].map((i) => (
        <div key={i} className="card job-card skeleton-card" aria-hidden="true">
          <span className="skeleton skeleton-score" />
          <div className="stack" style={{ gap: 10, flex: 1 }}>
            <span className="skeleton" style={{ width: "55%", height: 18 }} />
            <span className="skeleton" style={{ width: "35%" }} />
            <span className="skeleton" style={{ width: "80%" }} />
            <span className="skeleton" style={{ width: "70%" }} />
          </div>
        </div>
      ))}
    </div>
  );
}

/** A slim bar above the existing list, so people can keep browsing while it updates. */
export function MatchingBanner() {
  const seconds = useElapsed();
  const current = stepAt(seconds);
  return (
    <div className="card tight matching matching-banner" aria-busy="true">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 12 }}>
        <span className="small" aria-live="polite">
          <strong>Updating your matches.</strong> {STEPS[current].label}…
        </span>
        <span className="small muted" style={{ flex: "none" }} aria-hidden="true">{seconds}s</span>
      </div>
      <div className="progress" role="progressbar" aria-label="Updating matches" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent(seconds)}>
        <span style={{ width: `${percent(seconds)}%` }} />
      </div>
    </div>
  );
}

/** Placeholder cards for the first load, before the feed arrives. */
export function FeedSkeleton() {
  return (
    <div className="stack" style={{ gap: 16 }} aria-busy="true" aria-label="Loading your matches">
      {[0, 1, 2].map((i) => (
        <div key={i} className="card job-card skeleton-card" aria-hidden="true">
          <span className="skeleton skeleton-score" />
          <div className="stack" style={{ gap: 10, flex: 1 }}>
            <span className="skeleton" style={{ width: "55%", height: 18 }} />
            <span className="skeleton" style={{ width: "35%" }} />
            <span className="skeleton" style={{ width: "80%" }} />
          </div>
        </div>
      ))}
    </div>
  );
}
