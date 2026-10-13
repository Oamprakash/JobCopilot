"use client";

import { useEffect, useState } from "react";
import { Problems } from "@/components/Problems";
import { ApiError, api, type InterviewPrepView } from "@/lib/api";

const kindLabels: Record<string, string> = {
  role: "About you",
  skill: "Skill",
  gap: "Gap",
  behavioural: "Behavioural",
  company: "Company",
};

/**
 * Likely interview questions for one tracked job, each with the user's own resume bullets to draw on.
 * Loads a saved prep when opened; "Prepare" makes one (or a fresh one).
 */
export function InterviewPrepPanel({ jobId, company }: { jobId: string; company: string }) {
  const [view, setView] = useState<InterviewPrepView | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    api
      .interviewPrep(jobId)
      .then(setView)
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 404)) setError(e);
      })
      .finally(() => setLoaded(true));
  }, [jobId]);

  async function prepare() {
    setBusy(true);
    setError(null);
    try {
      setView(await api.prepareInterview(jobId));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (!loaded) return <p className="muted small">Loading interview prep…</p>;

  return (
    <section className="prep stack" style={{ gap: 12 }} aria-label={`Interview prep for ${company}`}>
      <Problems error={error} />
      {!view ? (
        <div className="stack" style={{ gap: 8 }}>
          <p className="small" style={{ margin: 0 }}>
            Get the questions {company} is likely to ask, with the parts of your own experience that answer each one.
          </p>
          <div>
            <button type="button" className="btn btn-primary" disabled={busy} onClick={prepare}>
              {busy ? "Preparing…" : "Prepare for this interview"}
            </button>
          </div>
        </div>
      ) : (
        <>
          <ol className="prep-list">
            {view.prep.questions.map((q, i) => (
              <li key={i} className="prep-item">
                <div className="row" style={{ gap: 8, alignItems: "baseline" }}>
                  <strong>{q.question}</strong>
                  <span className="prep-kind">{kindLabels[q.kind] ?? q.kind}</span>
                </div>
                {q.why && <p className="muted small" style={{ margin: "4px 0 0" }}>{q.why}</p>}
                {q.points.length > 0 && (
                  <ul className="prep-points">
                    {q.points.map((p, j) => (
                      <li key={j}>
                        {p.text}
                        {p.role && <span className="muted"> · {p.role}</span>}
                      </li>
                    ))}
                  </ul>
                )}
                {q.tip && <p className="small prep-tip">{q.tip}</p>}
              </li>
            ))}
          </ol>
          {view.prep.gaps.length > 0 && (
            <p className="small" style={{ margin: 0 }}>
              <strong>Have an honest answer ready for:</strong> {view.prep.gaps.join(", ")}. The job mentions these and your profile doesn&apos;t
              show them; say what you&apos;ve done that&apos;s closest and how you&apos;d pick them up.
            </p>
          )}
          <div className="row small" style={{ gap: 8 }}>
            <span className="muted">
              {view.generator === "claude" ? "Written by AI from your profile and the job description." : "Built from your profile and the job description."} Talking points are your own resume lines.
            </span>
            <button type="button" className="btn btn-ghost small" disabled={busy} onClick={prepare}>
              {busy ? "Preparing…" : "Make a new set"}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
