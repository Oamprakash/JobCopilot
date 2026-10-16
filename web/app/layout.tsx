import { AcquisitionCapture } from "@/components/AcquisitionCapture";
import { SiteFooter } from "@/components/SiteFooter";
import { brand } from "@/lib/brand";
import type { Metadata } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import "./globals.css";

const sans = Plus_Jakarta_Sans({ subsets: ["latin"], variable: "--font-sans", display: "swap" });

const description =
  "Get well-matched jobs every morning, tailor your resume and cover letter from your real experience, and fill application forms in one click. You always press Submit. Free during the beta.";

export const metadata: Metadata = {
  metadataBase: new URL(brand.siteUrl),
  title: { default: `${brand.name}: job matches and tailored resumes, without auto-apply`, template: `%s · ${brand.name}` },
  description,
  applicationName: brand.name,
  keywords: ["job search", "resume tailoring", "tailored resume", "cover letter", "job matches", "ATS resume", "remote jobs India", "job application tracker"],
  openGraph: {
    type: "website",
    siteName: brand.name,
    url: "/",
    title: `${brand.name}: jobs that fit, applications that are truly yours`,
    description,
    locale: "en_IN",
  },
  twitter: { card: "summary_large_image", title: `${brand.name}: jobs that fit, applications that are truly yours`, description },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={sans.variable}>
      <body>
        <AcquisitionCapture />
        {children}
        <SiteFooter />
      </body>
    </html>
  );
}
