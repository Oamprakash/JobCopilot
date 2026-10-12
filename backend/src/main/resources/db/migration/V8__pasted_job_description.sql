-- The full job description the user pasted for this application, when the source gave only a summary.
-- Tailoring and keyword coverage use it instead of jobs.description. NULL means use the source's text.
ALTER TABLE applications ADD COLUMN job_description TEXT;
