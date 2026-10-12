-- Phase 2 (Job feed): companies whose public job boards we read, their jobs, and per-user matches.

CREATE TABLE companies (
    id              UUID PRIMARY KEY,
    name            TEXT NOT NULL,
    ats             TEXT NOT NULL CHECK (ats IN ('greenhouse', 'lever', 'ashby')),
    board_token     TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    last_synced_at  TIMESTAMPTZ,
    last_error      TEXT,
    open_jobs       INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ats, board_token)
);

CREATE TABLE jobs (
    id              UUID PRIMARY KEY,
    company_id      UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    source          TEXT NOT NULL,
    source_job_id   TEXT NOT NULL,
    title           TEXT NOT NULL,
    location        TEXT NOT NULL DEFAULT '',
    remote          BOOLEAN NOT NULL DEFAULT false,
    description     TEXT NOT NULL DEFAULT '',
    apply_url       TEXT NOT NULL,
    posted_at       TIMESTAMPTZ,
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Set when a job disappears from its board; cleared if it comes back.
    closed_at       TIMESTAMPTZ,
    UNIQUE (source, source_job_id)
);
CREATE INDEX jobs_open_idx ON jobs (company_id) WHERE closed_at IS NULL;

CREATE TABLE matches (
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id             UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    score              INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    reasons            TEXT[] NOT NULL DEFAULT '{}',
    missing_skills     TEXT[] NOT NULL DEFAULT '{}',
    scorer             TEXT NOT NULL CHECK (scorer IN ('claude', 'heuristic')),
    profile_version    INTEGER NOT NULL,
    status             TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'saved', 'skipped')),
    scored_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    status_changed_at  TIMESTAMPTZ,
    PRIMARY KEY (user_id, job_id)
);
CREATE INDEX matches_user_status_idx ON matches (user_id, status, score DESC);

CREATE TABLE match_runs (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    considered     INTEGER NOT NULL DEFAULT 0,
    passed_filter  INTEGER NOT NULL DEFAULT 0,
    scored         INTEGER NOT NULL DEFAULT 0,
    llm_scored     INTEGER NOT NULL DEFAULT 0,
    error          TEXT
);
CREATE INDEX match_runs_user_idx ON match_runs (user_id, started_at DESC);

-- Starter boards, each checked by hand to be a live public board with India openings (Oct 2026).
INSERT INTO companies (id, name, ats, board_token) VALUES
    (gen_random_uuid(), 'Groww', 'greenhouse', 'groww'),
    (gen_random_uuid(), 'Razorpay', 'greenhouse', 'razorpaysoftwareprivatelimited'),
    (gen_random_uuid(), 'Databricks', 'greenhouse', 'databricks'),
    (gen_random_uuid(), 'Rubrik', 'greenhouse', 'rubrik'),
    (gen_random_uuid(), 'Coinbase', 'greenhouse', 'coinbase'),
    (gen_random_uuid(), 'HackerRank', 'greenhouse', 'hackerrank'),
    (gen_random_uuid(), 'CRED', 'lever', 'cred'),
    (gen_random_uuid(), 'Meesho', 'lever', 'meesho'),
    (gen_random_uuid(), 'Zeta', 'lever', 'zeta'),
    (gen_random_uuid(), 'Sarvam AI', 'ashby', 'sarvam'),
    (gen_random_uuid(), 'Atlan', 'ashby', 'atlan');
