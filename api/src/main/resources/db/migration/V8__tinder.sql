-- V8: TodoTinder (issue #56, part of the epic #44). Three tables: the decks,
-- the cards in them, and who swiped what. A right swipe creates an ordinary
-- row in `items` through the existing TodoService, so nothing here duplicates
-- the todo model: these tables only hold the swiping, never the todo.
--
-- The "tinder_" prefix is deliberate, for the same reason "fun_" is on
-- fun_counters: it marks the family as owned by a feature of this API and not
-- part of the website's Drizzle schema.
--
-- Additive and idempotent like V2 through V7: IF NOT EXISTS everywhere, no
-- DROP, no rename, no retype, so re-running it is a no-op and it is safe
-- against the live Neon database. The CHECK constraints are declared INSIDE
-- the CREATE TABLE bodies rather than as separate ALTER TABLE statements,
-- because ALTER TABLE ... ADD CONSTRAINT has no IF NOT EXISTS form and would
-- make a second run fail.
--
-- Rollback note: an older API jar simply never queries these tables, so
-- rolling the API back does NOT need a schema rollback. Leave them in place.
--
-- No deck rows are seeded here on purpose. V8 is the shape only; the four
-- launch decks and their cards arrive with issue #57 as committed datasets,
-- and a deck's target_list_id has to be resolved against real lists, which a
-- migration cannot do sensibly.

-- A deck is config, not code: adding a fifth deck is one row plus one dataset.
CREATE TABLE IF NOT EXISTS tinder_decks (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    key            text NOT NULL,
    display_name   text NOT NULL,
    -- ON DELETE SET NULL, not CASCADE: deleting the "Indkøb" list must not take
    -- the grocery deck and every swipe on it with it. A deck with no target
    -- list still swipes, it just creates nothing (TinderService handles that).
    target_list_id uuid REFERENCES lists (id) ON DELETE SET NULL,
    -- 'deplete': a swiped card never comes back for that user (idea decks).
    -- 'recycle': every card is offered again on the next run (groceries).
    recycle_mode   text NOT NULL DEFAULT 'deplete'
                       CHECK (recycle_mode IN ('deplete', 'recycle')),
    dataset_key    text,
    active         boolean NOT NULL DEFAULT true,
    created_at     timestamp NOT NULL DEFAULT now()
);

-- The key is the public handle: every route takes /tinder/decks/{key}, never a
-- uuid, so it has to be unique. A unique INDEX rather than a UNIQUE column
-- constraint, because only the index form has IF NOT EXISTS (same reasoning as
-- list_shares_token_key in V6).
CREATE UNIQUE INDEX IF NOT EXISTS tinder_decks_key_key ON tinder_decks (key);
CREATE INDEX IF NOT EXISTS tinder_decks_active_idx ON tinder_decks (active, created_at);

CREATE TABLE IF NOT EXISTS tinder_entries (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    deck_id    uuid NOT NULL REFERENCES tinder_decks (id) ON DELETE CASCADE,
    text       text NOT NULL,
    metadata   jsonb NOT NULL DEFAULT '{}'::jsonb,
    source     text,
    active     boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now()
);

-- The refill import (issue #59) dedupes HERE, in the database, via
-- INSERT ... ON CONFLICT (deck_id, text) DO NOTHING, rather than by reading the
-- deck first and hoping nothing changed in between. That is what makes a
-- partially duplicate batch insert only its new rows instead of failing whole.
CREATE UNIQUE INDEX IF NOT EXISTS tinder_entries_deck_text_key ON tinder_entries (deck_id, text);
-- The card read path filters on exactly (deck_id, active) and orders by
-- created_at, so that is the index it gets.
CREATE INDEX IF NOT EXISTS tinder_entries_deck_active_idx ON tinder_entries (deck_id, active, created_at);

CREATE TABLE IF NOT EXISTS tinder_swipes (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    entry_id   uuid NOT NULL REFERENCES tinder_entries (id) ON DELETE CASCADE,
    direction  text NOT NULL CHECK (direction IN ('right', 'left')),
    created_at timestamp NOT NULL DEFAULT now()
);

-- WHY GROCERIES DO NOT ACCUMULATE SWIPE ROWS, read this before changing it.
--
-- The spec wants "one swipe per (user, entry)" for the NON-recycling decks
-- only. That cannot be expressed as a partial unique index: a partial index
-- predicate may only reference columns of its own table, and the recycle mode
-- lives on tinder_decks, one join away. So the index below is PLAIN, it applies
-- to every deck, and the mode difference lives in the INSERT instead:
-- TinderService writes every swipe as
--   INSERT ... ON CONFLICT (user_id, entry_id) DO UPDATE SET direction = ..., created_at = now()
-- The recycling deck re-offers the same milk every week, so the same
-- (user, entry) pair is swiped over and over; the upsert keeps exactly ONE row
-- per pair and moves it forward in time, instead of growing the table without
-- bound or throwing a unique violation on the second week.
--
-- The consequence to keep in mind: tinder_swipes is a "latest swipe" table, not
-- a swipe log. Nothing here can answer "how many times did she buy milk". If
-- that is ever wanted it needs its own append-only table, not a relaxed index.
-- This index does double duty: it is the upsert's conflict target AND the index
-- the card query's "has this user already swiped this entry" probe rides on, so
-- no separate (user_id, entry_id) index is needed.
CREATE UNIQUE INDEX IF NOT EXISTS tinder_swipes_user_entry_key ON tinder_swipes (user_id, entry_id);
-- The match query groups right swipes by entry, which the pair above cannot
-- serve because it leads with user_id.
CREATE INDEX IF NOT EXISTS tinder_swipes_entry_direction_idx ON tinder_swipes (entry_id, direction);
