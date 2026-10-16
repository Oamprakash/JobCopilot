"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Problems } from "@/components/Problems";
import { api, type AdminUserDetail } from "@/lib/api";
import { ago, describe, when } from "@/lib/adminFormat";
import { useMe } from "@/lib/useMe";

export default function AdminUserPage() {
  const me = useMe();
  const id = String(useParams().id);
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (!me?.isAdmin) return;
    api.adminUser(id).then(setDetail).catch(setError);
  }, [me, id]);

  const u = detail?.user;
  const p = detail?.preferences;
  return (
    <>
      <AppHeader email={me?.email} isAdmin={me?.isAdmin} />
      <main className="container stack" style={{ gap: 20 }}>
        <Link href="/admin" className="small" style={{ fontWeight: 600 }}>← Admin</Link>
        <Problems error={error} />
        {me && !me.isAdmin && <div className="card muted">Admins only.</div>}
        {!detail || !u ? (
          !error && me?.isAdmin && <div className="card muted">Loading…</div>
        ) : (
          <>
            <div>
              <h1>{u.name ?? u.email}</h1>
              <p className="muted" style={{ margin: "6px 0 0" }}>
                {[u.name ? u.email : null, u.headline, `joined ${new Date(u.createdAt).toLocaleDateString()}`, `last active ${ago(u.lastSeenAt)}`, u.plan === "pro" ? "Pro" : "Free plan"]
                  .filter(Boolean)
                  .join(" · ")}
              </p>
            </div>

            <div className="split">
              <section className="main card stack" style={{ gap: 4 }}>
                <h2 style={{ marginBottom: 8 }}>Activity ({detail.timeline.length}{detail.timeline.length === 200 ? ", latest" : ""})</h2>
                {detail.timeline.length === 0 && <p className="muted small" style={{ margin: 0 }}>Nothing recorded yet.</p>}
                <ol className="timeline">
                  {detail.timeline.map((e, i) => (
                    <li key={i}>
                      <span className="timeline-time" title={when(e.at)}>{ago(e.at)}</span>
                      <span>{describe(e)}</span>
                    </li>
                  ))}
                </ol>
              </section>

              <aside className="side stack">
                <section className="card stack" style={{ gap: 8 }}>
                  <h2>Current search</h2>
                  {p ? (
                    <>
                      <div className="kv"><span>Roles</span><strong>{p.roles.join(", ") || "—"}</strong></div>
                      <div className="kv"><span>Locations</span><strong>{p.locations.join(", ") || "Anywhere in India"}</strong></div>
                      <div className="kv"><span>Work mode</span><strong>{p.remoteMode}</strong></div>
                      {p.needsVisaSponsorship && <div className="kv"><span>Visa</span><strong>Needs sponsorship</strong></div>}
                    </>
                  ) : (
                    <p className="muted small" style={{ margin: 0 }}>No preferences saved.</p>
                  )}
                </section>
                <section className="card stack" style={{ gap: 8 }}>
                  <h2>Totals</h2>
                  <div className="kv"><span>Found us via</span><strong>{u.source ? [u.source, u.campaign].filter(Boolean).join(" · ") : "Unknown"}</strong></div>
                  {u.referrer && <div className="kv"><span>Referring site</span><strong>{u.referrer}</strong></div>}
                  <div className="kv"><span>Feed refreshes</span><strong>{u.refreshes}</strong></div>
                  <div className="kv"><span>Applications tailored</span><strong>{u.tailored}</strong></div>
                  <div className="kv"><span>Approved</span><strong>{u.approved}</strong></div>
                  <div className="kv"><span>Clicks to apply</span><strong>{u.applyClicks}</strong></div>
                </section>
                <section className="card stack" style={{ gap: 8 }}>
                  <h2>Applications ({detail.applications.length})</h2>
                  {detail.applications.length === 0 && <p className="muted small" style={{ margin: 0 }}>None yet.</p>}
                  {detail.applications.map((a) => (
                    <div key={a.jobId}>
                      <a href={a.applyUrl} target="_blank" rel="noopener noreferrer" style={{ fontWeight: 600 }}>{a.title}</a>
                      <span className="hint" style={{ display: "block" }}>{a.company} · {a.status} · {ago(a.updatedAt)}</span>
                    </div>
                  ))}
                </section>
              </aside>
            </div>
          </>
        )}
      </main>
    </>
  );
}
