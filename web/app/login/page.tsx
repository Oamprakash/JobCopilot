"use client";

import { useState, type FormEvent } from "react";
import { Logo } from "@/components/AppHeader";
import { Problems } from "@/components/Problems";
import { api } from "@/lib/api";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [devLink, setDevLink] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api.requestLink(email);
      setSentTo(email.trim());
      setDevLink(res.devLink ?? null);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="narrow stack" style={{ gap: 24 }}>
      <div className="row" style={{ gap: 10, fontWeight: 700, fontSize: 18 }}>
        <Logo /> Apply Copilot
      </div>

      {sentTo ? (
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
            No password. We&apos;ll email you a one-time link.
          </p>
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
        </form>
      )}
    </main>
  );
}
