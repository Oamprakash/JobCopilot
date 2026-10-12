-- What each job's description says about visa sponsorship and relocation, worked out when the job is synced.
-- NULL visa means the description doesn't say (the usual case). Existing jobs fill in on their next sync.
ALTER TABLE jobs ADD COLUMN visa TEXT CHECK (visa IN ('offered', 'not_offered'));
ALTER TABLE jobs ADD COLUMN relocation BOOLEAN NOT NULL DEFAULT false;

-- When set, jobs abroad whose description rules out sponsorship are filtered from the user's feed.
ALTER TABLE preferences ADD COLUMN needs_visa_sponsorship BOOLEAN NOT NULL DEFAULT false;
