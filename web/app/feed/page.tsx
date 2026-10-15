"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { PartialDescriptionNote, SourceAttribution } from "@/components/SourceAttribution";
import { Problems } from "@/components/Problems";
import { FeedSkeleton, MatchingBanner, MatchingProgress } from "@/components/MatchingProgress";
import {
  api,
  ApiError,
  type Feed,
  type FeedJob,
  type JobDetail,
  type MatchStatus,
  type Preferences,
  type Suggestion,
} from "@/lib/api";
import { useMe } from "@/lib/useMe";

const SHOW_WEAK_KEY = "feed.showWeak";

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
  // Off by default: the New tab hides matches scoring below the cutoff. Remembered per browser.
  const [showWeak, setShowWeakState] = useState(false);
  const setShowWeak = (on: boolean) => {
    setShowWeakState(on);
    try {
      localStorage.setItem(SHOW_WEAK_KEY, on ? "1" : "0");
    } catch {
      // Storage blocked (private window): the choice lasts for this visit only.
    }
  };
  useEffect(() => {
    try {
      if (localStorage.getItem(SHOW_WEAK_KEY) === "1") setShowWeakState(true);
    } catch {
      // Storage blocked: keep the default.
    }
  }, []);
  // One automatic rebuild per visit when the profile or preferences changed since the last run.
  const autoRefreshed = useRef(false);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      const fresh = await api.refreshFeed(showWeak);
      if (status === "new") setFeed(fresh);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) router.replace("/onboarding");
      else setError(e);
    } finally {
      setRefreshing(false);
    }
  }, [router, status, showWeak]);

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
      .feed(status, showWeak)
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
    // refresh is stable for a given status and toggle; re-running on it would double-fetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me, status, showWeak]);

  // One tap widens the preferences and rebuilds the feed with the change.
  async function applySuggestion(s: Suggestion) {
    if (!prefs) return;
    const next: Preferences =
      s.kind === "location"
        ? { ...prefs, locations: [...prefs.locations, s.value] }
        : s.kind === "role"
          ? { ...prefs, roles: [...prefs.roles, s.value] }
          : { ...prefs, remoteMode: "any" };
    setRefreshing(true);
    setError(null);
    try {
      setPrefs(await api.savePreferences(next));
      const fresh = await api.refreshFeed(showWeak);
      setStatus("new");
      setFeed(fresh);
    } catch (e) {
      setError(e);
    } finally {
      setRefreshing(false);
    }
  }

  // Moves the card and both tab counts at once; undone if the server refuses.
  const shift = (f: Feed, from: MatchStatus, to: MatchStatus, by: number): Feed => {
    if (!f.counts) return f;
    const counts = { ...f.counts };
    counts[from] = Math.max(0, (counts[from] ?? 0) - by);
    counts[to] = (counts[to] ?? 0) + by;
    return { ...f, counts };
  };

  async function move(job: FeedJob, to: MatchStatus) {
    const from = job.status;
    setFeed((f) => (f ? shift({ ...f, jobs: f.jobs.filter((j) => j.jobId !== job.jobId) }, from, to, 1) : f));
    try {
      await api.setMatchStatus(job.jobId, to);
    } catch (e) {
      setError(e);
      setFeed((f) => (f ? shift({ ...f, jobs: [...f.jobs, job].sort((a, b) => b.score - a.score) }, from, to, -1) : f));
    }
  }

  const lastRun = feed?.lastRun;

  return (
    <>
      <AppHeader email={me?.email} isAdmin={me?.isAdmin} />
      <main className="container split" style={{ gap: 24 }}>
        <aside className="side stack feed-side">
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
          {status === "new" && (lastRun?.suggestions?.length ?? 0) > 0 && (
            <SuggestionsCard suggestions={lastRun!.suggestions!} onApply={applySuggestion} busy={refreshing} />
          )}
          <section className="card stack" style={{ gap: 8 }}>
            <h2>How matching works</h2>
            <p className="muted small" style={{ margin: 0 }}>
              Every day we read public job boards, drop jobs outside your roles, locations and experience, then score the rest
              against your profile. You decide what to apply to.
            </p>
          </section>
        </aside>

        <section className="main stack">
          <div className="row page-title" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
            <h1>Today&apos;s matches</h1>
            <span className="small muted">{runSummary(lastRun, refreshing)}</span>
          </div>

          <div className="row tabs-row" style={{ justifyContent: "space-between" }}>
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
                  {feed?.counts?.[t.status] != null && <span className="tab-count">{feed.counts[t.status]}</span>}
                </button>
              ))}
            </nav>
            {status === "new" && (
              <button type="button" className="btn btn-secondary" onClick={refresh} disabled={refreshing}>
                {refreshing ? "Updating…" : "Refresh"}
              </button>
            )}
          </div>

          {status === "new" && (
            <WeakToggle on={showWeak} onChange={setShowWeak} hidden={feed?.hiddenWeak ?? 0} minScore={feed?.minScore ?? 40} />
          )}

          <Problems error={error} />

          {refreshing && feed && feed.jobs.length > 0 && <MatchingBanner />}

          {refreshing && status === "new" && (!feed || feed.jobs.length === 0) ? (
            <MatchingProgress />
          ) : !feed ? (
            <FeedSkeleton />
          ) : feed.jobs.length === 0 ? (
            <EmptyState status={status} feed={feed} onShowWeak={() => setShowWeak(true)} />
          ) : (
            feed.jobs.map((job) => <JobCard key={job.jobId} job={job} onMove={move} />)
          )}
        </section>
      </main>
    </>
  );
}

