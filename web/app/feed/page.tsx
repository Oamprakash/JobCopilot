"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Problems } from "@/components/Problems";
import { api, ApiError, type Feed, type FeedJob, type JobDetail, type MatchStatus, type Preferences } from "@/lib/api";
import { useMe } from "@/lib/useMe";

const tabs: { status: MatchStatus; label: string }[] = [
  { status: "new", label: "New" },
  { status: "saved", label: "Saved" },
  { status: "skipped", label: "Skipped" },
];

export default function FeedPage() {
  const router = useRouter();
  const me = useMe();
  const [status, setStatus] = useState<MatchStatus>("new");
  const [feed, setFeed] = useState<Feed | null>(null);
  const [prefs, setPrefs] = useState<Preferences | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<unknown>(null);
  // One automatic rebuild per visit when the profile or preferences changed since the last run.
  const autoRefreshed = useRef(false);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      const fresh = await api.refreshFeed();
      if (status === "new") setFeed(fresh);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) router.replace("/onboarding");
      else setError(e);
    } finally {
      setRefreshing(false);
    }
  }, [router, status]);

  useEffect(() => {
    if (!me) return;
    if (!me.hasProfile) {
      router.replace("/onboarding");
      return;
    }
    api.getPreferences().then(setPrefs).catch(() => undefined);
  }, [me, router]);

  useEffect(() => {
    if (!me?.hasProfile) return;
    let cancelled = false;
    setFeed(null);
    api
      .feed(status)
      .then((f) => {
        if (cancelled) return;
        setFeed(f);
        if (f.needsRefresh && !autoRefreshed.current) {
          autoRefreshed.current = true;
          refresh();
        }
      })
      .catch((e) => !cancelled && setError(e));
    return () => {
      cancelled = true;
    };
    // refresh is stable for a given status; re-running on it would double-fetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me, status]);

  async function move(job: FeedJob, to: MatchStatus) {
    setFeed((f) => (f ? { ...f, jobs: f.jobs.filter((j) => j.jobId !== job.jobId) } : f));
    try {
      await api.setMatchStatus(job.jobId, to);
    } catch (e) {
      setError(e);
      setFeed((f) => (f ? { ...f, jobs: [...f.jobs, job].sort((a, b) => b.score - a.score) } : f));
    }
  }

  const lastRun = feed?.lastRun;

  return (
    <>
      <AppHeader email={me?.email} />
      <main className="container split" style={{ gap: 24 }}>
        <aside className="side stack" style={{ maxWidth: 300 }}>
          <section className="card stack" style={{ gap: 12 }}>
            <h2>Matching on</h2>
            {prefs ? (
              <div className="chips">
                {[...prefs.roles, ...prefs.locations, prefs.remoteMode !== "any" ? prefs.remoteMode : null, prefs.minCtcLpa ? `${prefs.minCtcLpa} LPA+` : null]
                  .filter((x): x is string => Boolean(x))
                  .map((label) => (
                    <span key={label} className="chip chip-plain">
                      {label}
                    </span>
                  ))}
              </div>
            ) : (
              <p className="muted small">Loading…</p>
            )}
            <Link href="/profile" className="small" style={{ fontWeight: 600 }}>
              Edit preferences
            </Link>
          </section>
          <section className="card stack" style={{ gap: 8 }}>
            <h2>How matching works</h2>
            <p className="muted small" style={{ margin: 0 }}>
              Every day we read public job boards, drop jobs outside your roles, locations and experience, then score the rest
              against your profile. You decide what to apply to.
            </p>
          </section>
        </aside>

        <section className="main stack">
          <div className="row" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
            <h1>Today&apos;s matches</h1>
            <span className="small muted">{runSummary(lastRun, refreshing)}</span>
          </div>

          <div className="row" style={{ justifyContent: "space-between" }}>
            <nav className="tabs" aria-label="Match lists">
              {tabs.map((t) => (
                <button
                  key={t.status}
                  type="button"
                  className="tab"
                  aria-pressed={status === t.status}
                  onClick={() => setStatus(t.status)}
                >
                  {t.label}
                </button>
              ))}
            </nav>
            {status === "new" && (
              <button type="button" className="btn btn-secondary" onClick={refresh} disabled={refreshing}>
                {refreshing ? "Finding matches…" : "Refresh"}
              </button>
            )}
          </div>

          <Problems error={error} />

          {!feed || (refreshing && feed.jobs.length === 0) ? (
            <div className="card muted">{refreshing ? "Checking open jobs against your profile…" : "Loading…"}</div>
          ) : feed.jobs.length === 0 ? (
            <EmptyState status={status} feed={feed} />
          ) : (
            feed.jobs.map((job) => <JobCard key={job.jobId} job={job} onMove={move} />)
          )}
        </section>
      </main>
    </>
  );
}

function JobCard({ job, onMove }: { job: FeedJob; onMove: (job: FeedJob, to: MatchStatus) => void }) {
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<JobDetail | null>(null);
  const tone = job.score >= 75 ? "good" : job.score >= 55 ? "warn" : "plain";

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (next && !detail) setDetail(await api.job(job.jobId).catch(() => null));
  }

  return (
    <article className="card job-card">
      <div className={`score score-${tone}`} aria-label={`Fit score ${job.score} out of 100`}>
        <span className="score-value">{job.score}</span>
        <span className="score-label">FIT</span>
      </div>
      <div className="job-body stack" style={{ gap: 10 }}>
        <div>
          <h3 className="job-title">{job.title}</h3>
          <p className="muted small" style={{ margin: "4px 0 0" }}>
            {[job.company, job.location || null, job.remote ? "Remote" : null, posted(job.postedAt)].filter(Boolean).join(" · ")}
            {job.closed && <span className="badge-closed">No longer open</span>}
          </p>
        </div>
        {job.reasons.length > 0 && (
          <ul className="reasons">
            {job.reasons.map((r) => (
              <li key={r}>{r}</li>
            ))}
          </ul>
        )}
        {job.missingSkills.length > 0 && <p className="missing small">Missing: {job.missingSkills.join(", ")}</p>}
        <button type="button" className="btn btn-ghost small" style={{ alignSelf: "flex-start", paddingInline: 0 }} onClick={toggle} aria-expanded={open}>
          {open ? "Hide job description" : "Show job description"}
        </button>
        {open && <div className="jd">{detail ? detail.description || "No description provided." : "Loading…"}</div>}
      </div>
      <div className="job-actions">
        <a className="btn btn-primary" href={job.applyUrl} target="_blank" rel="noopener noreferrer">
          View job
        </a>
        {job.status === "new" && (
          <>
            <button type="button" className="btn btn-secondary" onClick={() => onMove(job, "saved")}>
              Save
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => onMove(job, "skipped")}>
              Skip
            </button>
          </>
        )}
        {job.status !== "new" && (
          <button type="button" className="btn btn-secondary" onClick={() => onMove(job, "new")}>
            {job.status === "saved" ? "Unsave" : "Restore"}
          </button>
        )}
      </div>
    </article>
  );
}

