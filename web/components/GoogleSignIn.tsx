"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";

type GoogleId = {
  initialize: (o: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: "popup"; auto_select?: boolean; context?: string }) => void;
  renderButton: (el: HTMLElement, o: Record<string, unknown>) => void;
};
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

const SCRIPT = "https://accounts.google.com/gsi/client";

function loadScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT}"]`);
  return new Promise((resolve, reject) => {
    const s = existing ?? document.createElement("script");
    s.addEventListener("load", () => resolve());
    s.addEventListener("error", () => reject(new Error("Google sign-in didn't load")));
    if (!existing) {
      s.src = SCRIPT;
      s.async = true;
      document.head.appendChild(s);
    }
  });
}

/** The email inside Google's ID token, read only to prefill the waitlist when sign-up is invite-only. */
function emailIn(credential: string): string {
  try {
    const payload = credential.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return (JSON.parse(atob(payload)) as { email?: string }).email ?? "";
  } catch {
    return "";
  }
}

/**
 * "Continue with Google", shown when the server has a Google client ID. Google checks the account in a popup and
 * hands us an ID token; the server verifies it and signs the person in. Renders nothing when it isn't set up.
 */
export function GoogleSignIn({ onNotInvited, onShown }: { onNotInvited: (email: string) => void; onShown?: (shown: boolean) => void }) {
  const router = useRouter();
  const box = useRef<HTMLDivElement>(null);
  const [clientId, setClientId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .site()
      .then((s) => setClientId(s.googleClientId ?? null))
      .catch(() => setClientId(null));
  }, []);

  useEffect(() => {
    if (!clientId) return;
    let cancelled = false;
    loadScript()
      .then(() => {
        const id = window.google?.accounts.id;
        if (cancelled || !id || !box.current) return;
        id.initialize({
          client_id: clientId,
          ux_mode: "popup",
          context: "signin",
          callback: async ({ credential }) => {
            setBusy(true);
            setError(null);
            try {
              const me = await api.googleSignIn(credential);
              router.replace(me.hasProfile ? "/feed" : "/onboarding");
            } catch (e) {
              if (e instanceof ApiError && e.status === 403) onNotInvited(emailIn(credential));
              else setError(e instanceof ApiError ? e.message : "Google sign-in didn't work. Try again, or use your email.");
              setBusy(false);
            }
          },
        });
        id.renderButton(box.current, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: "continue_with",
          shape: "rectangular",
          logo_alignment: "center",
          width: Math.min(400, box.current.clientWidth || 320),
        });
        onShown?.(true);
      })
      .catch(() => onShown?.(false));
    return () => {
      cancelled = true;
    };
    // onNotInvited and onShown are stable enough for a one-time setup per client ID.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId, router]);

  if (!clientId) return null;
  return (
    <div className="stack" style={{ gap: 8 }}>
      <div ref={box} className="google-button" aria-busy={busy} style={{ minHeight: 44 }} />
      {busy && <p className="small muted" style={{ margin: 0 }}>Signing you in…</p>}
      {error && <p className="notice notice-bad small" role="alert" style={{ margin: 0 }}>{error}</p>}
    </div>
  );
}
