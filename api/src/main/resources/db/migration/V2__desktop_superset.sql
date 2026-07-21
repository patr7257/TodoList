-- V2 desktop superset: additive, NULLABLE columns the JavaFX desktop app tracks
-- that the website schema does not have yet. Every column is nullable and guarded
-- with IF NOT EXISTS, so this migration is safe to run against the existing Neon
-- database (which gets baselined at V1 and then runs only this migration) without
-- touching any existing data.

ALTER TABLE lists ADD COLUMN IF NOT EXISTS owner             text;
ALTER TABLE lists ADD COLUMN IF NOT EXISTS priority          integer;
ALTER TABLE lists ADD COLUMN IF NOT EXISTS year              integer;
ALTER TABLE lists ADD COLUMN IF NOT EXISTS location          text;
ALTER TABLE lists ADD COLUMN IF NOT EXISTS description       text;
ALTER TABLE lists ADD COLUMN IF NOT EXISTS task_columns_json text;

ALTER TABLE items ADD COLUMN IF NOT EXISTS year integer;
