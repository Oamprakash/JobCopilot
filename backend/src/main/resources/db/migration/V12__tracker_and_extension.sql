-- Phase 4: the application tracker and the browser extension.

-- Jobs saved from any web page through the extension, or added by hand. They hang off one inactive companies row,
-- so they never enter anyone's feed; each names its own employer like aggregator jobs do.
ALTER TABLE companies DROP CONSTRAINT companies_ats_check;
ALTER TABLE companies ADD CONSTRAINT companies_ats_check
    CHECK (ats IN ('greenhouse', 'lever', 'ashby', 'smartrecruiters', 'workable', 'adzuna', 'manual'));
INSERT INTO companies (id, name, ats, board_token, active) VALUES (gen_random_uuid(), 'Saved jobs', 'manual', 'saved', false)
ON CONFLICT (ats, board_token) DO NOTHING;

-- One row per job the user is tracking, from saved through to an outcome.
CREATE TABLE tracker (
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id         UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    stage          TEXT NOT NULL DEFAULT 'saved'
                   CHECK (stage IN ('saved', 'applied', 'interview', 'offer', 'rejected', 'withdrawn')),
    applied_at     TIMESTAMPTZ,
    follow_up_at   TIMESTAMPTZ,
    -- The last follow-up reminder sent, so each due date is reminded once.
    reminded_at    TIMESTAMPTZ,
    notes          TEXT NOT NULL DEFAULT '',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, job_id)
);
CREATE INDEX tracker_follow_up_idx ON tracker (follow_up_at) WHERE follow_up_at IS NOT NULL;

-- Browser extension sign-in: a short code from the web app is exchanged once for a long-lived token.
-- Only hashes are stored.
CREATE TABLE extension_pair_codes (
    code_hash   TEXT PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL
);
CREATE TABLE extension_tokens (
    token_hash    TEXT PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ
);
CREATE INDEX extension_tokens_user_idx ON extension_tokens (user_id);
