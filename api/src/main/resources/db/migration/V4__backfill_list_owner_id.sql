-- V4: backfill lists.owner_id from the legacy free-text lists.owner column.
--
-- Exactly ONE statement (so a test can execute this file verbatim off the
-- classpath). Matches lower(btrim(users.name)) = lower(btrim(lists.owner)),
-- only for rows where owner_id is still NULL and owner is non-null/non-blank.
-- users.name is NOT unique: when a name matches more than one user the list
-- is intentionally left with owner_id NULL (to be picked by hand in the app)
-- rather than silently attaching it to the wrong person. Re-running this
-- statement is a no-op, since it only ever touches rows where owner_id IS NULL.

UPDATE lists
SET owner_id = matched.user_id
FROM (
    -- Postgres has no MIN/MAX aggregate for uuid; cast through text (safe here
    -- since the HAVING clause below guarantees at most one distinct u.id).
    SELECT l.id AS list_id, MIN(u.id::text)::uuid AS user_id
    FROM lists l
    JOIN users u ON lower(btrim(u.name)) = lower(btrim(l.owner))
    WHERE l.owner_id IS NULL
      AND l.owner IS NOT NULL
      AND btrim(l.owner) <> ''
    GROUP BY l.id
    HAVING COUNT(*) = 1
) AS matched
WHERE lists.id = matched.list_id;
