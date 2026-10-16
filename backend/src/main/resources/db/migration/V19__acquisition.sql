-- How each person first found the site: campaign tags from the link (utm_*), the referring site (host only) and
-- the landing page (path only). Set once, on the first sign-in after a tracked first visit; shown to admins.
ALTER TABLE users ADD COLUMN acq_source TEXT;
ALTER TABLE users ADD COLUMN acq_medium TEXT;
ALTER TABLE users ADD COLUMN acq_campaign TEXT;
ALTER TABLE users ADD COLUMN acq_referrer TEXT;
ALTER TABLE users ADD COLUMN acq_landing TEXT;

ALTER TABLE waitlist ADD COLUMN acq_source TEXT;
ALTER TABLE waitlist ADD COLUMN acq_medium TEXT;
ALTER TABLE waitlist ADD COLUMN acq_campaign TEXT;
ALTER TABLE waitlist ADD COLUMN acq_referrer TEXT;
