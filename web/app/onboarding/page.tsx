"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { PreferencesForm } from "@/components/PreferencesForm";
import { Problems } from "@/components/Problems";
import { cleanProfile, ProfileEditor } from "@/components/ProfileEditor";
import { ResumeUpload } from "@/components/ResumeUpload";
import { api, emptyPreferences, type Preferences, type Profile, type ResumeUpload as Upload } from "@/lib/api";
import { clearHandoff, takeHandoff } from "@/lib/fitCheckHandoff";
import { useMe } from "@/lib/useMe";

type Step = "upload" | "review" | "done";

export default function OnboardingPage() {
  const me = useMe();
  const [step, setStep] = useState<Step>("upload");
  const [upload, setUpload] = useState<Upload | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [preferences, setPreferences] = useState<Preferences>(emptyPreferences);
  const [version, setVersion] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);

  function onUploaded(u: Upload) {
    setUpload(u);
    setProfile(u.profile);
    // Seed preferences from the resume; the user changes them on the same screen.
    setPreferences((p) => ({
      ...p,
      roles: p.roles.length ? p.roles : u.profile.headline ? [u.profile.headline] : [],
      locations: p.locations.length ? p.locations : u.profile.location ? [u.profile.location] : [],
      experienceYears: p.experienceYears ?? u.profile.totalExperienceYears ?? null,
    }));
    setStep("review");
  }

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [step]);

  // Coming from the home page's fit check: the resume was already read, so start at the review step.
  const [fromFitCheck, setFromFitCheck] = useState(false);
  useEffect(() => {
    const h = takeHandoff();
    if (!h) return;
    setFromFitCheck(true);
    onUploaded({ resumeFile: { id: "", fileName: h.fileName }, parser: "heuristic", profile: h.profile, removed: h.removed });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function save() {
    if (!profile || !upload) return;
    setSaving(true);
    setError(null);
    try {
      const saved = await api.saveProfile(cleanProfile(profile), upload.resumeFile.id || undefined);
      clearHandoff();
      await api.savePreferences(preferences);
      setVersion(saved.version);
      setStep("done");
    } catch (e) {
      setError(e);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } finally {
      setSaving(false);
    }
  }

  const stepState = (s: Step) => {
    const order: Step[] = ["upload", "review", "done"];
    const diff = order.indexOf(s) - order.indexOf(step);
    return diff < 0 ? "done" : diff === 0 ? "current" : "todo";
  };

  return (
    <>
      <AppHeader email={me?.email} isAdmin={me?.isAdmin} />
      <main className="container stack" style={{ gap: 24 }}>
        <ol className="steps" aria-label="Onboarding progress">
          <li data-state={stepState("upload")}>1 Resume</li>
          <li data-state={stepState("review")}>2 Confirm and set preferences</li>
          <li data-state={stepState("done")}>3 Ready</li>
        </ol>

        {step === "upload" && (
          <>
            <div className="stack" style={{ gap: 8 }}>
              <h1>Start with your resume</h1>
              <p className="muted" style={{ margin: 0, maxWidth: 640 }}>
                We turn it into your master profile. You check every field before anything is saved.
              </p>
            </div>
            <ResumeUpload onUploaded={onUploaded} />
          </>
        )}

        {step === "review" && profile && upload && (
          <>
            <div className="stack" style={{ gap: 8 }}>
              <h1>Check what we read from your resume</h1>
              <p className="muted" style={{ margin: 0, maxWidth: 640 }}>
                This becomes your master profile. Tailored resumes can reorder and reword it, but never add anything that
                isn&apos;t here.
              </p>
            </div>

            {upload.removed.length > 0 && (
              <div className="notice notice-warn">
                We left out {upload.removed.length} item{upload.removed.length === 1 ? "" : "s"} we couldn&apos;t find in your
                resume. Add them back below if they&apos;re accurate.
                <ul>
                  {upload.removed.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
              </div>
            )}
            <Problems error={error} />

            <div className="split">
              <section className="card main">
                <p className="small muted" style={{ marginTop: 0 }}>
                  From <strong>{upload.resumeFile.fileName}</strong>
                  {fromFitCheck && ", the resume you used to check a job"}
                </p>
                <ProfileEditor value={profile} onChange={setProfile} />
              </section>
              <aside className="card side stack">
                <h2 style={{ fontSize: 18 }}>What are you looking for?</h2>
                <PreferencesForm value={preferences} onChange={setPreferences} />
                <button type="button" className="btn btn-primary btn-block" onClick={save} disabled={saving}>
                  {saving ? "Saving…" : "Save my profile"}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => {
                    clearHandoff();
                    setFromFitCheck(false);
                    setStep("upload");
                  }}
                  disabled={saving}
                >
                  Upload a different resume
                </button>
              </aside>
            </div>
          </>
        )}

        {step === "done" && (
          <section className="card stack" style={{ maxWidth: 640 }}>
            <div className="notice notice-good">Profile saved{version ? ` (version ${version})` : ""}.</div>
            <h1>You&apos;re set up</h1>
            <p className="muted" style={{ margin: 0 }}>
              We&apos;ll now check open jobs against your profile and preferences. New matches arrive every day.
            </p>
            <div className="row">
              <Link href="/feed" className="btn btn-primary">
                See my matches
              </Link>
              <Link href="/profile" className="btn btn-secondary">
                Edit profile
              </Link>
            </div>
          </section>
        )}
      </main>
    </>
  );
}
