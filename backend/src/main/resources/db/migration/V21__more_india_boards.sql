-- More public company boards with engineering openings in India, found by tools/probe_boards.py (Oct 2026).
INSERT INTO companies (id, name, ats, board_token) VALUES
    (gen_random_uuid(), 'Atlys', 'ashby', 'atlys'),
    (gen_random_uuid(), 'HighRadius', 'greenhouse', 'highradius'),
    (gen_random_uuid(), 'Commvault', 'greenhouse', 'commvault'),
    (gen_random_uuid(), 'Sigmoid', 'greenhouse', 'sigmoid'),
    (gen_random_uuid(), 'YugabyteDB', 'greenhouse', 'yugabyte'),
    (gen_random_uuid(), 'Harness', 'greenhouse', 'harnessinc')
ON CONFLICT (ats, board_token) DO NOTHING;
