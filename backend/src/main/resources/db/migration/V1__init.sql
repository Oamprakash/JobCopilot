-- Phase 1 (Foundation): users, magic-link auth, resume files, versioned profiles, preferences.

CREATE TABLE users (
    id          UUID PRIMARY KEY,
    email       TEXT NOT NULL UNIQUE,
    plan        TEXT NOT NULL DEFAULT 'free' CHECK (plan IN ('free', 'pro')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Only the SHA-256 hash of a magic-link token is stored, never the token itself.
CREATE TABLE magic_link_tokens (
    token_hash  TEXT PRIMARY KEY,
    email       TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX magic_link_tokens_email_created_idx ON magic_link_tokens (email, created_at);

CREATE TABLE resume_files (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    storage_key   TEXT NOT NULL,
    file_name     TEXT NOT NULL,
    content_type  TEXT NOT NULL,
    size_bytes    INTEGER NOT NULL,
    text_content  TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX resume_files_user_idx ON resume_files (user_id);

-- Every confirmed edit creates a new version; the highest version is the active profile.
CREATE TABLE profiles (
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    version         INTEGER NOT NULL CHECK (version > 0),
    profile_json    JSONB NOT NULL,
    resume_file_id  UUID REFERENCES resume_files (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, version)
);

CREATE TABLE preferences (
    user_id             UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    roles               TEXT[] NOT NULL DEFAULT '{}',
    locations           TEXT[] NOT NULL DEFAULT '{}',
    remote_mode         TEXT NOT NULL DEFAULT 'any' CHECK (remote_mode IN ('onsite', 'hybrid', 'remote', 'any')),
    min_ctc_lpa         NUMERIC(6, 2),
    experience_years    NUMERIC(4, 1),
    notice_period_days  INTEGER,
    skip_companies      TEXT[] NOT NULL DEFAULT '{}',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
