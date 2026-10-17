"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type FeedJob } from "@/lib/api";

const DISMISSED_KEY = "ta.firstTailor.dismissed";

/**
 * Nudges someone who hasn't tailored anything yet to try it on today's best match: one tailored application is
 * the moment the app's value is clear. Shows until they tailor one this month or tap "Not now".
 */
export function FirstTailorPrompt({ job, welcome }: { job: FeedJob | undefined; welcome: boolean }) {
  const [show, setShow] = useState(false);

  useEffect(() => {
    let dismissed = false;
    try {
      dismissed = localStorage.getItem(DISMISSED_KEY) === "1";
    } catch {
      // Storage blocked: decide from usage alone.
    }
    if (dismissed && !welcome) return;
    api
      .usage()
      .then((u) => setShow(u.tailoredThisMonth === 0))
      .catch(() => undefined);
  }, [welcome]);

  if (!show || !job || job.score < 50) return null;

  function dismiss() {
    setShow(false);
    try {
      localStorage.setItem(DISMISSED_KEY, "1");
    } catch {
      // Hidden for this visit only.
    }
  }

  return (
    <section className="card first-tailor" aria-labelledby="first-tailor-title">
      <div className="stack" style={{ gap: 6, minWidth: 0 }}>
        <span className="eyebrow-small">{welcome ? "You're all set" : "Try this next"}</span>
        <h2 id="first-tailor-title" style={{ margin: 0 }}>
          Your best match today: {job.title}
          {job.company ? ` at ${job.company}` : ""}, {job.score} fit
        </h2>
        <p className="muted small" style={{ margin: 0 }}>
          Tailor your first application: we shape your resume and cover letter for this job from your profile, without adding
          anything that isn&apos;t true. You review every line before you apply.
        </p>
      </div>
      <div className="first-tailor-actions">
        <Link href={`/apply/${job.jobId}`} className="btn btn-primary">Tailor it now</Link>
        <button type="button" className="btn btn-ghost" onClick={dismiss}>Not now</button>
      </div>
    </section>
  );
}
