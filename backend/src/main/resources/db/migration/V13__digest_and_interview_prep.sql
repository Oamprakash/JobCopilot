-- Daily match email: on by default, off from Preferences or the email's unsubscribe link.
ALTER TABLE preferences ADD COLUMN daily_digest BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN digest_sent_at TIMESTAMPTZ;

-- Interview prep for a tracked job: likely questions with talking points from the user's own profile.
CREATE TABLE interview_prep (
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id       UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    generator    TEXT NOT NULL CHECK (generator IN ('claude', 'heuristic')),
    content_json JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, job_id)
);
