import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { BlogCta } from "@/components/BlogCta";
import { LiveInsights } from "@/components/LiveInsights";
import { PublicHeader } from "@/components/PublicHeader";
import { formatDate, getPost } from "@/lib/blog";
import { brand } from "@/lib/brand";
import { render } from "@/lib/markdown";

export const dynamic = "force-dynamic";

type Props = { params: { slug: string } };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const post = await getPost(params.slug);
  if (!post) return { title: "Blog", robots: { index: false } };
  return {
    title: post.title,
    description: post.description,
    alternates: { canonical: `/blog/${post.slug}` },
    // Drafts can be read by their address for review, but stay out of search until published.
    robots: post.draft ? { index: false, follow: false } : undefined,
    openGraph: { type: "article", title: post.title, description: post.description, url: `/blog/${post.slug}`, publishedTime: post.date },
  };
}

export default async function BlogPost({ params }: Props) {
  const post = await getPost(params.slug);
  if (!post) notFound();
  const { blocks } = render(post.body);
  const article = {
    "@context": "https://schema.org",
    "@type": "Article",
    headline: post.title,
    description: post.description,
    datePublished: post.date,
    author: { "@type": "Organization", name: brand.name, url: brand.siteUrl },
    publisher: { "@type": "Organization", name: brand.name, logo: { "@type": "ImageObject", url: `${brand.siteUrl}/logo-512.png` } },
    mainEntityOfPage: `${brand.siteUrl}/blog/${post.slug}`,
  };

  return (
    <div className="landing">
      {!post.draft && <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(article) }} />}
      <PublicHeader />
      <main className="landing-section blog-post">
        {post.draft && <p className="notice notice-warn small">Draft: not listed or indexed yet. Set <code>draft: false</code> to publish.</p>}
        <nav className="small" aria-label="Breadcrumb">
          <Link href="/blog">Blog</Link>
        </nav>
        <h1>{post.title}</h1>
        <p className="small muted blog-byline">
          {brand.founder.byline} · {formatDate(post.date)}
        </p>
        <article className="prose">
          {blocks.map((b, i) =>
            b.kind === "html" ? (
              <div key={i} dangerouslySetInnerHTML={{ __html: b.html }} />
            ) : b.kind === "insights" ? (
              <LiveInsights key={i} role={b.role} />
            ) : (
              <BlogCta key={i} />
            ),
          )}
        </article>
      </main>
    </div>
  );
}
