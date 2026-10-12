"use client";

import { useEffect, useState } from "react";
import { api, type AutocompleteOptions, type Preferences, type RemoteMode } from "@/lib/api";
import { fallbackOptions } from "@/lib/autocomplete";
import { ChipInput } from "./ChipInput";
import { NumberField } from "./NumberField";

const remoteOptions: { value: RemoteMode; label: string }[] = [
  { value: "any", label: "Any" },
  { value: "remote", label: "Remote" },
  { value: "hybrid", label: "Hybrid" },
  { value: "onsite", label: "Onsite" },
];

export function PreferencesForm({ value, onChange }: { value: Preferences; onChange: (p: Preferences) => void }) {
  const set = <K extends keyof Preferences>(key: K, v: Preferences[K]) => onChange({ ...value, [key]: v });
  const [options, setOptions] = useState<AutocompleteOptions>(fallbackOptions);

  // Live options carry open-job counts; the built-in lists cover the wait and any failure.
  useEffect(() => {
    let cancelled = false;
    api.autocomplete().then((o) => !cancelled && setOptions(o)).catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="stack">
      <ChipInput label="Target roles" values={value.roles} onChange={(v) => set("roles", v)} placeholder="e.g. Senior Android Engineer" suggestions={options.roles} />
      <ChipInput label="Locations" values={value.locations} onChange={(v) => set("locations", v)} placeholder="e.g. Chennai" suggestions={options.locations} />
      <label className="field">
        Work mode
        <select className="select" value={value.remoteMode} onChange={(e) => set("remoteMode", e.target.value as RemoteMode)}>
          {remoteOptions.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
      <div className="grid-2">
        <NumberField label="Min CTC (LPA)" value={value.minCtcLpa} onChange={(v) => set("minCtcLpa", v)} />
        <NumberField label="Notice period (days)" integer value={value.noticePeriodDays} onChange={(v) => set("noticePeriodDays", v)} />
      </div>
      <ChipInput
        label="Companies to skip"
        hint="We'll never show jobs from these, e.g. your current employer."
        values={value.skipCompanies}
        onChange={(v) => set("skipCompanies", v)}
        placeholder="Add a company"
      />
    </div>
  );
}
