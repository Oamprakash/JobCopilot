import type { Metadata } from "next";
import Link from "next/link";
import { LegalPage } from "@/components/LegalPage";
import { brand } from "@/lib/brand";

export const metadata: Metadata = { title: `Privacy policy · ${brand.name}` };

export default function PrivacyPage() {
  const mail = <a href={`mailto:${brand.contactEmail}`}>{brand.contactEmail}</a>;
  return (
    <LegalPage title="Privacy policy">
      <p>
        {brand.name} (&ldquo;we&rdquo;) helps you find jobs that fit, prepare honest tailored applications, and keep track of them. This
        policy explains what personal data we collect, why, who processes it for us, how long we keep it, and your rights under
        India&apos;s Digital Personal Data Protection Act, 2023. {brand.operator} is responsible for your data (the &ldquo;Data
        Fiduciary&rdquo;).
      </p>

      <h2>What we collect, and why</h2>
      <table className="legal-table">
        <thead>
          <tr><th>Data</th><th>Why we use it</th></tr>
        </thead>
        <tbody>
          <tr><td>Your email address</td><td>To sign you in with one-time links and send emails you asked for, such as follow-up reminders.</td></tr>
          <tr>
            <td>Your resume file and its text</td>
            <td>To build your profile. We read the text only to fill in the profile you then review and save.</td>
          </tr>
          <tr>
            <td>Your profile: name, contact details, location, links, work history, education, skills</td>
            <td>To match you with jobs, tailor applications from facts you confirmed, and fill application forms when you ask.</td>
          </tr>
          <tr><td>Your job preferences: roles, locations, work mode, pay floor, notice period, companies to skip, visa needs</td><td>To find and filter jobs for you.</td></tr>
          <tr>
            <td>Your matches, tailored applications (resume, cover letter, answers, any job description you paste) and tracker (stages, dates, notes)</td>
            <td>To show your feed, let you review and download applications, and remind you to follow up.</td>
          </tr>
          <tr>
            <td>Activity in the app: sign-ins, searches you save, feed refreshes, jobs you open, save or skip, applications you tailor, approve or download, clicks through to apply, and use of the browser extension</td>
            <td>To run the service, fix problems, prevent abuse and understand which features help. Our administrators can see this for each account; their own access to it is logged.</td>
          </tr>
          <tr><td>A session cookie</td><td>To keep you signed in (30 days). It is the only cookie we set. We use no advertising or third-party tracking cookies.</td></tr>
        </tbody>
      </table>
      <p>
        We don&apos;t collect sensitive details such as caste, religion, health or government ID numbers, and we never fill pay, visa or
        demographic questions on application forms for you.
      </p>

      <h2>The browser extension</h2>
      <p>
        The extension works only when you click it. On the page you&apos;re on, it fills empty form fields with your profile, tailored
        answers and resume, or reads the job posting when you choose <em>Save this job</em>. It doesn&apos;t read other pages, your
        browsing history or anything you type, and it never submits a form. It stores one sign-in token on your device; you can
        disconnect it from your profile at any time.
      </p>

      <h2>Who processes data for us</h2>
      <p>We share data only with these service providers, only to run {brand.name}, and under their own security and privacy commitments:</p>
      <ul>
        <li><strong>Render</strong> (hosting and database) and <strong>Cloudflare</strong> (file storage and network): store and serve everything above.</li>
        <li><strong>Resend</strong>: sends sign-in and reminder emails to your address.</li>
        <li>
          <strong>Anthropic</strong> (the Claude AI model), when enabled: reads your resume text and profile to fill in your profile,
          score job fit and draft tailored applications. Under its commercial terms, Anthropic doesn&apos;t train its models on this data.
        </li>
        <li><strong>Adzuna</strong> (job search): we send search keywords and cities, never your name, email or resume.</li>
      </ul>
      <p>Some of these providers process data outside India. We don&apos;t sell your data or share it with employers or recruiters.</p>

      <h2>How long we keep it</h2>
      <ul>
        <li>Your account, profile, preferences, applications and tracker: until you delete your account.</li>
        <li>Activity records: 180 days, then deleted automatically.</li>
        <li>Unused sign-in links expire after 15 minutes; extension connection codes after 10 minutes.</li>
      </ul>

      <h2>Your rights</h2>
      <ul>
        <li><strong>Access and correction</strong>: see and edit your profile and preferences any time in the app.</li>
        <li>
          <strong>Erasure</strong>: <Link href="/profile">delete your account</Link> in one step from your profile. It removes your
          account, every profile version, preferences, applications, tracker, activity records and uploaded files.
        </li>
        <li><strong>Withdraw consent</strong>: deleting your account withdraws it; you can also disconnect the extension at any time.</li>
        <li>
          <strong>Grievances</strong>: write to {mail}. We aim to reply within 7 days and resolve within 30. If you&apos;re not
          satisfied, you can complain to the Data Protection Board of India.
        </li>
      </ul>

      <h2>Security</h2>
      <p>
        Connections are encrypted (HTTPS). We store only hashes of sign-in links and extension tokens, the session cookie can&apos;t be
        read by page scripts, and access to production data is limited to administrators.
      </p>

      <h2>Children</h2>
      <p>{brand.name} is for people aged 18 and over. We don&apos;t knowingly collect data from anyone younger.</p>

      <h2>Changes</h2>
      <p>If we change this policy in a way that matters, we&apos;ll tell you by email or in the app before the change applies.</p>

      <h2>Contact</h2>
      <p>Questions about your data: {mail}.</p>
    </LegalPage>
  );
}
