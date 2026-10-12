"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { PartialDescriptionNote, SourceAttribution } from "@/components/SourceAttribution";
import { Problems } from "@/components/Problems";
import {
  api,
  ApiError,
  type ApplicationView,
  type ScreeningAnswer,
  type TailoredApplication,
  type TailoredBullet,
  type TailoredExperience,
} from "@/lib/api";
import { useMe } from "@/lib/useMe";

type Tab = "resume" | "letter" | "answers";

export default function ApplyPage() {
  const me = useMe();
  const params = useParams();
  const jobId = String(params.jobId);
  const [view, setView] = useState<ApplicationView | null>(null);
  const [draft, setDraft] = useState<TailoredApplication | null>(null);
  const [dirty, setDirty] = useState(false);
  const [tab, setTab] = useState<Tab>("resume");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [limitHit, setLimitHit] = useState<string | null>(null);
  const started = useRef(false);

  function show(v: ApplicationView) {
    setView(v);
    setDraft(v.application);
    setDirty(false);
  }

  async function generate(jobDescription?: string) {
    setBusy("Tailoring your resume, cover letter and answers…");
    setError(null);
    try {
      show(await api.tailor(jobId, undefined, jobDescription));
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) setLimitHit(e.message);
      else setError(e);
    } finally {
      setBusy(null);
    }
  }

  // Open the existing draft, or tailor one the first time this job is opened.
  useEffect(() => {
    if (!me || started.current) return;
    started.current = true;
    api
      .getApplication(jobId)
      .then(show)
      .catch((e) => {
        if (e instanceof ApiError && e.status === 404) generate();
        else setError(e);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me, jobId]);

  function edit(next: TailoredApplication) {
    setDraft(next);
    setDirty(true);
  }

  async function save() {
    if (!draft) return;
    setBusy("Saving…");
    setError(null);
    try {
      show(await api.saveApplication(jobId, draft));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  async function approve() {
    setBusy("Approving…");
    setError(null);
    try {
      if (dirty && draft) await api.saveApplication(jobId, draft);
      show(await api.approveApplication(jobId));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  async function regenerate(jobDescription?: string) {
    if (dirty && !window.confirm("Regenerating replaces your edits. Continue?")) return;
    await generate(jobDescription);
  }

  const header = <AppHeader email={me?.email} />;

  if (limitHit) {
    return (
      <>
        {header}
        <main className="narrow stack">
          <Link href="/feed" className="small">← Today&apos;s matches</Link>
          <section className="card stack">
            <h1>Monthly limit reached</h1>
            <p className="muted" style={{ margin: 0 }}>{limitHit}</p>
            <p className="small muted" style={{ margin: 0 }}>Jobs you&apos;ve already tailored stay open to review, edit and download.</p>
          </section>
        </main>
      </>
    );
  }

  if (!view || !draft) {
    return (
      <>
        {header}
        <main className="container stack">
          <Link href="/feed" className="small">← Today&apos;s matches</Link>
          <Problems error={error} />
          {!error && <div className="card muted">{busy ?? "Loading…"}</div>}
        </main>
      </>
    );
  }

  const approved = view.status === "approved";
  const answersToFill = draft.answers.filter((a) => a.needsUser && !a.answer.trim()).length;

  return (
    <>
      {header}
      <main className="container split" style={{ gap: 24 }}>
        <section className="main stack">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <Link href="/feed" className="small" style={{ fontWeight: 600 }}>← Today&apos;s matches</Link>
            <span className="small muted">{dirty ? "Unsaved changes" : `Saved · ${view.generator === "claude" ? "drafted by Claude" : "rule-based draft"}`}</span>
          </div>
          <div>
            <h1>Review before you apply</h1>
            <p className="muted" style={{ margin: "6px 0 0" }}>
              {view.job.title} · {view.job.company}
              {view.job.closed && <span className="badge-closed">No longer open</span>}
            </p>
            <SourceAttribution source={view.job.source ?? ""} />
          </div>
          {!view.pastedDescription && <PartialDescriptionNote partial={view.job.descriptionPartial ?? false} applyUrl={view.job.applyUrl} />}
          {(view.job.descriptionPartial || view.pastedDescription) && (
            <FullDescription
              key={view.pastedDescription ?? ""}
              pasted={view.pastedDescription ?? null}
              applyUrl={view.job.applyUrl}
              busy={!!busy}
              onUse={(text) => regenerate(text)}
            />
          )}

          <nav className="tabs" aria-label="Application parts">
            {(
              [
                ["resume", "Resume"],
                ["letter", "Cover letter"],
                ["answers", `Screening answers (${draft.answers.length})`],
              ] as [Tab, string][]
            ).map(([key, label]) => (
              <button key={key} type="button" className="tab" aria-pressed={tab === key} onClick={() => setTab(key)}>
                {label}
              </button>
            ))}
          </nav>

          <GuardBanner removed={view.removed} />
          {view.warnings.length > 0 && (
            <div className="notice notice-warn">
              Check your edits: these lines claim things your profile doesn&apos;t show.
              <ul>
                {view.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </ul>
            </div>
          )}
          <Problems error={error} />

          {tab === "resume" && <ResumeEditor app={draft} onChange={edit} />}
          {tab === "letter" && <LetterEditor app={draft} onChange={edit} />}
          {tab === "answers" && <AnswersEditor app={draft} onChange={edit} />}

          <div className="row">
            <button type="button" className="btn btn-primary" onClick={save} disabled={!dirty || !!busy}>
              {busy === "Saving…" ? "Saving…" : "Save edits"}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => regenerate()} disabled={!!busy}>
              Regenerate
            </button>
          </div>
        </section>

        <aside className="side stack" style={{ maxWidth: 360 }}>
          <section className="card stack" style={{ gap: 12 }}>
            {view.score != null && (
              <div className="row" style={{ gap: 12 }}>
                <span className={`score score-${view.score >= 75 ? "good" : view.score >= 55 ? "warn" : "plain"}`} style={{ width: 56, height: 56 }}>
                  <span className="score-value" style={{ fontSize: 20 }}>{view.score}</span>
                </span>
                <strong>{view.score >= 75 ? "Strong fit" : view.score >= 55 ? "Good fit" : "Partial fit"}</strong>
              </div>
            )}
            <h2>Keywords this version covers</h2>
            <div className="chips">
              {view.keywordsCovered.length ? view.keywordsCovered.map((k) => <span key={k} className="chip chip-plain">{k}</span>) : <span className="muted small">None detected</span>}
            </div>
            {view.keywordsMissing.length > 0 && (
              <p className="missing small">Not covered: {view.keywordsMissing.join(", ")} (not in your profile)</p>
            )}
          </section>

          <section className="card stack" style={{ gap: 12 }}>
            <h2>{approved ? "Approved" : "Ready to apply?"}</h2>
            {answersToFill > 0 && (
              <p className="notice notice-warn small" style={{ margin: 0 }}>
                {answersToFill} answer{answersToFill === 1 ? "" : "s"} left for you, like pay or notice period.
              </p>
            )}
            {!approved ? (
              <>
                <p className="muted small" style={{ margin: 0 }}>
                  Approving locks in this version. Nothing is sent anywhere: you apply on {view.job.company}&apos;s own site.
                </p>
                <button type="button" className="btn btn-primary btn-block" onClick={approve} disabled={!!busy}>
                  {busy === "Approving…" ? "Approving…" : "Approve"}
                </button>
              </>
            ) : (
              <>
                <a className="btn btn-primary btn-block" href={api.resumePdfUrl(jobId)} download>
                  Download resume PDF
                </a>
                <a className="btn btn-secondary btn-block" href={view.job.applyUrl} target="_blank" rel="noopener noreferrer">
                  Open application page
                </a>
                <p className="muted small" style={{ margin: 0 }}>
                  Copy the cover letter and answers from the tabs here. You click Submit on their site.
                </p>
              </>
            )}
            {view.usage.limit != null && (
              <p className="small muted" style={{ margin: 0 }}>
                Free plan · {Math.max(0, view.usage.limit - view.usage.tailoredThisMonth)} of {view.usage.limit} tailored applications left this month
              </p>
            )}
          </section>
        </aside>
      </main>
    </>
  );
}

const MIN_PASTED = 200;

/**
 * For jobs whose source gave only a summary: the user pastes the full posting and the draft is rebuilt from it.
 * "Use the summary" sends "" to go back to the source's text.
 */
function FullDescription({ pasted, applyUrl, busy, onUse }: { pasted: string | null; applyUrl: string; busy: boolean; onUse: (text: string) => void }) {
  const [text, setText] = useState(pasted ?? "");
  const [open, setOpen] = useState(pasted != null);
  const trimmed = text.trim();
  const tooShort = trimmed.length > 0 && trimmed.length < MIN_PASTED;
  const changed = trimmed !== (pasted ?? "");

  if (!open) {
    return (
      <div>
        <button type="button" className="btn btn-secondary" onClick={() => setOpen(true)}>
          Paste the full job description
        </button>
      </div>
    );
  }
  return (
    <section className="card stack" style={{ gap: 10 }}>
      <h2>Full job description</h2>
      <p className="muted small" style={{ margin: 0 }}>
        {pasted
          ? "This draft and its keywords are based on the description you pasted."
          : <>Copy everything from the <a href={applyUrl} target="_blank" rel="noopener noreferrer">job posting</a> and paste it here. The draft is rebuilt from it, which doesn&apos;t use another tailored application.</>}
      </p>
      <label className="field">
        <span className="sr-only">Full job description</span>
        <textarea className="textarea" rows={8} value={text} onChange={(e) => setText(e.target.value)} placeholder="Paste the whole posting: responsibilities, requirements, nice-to-haves…" />
      </label>
      {tooShort && <p className="small missing" style={{ margin: 0 }}>That looks too short for a full description ({trimmed.length} of at least {MIN_PASTED} characters).</p>}
      <div className="row">
        <button type="button" className="btn btn-primary" disabled={busy || !trimmed || tooShort || !changed} onClick={() => onUse(trimmed)}>
          {pasted ? "Re-tailor with this text" : "Re-tailor with full description"}
        </button>
        {pasted && (
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => onUse("")}>
            Use the summary instead
          </button>
        )}
      </div>
    </section>
  );
}

function GuardBanner({ removed }: { removed: string[] }) {
  if (removed.length === 0) {
    return (
      <div className="notice notice-good" style={{ fontWeight: 600 }}>
        No new claims. Every line traces to your master profile.
      </div>
    );
  }
  return (
    <details className="notice notice-warn">
      <summary style={{ cursor: "pointer", fontWeight: 600 }}>
        We kept {removed.length} thing{removed.length === 1 ? "" : "s"} the draft claimed out of your application
      </summary>
      <ul>
        {removed.map((r) => (
          <li key={r}>{r}</li>
        ))}
      </ul>
    </details>
  );
}

const changeLabel: Record<TailoredBullet["change"], string> = {
  moved_up: "MOVED UP",
  reworded: "REWORDED",
  unchanged: "UNCHANGED",
};

function ResumeEditor({ app, onChange }: { app: TailoredApplication; onChange: (a: TailoredApplication) => void }) {
  const resume = app.resume;
  const setRole = (i: number, role: TailoredExperience) =>
    onChange({ ...app, resume: { ...resume, experience: resume.experience.map((e, j) => (j === i ? role : e)) } });

  return (
    <div className="stack">
      <section className="card stack" style={{ gap: 10 }}>
        <h2>Summary</h2>
        <textarea
          className="textarea"
          value={resume.summary ?? ""}
          onChange={(e) => onChange({ ...app, resume: { ...resume, summary: e.target.value } })}
        />
        <h2 style={{ marginTop: 6 }}>Skills, most relevant first</h2>
        <div className="chips">
          {resume.skills.map((s) => (
            <span key={s} className="chip chip-plain">{s}</span>
          ))}
        </div>
      </section>

      {resume.experience.map((role, i) => (
        <article key={role.sourceIndex} className="card stack" style={{ gap: 12 }}>
          <div>
            <h2>
              {role.title} · {role.company}
            </h2>
            <p className="small muted" style={{ margin: "4px 0 0" }}>
              {[role.startDate, role.current ? "Present" : role.endDate].filter(Boolean).join(" – ")}
            </p>
          </div>
          {role.bullets.map((b, j) => (
            <div key={`${b.sourceIndex}-${j}`} className={`bullet bullet-${b.change}`}>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <span className="bullet-label">{changeLabel[b.change]}</span>
                <span className="row" style={{ gap: 4 }}>
                  <button
                    type="button"
                    className="btn btn-ghost small"
                    aria-label="Move bullet up"
                    disabled={j === 0}
                    onClick={() => {
                      const next = [...role.bullets];
                      [next[j - 1], next[j]] = [next[j], next[j - 1]];
                      setRole(i, { ...role, bullets: next });
                    }}
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost small"
                    aria-label="Remove bullet"
                    onClick={() => setRole(i, { ...role, bullets: role.bullets.filter((_, k) => k !== j) })}
                  >
                    Remove
                  </button>
                </span>
              </div>
              {b.change === "reworded" && b.original && <p className="bullet-original">{b.original}</p>}
              <textarea
                className="textarea bullet-text"
                rows={2}
                aria-label={`Bullet ${j + 1} at ${role.company}`}
                value={b.text}
                onChange={(e) => setRole(i, { ...role, bullets: role.bullets.map((x, k) => (k === j ? { ...x, text: e.target.value } : x)) })}
              />
            </div>
          ))}
        </article>
      ))}
    </div>
  );
}

function LetterEditor({ app, onChange }: { app: TailoredApplication; onChange: (a: TailoredApplication) => void }) {
  return (
    <section className="card stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Cover letter</h2>
        <CopyButton text={app.coverLetter} />
      </div>
      <textarea
        className="textarea"
        rows={16}
        aria-label="Cover letter"
        value={app.coverLetter}
        onChange={(e) => onChange({ ...app, coverLetter: e.target.value })}
      />
    </section>
  );
}

function AnswersEditor({ app, onChange }: { app: TailoredApplication; onChange: (a: TailoredApplication) => void }) {
  const setAnswer = (i: number, a: ScreeningAnswer) => onChange({ ...app, answers: app.answers.map((x, j) => (j === i ? a : x)) });
  return (
    <div className="stack">
      {app.answers.map((a, i) => (
        <section key={a.question} className="card stack" style={{ gap: 8 }}>
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h2 style={{ fontSize: 15 }}>{a.question}</h2>
            {a.answer.trim() && <CopyButton text={a.answer} />}
          </div>
          {a.note && <p className={`small ${a.needsUser ? "missing" : "muted"}`} style={{ margin: 0 }}>{a.note}</p>}
          <textarea
            className="textarea"
            rows={3}
            aria-label={a.question}
            placeholder={a.needsUser ? "Your answer" : ""}
            value={a.answer}
            onChange={(e) => setAnswer(i, { ...a, answer: e.target.value })}
          />
        </section>
      ))}
    </div>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      className="btn btn-secondary small"
      onClick={async () => {
        await navigator.clipboard.writeText(text).catch(() => undefined);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      }}
    >
      {copied ? "Copied" : "Copy"}
    </button>
  );
}
