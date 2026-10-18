"use client";

import Link from "next/link";
import { useId, useRef, useState, type FormEvent } from "react";
import { api, ApiError, type FitCheckResult } from "@/lib/api";
import { saveHandoff } from "@/lib/fitCheckHandoff";
import { WaitlistForm } from "./WaitlistForm";

const MAX_BYTES = 5 * 1024 * 1024;
const MIN_DESCRIPTION = 200;
const ACCEPT = ".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/**
 * The home page's "try it" box: a resume and a pasted job in, a fit score, the skills that match and the ones
 * missing, and a few of the visitor's own lines tailored for the job out. No account needed and nothing is kept.
 */
export function FitCheck({ inviteOnly, job }: { inviteOnly: boolean; job?: { id: string; label: string } | null }) {
  const id = useId();
  const fileInput = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<FitCheckResult | null>(null);
  const resultRef = useRef<HTMLDivElement>(null);

  function pick(f: File | undefined) {
    setError(null);
    if (!f) return;
    if (f.size > MAX_BYTES) {
      setError("Resumes must be 5 MB or smaller.");
      return;
    }
    setFile(f);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!file) return setError("Add your resume first (PDF or Word).");
    if (!job && description.trim().length < MIN_DESCRIPTION) return setError("Paste the whole job description: what the role does and what it asks for.");
    setBusy(true);
    setError(null);
    try {
      const r = await api.fitCheck(file, job ? "" : description.trim(), job ? undefined : title.trim() || undefined, job?.id);
      setResult(r);
      saveHandoff({ profile: r.profile, removed: r.removed, fileName: file.name });
      requestAnimationFrame(() => resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "That didn't work. Check your connection and try again.");
    } finally {
      setBusy(false);
    }
  }

  const tone = (s: number) => (s >= 75 ? "good" : s >= 55 ? "warn" : "plain");

  return (
    <div className="stack" style={{ gap: 20 }}>
      <form className="fitcheck-form" onSubmit={submit} aria-busy={busy}>
        <div className="stack" style={{ gap: 8 }}>
          <span className="fitcheck-label" id={`${id}-resume`}>1. Your resume</span>
          <input ref={fileInput} type="file" accept={ACCEPT} className="sr-only" tabIndex={-1} aria-hidden="true" onChange={(e) => pick(e.target.files?.[0])} />
          <button
            type="button"
            className={`fitcheck-drop${file ? " has-file" : ""}`}
            aria-describedby={`${id}-resume`}
            onClick={() => fileInput.current?.click()}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              pick(e.dataTransfer.files[0]);
            }}
          >
            {file ? (
              <>
                <strong>{file.name}</strong>
                <span className="small muted">Tap to choose another</span>
              </>
            ) : (
              <>
                <strong>Choose your resume</strong>
                <span className="small muted">PDF or Word, up to 5 MB</span>
              </>
            )}
          </button>
        </div>
        <div className="stack" style={{ gap: 8 }}>
          <span className="fitcheck-label">2. The job</span>
          {job ? (
            <div className="fitcheck-job">
              <strong>{job.label}</strong>
              <span className="small muted">We&apos;ll read the full description from the posting.</span>
            </div>
          ) : (
          <>
          <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Job title (optional)" aria-label="Job title (optional)" maxLength={120} />
          <textarea
            className="textarea"
            rows={6}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Paste the job description from LinkedIn, Naukri or the company's careers page"
            aria-label="Job description"
            maxLength={30000}
          />
          </>
          )}
        </div>
        <div className="fitcheck-submit">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? "Reading your resume and the job…" : "Check my fit"}
          </button>
          <span className="small muted">No sign-up. We don&apos;t keep your resume or the job.</span>
        </div>
        {error && (
          <p className="notice notice-bad" role="alert" style={{ margin: 0 }}>
            {error}
          </p>
        )}
      </form>

      {result && (
        <div ref={resultRef} className="card stack fitcheck-result" style={{ gap: 16 }} aria-live="polite">
          <div className="job-card" style={{ padding: 0, border: 0, boxShadow: "none" }}>
            <div className={`score score-${tone(result.score)}`} aria-label={`Fit score ${result.score} out of 100`}>
              <span className="score-value">{result.score}</span>
              <span className="score-label">FIT</span>
            </div>
            <div className="stack" style={{ gap: 6, minWidth: 0 }}>
              <h3 className="job-title" style={{ margin: 0 }}>{result.jobTitle}</h3>
              {result.reasons.length > 0 && (
                <ul className="reasons" style={{ margin: 0 }}>
                  {result.reasons.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {(result.matchedSkills.length > 0 || result.missingSkills.length > 0) && (
            <div className="grid-2" style={{ gap: 12 }}>
              {result.matchedSkills.length > 0 && (
                <div className="stack" style={{ gap: 6 }}>
                  <span className="bullet-label">YOUR SKILLS IT ASKS FOR</span>
                  <div className="chips">
                    {result.matchedSkills.map((s) => (
                      <span key={s} className="chip chip-plain">{s}</span>
                    ))}
                  </div>
                </div>
              )}
              {result.missingSkills.length > 0 && (
                <div className="stack" style={{ gap: 6 }}>
                  <span className="bullet-label">IT ASKS FOR, YOUR RESUME DOESN&apos;T SHOW</span>
                  <div className="chips">
                    {result.missingSkills.map((s) => (
                      <span key={s} className="chip chip-missing">{s}</span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {result.bullets.length > 0 && (
            <div className="stack" style={{ gap: 10 }}>
              <span className="bullet-label">
                {result.bullets.some((b) => b.reworded) ? "YOUR LINES, TAILORED FOR THIS JOB" : "THE LINES THAT SHOULD LEAD YOUR RESUME FOR THIS JOB"}
              </span>
              {result.bullets.map((b) => (
                <div key={b.original} className="fitcheck-line">
                  {b.reworded && <p className="bullet-original">{b.original}</p>}
                  <p style={{ margin: 0 }}>{b.tailored}</p>
                </div>
              ))}
              <span className="landing-check">✓ Every fact is from your resume</span>
            </div>
          )}

          <div className="fitcheck-cta stack" style={{ gap: 10 }}>
            <strong>Get jobs like this matched to you every morning, and tailor a full resume and cover letter for each one.</strong>
            {inviteOnly ? (
              <WaitlistForm />
            ) : (
              <div className="row">
                <Link href="/login" className="btn btn-primary">Continue free with your email</Link>
                <span className="small muted">Your resume details carry over, so you won&apos;t upload it again.</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
