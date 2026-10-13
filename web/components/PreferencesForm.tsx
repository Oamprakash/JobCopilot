"use client";

import { useEffect, useState } from "react";
import { api, type AutocompleteOptions, type Preferences, type RemoteMode } from "@/lib/api";
import { fallbackOptions } from "@/lib/autocomplete";
import { visaCountries } from "@/lib/visaCountries";
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
      <VisaCountries locations={value.locations} onAdd={(country) => set("locations", [...value.locations, country])} />
      <label className="checkbox">
        <input type="checkbox" checked={value.needsVisaSponsorship ?? false} onChange={(e) => set("needsVisaSponsorship", e.target.checked)} />
        <span>
          I&apos;ll need visa sponsorship for jobs abroad
          <span className="hint" style={{ display: "block" }}>Hides jobs outside India whose description says there&apos;s no sponsorship.</span>
        </span>
      </label>
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
      <label className="checkbox">
        <input type="checkbox" checked={value.dailyDigest ?? true} onChange={(e) => set("dailyDigest", e.target.checked)} />
        <span>
          Email me my best new matches each morning
          <span className="hint" style={{ display: "block" }}>Up to 5 jobs, only on days with something new. Every email has a one-click unsubscribe.</span>
        </span>
      </label>
    </div>
  );
}

/** Countries with an employer-sponsored work route, one tap to add each to Locations. */
function VisaCountries({ locations, onAdd }: { locations: string[]; onAdd: (country: string) => void }) {
  const [open, setOpen] = useState(false);
  const chosen = new Set(locations.map((l) => l.toLowerCase()));
  return (
    <details className="visa-guide" open={open} onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
      <summary>Countries that sponsor work visas for tech roles</summary>
      <p className="hint" style={{ margin: "8px 0" }}>
        Each has an employer-sponsored route. Rules and salary minimums change, so check the official page before you apply.
      </p>
      <ul className="visa-list">
        {visaCountries.map((c) => {
          const added = chosen.has(c.location.toLowerCase());
          return (
            <li key={c.location}>
              <div style={{ minWidth: 0 }}>
                <strong>{c.location}</strong> · <a href={c.officialUrl} target="_blank" rel="noopener noreferrer">{c.route}</a>
                <span className="hint" style={{ display: "block" }}>
                  {c.note}
                  {!c.adzuna && " Jobs here come from company boards only."}
                </span>
              </div>
              <button type="button" className="btn btn-secondary" disabled={added} onClick={() => onAdd(c.location)}>
                {added ? "Added" : "Add"}
              </button>
            </li>
          );
        })}
      </ul>
    </details>
  );
}
