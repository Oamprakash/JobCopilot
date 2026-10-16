"use client";

import { useEffect, useId, useState, type FormEvent } from "react";
import { api, type ProChoice } from "@/lib/api";
import { Problems } from "./Problems";

export const proChoices: { value: ProChoice; label: string; hint?: string }[] = [
  { value: "month_199", label: "₹199 a month" },
  { value: "pass_499", label: "₹499 for 3 months", hint: "A job-hunt pass that doesn't renew" },
  { value: "year_999", label: "₹999 a year" },
  { value: "no", label: "I wouldn't pay for this" },
];

/**
 * Asked when a free user reaches the monthly limit: what they'd pay for unlimited tailoring. There's no paid plan
 * yet and nothing is charged; the answers decide whether to offer one and at what price.
 */
export function ProInterest() {
  const id = useId();
  const [choice, setChoice] = useState<ProChoice | null>(null);
  const [note, setNote] = useState("");
  const [saved, setSaved] = useState<ProChoice | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    api
      .proInterest()
      .then((a) => {
        if (a.choice) {
          setChoice(a.choice);
          setSaved(a.choice);
        }
        if (a.note) setNote(a.note);
      })
      .catch(() => undefined);
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!choice) return;
    setBusy(true);
    setError(null);
    try {
      const a = await api.saveProInterest(choice, note.trim());
      setSaved(a.choice);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (saved) {
    return (
      <div className="notice notice-good stack" style={{ gap: 6 }} role="status">
        <strong>Thanks, that really helps.</strong>
        <span className="small">
          {saved === "no"
            ? "Got it. The free plan isn't going away."
            : "We'll email you first if unlimited tailoring launches. Nothing is charged unless you choose to buy it then."}
        </span>
        <button type="button" className="btn btn-ghost small" style={{ alignSelf: "flex-start", paddingInline: 0 }} onClick={() => setSaved(null)}>
          Change my answer
        </button>
      </div>
    );
  }

  return (
    <form className="stack" style={{ gap: 12 }} onSubmit={submit}>
      <fieldset className="pro-choices">
        <legend>Unlimited tailoring may come soon. What would you pay for it?</legend>
        {proChoices.map((c) => (
          <label key={c.value} className="pro-choice">
            <input type="radio" name={`${id}-choice`} value={c.value} checked={choice === c.value} onChange={() => setChoice(c.value)} />
            <span>
              {c.label}
              {c.hint && <span className="hint" style={{ display: "block" }}>{c.hint}</span>}
            </span>
          </label>
        ))}
      </fieldset>
      <label className="field">
        What would make it worth paying for? (optional)
        <textarea className="textarea" rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. more interview prep, LinkedIn messages…" />
      </label>
      <Problems error={error} />
      <div>
        <button type="submit" className="btn btn-primary" disabled={!choice || busy}>
          {busy ? "Sending…" : "Send"}
        </button>
      </div>
    </form>
  );
}
