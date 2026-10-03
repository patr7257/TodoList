-- V11: external references on items, for the integration API.
--
-- An integration (the bartender app is the first) keeps one of its own lists
-- in sync with a TodoList list, and needs to find "its" item again on the next
-- sync without matching on display text, which a person may edit. So an item
-- can now carry where it came from (external_source, for example 'bartender')
-- and that system's own id for it (external_id, for example an ingredient key).
--
-- Both columns are NULLABLE and every existing row stays NULL: an item created
-- by a person has no external reference, and nothing that exists today reads
-- these columns. GET /api/todo/state does not expose them (Views is unchanged),
-- so the append-only /state contract is untouched.
--
-- The unique index is PARTIAL so it constrains only rows that actually carry a
-- reference. Two hand-made items called "milk" in one list stay legal; two
-- rows claiming to be the same ingredient of the same source in the same list
-- do not. It is the backstop for the upsert in IntegrationService; the advisory
-- lock there is what keeps concurrent syncs from ever reaching it.
--
-- Not CREATE INDEX CONCURRENTLY: Flyway runs this in a transaction and items is
-- a small table, so a brief lock at deploy time is the cheaper trade.
--
-- Additive and idempotent like V2 through V10. See the version register in
-- CLAUDE.md. outOfOrder is false, so the order these reach production in is
-- load bearing.

ALTER TABLE items ADD COLUMN IF NOT EXISTS external_source text;
ALTER TABLE items ADD COLUMN IF NOT EXISTS external_id     text;

CREATE UNIQUE INDEX IF NOT EXISTS items_external_ref_uq
    ON items (list_id, external_source, external_id)
    WHERE external_source IS NOT NULL AND external_id IS NOT NULL;
