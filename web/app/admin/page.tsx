"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Problems } from "@/components/Problems";
import { api, type AdminOverview, type AdminUser, type JobSource, type SourceStatus, type Counted, type FeedbackItem, type WaitlistEntry } from "@/lib/api";
import { ago, eventLabels, funnelLabels } from "@/lib/adminFormat";
import { useMe } from "@/lib/useMe";

const windows = [7, 30, 90];

export default function AdminPage() {
  const me = useMe();
  const [days, setDays] = useState(30);
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (!me?.isAdmin) return;
    setOverview(null);
    api.adminOverview(days).then(setOverview).catch(setError);
  }, [me, days]);

  if (me && !me.isAdmin) {
    return (
      <>
        <AppHeader email={me.email} />
        <main className="narrow stack">
          <section className="card stack">
            <h1>Admins only</h1>
            <p className="muted" style={{ margin: 0 }}>
              This page is for the accounts listed in ADMIN_EMAILS.
            </p>
          </section>
        </main>
      </>
    );
  }

  return (
    <>
      <AppHeader email={me?.email} isAdmin={me?.isAdmin} />
      <main className="container stack" style={{ gap: 24 }}>
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h1>Admin</h1>
          <nav className="tabs" aria-label="Time window">
            {windows.map((d) => (
              <button key={d} type="button" className="tab" aria-pressed={days === d} onClick={() => setDays(d)}>
                Last {d} days
              </button>
            ))}
          </nav>
        </div>
        <Problems error={error} />

        {!overview ? (
          <div className="card muted">Loading…</div>
        ) : (
          <>
            <div className="stat-grid">
              <Stat label="Users" value={overview.totalUsers} />
              <Stat label={`New in ${overview.windowDays} days`} value={overview.newUsers} />
              <Stat label="Active, 7 days" value={overview.activeUsers7} />
              <Stat label="Active, 30 days" value={overview.activeUsers30} />
            </div>

            <section className="card stack" style={{ gap: 12 }}>
              <h2>Active users per day</h2>
              <DailyBars days={overview.days} />
            </section>

            <div className="grid-2">
              <section className="card stack" style={{ gap: 10 }}>
                <h2>How far users get</h2>
                <p className="hint" style={{ margin: 0 }}>All time: users who ever reached each step.</p>
                {funnelLabels.map(([key, label]) => (
                  <Bar key={key} label={label} value={overview.funnel[key] ?? 0} max={overview.funnel.signedUp || 1} />
                ))}
              </section>
              <section className="card stack" style={{ gap: 10 }}>
                <h2>Activity in the last {overview.windowDays} days</h2>
                {Object.keys(eventLabels).map((type) => (
                  <div key={type} className="kv">
                    <span>{eventLabels[type]}</span>
                    <strong>{overview.eventCounts[type] ?? 0}</strong>
                  </div>
                ))}
              </section>
            </div>

            <div className="grid-3">
              <TopList title="Most searched roles" items={overview.topRoles} empty="No searches yet" />
              <TopList title="Most searched locations" items={overview.topLocations} empty="No searches yet" />
              <TopList title={`Companies applied to, ${overview.windowDays} days`} items={overview.topAppliedCompanies} empty="No clicks to apply yet" />
            </div>
          </>
        )}

        {me?.isAdmin && <FeedbackList />}
        {me?.isAdmin && <WaitlistSection />}
        {me?.isAdmin && <JobSources />}
        {me?.isAdmin && <UsersTable />}
      </main>
    </>
  );
}

