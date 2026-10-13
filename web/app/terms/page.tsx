import type { Metadata } from "next";
import Link from "next/link";
import { LegalPage } from "@/components/LegalPage";
import { brand } from "@/lib/brand";

export const metadata: Metadata = { title: `Terms of use · ${brand.name}` };

export default function TermsPage() {
  return (
    <LegalPage title="Terms of use">
      <p>
        These terms are an agreement between you and {brand.operator} about using {brand.name}, its website and its browser extension.
        By signing in you accept them. Our <Link href="/privacy">privacy policy</Link> explains how we handle your data.
      </p>

      <h2>What {brand.name} does</h2>
      <p>
        It shows jobs from public job boards and licensed job-search providers, scores how well they fit your profile, drafts tailored
        resumes, cover letters and answers from facts you confirmed, fills application forms when you ask, and tracks your
        applications. It never submits an application for you: you review everything and click Submit on the employer&apos;s site.
      </p>

      <h2>Your account</h2>
      <ul>
        <li>You must be 18 or older and use your own email address.</li>
        <li>Keep access to your email secure: anyone who can open your sign-in emails can open your account.</li>
        <li>You can delete your account at any time from your profile.</li>
      </ul>

      <h2>Your responsibilities</h2>
      <ul>
        <li>
          <strong>Be truthful.</strong> Give accurate information in your profile. Before you send anything, check that every claim is
          true; you are responsible for what you submit to employers.
        </li>
        <li>
          <strong>Review AI drafts.</strong> Drafts and fit scores are generated automatically, including by AI. We guard against invented
          claims, but drafts can still contain mistakes.
        </li>
        <li>
          <strong>Follow employers&apos; and job sites&apos; rules.</strong> Use the extension only on application forms you are
          completing for yourself.
        </li>
        <li>
          <strong>Don&apos;t misuse the service:</strong> no automated access, scraping, reselling, attempts to reach other users&apos;
          data, or anything unlawful.
        </li>
      </ul>

      <h2>Jobs and outcomes</h2>
      <p>
        Job listings come from third parties. We don&apos;t guarantee that a listing is accurate, still open, or that you&apos;ll get
        an interview or offer. Links take you to other sites, whose own terms apply.
      </p>

      <h2>Plans</h2>
      <p>
        The free plan includes a limited number of tailored applications each month, shown in the app. If we offer paid plans,
        their price and terms will be shown before you pay.
      </p>

      <h2>Your content</h2>
      <p>
        You own your resume, profile and the applications prepared for you. You let us store and process them only to provide{" "}
        {brand.name} to you, as the privacy policy describes.
      </p>

      <h2>Changes and availability</h2>
      <p>
        We may change or pause features, and update these terms. If a change matters, we&apos;ll tell you before it applies. We may
        suspend accounts that break these terms.
      </p>

      <h2>Liability</h2>
      <p>
        {brand.name} is provided &ldquo;as is&rdquo;. To the extent the law allows, we aren&apos;t liable for indirect losses, lost
        opportunities, or decisions made by employers, and our total liability is limited to the amount you paid us in the 12
        months before the claim (or ₹1,000 if you paid nothing).
      </p>

      <h2>Law</h2>
      <p>These terms are governed by the laws of India. Disputes go to the courts of {brand.jurisdiction}.</p>

      <h2>Contact</h2>
      <p>
        <a href={`mailto:${brand.contactEmail}`}>{brand.contactEmail}</a>
      </p>
    </LegalPage>
  );
}
