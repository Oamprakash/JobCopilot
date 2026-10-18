import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { PublicHeader } from "@/components/PublicHeader";
import { brand } from "@/lib/brand";
import { postedLabel, publicPage } from "@/lib/publicJobs";

export const dynamic = "force-dynamic";

type Props = { params: { slug: string } };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const data = await publicPage(params.slug);
  if (!data) return { title: "Jobs", robots: { index: false } };
  const { page } = data;
  return {
    title: `${page.title} (${page.count} open)`,
    description: `${page.count} open ${page.role} roles${page.placeSlug === "remote" ? " you can do remotely from India" : ` in ${page.place}`}, from companies' own career sites and updated every hour. See how your resume fits any of them in a minute, free.`,
    alternates: { canonical: `/jobs/${page.slug}` },
    openGraph: { title: `${page.title} · ${brand.name}`, url: `/jobs/${page.slug}` },
  };
}

export default async function JobPage({ params }: Props) {
  const data = await publicPage(params.slug);
  if (!data) notFound();
  const { page, jobs, related } = data;
  const sameRole = related.filter((r) => r.roleSlug === page.roleSlug);
  const samePlace = related.filter((r) => r.placeSlug === page.placeSlug);
  const breadcrumbs = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      { "@type": "ListItem", position: 1, name: "Jobs", item: `${brand.siteUrl}/jobs` },
      { "@type": "ListItem", position: 2, name: page.title, item: `${brand.siteUrl}/jobs/${page.slug}` },
    ],
  };

  return (
    <div className="landing">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbs) }} />
      <PublicHeader />
      <main className="landing-section public-jobs">
        <nav className="small" aria-label="Breadcrumb">
          <Link href="/jobs">Jobs</Link> <span className="muted">/</span> <span className="muted">{page.title}</span>
        </nav>
        <h1>{page.title}</h1>
        <p className="muted">
          {page.count} open {page.count === 1 ? "role" : "roles"} from companies&apos; own career sites, updated every hour.
        </p>

        <section className="card public-cta">
          <div className="stack" style={{ gap: 4 }}>
            <strong>Not sure which ones you&apos;d get?</strong>
            <span className="small muted">
              Check your resume against any job below in a minute, free and without signing up. Or get the best fits sent to you every
              morning.
            </span>
          </div>
          <Link href="/login" className="btn btn-primary">Get matched daily</Link>
        </section>

        <ol className="public-job-list">
          {jobs.map((j) => (
            <li key={j.id} className="card stack" style={{ gap: 8 }}>
              <div>
                <h2 className="job-title" style={{ margin: 0 }}>{j.title}</h2>
                <p className="muted small" style={{ margin: "4px 0 0" }}>
                  {[j.company, j.location || null, j.remote && !/remote/i.test(j.location) ? "Remote" : null, postedLabel(j.postedAt)]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </div>
              {j.snippet && <p className="small" style={{ margin: 0 }}>{j.snippet}</p>}
              <div className="row" style={{ gap: 8 }}>
                <Link
                  href={`/?fit=${encodeURIComponent(j.id)}&fitTitle=${encodeURIComponent(`${j.title} at ${j.company}`)}#try`}
                  className="btn btn-primary"
                >
                  Check my fit
                </Link>
                <a href={j.applyUrl} className="btn btn-secondary" target="_blank" rel="nofollow noopener noreferrer">
                  View job
                </a>
              </div>
            </li>
          ))}
        </ol>

        {(sameRole.length > 0 || samePlace.length > 0) && (
          <div className="grid-2" style={{ marginTop: 32 }}>
            {sameRole.length > 0 && (
              <section className="stack" style={{ gap: 8 }}>
                <h2 style={{ fontSize: 18 }}>{page.role} jobs elsewhere</h2>
                <ul className="public-links">
                  {sameRole.map((r) => (
                    <li key={r.slug}>
                      <Link href={`/jobs/${r.slug}`}>{r.title}</Link> <span className="muted small">{r.count}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}
            {samePlace.length > 0 && (
              <section className="stack" style={{ gap: 8 }}>
                <h2 style={{ fontSize: 18 }}>More jobs {page.placeSlug === "remote" ? "you can do remotely" : `in ${page.place}`}</h2>
                <ul className="public-links">
                  {samePlace.map((r) => (
                    <li key={r.slug}>
                      <Link href={`/jobs/${r.slug}`}>{r.title}</Link> <span className="muted small">{r.count}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
