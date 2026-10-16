-- What free users say they'd pay for unlimited tailoring, asked when they reach the monthly limit. Nobody is
-- charged; this decides whether and how to price a paid plan. One answer per user, which they can change.
CREATE TABLE pro_interest (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    choice     TEXT NOT NULL CHECK (choice IN ('month_199', 'pass_499', 'year_999', 'no')),
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