function suggestionLabel(s: Suggestion): string {
  if (s.kind === "location") return `Add ${s.value}`;
  if (s.kind === "role") return `Add role: ${s.value}`;
  return "Include remote jobs in India";
}

function SuggestionsCard({ suggestions, onApply, busy }: { suggestions: Suggestion[]; onApply: (s: Suggestion) => void; busy: boolean }) {
  return (
    <section className="card stack" style={{ gap: 10 }}>
      <h2>Widen your search</h2>
      <p className="muted small" style={{ margin: 0 }}>
        Jobs that fit your skills but were filtered out. One tap updates your preferences.
      </p>
      {suggestions.map((s) => (
        <button
          key={`${s.kind}:${s.value}`}
          type="button"
          className="btn btn-secondary suggestion"
          disabled={busy}
          onClick={() => onApply(s)}
        >
          <span>{suggestionLabel(s)}</span>
          <span className="suggestion-count">
            +{s.count} job{s.count === 1 ? "" : "s"}
          </span>
        </button>
      ))}
    </section>
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
        <span className="score-label">{job.score < 40 ? "LOW" : "FIT"}</span>
      </div>
      <div className="job-body stack" style={{ gap: 10 }}>
        <div>
          <h3 className="job-title">{job.title}</h3>
          <p className="muted small" style={{ margin: "4px 0 0" }}>
            {[job.company, job.location || null, job.remote ? "Remote" : null, posted(job.postedAt)].filter(Boolean).join(" · ")}
            {job.closed && <span className="badge-closed">No longer open</span>}
          </p>
          <SourceAttribution source={job.source ?? ""} />
          {(job.visa || job.relocation) && (
            <div className="chips" style={{ marginTop: 6 }}>
              {job.visa === "offered" && <span className="badge badge-good">Visa sponsorship mentioned</span>}
              {job.visa === "not_offered" && <span className="badge badge-warn">Says no visa sponsorship</span>}
              {job.relocation && <span className="badge badge-good">Relocation support</span>}
            </div>
          )}
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
        {open && <PartialDescriptionNote partial={job.descriptionPartial ?? false} applyUrl={job.applyUrl} />}
        {open && <div className="jd">{detail ? detail.description || "No description provided." : "Loading…"}</div>}
      </div>
      <div className="job-actions">
        <Link className="btn btn-primary" href={`/apply/${job.jobId}`}>
          Tailor and apply
        </Link>
        <a className="btn btn-secondary" href={job.applyUrl} target="_blank" rel="noopener noreferrer" onClick={() => api.track("job_opened", job.jobId)}>
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

function WeakToggle({ on, onChange, hidden, minScore }: { on: boolean; onChange: (on: boolean) => void; hidden: number; minScore: number }) {
  return (
    <label className="checkbox">
      <input type="checkbox" checked={on} onChange={(e) => onChange(e.target.checked)} />
      <span>
        Show weaker matches
        <span className="muted small">
          {" "}
          (score below {minScore}
          {!on && hidden > 0 ? `, ${hidden} hidden` : ""})
        </span>
      </span>
    </label>
  );
}

function EmptyState({ status, feed, onShowWeak }: { status: MatchStatus; feed: Feed; onShowWeak: () => void }) {
  if (status === "saved") return <div className="card muted">Jobs you save show up here.</div>;
  if (status === "skipped") return <div className="card muted">Jobs you skip show up here, in case you change your mind.</div>;
  const hidden = feed.hiddenWeak ?? 0;
  if (hidden > 0) {
    return (
      <div className="card stack" style={{ gap: 8 }}>
        <h2>No strong matches right now</h2>
        <p className="muted small" style={{ margin: 0 }}>
          {hidden} job{hidden === 1 ? "" : "s"} passed your filters but scored below {feed.minScore ?? 40}.
        </p>
        <div>
          <button type="button" className="btn btn-secondary" onClick={onShowWeak}>
            Show {hidden === 1 ? "it" : `all ${hidden}`}
          </button>
        </div>
      </div>
    );
  }
  const run = feed.lastRun;
  const reasons = run ? filterSummary(run.filtered) : [];
  return (
    <div className="card stack" style={{ gap: 8 }}>
      <h2>No new matches right now</h2>
      <p className="muted small" style={{ margin: 0 }}>
        {run && run.considered === 0
          ? "There are no new open jobs to check yet. Job boards sync every day."
          : run
            ? `We checked ${run.considered} new job${run.considered === 1 ? "" : "s"} and none fit yet.`
            : "Refresh to check open jobs against your profile."}
      </p>
      {reasons.length > 0 && (
        <ul className="reasons small muted">
          {reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      )}
      {reasons.length > 0 && (
        <p className="small" style={{ margin: 0 }}>
          {(run?.suggestions?.length ?? 0) > 0 ? "Try a suggestion under “Widen your search”, or " : ""}
          <Link href="/profile">edit your roles and locations</Link>.
        </p>
      )}
    </div>
  );
}

const filterLabels: Record<string, (n: number) => string> = {
  role: (n) => `${n} ${n === 1 ? "was a different role" : "were different roles"}`,
  location: (n) => `${n} ${n === 1 ? "was" : "were"} outside your locations or work mode`,
  experience: (n) => `${n} asked for a different experience level`,
  seniority: (n) => `${n} ${n === 1 ? "was an entry-level or junior role" : "were entry-level or junior roles"}`,
  skippedCompany: (n) => `${n} ${n === 1 ? "was" : "were"} at companies you skip`,
  visa: (n) => `${n} abroad ${n === 1 ? "says" : "say"} there's no visa sponsorship`,
  other: (n) => `${n} filtered for other reasons`,
};

function filterSummary(filtered: Record<string, number | undefined> | null | undefined): string[] {
  // Older API builds (before the V3 migration) don't send this field.
  return Object.entries(filtered ?? {})
    .filter(([, n]) => (n ?? 0) > 0)
    .sort((a, b) => (b[1] ?? 0) - (a[1] ?? 0))
    .map(([k, n]) => (filterLabels[k] ?? filterLabels.other)(n ?? 0));
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
