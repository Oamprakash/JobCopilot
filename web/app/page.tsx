import type { Metadata } from "next";
import { LandingPage } from "@/components/LandingPage";
import { brand } from "@/lib/brand";

export const metadata: Metadata = { alternates: { canonical: "/" } };

/** Describes the app to search engines (schema.org), so results can show what it is and that it's free. */
const structuredData = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: brand.name,
  url: brand.siteUrl,
  applicationCategory: "BusinessApplication",
  operatingSystem: "Any (web browser)",
  description:
    "Daily job matches with a fit score, resumes and cover letters tailored from your real experience, form filling for Greenhouse, Lever, Ashby and Workday, and an application tracker. Never auto-applies.",
  offers: { "@type": "Offer", price: "0", priceCurrency: "INR", description: "Free during the beta" },
  sameAs: [brand.instagram],
};

/** Who runs the site, with the logo search engines may show next to it. */
const organization = {
  "@context": "https://schema.org",
  "@type": "Organization",
  name: brand.name,
  url: brand.siteUrl,
  logo: `${brand.siteUrl}/logo-512.png`,
  email: brand.contactEmail,
  sameAs: [brand.instagram],
};

export default function Home() {
  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }} />
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(organization) }} />
      <LandingPage />
    </>
  );
}
