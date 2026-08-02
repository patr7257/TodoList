-- V3: promote the free-text lists.owner column to a real user reference.
-- Additive and idempotent (IF NOT EXISTS everywhere), so it is safe to run
-- against the existing Neon database (baselined at V1, already carrying V2)
-- without touching any existing data. lists.owner (free text) is untouched
-- and keeps being written by the API for backward compatibility; owner_id is
-- the new source of truth going forward. Backfilling existing rows from the
-- text column happens in V4.

ALTER TABLE lists ADD COLUMN IF NOT EXISTS owner_id uuid REFERENCES users (id);
CREATE INDEX IF NOT EXISTS lists_owner_id_idx ON lists (owner_id);
