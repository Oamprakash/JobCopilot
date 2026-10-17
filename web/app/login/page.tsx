"use client";

import Link from "next/link";
import { brand } from "@/lib/brand";
import { useEffect, useState, type FormEvent } from "react";
import { Logo } from "@/components/AppHeader";
import { GoogleSignIn } from "@/components/GoogleSignIn";
import { Problems } from "@/components/Problems";
import { WaitlistForm } from "@/components/WaitlistForm";
import { ApiError, api } from "@/lib/api";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [devLink, setDevLink] = useState<string | null>(null);
  const [notInvited, setNotInvited] = useState(false);
  const [googleShown, setGoogleShown] = useState(false);

  // Invite emails link here with ?email=, so the address is already filled in.
  useEffect(() => {
    const fromLink = new URLSearchParams(window.location.search).get("email");
    if (fromLink) setEmail(fromLink);
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setNotInvited(false);
    try {
      const res = await api.requestLink(email);
      setSentTo(email.trim());
      setDevLink(res.devLink ?? null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) setNotInvited(true);
      else setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="narrow stack" style={{ gap: 24 }}>
      <Link href="/" className="brand" style={{ alignSelf: "flex-start" }}>
        <Logo /> {brand.name}
      </Link>

      {notInvited && !sentTo ? (
        <section className="card stack">
          <h1>You&apos;re not on the list yet</h1>
          <p className="muted" style={{ margin: 0 }}>
            {brand.name} is invite-only during the beta. Join the waitlist and we&apos;ll email you when your spot comes up.
          </p>
          <WaitlistForm initialEmail={email} withNote={false} />
          <button type="button" className="btn btn-ghost" style={{ alignSelf: "flex-start", paddingInline: 0 }} onClick={() => setNotInvited(false)}>
            Try a different email
          </button>
        </section>
      ) : sentTo ? (
        <section className="card stack">
          <h1>Check your email</h1>
          <p className="muted" style={{ margin: 0 }}>
            We sent a sign-in link to <strong>{sentTo}</strong>. It works once and expires in 15 minutes.
          </p>
          {devLink && (
            <p className="notice notice-warn small" style={{ margin: 0 }}>
              Dev mode: <a href={devLink}>open the sign-in link</a>
            </p>
          )}
          <button type="button" className="btn btn-secondary" onClick={() => setSentTo(null)}>
            Use a different email
          </button>
        </section>
      ) : (
        <form className="card stack" onSubmit={submit}>
          <h1>Sign in</h1>
          <p className="muted" style={{ margin: 0 }}>
            No password needed.
          </p>
          <GoogleSignIn
            onShown={setGoogleShown}
            onNotInvited={(googleEmail) => {
              if (googleEmail) setEmail(googleEmail);
              setNotInvited(true);
            }}
          />
          {googleShown && <div className="or-divider" role="separator">or</div>}
          <label className="field">
            Email
            <input
              className="input"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <Problems error={error} />
          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? "Sending…" : "Email me a sign-in link"}
          </button>
          <p className="hint" style={{ margin: 0 }}>
            By signing in you agree to the <Link href="/terms">terms</Link> and <Link href="/privacy">privacy policy</Link>. We keep a
            record of how you use {brand.name} (searches, jobs you open, applications you prepare) to run and improve it; deleting your
            account from your profile erases it.
          </p>
        </form>
      )}
    </main>
  );
}
