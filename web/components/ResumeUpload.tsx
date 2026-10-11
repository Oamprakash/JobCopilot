"use client";

import { useRef, useState, type DragEvent } from "react";
import { api, ApiError, type ResumeUpload as Upload } from "@/lib/api";

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT = ".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

export function ResumeUpload({ onUploaded, compact = false }: { onUploaded: (u: Upload) => void; compact?: boolean }) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [over, setOver] = useState(false);

  async function handle(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (file.size > MAX_BYTES) {
      setError("Resumes must be 5 MB or smaller.");
      return;
    }
    setBusy(true);
    try {
      onUploaded(await api.uploadResume(file));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Upload failed. Check your connection and try again.");
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setOver(false);
    handle(e.dataTransfer.files[0]);
  }

  const picker = (
    <input
      ref={input}
      type="file"
      accept={ACCEPT}
      className="sr-only"
      tabIndex={-1}
      aria-hidden="true"
      onChange={(e) => handle(e.target.files?.[0])}
    />
  );

  if (compact) {
    return (
      <div className="stack" style={{ gap: 8 }}>
        {picker}
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => input.current?.click()}>
          {busy ? "Reading your resume…" : "Replace resume"}
        </button>
        {error && <p className="notice notice-bad" role="alert">{error}</p>}
      </div>
    );
  }

  return (
    <div
      className="dropzone"
      data-over={over}
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={onDrop}
    >
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#5B6478" strokeWidth="1.6" aria-hidden="true">
        <path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
        <path d="M14 3v6h6M12 17v-6M9 14l3-3 3 3" />
      </svg>
      <h2>{busy ? "Reading your resume…" : "Drop your resume here"}</h2>
      <p className="muted small" style={{ margin: 0 }}>
        PDF or Word (.docx), up to 5 MB. We read it once to build your profile.
      </p>
      {picker}
      <button type="button" className="btn btn-primary" disabled={busy} onClick={() => input.current?.click()}>
        Choose file
      </button>
      {error && <p className="notice notice-bad" role="alert">{error}</p>}
    </div>
  );
}
