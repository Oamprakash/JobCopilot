import type { MetadataRoute } from "next";
import { brand } from "@/lib/brand";

/** The public pages search engines should know about. */
export default function sitemap(): MetadataRoute.Sitemap {
  const updated = new Date("2026-10-17");
  return [
    { url: `${brand.siteUrl}/`, lastModified: updated, changeFrequency: "weekly", priority: 1 },
    { url: `${brand.siteUrl}/login`, lastModified: updated, changeFrequency: "yearly", priority: 0.5 },
    { url: `${brand.siteUrl}/privacy`, lastModified: updated, changeFrequency: "yearly", priority: 0.2 },
    { url: `${brand.siteUrl}/terms`, lastModified: updated, changeFrequency: "yearly", priority: 0.2 },
  ];
}
