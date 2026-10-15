"use client";

import { useId, useMemo, useState, type KeyboardEvent } from "react";
import type { AutocompleteOption } from "@/lib/api";
import { matchOptions } from "@/lib/autocomplete";

type Props = {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  hint?: string;
  /** Values to offer while typing. Without them the input is a plain chip input. */
  suggestions?: AutocompleteOption[];
  /** Show only the first N chips until "+M more" is clicked (long lists like skills). */
  collapseAfter?: number;
};

/**
 * A list of removable chips. Enter or comma adds what's typed. Backspace only edits the text being typed;
 * chips are removed with their × button, so holding Backspace can't wipe saved values.
 * With [suggestions], a list of matches opens while typing: ↑/↓ move, Enter or a click adds one, Escape closes it.
 */
export function ChipInput({ label, values, onChange, placeholder, hint, suggestions, collapseAfter }: Props) {
  const id = useId();
  const listId = `${id}-list`;
  const [draft, setDraft] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [expanded, setExpanded] = useState(false);
  const collapsed = collapseAfter != null && !expanded && values.length > collapseAfter;
  const shown = collapsed ? values.slice(0, collapseAfter) : values;

  const matches = useMemo(
    () => (suggestions ? matchOptions(suggestions, draft, values) : []),
    [suggestions, draft, values],
  );
  const showList = open && matches.length > 0;

  function add(raw: string) {
    const parts = raw.split(",").map((p) => p.trim()).filter(Boolean);
    const next = [...values];
    for (const part of parts) {
      if (!next.some((v) => v.toLowerCase() === part.toLowerCase())) next.push(part);
    }
    onChange(next);
    setDraft("");
    setActive(-1);
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (!matches.length) return;
      e.preventDefault();
      setOpen(true);
      const step = e.key === "ArrowDown" ? 1 : -1;
      setActive((i) => (showList ? (i + step + matches.length) % matches.length : step === 1 ? 0 : matches.length - 1));
    } else if (e.key === "Enter" || e.key === ",") {
      e.preventDefault();
      if (e.key === "Enter" && showList && active >= 0 && matches[active]) add(matches[active].value);
      else if (draft.trim()) add(draft);
    } else if (e.key === "Escape" && showList) {
      e.preventDefault();
      setOpen(false);
      setActive(-1);
    }
  }

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {hint && <span className="hint">{hint}</span>}
      <div className="combo">
        <div className="chips">
          {shown.map((value) => (
            <span className="chip" key={value}>
              {value}
              <button type="button" aria-label={`Remove ${value}`} onClick={() => onChange(values.filter((v) => v !== value))}>
                ×
              </button>
            </span>
          ))}
          {collapsed && (
            <button type="button" className="chip chip-more" onClick={() => setExpanded(true)}>
              +{values.length - (collapseAfter ?? 0)} more
            </button>
          )}
          {collapseAfter != null && expanded && values.length > collapseAfter && (
            <button type="button" className="chip chip-more" onClick={() => setExpanded(false)}>
              Show top {collapseAfter}
            </button>
          )}
          <input
            id={id}
            className="input chip-input"
            value={draft}
            placeholder={placeholder}
            autoComplete="off"
            role={suggestions ? "combobox" : undefined}
            aria-autocomplete={suggestions ? "list" : undefined}
            aria-expanded={suggestions ? showList : undefined}
            aria-controls={suggestions ? listId : undefined}
            aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
            onChange={(e) => {
              setDraft(e.target.value);
              setOpen(true);
              // Typing highlights the best match so Enter picks it; with nothing typed, Enter does nothing.
              setActive(e.target.value.trim() ? 0 : -1);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={onKeyDown}
            onBlur={() => {
              setOpen(false);
              if (draft.trim()) add(draft);
            }}
          />
        </div>
        {suggestions && (
          <ul id={listId} role="listbox" aria-label={`${label} suggestions`} className="combo-list" hidden={!showList}>
            {matches.map((option, i) => (
              <li
                key={option.value}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                className="combo-option"
                // mousedown, not click: it fires before the input's blur, which would otherwise add the half-typed text.
                onMouseDown={(e) => {
                  e.preventDefault();
                  add(option.value);
                }}
                onMouseEnter={() => setActive(i)}
              >
                <span>{option.value}</span>
                {option.count != null && option.count > 0 && (
                  <span className="combo-count">
                    {option.count} open {option.count === 1 ? "job" : "jobs"}
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
