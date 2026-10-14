-- People asking for access while sign-up is invite-only. Inviting one lets that email sign in
-- (alongside INVITE_EMAILS). Admins can delete entries on request.
CREATE TABLE waitlist (
    id         UUID PRIMARY KEY,
    email      TEXT NOT NULL UNIQUE,
    note       TEXT CHECK (note IS NULL OR length(note) <= 300),
    status     TEXT NOT NULL DEFAULT 'waiting' CHECK (status IN ('waiting', 'invited')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    invited_at TIMESTAMPTZ
);
CREATE INDEX waitlist_status_idx ON waitlist (status, created_at);
