-- What users do in the app, for the admin dashboard: sign-ins, searches (saved preferences), feed refreshes,
-- jobs opened, saved or skipped, applications tailored and approved, and clicks through to apply.
-- Deleting an account deletes its events. Events older than EVENT_RETENTION_DAYS are removed daily.
CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        TEXT NOT NULL,
    job_id      UUID REFERENCES jobs (id) ON DELETE SET NULL,
    data        JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX events_user_time_idx ON events (user_id, created_at DESC);
CREATE INDEX events_type_time_idx ON events (type, created_at DESC);

ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMPTZ;
