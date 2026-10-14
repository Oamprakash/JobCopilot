-- Feedback users send from the app. Deleted with the account.
CREATE TABLE feedback (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       TEXT NOT NULL CHECK (kind IN ('idea', 'problem', 'praise', 'other')),
    message    TEXT NOT NULL CHECK (length(message) BETWEEN 1 AND 2000),
    -- The app page it was sent from, e.g. /feed (our own pages only; never another site).
    page       TEXT,
    status     TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'done')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX feedback_created_idx ON feedback (created_at DESC);
CREATE INDEX feedback_user_idx ON feedback (user_id, created_at DESC);
