-- V9: per-user ordering of lists and tasks (issue #77).
--
-- The problem: lists.sort and items.sort are plain integer columns from V1 with
-- no user_id anywhere, so the two account holders share ONE arrangement and
-- whoever dragged last wins for both. Drag reordering itself already works; the
-- "per user" half never existed.
--
-- The fix is two override tables rather than a column change. lists.sort and
-- items.sort are KEPT exactly as they are and become the baseline an account
-- that has never reordered still sees; they simply stop being written by the
-- reorder path. That choice is what keeps this migration inside the additive,
-- idempotent, never-DROP-rename-or-retype rule in CLAUDE.md: nothing existing
-- is touched, and a rollback is "stop reading the override".
--
-- The read path resolves the override server side as
-- COALESCE(override.sort, base.sort) and serves it through the EXISTING sort
-- field, so GET /api/todo/state gains no key and the append-only contract (plus
-- the ViewsTest key-order guard, plus the website's whole read path) is
-- untouched. A per-user column on lists could not have done that.
--
-- Additive and idempotent like V2 through V8. See the version register in
-- CLAUDE.md: V8 is #56 (tinder), V9 is this, and V10 is pre-assigned to #74.
-- outOfOrder is false, so the order these reach production in is load bearing.

CREATE TABLE IF NOT EXISTS list_order (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    list_id uuid NOT NULL REFERENCES lists (id) ON DELETE CASCADE,
    sort    integer NOT NULL,
    PRIMARY KEY (user_id, list_id)
);

CREATE TABLE IF NOT EXISTS item_order (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_id uuid NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    sort    integer NOT NULL,
    PRIMARY KEY (user_id, item_id)
);

-- Why the composite primary key is (user_id, <resource>_id) in that order and
-- why there is no extra index:
--
--   * it IS the uniqueness rule, one override row per user per resource, so a
--     reorder can be a plain ON CONFLICT upsert instead of a read-then-write,
--     the same way a tinder swipe is (see V8);
--   * every read is "the caller's overrides", which is a leading-column prefix
--     scan on that index, so a second index on user_id alone would only cost
--     write time;
--   * ON DELETE CASCADE on both sides means deleting a user or a list or an
--     item takes its override rows with it. Without it a deleted list would
--     leave an orphan row that blocks nothing and confuses everything.
--
-- sort is NOT NULL with no default on purpose: a row only exists because
-- somebody dragged something, so "no opinion" is the ABSENCE of a row (and
-- COALESCE then falls through to the baseline column), never a row holding a
-- default 0 that would silently pin the resource to the top.
