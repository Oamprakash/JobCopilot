"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Logo } from "@/components/AppHeader";
import { WaitlistForm } from "@/components/WaitlistForm";
import { api } from "@/lib/api";
import { brand } from "@/lib/brand";

const steps = [
  { title: "Add your resume", text: "Upload it once. Check the profile we read from it, and set the roles, cities and pay you want." },
  { title: "Get matched every morning", text: "We read company job boards and score each opening against your profile, with the reasons and the skills you're missing." },
  { title: "Tailor, check, apply", text: "Get a resume and cover letter shaped for the job from your real experience. Review every line, then apply on the company's own site." },
];

const features = [
  { title: "Daily matches with a fit score", text: "Only jobs that fit your roles, places and experience, ranked, with why each one fits." },
  { title: "Tailored resume and cover letter", text: "Your bullets reordered and reworded for the job. A PDF ready to upload." },
  { title: "Form filling in one click", text: "The Chrome extension fills Greenhouse, Lever, Ashby and Workday forms with your details and answers." },
  { title: "A tracker that reminds you", text: "Every application in one place, with a nudge when it's time to follow up." },
  { title: "Interview prep", text: "Likely questions for that job, with the lines from your own resume that answer them." },
  { title: "Jobs abroad, honestly labelled", text: "Singapore, Dubai, Europe and more, with visa sponsorship mentions flagged and \"no sponsorship\" jobs filtered out." },
];

const wont = [
  "Apply or press Submit for you. You always do that.",
  "Add skills, titles, numbers or employers you don't have.",
  "Answer pay, visa or diversity questions on your behalf.",
  "Show ads, or sell or share your data.",
];

const faqs = [
  {
    q: "Does it apply to jobs automatically?",
    a: "No, and it never will. It prepares everything and fills the form, but you review it and press Submit on the employer's site. Mass auto-applying gets candidates ignored; careful applications get replies.",
  },
  {
    q: "Will it make things up to impress recruiters?",
    a: "No. Every line in a tailored resume comes from your own profile. A check runs on every draft and removes any claim, number or skill your profile doesn't show, and tells you what it removed.",
  },
  { q: "Where do the jobs come from?", a: "Public job boards of companies that hire through Greenhouse, Lever and Ashby, plus licensed job search results. Each job links to the original posting." },
  { q: "What does it cost?", a: "It's free during the beta. A paid plan with unlimited tailoring may come later; you'll hear about it before anything changes." },
  {
    q: "What happens to my data?",
    a: "It's used only to find and prepare your applications. No ads, no selling. Delete your account from your profile at any time and everything goes with it.",
  },
];

/** The public home page. Signed-in users go straight to the app. */
export default function Home() {
  const router = useRouter();
  const [inviteOnly, setInviteOnly] = useState(true);

  useEffect(() => {
    api
      .me()
      .then((me) => router.replace(me.hasProfile ? "/feed" : "/onboarding"))
      .catch(() => undefined);
    api
      .site()
      .then((s) => setInviteOnly(s.inviteOnly))
      .catch(() => undefined);
  }, [router]);

  const cta = inviteOnly ? (
    <WaitlistForm />
  ) : (
    <div className="row">
      <Link href="/login" className="btn btn-primary">Get started free</Link>
      <span className="muted small">No password. Sign in with your email.</span>
    </div>
  );

  return (
    <div className="landing">
      <header className="landing-bar">
        <Link href="/" className="brand"><Logo /> {brand.name}</Link>
        <nav className="row" style={{ gap: 4 }}>
          <Link href="/login" className="btn btn-ghost">Sign in</Link>
          <a href="#start" className="btn btn-primary landing-bar-cta">{inviteOnly ? "Join the waitlist" : "Get started"}</a>
        </nav>
      </header>

      <main>
        <section className="landing-hero">
          <div className="landing-hero-copy">
            <p className="landing-eyebrow">Job applications, done properly</p>
            <h1>Apply to jobs that fit, with applications that are truly yours.</h1>
            <p className="landing-lede">
              {brand.name} finds well-matched jobs every morning, tailors your resume and cover letter from your real experience, and
              fills in the application form. You check everything and press Submit yourself.
            </p>
            <div id="start" className="landing-cta">{cta}</div>
          </div>
          <ProductPreview />
        </section>

        <section className="landing-promises" aria-label="Our promises">
          <span>Never invents experience</span>
          <span>You always press Submit</span>
          <span>No ads. Your data isn&apos;t sold</span>
        </section>

        <section className="landing-section">
          <h2>How it works</h2>
          <ol className="landing-steps">
            {steps.map((s, i) => (
              <li key={s.title}>
                <span className="landing-step-n" aria-hidden="true">{i + 1}</span>
                <h3>{s.title}</h3>
                <p>{s.text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="landing-section">
          <h2>Everything for the job hunt, in one place</h2>
          <div className="landing-features">
            {features.map((f) => (
              <div key={f.title} className="landing-feature">
                <h3>{f.title}</h3>
                <p>{f.text}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="landing-section landing-wont">
          <div>
            <h2>What {brand.name} won&apos;t do</h2>
            <p className="muted">The name is a promise. Recruiters notice when an application overstates things, and a rejection costs you more than the job.</p>
          </div>
          <ul>
            {wont.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </section>

        <section className="landing-section">
          <h2>Questions</h2>
          <div className="landing-faq">
            {faqs.map((f) => (
              <details key={f.q}>
                <summary>{f.q}</summary>
                <p>{f.a}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="landing-final">
          <h2>{inviteOnly ? "Get your invite" : "Start your search"}</h2>
          <p className="muted">{inviteOnly ? "We're letting people in a few at a time during the beta." : "Free during the beta. Takes about three minutes to set up."}</p>
          <div className="landing-cta">{cta}</div>
        </section>
      </main>
    </div>
  );
}

/** A still of the product: one match and one tailored resume line, built from the app's own components' styles. */
function ProductPreview() {
  return (
    <div className="landing-preview" aria-hidden="true">
      <div className="card job-card landing-preview-card">
        <div className="score score-good">
          <span className="score-value">86</span>
          <span className="score-label">FIT</span>
        </div>
        <div className="job-body stack" style={{ gap: 8 }}>
          <div>
            <h3 className="job-title">Senior Android Engineer</h3>
            <p className="muted small" style={{ margin: "4px 0 0" }}>Fintech company · Bengaluru · Posted today</p>
          </div>
          <ul className="reasons">
            <li>Your title matches: Android Engineer</li>
            <li>8 of 10 skills match: Kotlin, Compose, Coroutines</li>
          </ul>
          <p className="missing small">Missing: GraphQL</p>
        </div>
      </div>
      <div className="card tight landing-preview-card landing-preview-bullet">
        <span className="bullet-label">REWORDED FOR THIS JOB</span>
        <p className="bullet-original">Built UI screens in Jetpack Compose using our design system</p>
        <p style={{ margin: 0 }}>Built Jetpack Compose UI on a shared design system</p>
        <span className="landing-check">✓ Every fact is from your resume</span>
      </div>
    </div>
  );
}
