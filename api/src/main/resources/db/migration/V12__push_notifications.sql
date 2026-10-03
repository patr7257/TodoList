-- V12: Web Push subscriptions and per-type notification preferences.
--
-- The website (patr7257/PatrickRobelWeb, the /todo PWA) owns everything about
-- push: it subscribes devices, stores preferences, and sends. The Java API
-- never reads these tables. The SCHEMA still lives here for the same reason as
-- V7 (todo_credentials): both tables carry a foreign key to users(id), users is
-- Flyway owned, and two migration engines emitting DDL against one schema is
-- the trap that already forced hand-edited Drizzle migrations in the website.
--
-- push_subscriptions: one row per device and browser. endpoint is UNIQUE
-- because subscribe is an upsert on it that re-keys user_id, so a shared device
-- follows whoever signed in last. A row is deleted when the push service
-- answers 404 or 410 for its endpoint.
--
-- notification_prefs: one row per user and notification type, opt-out. A
-- MISSING row means "the type's default", so reads never insert. type is plain
-- text, validated against the website's type registry at its api boundary, so
-- adding a notification type never needs a migration here.
--
-- Additive and idempotent like V2 through V11. See the version register in
-- CLAUDE.md. outOfOrder is false, so this must not reach production before V11.

CREATE TABLE IF NOT EXISTS push_subscriptions (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint   text NOT NULL UNIQUE,
    p256dh     text NOT NULL,
    auth       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS push_subscriptions_user_idx ON push_subscriptions (user_id);

CREATE TABLE IF NOT EXISTS notification_prefs (
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       text NOT NULL,
    enabled    boolean NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type)
);
