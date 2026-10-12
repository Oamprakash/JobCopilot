"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { Problems } from "./Problems";

/** Profile card: get a one-time code to connect the Chrome extension, or disconnect it. */
export function ExtensionConnect() {
  const [connected, setConnected] = useState<number | null>(null);
  const [code, setCode] = useState<{ code: string; expiresAt: string } | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const refresh = () => api.extensionStatus().then((s) => setConnected(s.connected)).catch(setError);
  useEffect(() => {
    refresh();
  }, []);

  async function getCode() {
    setBusy(true);
    setError(null);
    try {
      setCode(await api.extensionPairCode());
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  async function disconnect() {
    if (!window.confirm("Disconnect the extension everywhere it's signed in?")) return;
    await api.extensionDisconnect().catch(setError);
    setCode(null);
    refresh();
  }

  const origin = typeof window === "undefined" ? "" : window.location.origin;
  return (
    <section className="card stack" style={{ gap: 10 }}>
      <h2>Browser extension</h2>
      <p className="muted small" style={{ margin: 0 }}>
        The Chrome extension fills application forms on Greenhouse, Lever, Ashby and Workday from your approved application, and
        saves jobs from any page to your tracker. It never clicks Submit.
      </p>
      {connected != null && connected > 0 && (
        <p className="notice notice-good small" style={{ margin: 0 }}>
          Connected on {connected} browser{connected === 1 ? "" : "s"}.
        </p>
      )}
      {code ? (
        <div className="stack" style={{ gap: 6 }}>
          <span className="small">In the extension, enter:</span>
          <div className="pair-code" aria-label="Connection code">{code.code}</div>
          <span className="hint">
            App address: <strong>{origin}</strong>. The code works once, for 10 minutes.
          </span>
        </div>
      ) : (
        <button type="button" className="btn btn-secondary" onClick={getCode} disabled={busy}>
          {busy ? "Getting a code…" : connected ? "Connect another browser" : "Connect the extension"}
        </button>
      )}
      {connected != null && connected > 0 && (
        <button type="button" className="btn btn-ghost small" style={{ alignSelf: "flex-start", paddingInline: 0 }} onClick={disconnect}>
          Disconnect everywhere
        </button>
      )}
      <Problems error={error} />
    </section>
  );
}
