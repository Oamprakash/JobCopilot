"use client";

import type { Education, Experience, Profile } from "@/lib/api";
import { ChipInput } from "./ChipInput";
import { NumberField } from "./NumberField";

type Props = { value: Profile; onChange: (p: Profile) => void };

const emptyExperience: Experience = { title: "", company: "", startDate: "", endDate: "", current: false, bullets: [] };
const emptyEducation: Education = { institution: "", degree: "", field: "", endYear: null };

/** Controlled editor for the master profile. Every field the parser filled can be corrected here. */
export function ProfileEditor({ value, onChange }: Props) {
  const set = <K extends keyof Profile>(key: K, v: Profile[K]) => onChange({ ...value, [key]: v });

  const setExperience = (i: number, patch: Partial<Experience>) =>
    set("experience", value.experience.map((e, j) => (j === i ? { ...e, ...patch } : e)));
  const setEducation = (i: number, patch: Partial<Education>) =>
    set("education", value.education.map((e, j) => (j === i ? { ...e, ...patch } : e)));

  return (
    <div className="stack" style={{ gap: 24 }}>
      <section className="stack">
        <div className="grid-2">
          <TextField label="Full name" value={value.fullName} onChange={(v) => set("fullName", v)} required />
          <TextField label="Current title" value={value.headline} onChange={(v) => set("headline", v)} />
          <TextField label="Email on resume" value={value.email ?? ""} onChange={(v) => set("email", v || null)} type="email" />
          <TextField label="Phone" value={value.phone ?? ""} onChange={(v) => set("phone", v || null)} type="tel" />
          <TextField label="Location" value={value.location ?? ""} onChange={(v) => set("location", v || null)} />
          <NumberField label="Total experience (years)" value={value.totalExperienceYears} onChange={(v) => set("totalExperienceYears", v)} />
        </div>
        <label className="field">
          Summary
          <textarea className="textarea" value={value.summary ?? ""} onChange={(e) => set("summary", e.target.value || null)} />
        </label>
      </section>

      <section className="stack">
        <div className="entry-head">
          <h2>Experience</h2>
          <button type="button" className="btn btn-ghost" onClick={() => set("experience", [...value.experience, { ...emptyExperience }])}>
            + Add role
          </button>
        </div>
        {value.experience.length === 0 && <p className="muted small">No roles yet. Add your current and past roles.</p>}
        {value.experience.map((e, i) => (
          <div className="entry" key={i}>
            <div className="entry-head">
              <strong>{e.title || e.company ? `${e.title || "Role"} · ${e.company || "Company"}` : `Role ${i + 1}`}</strong>
              <button
                type="button"
                className="btn btn-ghost"
                aria-label={`Remove role ${i + 1}`}
                onClick={() => set("experience", value.experience.filter((_, j) => j !== i))}
              >
                Remove
              </button>
            </div>
            <div className="grid-2">
              <TextField label="Title" value={e.title} onChange={(v) => setExperience(i, { title: v })} required />
              <TextField label="Company" value={e.company} onChange={(v) => setExperience(i, { company: v })} required />
              <TextField label="Start" placeholder="YYYY-MM" value={e.startDate ?? ""} onChange={(v) => setExperience(i, { startDate: v || null })} />
              {!e.current && (
                <TextField label="End" placeholder="YYYY-MM" value={e.endDate ?? ""} onChange={(v) => setExperience(i, { endDate: v || null })} />
              )}
            </div>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={e.current}
                onChange={(ev) => setExperience(i, { current: ev.target.checked, endDate: ev.target.checked ? null : e.endDate })}
              />
              I work here now
            </label>
            <label className="field">
              Bullets
              <span className="hint">One per line. Tailored resumes reorder and reword these, but never add new claims.</span>
              <textarea
                className="textarea"
                rows={Math.max(3, e.bullets.length + 1)}
                value={e.bullets.join("\n")}
                onChange={(ev) => setExperience(i, { bullets: ev.target.value.split("\n") })}
              />
            </label>
          </div>
        ))}
      </section>

      <section className="stack">
        <div className="entry-head">
          <h2>Education</h2>
          <button type="button" className="btn btn-ghost" onClick={() => set("education", [...value.education, { ...emptyEducation }])}>
            + Add education
          </button>
        </div>
        {value.education.map((ed, i) => (
          <div className="entry" key={i}>
            <div className="grid-2">
              <TextField label="Institution" value={ed.institution} onChange={(v) => setEducation(i, { institution: v })} />
              <TextField label="Degree" value={ed.degree ?? ""} onChange={(v) => setEducation(i, { degree: v || null })} />
              <TextField label="Field" value={ed.field ?? ""} onChange={(v) => setEducation(i, { field: v || null })} />
              <NumberField label="Year completed" integer value={ed.endYear} onChange={(v) => setEducation(i, { endYear: v })} />
            </div>
            <div>
              <button type="button" className="btn btn-ghost" onClick={() => set("education", value.education.filter((_, j) => j !== i))}>
                Remove
              </button>
            </div>
          </div>
        ))}
      </section>

      <section className="stack">
        <h2>Skills and links</h2>
        <ChipInput label="Skills" values={value.skills} onChange={(v) => set("skills", v)} placeholder="Add a skill" />
        <ChipInput label="Links" values={value.links} onChange={(v) => set("links", v)} placeholder="linkedin.com/in/…" />
      </section>
    </div>
  );
}

function TextField(props: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  placeholder?: string;
  required?: boolean;
}) {
  return (
    <label className="field">
      {props.label}
      <input
        className="input"
        type={props.type ?? "text"}
        value={props.value}
        placeholder={props.placeholder}
        required={props.required}
        onChange={(e) => props.onChange(e.target.value)}
      />
    </label>
  );
}

/** Drops the empty lines the bullet textareas leave behind before saving. */
export function cleanProfile(p: Profile): Profile {
  return {
    ...p,
    experience: p.experience.map((e) => ({ ...e, bullets: e.bullets.map((b) => b.trim()).filter(Boolean) })),
  };
}
