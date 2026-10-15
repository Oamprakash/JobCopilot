-- Countries a user can work remotely from (ISO codes, lowercase): remote jobs open only to other countries are
-- hidden. Empty means India, which is what everyone had before this column.
ALTER TABLE preferences ADD COLUMN remote_countries TEXT[] NOT NULL DEFAULT '{}';
