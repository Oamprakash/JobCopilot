"use client";

import Link from "next/link";
import { useId, useState, type FormEvent } from "react";
import { Problems } from "@/components/Problems";
import { api, type WaitlistStatus } from "@/lib/api";

/**
 * Email (and an optional note) for the waitlist. When sign-up isn't invite-only, or the email is already
 * invited, it says so and points to sign-in instead.
 */
export function WaitlistForm({ initialEmail = "", withNote = true }: { initialEmail?: string; withNote?: boolean }) {
  const id = useId();
  const [email, setEmail] = useState(initialEmail);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<WaitlistStatus | null>(null);
  const [error, setError] = useState<unknown>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setStatus((await api.joinWaitlist(email.trim(), note.trim())).status);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  const signIn = `/login?email=${encodeURIComponent(email.trim())}`;
  if (status === "waiting") {
    return (
      <div className="waitlist-done" role="status">
        <strong>You&apos;re on the list.</strong> We&apos;ll email <span className="nowrap">{email.trim()}</span> as soon as there&apos;s a spot.
      </div>
    );
  }
  if (status === "invited" || status === "open") {
    return (
      <div className="waitlist-done" role="status">
        <strong>{status === "invited" ? "You're already in." : "No waitlist right now."}</strong>{" "}
        <Link href={signIn}>Sign in with {email.trim()}</Link>
      </div>
    );
  }

  return (
    <form className="waitlist-form" onSubmit={submit}>
      <div className="waitlist-row">
        <label htmlFor={`${id}-email`} className="sr-only">Email</label>
        <input
          id={`${id}-email`}
          className="input"
          type="email"
          autoComplete="email"
          required
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "Joining…" : "Join the waitlist"}
        </button>
      </div>
      {withNote && (
        <>
          <label htmlFor={`${id}-note`} className="sr-only">What roles are you looking for? (optional)</label>
          <input
            id={`${id}-note`}
            className="input"
            maxLength={300}
            placeholder="What roles are you looking for? (optional)"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </>
      )}
      <Problems error={error} />
      <p className="hint" style={{ margin: 0 }}>
        One email when your spot comes up. No spam. <Link href="/privacy">Privacy</Link>
      </p>
    </form>
  );
}
