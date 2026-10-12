-- Phase 3 (Tailoring): one application per user per job, holding the tailored resume, cover letter and answers.
CREATE TABLE applications (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id           UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    profile_version  INTEGER NOT NULL,
    status           TEXT NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft', 'approved', 'applied', 'interview', 'offer', 'rejected')),
    generator        TEXT NOT NULL CHECK (generator IN ('claude', 'heuristic')),
    content_json     JSONB NOT NULL,
    -- What the claim guardrail removed from the generated draft, shown on the review screen.
    removed          TEXT[] NOT NULL DEFAULT '{}',
    questions        TEXT[] NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at      TIMESTAMPTZ,
    UNIQUE (user_id, job_id)
);
CREATE INDEX applications_user_created_idx ON applications (user_id, created_at);
