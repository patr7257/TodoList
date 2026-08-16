#!/usr/bin/env bash
# Seeds the four TodoTinder decks (scripts/data/tinder-*.json) into Postgres.
# POSIX/bash 3.2 compatible counterpart to scripts/seed-tinder.ps1.
#
# Why this exists: issue #57 ships the datasets, issue #56 owns the V8 migration
# that creates tinder_decks / tinder_entries. This script is the bridge: it reads
# the committed JSON, resolves each deck's targetList NAME to a lists.id (ids
# differ per environment), and upserts idempotently so running it twice never
# duplicates anything.
#
# Assumed schema (see the design-session comment on issue #44; issue #56 owns the
# real migration, adjust the column names below if it lands differently):
#   tinder_decks(key text UNIQUE, display_name text, target_list_id uuid,
#                recycle_mode text, dataset_key text, active boolean)
#   tinder_entries(deck_id uuid, text text, metadata jsonb, source text,
#                  active boolean, UNIQUE (deck_id, text))
#
# Usage:
#   bash scripts/seed-tinder.sh
#
# Prompts for DATABASE_URL if it is not already in the environment (never pass
# it as an argument), shows a DRY RUN of what would be inserted / updated /
# skipped, then asks for confirmation before writing anything. Never deletes an
# existing entry.
#
# Requires the PostgreSQL client (psql) and jq on PATH.

set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"

DECK_FILES="scripts/data/tinder-aktiviteter.json scripts/data/tinder-rejsemaal.json scripts/data/tinder-indkoeb.json scripts/data/tinder-datenights.json"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql was not found on PATH."
  echo "Install the PostgreSQL client tools (they ship psql) and try again."
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq was not found on PATH. Install it and try again."
  exit 1
fi

for f in $DECK_FILES; do
  if [ ! -f "$f" ]; then
    echo "Missing dataset file: $f"
    exit 1
  fi
done

SET_BY_US=0
if [ -z "${DATABASE_URL:-}" ]; then
  read -r -p "DATABASE_URL (Postgres connection string): " DATABASE_URL
  SET_BY_US=1
fi
export DATABASE_URL

WORKDIR=$(mktemp -d)
cleanup() {
  rm -rf "$WORKDIR"
  # Never leave a pasted secret behind in this shell.
  if [ "$SET_BY_US" -eq 1 ]; then
    unset DATABASE_URL
  fi
}
trap cleanup EXIT

# --- Check the tinder_* tables exist before doing anything else ---
CHECK=$(psql "$DATABASE_URL" -tAc "SELECT (to_regclass('public.tinder_decks') IS NOT NULL AND to_regclass('public.tinder_entries') IS NOT NULL)::text;" 2>&1) || {
  echo "Could not connect to the database or run the check query."
  echo "$CHECK"
  exit 1
}
CHECK_TRIMMED=$(echo "$CHECK" | tr -d '[:space:]')
if [ "$CHECK_TRIMMED" != "t" ]; then
  echo ""
  echo "The tinder_decks / tinder_entries tables do not exist in this database yet."
  echo "They come from the V8 migration (issue #56). Start the API once against this"
  echo "DATABASE_URL after that migration has merged, so Flyway creates the schema,"
  echo "then re-run this script."
  exit 1
fi

# --- Build the VALUES lists from the JSON files ---
cat > "$WORKDIR/decks.jq" <<'JQEOF'
def esc: gsub("'"; "''");
.deck |
"(" + "'" + (.key|esc) + "', '" + (.displayName|esc) + "', '" + (.targetList|esc) + "', '" + (.recycleMode|esc) + "', '" + (.datasetKey|esc) + "')"
JQEOF

cat > "$WORKDIR/entries.jq" <<'JQEOF'
def esc: gsub("'"; "''");
.entries[] |
"(" + "'" + $deckkey + "', '" + (.text|esc) + "', '" + ((.metadata|tojson)|esc) + "'::jsonb)"
JQEOF

DECK_LINES="$WORKDIR/deck_lines.sql"
ENTRY_LINES="$WORKDIR/entry_lines.sql"
: > "$DECK_LINES"
: > "$ENTRY_LINES"