function EmptyState({ status, feed }: { status: MatchStatus; feed: Feed }) {
  if (status === "saved") return <div className="card muted">Jobs you save show up here.</div>;
  if (status === "skipped") return <div className="card muted">Jobs you skip show up here, in case you change your mind.</div>;
  const run = feed.lastRun;
  return (
    <div className="card stack" style={{ gap: 8 }}>
      <h2>No new matches right now</h2>
      <p className="muted small" style={{ margin: 0 }}>
        {run && run.considered === 0
          ? "There are no new open jobs to check yet. Job boards sync every day."
          : run
            ? `We checked ${run.considered} new job${run.considered === 1 ? "" : "s"} and none fit your roles, locations and experience. Widening your preferences brings in more.`
            : "Refresh to check open jobs against your profile."}
      </p>
    </div>
  );
}

function runSummary(run: Feed["lastRun"] | undefined, refreshing: boolean): string {
  if (refreshing) return "Updating…";
  if (!run?.finishedAt) return "";
  return `Updated ${ago(run.finishedAt)} · ${run.considered} job${run.considered === 1 ? "" : "s"} checked`;
}

function ago(iso: string): string {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.round(hours / 24)} d ago`;
}

function posted(iso: string | null): string | null {
  if (!iso) return null;
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  return days <= 0 ? "Posted today" : days === 1 ? "Posted yesterday" : `Posted ${days} days ago`;
}
