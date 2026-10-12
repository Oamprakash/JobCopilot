-- Aggregator searches now run per country (a Singapore search is not an India search), so the country is part
-- of each search's identity. Existing rows were all India searches.
ALTER TABLE aggregator_queries ADD COLUMN country TEXT NOT NULL DEFAULT 'in';
ALTER TABLE aggregator_queries DROP CONSTRAINT aggregator_queries_pkey;
ALTER TABLE aggregator_queries ADD PRIMARY KEY (source, country, what, location);
