"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Problems } from "@/components/Problems";
import { api, type TrackerItem, type TrackerList, type TrackerPatch, type TrackerStage } from "@/lib/api";
import { useMe } from "@/lib/useMe";

const stageLabels: Record<TrackerStage, string> = {
  saved: "Saved",
  applied: "Applied",
  interview: "Interviewing",
  offer: "Offer",
  rejected: "Rejected",
  withdrawn: "Withdrawn",
};
const stages = Object.keys(stageLabels) as TrackerStage[];
type Filter = "all" | "due" | TrackerStage | "closed";

const day = 86_400_000;
const fmt = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

/** 9 am local time, n days from today: follow-ups are dates, not moments. */
function daysFromNow(n: number): string {
  const d = new Date(Date.now() + n * day);
  d.setHours(9, 0, 0, 0);
  return d.toISOString();
}

export default function TrackerPage() {
  const me = useMe();
  const [list, setList] = useState<TrackerList | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (!me) return;
    api.tracker().then(setList).catch(setError);
  }, [me]);

  function replace(item: TrackerItem) {
    setList((l) => {
      if (!l) return l;
      const items = l.items.some((i) => i.jobId === item.jobId) ? l.items.map((i) => (i.jobId === item.jobId ? item : i)) : [item, ...l.items];
      return summarize(items);
    });
  }

  async function update(item: TrackerItem, patch: TrackerPatch) {
    setError(null);
    try {
      replace(await api.updateTracker(item.jobId, patch));
    } catch (e) {
      setError(e);
    }
  }

  async function remove(item: TrackerItem) {
    if (!window.confirm(`Remove ${item.title} at ${item.company} from your tracker?`)) return;
    try {
      await api.untrack(item.jobId);
      setList((l) => (l ? summarize(l.items.filter((i) => i.jobId !== item.jobId)) : l));
    } catch (e) {
      setError(e);
    }
  }

  const shown = useMemo(() => {
    const items = list?.items ?? [];
    const matches = items.filter((i) =>
      filter === "all" ? true : filter === "due" ? i.followUpDue : filter === "closed" ? i.stage === "rejected" || i.stage === "withdrawn" : i.stage === filter,
    );
    // Due follow-ups first, then most recently changed.
    return [...matches].sort((a, b) => Number(b.followUpDue) - Number(a.followUpDue) || b.updatedAt.localeCompare(a.updatedAt));
  }, [list, filter]);

  const counts = list?.counts;
  const filters: [Filter, string, number | undefined][] = [
    ["all", "All", list?.items.length],
    ["due", "Follow up", list?.dueCount],
    ["saved", "Saved", counts?.saved],
    ["applied", "Applied", counts?.applied],
    ["interview", "Interviewing", counts?.interview],
    ["offer", "Offers", counts?.offer],
    ["closed", "Closed", counts ? counts.rejected + counts.withdrawn : undefined],
  ];

  return (
    <>
      <AppHeader email={me?.email} isAdmin={me?.isAdmin} />
      <main className="container stack" style={{ gap: 20 }}>
        <div className="row" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
          <h1>Tracker</h1>
          <span className="small muted">Jobs you saved and applied to, with follow-up reminders.</span>
        </div>

        {list && list.dueCount > 0 && filter !== "due" && (
          <button type="button" className="notice notice-warn due-banner" onClick={() => setFilter("due")}>
            {list.dueCount} application{list.dueCount === 1 ? " is" : "s are"} due a follow-up. Show {list.dueCount === 1 ? "it" : "them"}
          </button>
        )}

        <nav className="tabs" aria-label="Filter by stage">
          {filters.map(([f, label, n]) => (
            <button key={f} type="button" className="tab" aria-pressed={filter === f} onClick={() => setFilter(f)}>
              {label}
              {n != null && <span className="tab-count">{n}</span>}
            </button>
          ))}
        </nav>

        <Problems error={error} />
        <AddJob onAdded={replace} />

        {!list ? (
          <div className="card muted">Loading…</div>
        ) : shown.length === 0 ? (
          <div className="card stack" style={{ gap: 6 }}>
            <h2>{list.items.length === 0 ? "Nothing tracked yet" : "Nothing here"}</h2>
            <p className="muted small" style={{ margin: 0 }}>
              {list.items.length === 0 ? (
                <>
                  Mark an application as applied from its review screen, save jobs from any page with the browser extension (connect it from
                  your <Link href="/profile">profile</Link>), or add one above.
                </>
              ) : (
                "No jobs match this filter."
              )}
            </p>
          </div>
        ) : (
          shown.map((item) => <TrackerCard key={item.jobId} item={item} onUpdate={update} onRemove={remove} />)
        )}
      </main>
    </>
  );
}

