/**
 * Credit for jobs that come from an aggregator. Adzuna's API terms ask for its logo (at least 116×23 px) linked
 * to its site on every listing; until the official logo from Adzuna's press page is added at /adzuna-logo.png,
 * this shows the name at that size.
 */
export function SourceAttribution({ source }: { source: string }) {
  if (source !== "adzuna") return null;
  return (
    <a className="attribution" href="https://www.adzuna.in" target="_blank" rel="noopener noreferrer" aria-label="Jobs by Adzuna">
      <span className="muted">Jobs by</span> <strong>Adzuna</strong>
    </a>
  );
}

/** Says when a job's description is only the source's summary. */
export function PartialDescriptionNote({ partial, applyUrl }: { partial: boolean; applyUrl: string }) {
  if (!partial) return null;
  return (
    <p className="notice notice-warn small" style={{ margin: 0 }}>
      This is a summary of the job. Fit score and tailoring are based on it, so{" "}
      <a href={applyUrl} target="_blank" rel="noopener noreferrer">
        read the full posting
      </a>{" "}
      before you apply.
    </p>
  );
}
