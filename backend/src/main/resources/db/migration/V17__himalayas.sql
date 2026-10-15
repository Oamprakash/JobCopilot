-- Himalayas: a free public API of remote jobs (himalayas.app/docs/remote-jobs-api). No key; its terms ask for a
-- visible link back to himalayas.app, which every listing carries. One companies row stands for the whole
-- source, like Adzuna; each job names its real employer. Switch it off on the Admin page like Adzuna.
ALTER TABLE companies DROP CONSTRAINT companies_ats_check;
ALTER TABLE companies ADD CONSTRAINT companies_ats_check
    CHECK (ats IN ('greenhouse', 'lever', 'ashby', 'smartrecruiters', 'workable', 'adzuna', 'manual', 'himalayas'));

INSERT INTO companies (id, name, ats, board_token) VALUES (gen_random_uuid(), 'Himalayas', 'himalayas', 'remote')
ON CONFLICT (ats, board_token) DO NOTHING;
