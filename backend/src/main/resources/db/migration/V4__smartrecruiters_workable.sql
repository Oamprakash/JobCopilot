-- Two more public board sources.
ALTER TABLE companies DROP CONSTRAINT companies_ats_check;
ALTER TABLE companies ADD CONSTRAINT companies_ats_check
    CHECK (ats IN ('greenhouse', 'lever', 'ashby', 'smartrecruiters', 'workable'));

-- Workable: public careers widget API.
INSERT INTO companies (id, name, ats, board_token) VALUES
    (gen_random_uuid(), 'apna', 'workable', 'apna')
ON CONFLICT (ats, board_token) DO NOTHING;

-- SmartRecruiters boards found by tools/probe_boards.py (Oct 2026), seeded PAUSED (active = false):
-- api.smartrecruiters.com disallows automated access in robots.txt and its docs describe API-key access,
-- so confirm their terms (or get a Posting API key) before turning these on via PUT /admin/companies/{id}/active.
INSERT INTO companies (id, name, ats, board_token, active) VALUES
    (gen_random_uuid(), 'Grab', 'smartrecruiters', 'grab', false),
    (gen_random_uuid(), 'Bosch', 'smartrecruiters', 'BoschGroup', false),
    (gen_random_uuid(), 'ServiceNow', 'smartrecruiters', 'ServiceNow', false),
    (gen_random_uuid(), 'Experian', 'smartrecruiters', 'Experian', false),
    (gen_random_uuid(), 'Freshworks', 'smartrecruiters', 'freshworks', false),
    (gen_random_uuid(), 'Swiggy', 'smartrecruiters', 'swiggy', false),
    (gen_random_uuid(), 'Captain Fresh', 'smartrecruiters', 'captainfresh', false),
    (gen_random_uuid(), 'ixigo', 'smartrecruiters', 'ixigo', false),
    (gen_random_uuid(), 'Wise', 'smartrecruiters', 'wise', false),
    (gen_random_uuid(), 'Shipsy', 'smartrecruiters', 'shipsy', false),
    (gen_random_uuid(), 'Unacademy', 'smartrecruiters', 'unacademy', false)
ON CONFLICT (ats, board_token) DO NOTHING;
