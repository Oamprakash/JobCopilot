-- Preference changes that would widen the feed, e.g. [{"kind":"location","value":"Hyderabad","count":7}].
ALTER TABLE match_runs ADD COLUMN suggestions JSONB NOT NULL DEFAULT '[]';
