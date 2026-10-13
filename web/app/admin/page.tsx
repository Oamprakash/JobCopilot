"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Problems } from "@/components/Problems";
import { api, type AdminOverview, type AdminUser, type AdzunaStatus, type Counted } from "@/lib/api";
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

        {me?.isAdmin && <JobSources />}
        {me?.isAdmin && <UsersTable />}
      </main>
    </>
  );
}

/** The Adzuna switch: off means no Adzuna calls, and its jobs stop showing as new matches and in daily emails. */
function JobSources() {
  const [status, setStatus] = useState<AdzunaStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    api.adzunaStatus().then(setStatus).catch(setError);
  }, []);

  async function toggle(enabled: boolean) {
    setBusy(true);
    setError(null);
    try {
      setStatus(await api.setAdzuna(enabled));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card stack" style={{ gap: 12 }}>
      <h2>Job sources</h2>
      <Problems error={error} />
      {!status ? (
        <p className="muted small" style={{ margin: 0 }}>Loading…</p>
      ) : (
        <div className="source-row">
          <div className="stack" style={{ gap: 4, minWidth: 0 }}>
            <strong>Adzuna job search</strong>
            <span className="muted small">
              {!status.configured
                ? "Add ADZUNA_APP_ID and ADZUNA_APP_KEY on Render to use it."
                : status.enabled
                  ? `On · ${status.openJobs.toLocaleString()} open jobs · ${status.callsToday} of ${status.dailyLimit} searches used today${status.lastSyncedAt ? ` · last search ${ago(status.lastSyncedAt)}` : ""}`
                  : "Off · no Adzuna searches; its jobs are hidden from new matches and daily emails. Saved jobs stay saved."}
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
              aria-label="Adzuna job search"
            />
            <span className="switch-track" aria-hidden="true"><span className="switch-thumb" /></span>
          </label>
        </div>
      )}
      <p className="muted small" style={{ margin: 0 }}>Company job boards (Greenhouse, Lever, Ashby) aren&apos;t affected by this switch.</p>
    </section>
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
