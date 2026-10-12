"use client";

import { useId, useState, type KeyboardEvent } from "react";

type Props = {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  hint?: string;
};

/**
 * A list of removable chips. Enter or comma adds what's typed. Backspace only edits the text being typed;
 * chips are removed with their × button, so holding Backspace can't wipe saved values.
 */
export function ChipInput({ label, values, onChange, placeholder, hint }: Props) {
  const id = useId();
  const [draft, setDraft] = useState("");

  function add(raw: string) {
    const parts = raw.split(",").map((p) => p.trim()).filter(Boolean);
    const next = [...values];
    for (const part of parts) {
      if (!next.some((v) => v.toLowerCase() === part.toLowerCase())) next.push(part);
    }
    onChange(next);
    setDraft("");
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" || e.key === ",") {
      e.preventDefault();
      if (draft.trim()) add(draft);
    }
  }

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {hint && <span className="hint">{hint}</span>}
      <div className="chips">
        {values.map((value) => (
          <span className="chip" key={value}>
            {value}
            <button type="button" aria-label={`Remove ${value}`} onClick={() => onChange(values.filter((v) => v !== value))}>
              ×
            </button>
          </span>
        ))}
        <input
          id={id}
          className="input chip-input"
          value={draft}
          placeholder={placeholder}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          onBlur={() => draft.trim() && add(draft)}
        />
      </div>
    </div>
  );
}
