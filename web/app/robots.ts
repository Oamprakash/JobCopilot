import type { MetadataRoute } from "next";
import { brand } from "@/lib/brand";

/** Public pages are open to search engines; signed-in pages and the API are not (they need an account anyway). */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/feed", "/apply/", "/tracker", "/profile", "/onboarding", "/admin", "/auth/"],
    },
    sitemap: `${brand.siteUrl}/sitemap.xml`,
    host: brand.siteUrl,
  };
}