for f in $DECK_FILES; do
  jq -r -f "$WORKDIR/decks.jq" "$f" >> "$DECK_LINES"
  DECKKEY=$(jq -r '.deck.key' "$f")
  jq -r --arg deckkey "$DECKKEY" -f "$WORKDIR/entries.jq" "$f" >> "$ENTRY_LINES"
done

DECK_VALUES=$(paste -sd, "$DECK_LINES")
ENTRY_VALUES=$(paste -sd, "$ENTRY_LINES")

SETUP_SQL="$WORKDIR/setup.sql"
cat > "$SETUP_SQL" <<SETUPEOF
CREATE TEMP TABLE input_decks (key text, display_name text, target_list_name text, recycle_mode text, dataset_key text);
INSERT INTO input_decks (key, display_name, target_list_name, recycle_mode, dataset_key) VALUES
$DECK_VALUES;

CREATE TEMP TABLE input_entries (deck_key text, entry_text text, metadata jsonb);
INSERT INTO input_entries (deck_key, entry_text, metadata) VALUES
$ENTRY_VALUES;
SETUPEOF

cat > "$WORKDIR/dryrun_query.sql" <<'SQL'
\echo '--- Decks ---'
SELECT
  d.key,
  d.target_list_name,
  CASE WHEN l.id IS NULL THEN 'MISSING target list, will be skipped' ELSE 'ok' END AS list_status,
  CASE WHEN td.id IS NULL THEN 'insert' ELSE 'update' END AS deck_action
FROM input_decks d
LEFT JOIN lists l ON l.name = d.target_list_name
LEFT JOIN tinder_decks td ON td.key = d.key
ORDER BY d.key;

\echo '--- Entries per deck (nothing written yet) ---'
SELECT
  e.deck_key,
  count(*) FILTER (WHERE ex.id IS NULL) AS to_insert,
  count(*) FILTER (WHERE ex.id IS NOT NULL AND ex.metadata IS DISTINCT FROM e.metadata) AS to_update,
  count(*) FILTER (WHERE ex.id IS NOT NULL AND ex.metadata IS NOT DISTINCT FROM e.metadata) AS unchanged,
  count(*) AS total_in_file
FROM input_entries e
LEFT JOIN tinder_decks td ON td.key = e.deck_key
LEFT JOIN tinder_entries ex ON ex.deck_id = td.id AND ex.text = e.entry_text
GROUP BY e.deck_key
ORDER BY e.deck_key;
SQL

cat "$SETUP_SQL" "$WORKDIR/dryrun_query.sql" > "$WORKDIR/dryrun.sql"

echo ""
echo "DRY RUN: nothing is written yet."
echo ""
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$WORKDIR/dryrun.sql"

echo ""
printf 'Proceed with these writes? [y/N] '
read -r CONFIRM
case "$CONFIRM" in
  y|Y|yes|YES) ;;
  *) echo "Nothing written."; exit 0 ;;
esac

cat > "$WORKDIR/apply_query.sql" <<'SQL'
BEGIN;

INSERT INTO tinder_decks (key, display_name, target_list_id, recycle_mode, dataset_key, active)
SELECT d.key, d.display_name, l.id, d.recycle_mode, d.dataset_key, true
FROM input_decks d
JOIN lists l ON l.name = d.target_list_name
ON CONFLICT (key) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  target_list_id = EXCLUDED.target_list_id,
  recycle_mode = EXCLUDED.recycle_mode,
  dataset_key = EXCLUDED.dataset_key,
  active = true;

INSERT INTO tinder_entries (deck_id, text, metadata, source, active)
SELECT td.id, e.entry_text, e.metadata, 'seed', true
FROM input_entries e
JOIN tinder_decks td ON td.key = e.deck_key
ON CONFLICT (deck_id, text) DO UPDATE SET
  metadata = EXCLUDED.metadata,
  source = EXCLUDED.source,
  active = true;

COMMIT;

\echo 'Seed complete.'
SQL

cat "$SETUP_SQL" "$WORKDIR/apply_query.sql" > "$WORKDIR/apply.sql"

echo ""
echo "Writing ..."
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$WORKDIR/apply.sql"
echo "Done."
