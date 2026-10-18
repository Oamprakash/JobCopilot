import type { Metadata } from "next";
import Link from "next/link";
import { PublicHeader } from "@/components/PublicHeader";
import { brand } from "@/lib/brand";
import { publicIndex, type PublicPage } from "@/lib/publicJobs";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Tech jobs in India by role and city",
  description:
    "Open software, data, design and product jobs from companies' own career sites, by role and city: Bengaluru, Chennai, Hyderabad, Pune, Mumbai, Delhi NCR and remote. Updated every hour.",
  alternates: { canonical: "/jobs" },
};

export default async function JobsIndex() {
  const index = await publicIndex();
  const byRole = new Map<string, PublicPage[]>();
  for (const p of index?.pages ?? []) byRole.set(p.role, [...(byRole.get(p.role) ?? []), p]);
  const roles = [...byRole.entries()].sort((a, b) => Math.max(...b[1].map((p) => p.count)) - Math.max(...a[1].map((p) => p.count)));

  return (
    <div className="landing">
      <PublicHeader />
      <main className="landing-section public-jobs">
        <h1>Tech jobs in India, by role and city</h1>
        <p className="muted">
          {index ? `${index.totalJobs.toLocaleString("en-IN")} open jobs` : "Open jobs"} from companies&apos; own career sites, updated every
          hour. Pick a role and place, then check how your resume fits any job in a minute, free.
        </p>
        {roles.length === 0 ? (
          <p className="card muted">Job pages are being prepared. Check back in a little while.</p>
        ) : (
          <div className="public-role-grid">
            {roles.map(([role, pages]) => (
              <section key={role} className="card stack" style={{ gap: 8 }}>
                <h2>{role}</h2>
                <ul className="public-links">
                  {pages
                    .sort((a, b) => b.count - a.count)
                    .map((p) => (
                      <li key={p.slug}>
                        <Link href={`/jobs/${p.slug}`}>{p.placeSlug === "remote" ? "Remote" : p.place}</Link>
                        <span className="muted small">{p.count}</span>
                      </li>
                    ))}
                </ul>
              </section>
            ))}
          </div>
        )}
        <p className="small muted" style={{ marginTop: 32 }}>
          {brand.name} lists jobs straight from employers&apos; job boards and links you to the original posting to apply. It never applies for you.
        </p>
      </main>
    </div>
  );
}
