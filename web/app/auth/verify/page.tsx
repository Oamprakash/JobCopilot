"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";

function Verify() {
  const router = useRouter();
  const token = useSearchParams().get("token");
  const [error, setError] = useState<string | null>(null);
  // React strict mode runs effects twice in dev; a magic link only works once, so guard the call.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (!token) {
      setError("This sign-in link is incomplete. Request a new one.");
      return;
    }
    api
      .verify(token)
      .then((me) => router.replace(me.hasProfile ? "/feed" : "/onboarding"))
      .catch((e) => setError(e instanceof ApiError ? e.message : "Sign-in failed. Please try again."));
  }, [token, router]);

  if (error) {
    return (
      <section className="card stack">
        <h1>Link didn&apos;t work</h1>
        <p className="muted" style={{ margin: 0 }}>{error}</p>
        <Link href="/login" className="btn btn-primary">
          Get a new link
        </Link>
      </section>
    );
  }
  return <p className="muted">Signing you in…</p>;
}

export default function VerifyPage() {
  return (
    <main className="narrow">
      <Suspense fallback={<p className="muted">Signing you in…</p>}>
        <Verify />
      </Suspense>
    </main>
  );
}
