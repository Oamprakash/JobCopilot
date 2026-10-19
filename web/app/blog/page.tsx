import type { Metadata } from "next";
import Link from "next/link";
import { PublicHeader } from "@/components/PublicHeader";
import { formatDate, publishedPosts } from "@/lib/blog";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Blog: job search advice from someone who hires",
  description: "Practical, India-focused advice on resumes, applications and offers, plus live data on what tech jobs are asking for.",
  alternates: { canonical: "/blog" },
};

export default async function BlogIndex() {
  const posts = await publishedPosts();
  return (
    <div className="landing">
      <PublicHeader />
      <main className="landing-section public-jobs blog">
        <h1>Blog</h1>
        <p className="muted">Practical job search advice from someone who hires engineers, and live data on what companies in India are asking for.</p>
        {posts.length === 0 ? (
          <p className="card muted">The first posts are on their way.</p>
        ) : (
          <ol className="public-job-list">
            {posts.map((p) => (
              <li key={p.slug} className="card stack" style={{ gap: 6 }}>
                <span className="small muted">{formatDate(p.date)}</span>
                <h2 className="job-title" style={{ margin: 0 }}>
                  <Link href={`/blog/${p.slug}`}>{p.title}</Link>
                </h2>
                <p className="small" style={{ margin: 0 }}>{p.description}</p>
              </li>
            ))}
          </ol>
        )}
      </main>
    </div>
  );
}
