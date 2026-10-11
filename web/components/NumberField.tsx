"use client";

import { useEffect, useState } from "react";

export function toNumber(raw: string, integer = false): number | null {
  const trimmed = raw.trim();
  if (trimmed === "") return null;
  const n = integer ? parseInt(trimmed, 10) : parseFloat(trimmed);
  return Number.isFinite(n) ? n : null;
}

/**
 * A number input that keeps what the user typed ("40." stays "40." while typing)
 * and reports the parsed number, or null when empty.
 */
export function NumberField(props: {
  label: string;
  value: number | null | undefined;
  onChange: (v: number | null) => void;
  integer?: boolean;
}) {
  const [text, setText] = useState(props.value?.toString() ?? "");

  // Pick up changes made outside this field, e.g. a newly parsed resume.
  useEffect(() => {
    if (toNumber(text, props.integer) !== (props.value ?? null)) setText(props.value?.toString() ?? "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.value]);

  return (
    <label className="field">
      {props.label}
      <input
        className="input"
        inputMode={props.integer ? "numeric" : "decimal"}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          props.onChange(toNumber(e.target.value, props.integer));
        }}
      />
    </label>
  );
}
