"use client";

import { useEffect, useRef, useState } from "react";
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

  // Roles are kept latest first: resumes, cover letters and matching all read the list in that order.
  // A new role goes on top, gets focus and is scrolled to, so it can't end up unnoticed at the bottom.
  const rolesRef = useRef<HTMLDivElement>(null);
  const educationRef = useRef<HTMLDivElement>(null);
  const [focusNew, setFocusNew] = useState<"role" | "education" | null>(null);
  useEffect(() => {
    if (!focusNew) return;
    const first = (focusNew === "role" ? rolesRef : educationRef).current?.querySelector<HTMLInputElement>(".entry input");
    first?.focus({ preventScroll: true });
    first?.closest(".entry")?.scrollIntoView({ behavior: "smooth", block: "center" });
    setFocusNew(null);
  }, [focusNew]);

  // Roles show as one-line summaries; Edit opens one. A role missing its title or company stays open.
  const [openRoles, setOpenRoles] = useState<Set<number>>(new Set());
  const isOpen = (i: number, e: Experience) => openRoles.has(i) || !e.title.trim() || !e.company.trim();
  const toggleRole = (i: number) =>
    setOpenRoles((s) => {
      const next = new Set(s);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      return next;
    });

  function addRole() {
    set("experience", [{ ...emptyExperience }, ...value.experience]);
    setOpenRoles((s) => new Set([0, ...Array.from(s, (i) => i + 1)]));
    setFocusNew("role");
  }
  function moveRole(i: number, by: -1 | 1) {
    const j = i + by;
    if (j < 0 || j >= value.experience.length) return;
    set("experience", move(value.experience, i, by));
    setOpenRoles((s) => new Set(Array.from(s, (k) => (k === i ? j : k === j ? i : k))));
  }
  function removeRole(i: number) {
    set("experience", value.experience.filter((_, j) => j !== i));
    setOpenRoles((s) => new Set(Array.from(s).filter((k) => k !== i).map((k) => (k > i ? k - 1 : k))));
  }
  function addEducation() {
    set("education", [{ ...emptyEducation }, ...value.education]);
    setFocusNew("education");
  }
  function move<T>(list: T[], i: number, by: -1 | 1): T[] {
    const j = i + by;
    if (j < 0 || j >= list.length) return list;
    const next = [...list];
    [next[i], next[j]] = [next[j], next[i]];
    return next;
  }

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
          <button type="button" className="btn btn-ghost" onClick={addRole}>
            + Add role
          </button>
        </div>
        <p className="hint" style={{ margin: "-8px 0 0" }}>
          Read from your resume. Tailored resumes are built from these, so open a role with Edit if a date or bullet came out wrong. Latest
          role first; ↑ ↓ reorder.
        </p>
        {value.experience.length === 0 && <p className="muted small">No roles yet. Add your current and past roles.</p>}
        <div className="stack" ref={rolesRef}>
        {value.experience.map((e, i) => {
          const open = isOpen(i, e);
          const bullets = e.bullets.filter((b) => b.trim()).length;
          return (
          <div className={`entry${open ? "" : " entry-closed"}`} key={i}>
            <div className="entry-head">
              <div className="stack" style={{ gap: 2, minWidth: 0 }}>
                <strong>{e.title || e.company ? `${e.title || "Role"} · ${e.company || "Company"}` : "New role"}</strong>
                {!open && (
                  <span className="muted small">
                    {[dates(e), bullets ? `${bullets} bullet${bullets === 1 ? "" : "s"}` : "no bullets"].filter(Boolean).join(" · ")}
                  </span>
                )}
              </div>
              <div className="entry-tools">
                <button type="button" className="btn btn-ghost icon-btn" aria-label={`Move role ${i + 1} up`} disabled={i === 0}
                  onClick={() => moveRole(i, -1)}>↑</button>
                <button type="button" className="btn btn-ghost icon-btn" aria-label={`Move role ${i + 1} down`} disabled={i === value.experience.length - 1}
                  onClick={() => moveRole(i, 1)}>↓</button>
                <button type="button" className="btn btn-ghost" aria-expanded={open} onClick={() => toggleRole(i)}>
                  {open ? "Done" : "Edit"}
                </button>
              </div>
            </div>
            {open && (
            <>
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
            <div>
              <button type="button" className="btn btn-ghost" style={{ paddingInline: 0, color: "var(--bad)" }} aria-label={`Remove role ${i + 1}`} onClick={() => removeRole(i)}>
                Remove this role
              </button>
            </div>
            </>
            )}
          </div>
          );
        })}
        </div>
      </section>

      <section className="stack">
        <div className="entry-head">
          <h2>Education</h2>
          <button type="button" className="btn btn-ghost" onClick={addEducation}>
            + Add education
          </button>
        </div>
        <div className="stack" ref={educationRef}>
        {value.education.map((ed, i) => (
          <div className="entry" key={i}>
            <div className="grid-2">
              <TextField label="Institution" value={ed.institution} onChange={(v) => setEducation(i, { institution: v })} />
              <TextField label="Degree" value={ed.degree ?? ""} onChange={(v) => setEducation(i, { degree: v || null })} />
              <TextField label="Field" value={ed.field ?? ""} onChange={(v) => setEducation(i, { field: v || null })} />
              <NumberField label="Year completed" integer value={ed.endYear} onChange={(v) => setEducation(i, { endYear: v })} />
            </div>
            <div className="entry-tools">
              <button type="button" className="btn btn-ghost icon-btn" aria-label={`Move education ${i + 1} up`} disabled={i === 0}
                onClick={() => set("education", move(value.education, i, -1))}>↑</button>
              <button type="button" className="btn btn-ghost icon-btn" aria-label={`Move education ${i + 1} down`} disabled={i === value.education.length - 1}
                onClick={() => set("education", move(value.education, i, 1))}>↓</button>
              <button type="button" className="btn btn-ghost" onClick={() => set("education", value.education.filter((_, j) => j !== i))}>
                Remove
              </button>
            </div>
          </div>
        ))}
        </div>
      </section>

      <section className="stack">
        <h2>Skills and links</h2>
        <ChipInput
          label="Skills"
          hint="Your most-used skills first. Tailored resumes lead with the ones each job asks for (12 at most); every skill here counts for matching."
          values={value.skills}
          onChange={(v) => set("skills", v)}
          placeholder="Add a skill"
          collapseAfter={10}
        />
        <ChipInput label="Links" values={value.links} onChange={(v) => set("links", v)} placeholder="linkedin.com/in/…" />
      </section>
    </div>
  );
}

/** "2020-07 – Present" from a role's dates, or "" when it has none. */
function dates(e: Experience): string {
  const start = e.startDate?.trim();
  const end = e.current ? "Present" : e.endDate?.trim();
  if (!start && !end) return "";
  return `${start || "?"} – ${end || "?"}`;
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
