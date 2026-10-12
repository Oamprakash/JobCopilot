"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { api } from "@/lib/api";

/** Entry point: route to sign-in, onboarding or the profile depending on the session. */
export default function Home() {
  const router = useRouter();

  useEffect(() => {
    api
      .me()
      .then((me) => router.replace(me.hasProfile ? "/feed" : "/onboarding"))
      .catch(() => router.replace("/login"));
  }, [router]);

  return <main className="narrow muted">Loading…</main>;
}
