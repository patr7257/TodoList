-- Local dev fixture for the TodoList API. Loaded by scripts/dev-db.ps1 -Fixture
-- (or scripts/dev-db.sh --fixture) AFTER the API has started once, because
-- Flyway (which runs inside the API at startup) is what creates these tables.
--
-- Purpose: exercise the lists.owner free-text to owner_id backfill locally,
-- without ever touching production data. The owner values below deliberately
-- cover every case the backfill has to get right:
--   exact match, different case, padded whitespace, a name that matches TWO
--   users (must stay unresolved), a name that matches nobody, and NULL.
--
-- These users CANNOT log in: pw_hash is a placeholder, not a real scrypt hash.
-- To create a login account use scripts/seed-user.ps1 (or seed-user.sh).
--
-- Safe to re-run: users upsert on their unique email, lists are guarded by name.

INSERT INTO users (email, name, pw_hash) VALUES
    ('alex@fixture.test',  'Alex', 'fixture-not-loginable'),
    ('sam1@fixture.test',  'Sam',  'fixture-not-loginable'),
    ('sam2@fixture.test',  'Sam',  'fixture-not-loginable')
ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO lists (name, owner, sort)
SELECT v.name, v.owner, v.sort
FROM (VALUES
    ('Fixture owner exact',      'Alex',         100),
    ('Fixture owner lowercase',  'alex',         101),
    ('Fixture owner padded',     '  Alex  ',     102),
    ('Fixture owner ambiguous',  'Sam',          103),
    ('Fixture owner no match',   'Nobody Here',  104),
    ('Fixture owner null',       NULL,           105)
) AS v(name, owner, sort)
WHERE NOT EXISTS (SELECT 1 FROM lists l WHERE l.name = v.name);
