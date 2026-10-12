-- Why jobs were filtered out on a run, e.g. {"role": 290, "location": 15}, so an empty feed can explain itself.
ALTER TABLE match_runs ADD COLUMN filtered JSONB NOT NULL DEFAULT '{}';

-- Boards found by tools/probe_boards.py (Oct 2026): live public boards with India engineering openings.
-- Truecaller and Adyen had Android roles in Bengaluru when probed.
INSERT INTO companies (id, name, ats, board_token) VALUES
    (gen_random_uuid(), 'Truecaller', 'greenhouse', 'truecaller'),
    (gen_random_uuid(), 'Adyen', 'greenhouse', 'adyen'),
    (gen_random_uuid(), 'Okta', 'greenhouse', 'okta'),
    (gen_random_uuid(), 'Tekion', 'ashby', 'tekion'),
    (gen_random_uuid(), 'Zscaler', 'greenhouse', 'zscaler'),
    (gen_random_uuid(), 'MongoDB', 'greenhouse', 'mongodb'),
    (gen_random_uuid(), 'Netskope', 'greenhouse', 'netskope'),
    (gen_random_uuid(), 'GitLab', 'greenhouse', 'gitlab'),
    (gen_random_uuid(), 'Twilio', 'greenhouse', 'twilio'),
    (gen_random_uuid(), 'ChargePoint', 'greenhouse', 'chargepoint'),
    (gen_random_uuid(), 'Druva', 'greenhouse', 'druva'),
    (gen_random_uuid(), 'Elastic', 'greenhouse', 'elastic'),
    (gen_random_uuid(), 'Stripe', 'greenhouse', 'stripe'),
    (gen_random_uuid(), 'Mindtickle', 'lever', 'mindtickle'),
    (gen_random_uuid(), 'Toast', 'greenhouse', 'toast'),
    (gen_random_uuid(), 'Agoda', 'greenhouse', 'agoda'),
    (gen_random_uuid(), 'Glance', 'greenhouse', 'glance'),
    (gen_random_uuid(), 'Samsara', 'greenhouse', 'samsara'),
    (gen_random_uuid(), 'FamPay', 'lever', 'fampay'),
    (gen_random_uuid(), 'New Relic', 'greenhouse', 'newrelic'),
    (gen_random_uuid(), 'InMobi', 'greenhouse', 'inmobi'),
    (gen_random_uuid(), 'Notion', 'ashby', 'notion'),
    (gen_random_uuid(), 'Observe.AI', 'greenhouse', 'observeai'),
    (gen_random_uuid(), 'Sumo Logic', 'greenhouse', 'sumologic'),
    (gen_random_uuid(), 'Turing', 'greenhouse', 'turing'),
    (gen_random_uuid(), 'Paytm', 'lever', 'paytm'),
    (gen_random_uuid(), 'Tide', 'greenhouse', 'tide'),
    (gen_random_uuid(), 'SpotDraft', 'ashby', 'spotdraft'),
    (gen_random_uuid(), 'Datadog', 'greenhouse', 'datadog'),
    (gen_random_uuid(), 'Airbnb', 'greenhouse', 'airbnb'),
    (gen_random_uuid(), 'Deliveroo', 'greenhouse', 'deliveroo'),
    (gen_random_uuid(), 'Pocket FM', 'lever', 'pocketfm')
ON CONFLICT (ats, board_token) DO NOTHING;
