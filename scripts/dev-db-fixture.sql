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
-- pw_hash is left NULL, which is what every account looks like since password
-- login was retired (issue #61). These rows exist only to give the backfill
-- something to match on; they are not sign-in identities, because sign-in also
-- needs the website's email allowlist. To create a real account use
-- scripts/seed-user.ps1 (or seed-user.sh).
--
-- Safe to re-run: users upsert on their unique email, lists are guarded by name.

INSERT INTO users (email, name, pw_hash) VALUES
    ('alex@fixture.test',  'Alex', NULL),
    ('sam1@fixture.test',  'Sam',  NULL),
    ('sam2@fixture.test',  'Sam',  NULL)
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
