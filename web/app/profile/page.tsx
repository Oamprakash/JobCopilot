"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { PreferencesForm } from "@/components/PreferencesForm";
import { Problems } from "@/components/Problems";
import { cleanProfile, ProfileEditor } from "@/components/ProfileEditor";
import { ResumeUpload } from "@/components/ResumeUpload";
import { api, ApiError, type Preferences, type Profile, type ResumeFile, type ResumeUpload as Upload } from "@/lib/api";
import { useMe } from "@/lib/useMe";

export default function ProfilePage() {
  const router = useRouter();
  const me = useMe();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [version, setVersion] = useState<number | null>(null);
  const [resumeFile, setResumeFile] = useState<ResumeFile | null>(null);
  const [pendingFileId, setPendingFileId] = useState<string | undefined>(undefined);
  const [removed, setRemoved] = useState<string[]>([]);
  const [preferences, setPreferences] = useState<Preferences | null>(null);

  const [profileStatus, setProfileStatus] = useState<{ busy: boolean; error: unknown; saved: boolean }>({ busy: false, error: null, saved: false });
  const [prefStatus, setPrefStatus] = useState<{ busy: boolean; error: unknown; saved: boolean }>({ busy: false, error: null, saved: false });

  useEffect(() => {
    if (!me) return;
    api
      .getProfile()
      .then((p) => {
        setProfile(p.profile);
        setVersion(p.version);
        setResumeFile(p.resumeFile);
      })
      .catch((e) => {
        if (e instanceof ApiError && e.status === 404) router.replace("/onboarding");
      });
    api.getPreferences().then(setPreferences).catch(() => undefined);
  }, [me, router]);

  function onNewResume(u: Upload) {
    setProfile(u.profile);
    setResumeFile(u.resumeFile);
    setPendingFileId(u.resumeFile.id);
    setRemoved(u.removed);
    setProfileStatus({ busy: false, error: null, saved: false });
  }

  async function saveProfile() {
    if (!profile) return;
    setProfileStatus({ busy: true, error: null, saved: false });
    try {
      const saved = await api.saveProfile(cleanProfile(profile), pendingFileId);
      setProfile(saved.profile);
      setVersion(saved.version);
      setResumeFile(saved.resumeFile);
      setPendingFileId(undefined);
      setRemoved([]);
      setProfileStatus({ busy: false, error: null, saved: true });
    } catch (e) {
      setProfileStatus({ busy: false, error: e, saved: false });
    }
  }

  async function savePreferences() {
    if (!preferences) return;
    setPrefStatus({ busy: true, error: null, saved: false });
    try {
      setPreferences(await api.savePreferences(preferences));
      setPrefStatus({ busy: false, error: null, saved: true });
    } catch (e) {
      setPrefStatus({ busy: false, error: e, saved: false });
    }
  }

  async function deleteAccount() {
    const ok = window.confirm("Delete your account, profile and uploaded resumes? This can't be undone.");
    if (!ok) return;
    try {
      await api.deleteAccount();
      router.replace("/login");
    } catch (e) {
      setProfileStatus({ busy: false, error: e, saved: false });
    }
  }

  return (
    <>
      <AppHeader email={me?.email} />
      <main className="container stack" style={{ gap: 24 }}>
        <div className="row" style={{ justifyContent: "space-between" }}>
          <div className="stack" style={{ gap: 4 }}>
            <h1>Your profile</h1>
            <p className="muted small" style={{ margin: 0 }}>
              {version ? `Version ${version}` : "Loading…"}
              {resumeFile ? ` · from ${resumeFile.fileName}` : ""}
            </p>
          </div>
        </div>

        {pendingFileId && (
          <div className="notice notice-warn">
            Loaded from your new resume. Review the fields and save to make it your profile.
            {removed.length > 0 && (
              <ul>
                {removed.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div className="split">
          <section className="card main stack">
            {profile ? <ProfileEditor value={profile} onChange={setProfile} /> : <p className="muted">Loading…</p>}
            <Problems error={profileStatus.error} />
            {profileStatus.saved && <div className="notice notice-good">Saved as version {version}.</div>}
            <div className="row">
              <button type="button" className="btn btn-primary" onClick={saveProfile} disabled={!profile || profileStatus.busy}>
                {profileStatus.busy ? "Saving…" : "Save profile"}
              </button>
            </div>
          </section>

          <aside className="side stack">
            <section className="card stack">
              <h2>Preferences</h2>
              {preferences ? <PreferencesForm value={preferences} onChange={setPreferences} /> : <p className="muted">Loading…</p>}
              <Problems error={prefStatus.error} />
              {prefStatus.saved && <div className="notice notice-good">Preferences saved.</div>}
              <button type="button" className="btn btn-primary" onClick={savePreferences} disabled={!preferences || prefStatus.busy}>
                {prefStatus.busy ? "Saving…" : "Save preferences"}
              </button>
            </section>

            <section className="card stack">
              <h2>Resume</h2>
              <p className="muted small" style={{ margin: 0 }}>
                Uploading a new resume refills the profile form. Nothing changes until you save.
              </p>
              <ResumeUpload compact onUploaded={onNewResume} />
            </section>

            <section className="card stack">
              <h2>Delete account</h2>
              <p className="muted small" style={{ margin: 0 }}>
                Removes your account, every profile version, preferences and uploaded resumes.
              </p>
              <button type="button" className="btn btn-danger" onClick={deleteAccount}>
                Delete my account
              </button>
            </section>
          </aside>
        </div>
      </main>
    </>
  );
}
