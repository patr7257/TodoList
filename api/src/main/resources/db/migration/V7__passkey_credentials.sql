-- V7: WebAuthn (passkey) credentials for the new sign-in flow (issue #51).
--
-- Sign-in moves off email plus password onto passkeys plus magic link. The
-- ceremony itself cannot happen in the JavaFX desktop client (WebAuthn is
-- browser and platform mediated), so it happens on a web page at
-- patrickrobel.dk and the desktop app receives the resulting session token
-- back over a localhost loopback listener. The website therefore both mints
-- the token and stores the credentials, but the SCHEMA still belongs here.
--
-- Why Flyway owns this table rather than the website's Drizzle schema: it
-- carries a foreign key to users(id), and users is Flyway owned. Two migration
-- engines emitting DDL against one schema is precisely the trap that already
-- forced two hand-edited Drizzle migration files in the website repo, where
-- drizzle-kit kept proposing DROP TABLE for the todo tables it did not know
-- about. Flyway also runs automatically at API boot, whereas a Drizzle
-- migration needs someone to remember to run it against production.
--
-- Column names deliberately mirror the website's existing hub_credentials
-- table, so the proven lib/hub/webauthn.ts can be copied with a rename rather
-- than rewritten.
--
-- Additive and idempotent like V2 through V6. See the version register in
-- CLAUDE.md: V6 is #52 (list_shares), V7 is this. outOfOrder is false, so the
-- order these reach production in is load bearing.

CREATE TABLE IF NOT EXISTS todo_credentials (
    id           text PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    public_key   text NOT NULL,
    counter      integer NOT NULL DEFAULT 0,
    transports   text,
    device_name  text,
    created_at   timestamp NOT NULL DEFAULT now(),
    last_used_at timestamp
);

-- The primary key is the authenticator's OWN credential id (base64url), not a
-- generated uuid, because that is the value the browser hands back on every
-- assertion and the only key we can look a credential up by. ON DELETE CASCADE
-- means removing a user takes their passkeys with them.
CREATE INDEX IF NOT EXISTS todo_credentials_user_idx ON todo_credentials (user_id);

-- A passkey-only or magic-link-only account has no password, but pw_hash was
-- declared NOT NULL with no default in V1. Relaxing a NOT NULL is a constraint
-- RELAXATION: it cannot fail on existing rows, cannot break the SeedUser tool
-- (which always supplies a value), and is a no-op when re-run. So it does not
-- violate the never-DROP-or-retype rule that governs every migration here.
--
-- This is also the kill switch for password login. Once the desktop cutover
-- has soaked, UPDATE users SET pw_hash = NULL turns password sign-in off
-- instantly and reversibly, WITHOUT deleting any code: Scrypt.verify already
-- returns false for a null hash, so the route answers 401 rather than 500.
-- Deleting the password code is a separate, later issue.
ALTER TABLE users ALTER COLUMN pw_hash DROP NOT NULL;
