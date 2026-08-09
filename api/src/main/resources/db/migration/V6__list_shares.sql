-- V6: public share links for a list (issue #52). A share is a bearer secret:
-- whoever holds the token can read that one list, unauthenticated, at
-- GET /api/todo/share/{token}.
--
-- Why a separate table rather than a lists.share_token column: every polling
-- client hits GET /api/todo/state, which is built from a plain SELECT over
-- lists. A column on lists would ride along in that payload (the /state list
-- object is append-only and consumed verbatim by the website), so the secret
-- would be handed to every signed-in client on every poll, and would end up in
-- logs, caches and browser devtools. A separate table keeps the secret off the
-- hot read path entirely: nothing reads list_shares except the share endpoints.
-- It also buys per-link revocation, expiry, labels and view counts, none of
-- which fit in a single column.
--
-- Additive and idempotent like V2 through V5: IF NOT EXISTS everywhere, no
-- DROP, no rename, no retype, so re-running it is a no-op and it is safe
-- against the live Neon database.
--
-- Rollback note: an older API jar simply never queries this table, so rolling
-- the API back does NOT need a schema rollback. Leave the table in place.

CREATE TABLE IF NOT EXISTS list_shares (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id        uuid NOT NULL REFERENCES lists (id) ON DELETE CASCADE,
    token          text NOT NULL,
    label          text,
    created_by     uuid REFERENCES users (id),
    created_at     timestamp NOT NULL DEFAULT now(),
    expires_at     timestamp,
    revoked_at     timestamp,
    last_viewed_at timestamp,
    view_count     integer NOT NULL DEFAULT 0
);

-- ON DELETE CASCADE above is deliberate: deleting a list must take its share
-- links with it, or a revoked-by-deletion list would leave a live token behind
-- pointing at nothing (or, worse, at a recycled id).

CREATE UNIQUE INDEX IF NOT EXISTS list_shares_token_key ON list_shares (token);
CREATE INDEX IF NOT EXISTS list_shares_list_id_idx ON list_shares (list_id, created_at);
