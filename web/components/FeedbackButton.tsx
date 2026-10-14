"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Problems } from "@/components/Problems";
import { api, type FeedbackKind } from "@/lib/api";

const kinds: { value: FeedbackKind; label: string }[] = [
  { value: "problem", label: "Something's broken" },
  { value: "idea", label: "An idea" },
  { value: "praise", label: "Something I like" },
  { value: "other", label: "Other" },
];

/** "Feedback" in the header: a short form in a dialog. The page it was sent from goes along with it. */
export function FeedbackButton() {
  const pathname = usePathname() ?? "/";
  const dialog = useRef<HTMLDialogElement>(null);
  const [kind, setKind] = useState<FeedbackKind>("problem");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const d = dialog.current;
    if (!d) return;
    // Clicking the dimmed area outside the form closes it.
    const onClick = (e: MouseEvent) => {
      if (e.target === d) d.close();
    };
    d.addEventListener("click", onClick);
    return () => d.removeEventListener("click", onClick);
  }, []);

  function open() {
    setSent(false);
    setError(null);
    dialog.current?.showModal();
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!message.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await api.sendFeedback(kind, message.trim(), pathname);
      setSent(true);
      setMessage("");
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button type="button" className="btn btn-ghost header-feedback" onClick={open}>
        Feedback
      </button>
      <dialog ref={dialog} className="dialog" aria-labelledby="feedback-title">
        {sent ? (
          <div className="stack" style={{ gap: 12 }}>
            <h2 id="feedback-title">Thank you</h2>
            <p className="muted" style={{ margin: 0 }}>Got it. Every message is read, and it helps decide what to fix and build next.</p>
            <div className="row" style={{ justifyContent: "flex-end" }}>
              <button type="button" className="btn btn-secondary" onClick={() => setSent(false)}>Send more</button>
              <button type="button" className="btn btn-primary" onClick={() => dialog.current?.close()}>Done</button>
            </div>
          </div>
        ) : (
          <form className="stack" style={{ gap: 14 }} onSubmit={submit}>
            <div className="stack" style={{ gap: 4 }}>
              <h2 id="feedback-title">Send feedback</h2>
              <p className="muted small" style={{ margin: 0 }}>What's working, what isn't, what you wish it did.</p>
            </div>
            <fieldset className="feedback-kinds">
              <legend className="sr-only">Kind of feedback</legend>
              {kinds.map((k) => (
                <label key={k.value} className="feedback-kind">
                  <input type="radio" name="feedback-kind" value={k.value} checked={kind === k.value} onChange={() => setKind(k.value)} />
                  <span>{k.label}</span>
                </label>
              ))}
            </fieldset>
            <label className="field">
              <span className="sr-only">Your feedback</span>
              <textarea
                className="textarea"
                rows={5}
                maxLength={2000}
                required
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder={kind === "problem" ? "What happened, and what did you expect?" : "Tell us more…"}
              />
            </label>
            <p className="hint" style={{ margin: 0 }}>We'll include the page you're on ({pathname}) and your account, so we can follow up.</p>
            <Problems error={error} />
            <div className="row" style={{ justifyContent: "flex-end" }}>
              <button type="button" className="btn btn-ghost" onClick={() => dialog.current?.close()}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={busy || !message.trim()}>{busy ? "Sending…" : "Send feedback"}</button>
            </div>
          </form>
        )}
      </dialog>
    </>
  );
}
