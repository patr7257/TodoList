-- V5: shared, manually maintained "fun counters" (Total Flights, Total Ships,
-- Tour de Brede walks, ...), desktop-only. The "fun_" table prefix is
-- deliberate: it marks the table as owned by the desktop app so nobody
-- mistakes it for part of the website's Drizzle schema.
--
-- Idempotent like V2/V3/V4: IF NOT EXISTS everywhere, and the seed insert is
-- guarded so re-running this migration (or deleting a seeded counter later)
-- never duplicates or resurrects rows.

CREATE TABLE IF NOT EXISTS fun_counters (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    label       text NOT NULL,
    description text,
    value       integer NOT NULL DEFAULT 0,
    icon        text,
    sort        integer NOT NULL DEFAULT 0,
    created_by  uuid REFERENCES users (id),
    created_at  timestamp NOT NULL DEFAULT now(),
    updated_at  timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS fun_counters_sort_idx ON fun_counters (sort, created_at);

-- Seed the three counters named in the issue, once. Guarded on the table being
-- entirely empty, so a later manual deletion of a seeded counter does not
-- resurrect it on the next migrate run.
INSERT INTO fun_counters (label, description, value, icon, sort)
SELECT * FROM (VALUES
    ('Total Flights', 'Flights taken together, fly-ud-fly-ind', 0, 'fth-send', 0),
    ('Total Ships', 'Ships and cruises taken together', 0, 'fth-anchor', 1),
    ('Tour de Brede', 'Tour de Brede walks completed together', 0, 'fth-compass', 2)
) AS seed(label, description, value, icon, sort)
WHERE NOT EXISTS (SELECT 1 FROM fun_counters);
