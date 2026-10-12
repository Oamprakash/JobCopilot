-- Adzuna: a licensed job-search API that aggregates employer career sites (including companies whose own sites
-- have no public API). One companies row stands for the whole source; each job names its real employer.
ALTER TABLE companies DROP CONSTRAINT companies_ats_check;
ALTER TABLE companies ADD CONSTRAINT companies_ats_check
    CHECK (ats IN ('greenhouse', 'lever', 'ashby', 'smartrecruiters', 'workable', 'adzuna'));

-- Aggregator jobs name their employer here; board jobs leave it NULL and use the company row's name.
ALTER TABLE jobs ADD COLUMN employer TEXT;
-- True when the source returns only a summary of the job description.
ALTER TABLE jobs ADD COLUMN description_partial BOOLEAN NOT NULL DEFAULT false;

-- Synced only when ADZUNA_APP_ID and ADZUNA_APP_KEY are set. Pause it like any board.
INSERT INTO companies (id, name, ats, board_token) VALUES (gen_random_uuid(), 'Adzuna', 'adzuna', 'in')
ON CONFLICT (ats, board_token) DO NOTHING;

-- Searches run against an aggregator: one row per (keywords, city), shared by every user who wants it.
CREATE TABLE aggregator_queries (
    source       TEXT NOT NULL,
    what         TEXT NOT NULL,
    location     TEXT NOT NULL DEFAULT '',
    last_run_at  TIMESTAMPTZ,
    last_count   INTEGER,
    last_error   TEXT,
    PRIMARY KEY (source, what, location)
);

-- API calls made per UTC day, so restarts and manual syncs can't overrun the provider's quota.
CREATE TABLE aggregator_calls (
    source  TEXT NOT NULL,
    day     DATE NOT NULL,
    calls   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (source, day)
);