function summarize(items: TrackerItem[]): TrackerList {
  const counts = Object.fromEntries(stages.map((s) => [s, items.filter((i) => i.stage === s).length])) as Record<TrackerStage, number>;
  return { items, counts, dueCount: items.filter((i) => i.followUpDue).length };
}

function TrackerCard({ item, onUpdate, onRemove }: { item: TrackerItem; onUpdate: (i: TrackerItem, p: TrackerPatch) => void; onRemove: (i: TrackerItem) => void }) {
  const [notes, setNotes] = useState(item.notes);
  const open = item.stage === "saved" || item.stage === "applied" || item.stage === "interview";
  return (
    <article className={`card tracker-card${item.followUpDue ? " tracker-due" : ""}`}>
      <div className="tracker-main stack" style={{ gap: 8 }}>
        <div>
          <h3 className="job-title">
            <a href={item.applyUrl} target="_blank" rel="noopener noreferrer">{item.title}</a>
            {item.closed && <span className="badge-closed">No longer listed</span>}
          </h3>
          <p className="muted small" style={{ margin: "4px 0 0" }}>
            {[item.company, item.location || null, item.appliedAt ? `applied ${fmt(item.appliedAt)}` : `added ${fmt(item.createdAt)}`].filter(Boolean).join(" · ")}
          </p>
        </div>

        {open && (
          <div className="row small" style={{ gap: 8 }}>
            {item.followUpAt ? (
              <span className={item.followUpDue ? "follow-due" : "muted"}>
                {item.followUpDue ? "Follow up now" : `Follow up ${fmt(item.followUpAt)}`}
              </span>
            ) : (
              <span className="muted">No follow-up set</span>
            )}
            <button type="button" className="btn btn-ghost small" onClick={() => onUpdate(item, { followUpAt: daysFromNow(3) })}>In 3 days</button>
            <button type="button" className="btn btn-ghost small" onClick={() => onUpdate(item, { followUpAt: daysFromNow(7) })}>In a week</button>
            {item.followUpAt && (
              <button type="button" className="btn btn-ghost small" onClick={() => onUpdate(item, { clearFollowUp: true })}>
                {item.followUpDue ? "Done" : "Clear"}
              </button>
            )}
          </div>
        )}

        <label className="field">
          <span className="sr-only">Notes</span>
          <textarea
            className="textarea tracker-notes"
            rows={2}
            placeholder="Notes: recruiter, interview dates, what was discussed…"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            onBlur={() => notes !== item.notes && onUpdate(item, { notes })}
          />
        </label>
      </div>

      <div className="job-actions">
        <label className="field">
          <span className="sr-only">Stage</span>
          <select className="select" value={item.stage} onChange={(e) => onUpdate(item, { stage: e.target.value as TrackerStage })}>
            {stages.map((s) => (
              <option key={s} value={s}>{stageLabels[s]}</option>
            ))}
          </select>
        </label>
        {item.applicationStatus && (
          <Link className="btn btn-secondary" href={`/apply/${item.jobId}`}>
            {item.applicationStatus === "approved" ? "Application" : "Draft application"}
          </Link>
        )}
        <button type="button" className="btn btn-ghost small" onClick={() => onRemove(item)}>Remove</button>
      </div>
    </article>
  );
}

function AddJob({ onAdded }: { onAdded: (i: TrackerItem) => void }) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ title: "", company: "", url: "", stage: "applied" as TrackerStage });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onAdded(await api.trackManual(form));
      setForm({ title: "", company: "", url: "", stage: form.stage });
      setOpen(false);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <div>
        <button type="button" className="btn btn-secondary" onClick={() => setOpen(true)}>Add a job by hand</button>
      </div>
    );
  }
  return (
    <form className="card stack" style={{ gap: 12 }} onSubmit={submit}>
      <h2>Add a job</h2>
      <div className="grid-2">
        <label className="field">Job title<input className="input" required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
        <label className="field">Company<input className="input" required value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} /></label>
        <label className="field">Link to the job<input className="input" type="url" required placeholder="https://" value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })} /></label>
        <label className="field">
          Stage
          <select className="select" value={form.stage} onChange={(e) => setForm({ ...form, stage: e.target.value as TrackerStage })}>
            {stages.map((s) => (
              <option key={s} value={s}>{stageLabels[s]}</option>
            ))}
          </select>
        </label>
      </div>
      <Problems error={error} />
      <div className="row">
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? "Adding…" : "Add to tracker"}</button>
        <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)}>Cancel</button>
      </div>
    </form>
  );
}