/** People who asked for access. Invite lets them sign in and emails them; Remove is for deletion requests. */
function WaitlistSection() {
  const [items, setItems] = useState<WaitlistEntry[] | null>(null);
  const [showInvited, setShowInvited] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    api.adminWaitlist().then((r) => setItems(r.items)).catch(setError);
  }, []);

  async function invite(entry: WaitlistEntry) {
    setBusyId(entry.id);
    setError(null);
    try {
      const updated = await api.inviteFromWaitlist(entry.id);
      setItems((list) => (list ?? []).map((i) => (i.id === entry.id ? updated : i)));
    } catch (e) {
      setError(e);
      // A failed email still leaves them invited; reload to show the real state.
      api.adminWaitlist().then((r) => setItems(r.items)).catch(() => undefined);
    } finally {
      setBusyId(null);
    }
  }

  async function remove(entry: WaitlistEntry) {
    if (!window.confirm(`Remove ${entry.email} from the waitlist?`)) return;
    try {
      await api.removeFromWaitlist(entry.id);
      setItems((list) => (list ?? []).filter((i) => i.id !== entry.id));
    } catch (e) {
      setError(e);
    }
  }

  const waiting = (items ?? []).filter((i) => i.status === "waiting");
  const invited = (items ?? []).filter((i) => i.status === "invited");
  const shown = showInvited ? invited : waiting;

  return (
    <section className="card stack" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Waitlist{waiting.length > 0 ? ` (${waiting.length} waiting)` : ""}</h2>
        <nav className="tabs" aria-label="Waitlist filter" style={{ border: 0 }}>
          <button type="button" className="tab" aria-pressed={!showInvited} onClick={() => setShowInvited(false)}>Waiting</button>
          <button type="button" className="tab" aria-pressed={showInvited} onClick={() => setShowInvited(true)}>
            Invited<span className="tab-count">{invited.length}</span>
          </button>
        </nav>
      </div>
      <Problems error={error} />
      {!items ? (
        <p className="muted small" style={{ margin: 0 }}>Loading…</p>
      ) : shown.length === 0 ? (
        <p className="muted small" style={{ margin: 0 }}>
          {showInvited ? "Nobody invited from the waitlist yet." : "Nobody waiting. People who aren't invited can join from the home page or the sign-in page."}
        </p>
      ) : (
        <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {shown.map((w) => (
            <li key={w.id} className="feedback-item">
              <div className="row" style={{ justifyContent: "space-between", gap: 8 }}>
                <div className="stack" style={{ gap: 2, minWidth: 0 }}>
                  <strong style={{ overflowWrap: "anywhere" }}>{w.email}</strong>
                  <span className="muted small">
                    {w.status === "invited" && w.invitedAt ? `Invited ${ago(w.invitedAt)} · joined ${ago(w.createdAt)}` : `Joined ${ago(w.createdAt)}`}
                  </span>
                  {w.note && <span className="small">&ldquo;{w.note}&rdquo;</span>}
                </div>
                <div className="row" style={{ gap: 4 }}>
                  {w.status === "waiting" && (
                    <button type="button" className="btn btn-primary" disabled={busyId === w.id} onClick={() => invite(w)}>
                      {busyId === w.id ? "Inviting…" : "Invite"}
                    </button>
                  )}
                  <button type="button" className="btn btn-ghost small" onClick={() => remove(w)}>Remove</button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      <p className="muted small" style={{ margin: 0 }}>Invite lets them sign in straight away and sends them one email with a sign-in button.</p>
    </section>
  );
}

const feedbackKinds: Record<string, { label: string; tone: string }> = {
  problem: { label: "Something's broken", tone: "badge-warn" },
  idea: { label: "Idea", tone: "badge-info" },
  praise: { label: "Likes", tone: "badge-good" },
  other: { label: "Other", tone: "badge-plain" },
};

/** What users sent with the Feedback button, newest first. New ones first by default; mark them done once handled. */
function FeedbackList() {
  const [showAll, setShowAll] = useState(false);
  const [items, setItems] = useState<FeedbackItem[] | null>(null);
  const [newCount, setNewCount] = useState(0);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    setItems(null);
    api
      .adminFeedback(showAll ? undefined : "new")
      .then((r) => {
        setItems(r.items);
        setNewCount(r.newCount);
      })
      .catch(setError);
  }, [showAll]);

  async function mark(item: FeedbackItem, status: "new" | "done") {
    try {
      await api.setFeedbackStatus(item.id, status);
      setNewCount((n) => n + (status === "done" ? -1 : 1));
      setItems((list) => (list ?? []).flatMap((i) => (i.id !== item.id ? [i] : showAll ? [{ ...i, status }] : [])));
    } catch (e) {
      setError(e);
    }
  }

  return (
    <section className="card stack" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Feedback{newCount > 0 ? ` (${newCount} new)` : ""}</h2>
        <nav className="tabs" aria-label="Feedback filter" style={{ border: 0 }}>
          <button type="button" className="tab" aria-pressed={!showAll} onClick={() => setShowAll(false)}>New</button>
          <button type="button" className="tab" aria-pressed={showAll} onClick={() => setShowAll(true)}>All</button>
        </nav>
      </div>
      <Problems error={error} />
      {!items ? (
        <p className="muted small" style={{ margin: 0 }}>Loading…</p>
      ) : items.length === 0 ? (
        <p className="muted small" style={{ margin: 0 }}>
          {showAll ? "No feedback yet. It arrives here when someone uses the Feedback button." : "Nothing new. Switch to All to see handled feedback."}
        </p>
      ) : (
        <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {items.map((f) => {
            const k = feedbackKinds[f.kind] ?? feedbackKinds.other;
            return (
              <li key={f.id} className={`feedback-item${f.status === "done" ? " done" : ""}`}>
                <div className="row small" style={{ gap: 8 }}>
                  <span className={`badge ${k.tone}`}>{k.label}</span>
                  <Link href={`/admin/users/${f.userId}`}>{f.email}</Link>
                  <span className="muted">{ago(f.createdAt)}{f.page ? ` · on ${f.page}` : ""}</span>
                </div>
                <p className="feedback-message">{f.message}</p>
                <div>
                  <button type="button" className="btn btn-ghost small" style={{ paddingInline: 0, minHeight: 32 }} onClick={() => mark(f, f.status === "new" ? "done" : "new")}>
                    {f.status === "new" ? "Mark done" : "Mark as new"}
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

const SOURCES: { id: JobSource; name: string; off: string; unconfigured: string }[] = [
  {
    id: "himalayas",
    name: "Himalayas remote jobs",
    off: "Off · no Himalayas searches; its remote jobs are hidden from new matches and daily emails. Saved jobs stay saved.",
    unconfigured: "",
  },
  {
    id: "adzuna",
    name: "Adzuna job search",
    off: "Off · no Adzuna searches; its jobs are hidden from new matches and daily emails. Saved jobs stay saved.",
    unconfigured: "Add ADZUNA_APP_ID and ADZUNA_APP_KEY on Render to use it.",
  },
];

/** Aggregator switches: off means no calls to that source, and its jobs stop showing as new matches and in daily emails. */
function JobSources() {
  return (
    <section className="card stack" style={{ gap: 12 }}>
      <h2>Job sources</h2>
      {SOURCES.map((s) => (
        <SourceSwitch key={s.id} source={s} />
      ))}
      <p className="muted small" style={{ margin: 0 }}>Company job boards (Greenhouse, Lever, Ashby) aren&apos;t affected by these switches.</p>
    </section>
  );
}

function SourceSwitch({ source }: { source: (typeof SOURCES)[number] }) {
  const [status, setStatus] = useState<SourceStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    api.sourceStatus(source.id).then(setStatus).catch(setError);
  }, [source.id]);

  async function toggle(enabled: boolean) {
    setBusy(true);
    setError(null);
    try {
      setStatus(await api.setSource(source.id, enabled));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (error && !status) return <Problems error={error} />;
  if (!status) return <p className="muted small" style={{ margin: 0 }}>Loading…</p>;
  return (
    <div className="stack" style={{ gap: 6 }}>
      <Problems error={error} />
      <div className="source-row">
        <div className="stack" style={{ gap: 4, minWidth: 0 }}>
          <strong>{source.name}</strong>
          <span className="muted small">
            {!status.configured
              ? source.unconfigured
              : status.enabled
                ? `On · ${status.openJobs.toLocaleString()} open jobs · ${status.callsToday} of ${status.dailyLimit} searches used today${status.lastSyncedAt ? ` · last search ${ago(status.lastSyncedAt)}` : ""}`
                : source.off}
          </span>
          {status.enabled && status.lastError && <span className="small" style={{ color: "var(--bad)" }}>Last error: {status.lastError}</span>}
        </div>
        <label className="switch">
          <input
            type="checkbox"
            role="switch"
            checked={status.enabled}
            disabled={busy || !status.configured}
            onChange={(e) => toggle(e.target.checked)}
            aria-label={source.name}
          />
          <span className="switch-track" aria-hidden="true"><span className="switch-thumb" /></span>
        </label>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="card tight stat">
      <span className="stat-value">{value.toLocaleString()}</span>
      <span className="muted small">{label}</span>
    </div>
  );
}

function Bar({ label, value, max }: { label: string; value: number; max: number }) {
  return (
    <div className="bar-row">
      <div className="kv">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <div className="bar-track" aria-hidden="true">
        <div className="bar-fill" style={{ width: `${Math.round((value / max) * 100)}%` }} />
      </div>
    </div>
  );
}

function DailyBars({ days }: { days: AdminOverview["days"] }) {
  const max = Math.max(1, ...days.map((d) => d.activeUsers));
  const label = (iso: string) => new Date(iso + "T00:00:00").toLocaleDateString(undefined, { day: "numeric", month: "short" });
  return (
    <div>
      <div className="daily" role="img" aria-label={`Active users per day, peak ${max}`}>
        {days.map((d) => (
          <div
            key={d.day}
            className="daily-bar"
            style={{ height: `${Math.max(2, (d.activeUsers / max) * 100)}%` }}
            title={`${label(d.day)}: ${d.activeUsers} active, ${d.newUsers} new, ${d.events} actions`}
          />
        ))}
      </div>
      <div className="row small muted" style={{ justifyContent: "space-between", marginTop: 6 }}>
        <span>{label(days[0].day)}</span>
        <span>peak {max}</span>
        <span>{label(days[days.length - 1].day)}</span>
      </div>
    </div>
  );
}

function TopList({ title, items, empty }: { title: string; items: Counted[]; empty: string }) {
  return (
    <section className="card stack" style={{ gap: 8 }}>
      <h2>{title}</h2>
      {items.length === 0 ? (
        <p className="muted small" style={{ margin: 0 }}>{empty}</p>
      ) : (
        items.map((i) => (
          <div key={i.value} className="kv">
            <span>{i.value}</span>
            <strong>{i.count}</strong>
          </div>
        ))
      )}
    </section>
  );
}

function UsersTable() {
  const [q, setQ] = useState("");
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      api
        .adminUsers(q)
        .then((r) => {
          setUsers(r.users);
          setTotal(r.total);
        })
        .catch(setError);
    }, 250);
    return () => clearTimeout(t);
  }, [q]);

  async function more() {
    const r = await api.adminUsers(q, users.length).catch((e) => {
      setError(e);
      return null;
    });
    if (r) setUsers((u) => [...u, ...r.users]);
  }

  return (
    <section className="card stack" style={{ gap: 12 }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Users ({total})</h2>
        <input className="input" style={{ maxWidth: 280 }} placeholder="Search email or name" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search users" />
      </div>
      <Problems error={error} />
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>User</th>
              <th>Last active</th>
              <th>Searching for</th>
              <th className="num">Refreshes</th>
              <th className="num">Tailored</th>
              <th className="num">Approved</th>
              <th className="num">Apply clicks</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>
                  <Link href={`/admin/users/${u.id}`} style={{ fontWeight: 600 }}>{u.name ?? u.email}</Link>
                  <span className="hint" style={{ display: "block" }}>{u.name ? u.email : u.headline ?? ""}{u.plan === "pro" ? " · Pro" : ""}</span>
                </td>
                <td className="nowrap">{ago(u.lastSeenAt ?? u.createdAt)}</td>
                <td>
                  {u.roles.join(", ") || <span className="muted">No roles</span>}
                  <span className="hint" style={{ display: "block" }}>{u.locations.join(", ")}</span>
                </td>
                <td className="num">{u.refreshes}</td>
                <td className="num">{u.tailored}</td>
                <td className="num">{u.approved}</td>
                <td className="num">{u.applyClicks}</td>
              </tr>
            ))}
            {users.length === 0 && (
              <tr>
                <td colSpan={7} className="muted">No users match.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {users.length < total && (
        <div>
          <button type="button" className="btn btn-secondary" onClick={more}>Load more</button>
        </div>
      )}
    </section>
  );
}
