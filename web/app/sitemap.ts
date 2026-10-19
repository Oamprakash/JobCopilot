import type { MetadataRoute } from "next";
import { brand } from "@/lib/brand";
import { publishedPosts } from "@/lib/blog";
import { publicIndex } from "@/lib/publicJobs";

// Built on request, so new job pages show up without a redeploy.
export const dynamic = "force-dynamic";

/** The public pages search engines should know about, including every job page with enough open jobs. */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const fixed = new Date("2026-10-17");
  const [index, posts] = await Promise.all([publicIndex(), publishedPosts()]);
  const now = index ? new Date(index.updatedAt) : fixed;
  return [
    { url: `${brand.siteUrl}/`, lastModified: fixed, changeFrequency: "weekly", priority: 1 },
    { url: `${brand.siteUrl}/jobs`, lastModified: now, changeFrequency: "daily", priority: 0.8 },
    ...(index?.pages ?? []).map((p) => ({
      url: `${brand.siteUrl}/jobs/${p.slug}`,
      lastModified: now,
      changeFrequency: "daily" as const,
      priority: 0.7,
    })),
    { url: `${brand.siteUrl}/blog`, lastModified: posts[0] ? new Date(posts[0].date) : fixed, changeFrequency: "weekly", priority: 0.7 },
    ...posts.map((p) => ({ url: `${brand.siteUrl}/blog/${p.slug}`, lastModified: new Date(p.date), changeFrequency: "monthly" as const, priority: 0.6 })),
    { url: `${brand.siteUrl}/login`, lastModified: fixed, changeFrequency: "yearly", priority: 0.5 },
    { url: `${brand.siteUrl}/privacy`, lastModified: fixed, changeFrequency: "yearly", priority: 0.2 },
    { url: `${brand.siteUrl}/terms`, lastModified: fixed, changeFrequency: "yearly", priority: 0.2 },
  ];
}
