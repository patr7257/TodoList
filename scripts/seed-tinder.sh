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
# Schema, as shipped by V8:
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
# The four target lists (Aktiviteter, Rejsemaal, Indkoeb, Date nights, spelled
# with their real Danish letters) must already exist by NAME. A deck whose list
# is missing is reported in the dry run and skipped rather than pointed at
# nothing, and the shortfall check at the end turns that into a loud failure.
#
# Requires jq. Uses psql if it is installed, otherwise borrows one from a
# throwaway docker container.

set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"

DECK_FILES="scripts/data/tinder-aktiviteter.json scripts/data/tinder-rejsemaal.json scripts/data/tinder-indkoeb.json scripts/data/tinder-datenights.json"

USE_DOCKER=0
if ! command -v psql >/dev/null 2>&1; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "Neither psql nor docker was found on PATH."
    echo "Install the PostgreSQL client tools, or start Docker, and try again."
    exit 1
  fi
  USE_DOCKER=1
  echo "psql is not installed locally, borrowing one from a docker container."
fi

# Runs psql against DATABASE_URL. Always reads SQL from a FILE, never a pipe, so
# the Danish letters in the deck and list names cannot be mangled in transit.
run_psql() {
  sql_file="$1"; shift
  if [ "$USE_DOCKER" -eq 1 ]; then
    # localhost inside a container is the CONTAINER, not this machine.
    url=$(printf '%s' "$DATABASE_URL" | sed -e 's/localhost/host.docker.internal/' -e 's/127\.0\.0\.1/host.docker.internal/')
    host_dir=$(dirname "$sql_file")
    # Git Bash rewrites anything that looks like a unix path in a command line,
    # so the container-side /sql becomes C:/Program Files/Git/sql and the mount
    # source needs to be a Windows path. cygpath exists only there, so this is a
    # no-op on macOS and Linux.
    if command -v cygpath >/dev/null 2>&1; then
      host_dir=$(cygpath -m "$host_dir")
    fi
    MSYS_NO_PATHCONV=1 docker run --rm -e PGCLIENTENCODING=UTF8 -v "$host_dir:/sql:ro" postgres:16       psql "$url" -v ON_ERROR_STOP=1 "$@" -f "/sql/$(basename "$sql_file")"
  else
    PGCLIENTENCODING=UTF8 psql "$DATABASE_URL" -v ON_ERROR_STOP=1 "$@" -f "$sql_file"
  fi
}

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
# Casting the boolean to text yields 'true' / 'false', NOT 't' / 'f'; the short
# forms are psql's aligned DISPLAY, not the value. Comparing against 't' made
# this check fail even on a database that had the tables.
printf "SELECT (to_regclass('public.tinder_decks') IS NOT NULL AND to_regclass('public.tinder_entries') IS NOT NULL)::text;
" > "$WORKDIR/check.sql"
CHECK=$(run_psql "$WORKDIR/check.sql" -tA 2>&1) || {
  echo "Could not connect to the database or run the check query."
  echo "$CHECK"
  exit 1
}
CHECK_TRIMMED=$(echo "$CHECK" | tr -d '[:space:]')
if [ "$CHECK_TRIMMED" != "true" ]; then
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
  (SELECT count(*) FROM lists m WHERE m.name = d.target_list_name) AS lists_with_that_name,
  CASE WHEN td.id IS NULL THEN 'insert' ELSE 'update' END AS deck_action
FROM input_decks d
LEFT JOIN LATERAL (
  SELECT id FROM lists WHERE name = d.target_list_name ORDER BY created_at ASC, id ASC LIMIT 1
) l ON true
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
run_psql "$WORKDIR/dryrun.sql"

echo ""
printf 'Proceed with these writes? [y/N] '
read -r CONFIRM
case "$CONFIRM" in
  y|Y|yes|YES) ;;
  *) echo "Nothing written."; exit 0 ;;
esac

cat > "$WORKDIR/apply_query.sql" <<'SQL'
BEGIN;

-- LATERAL ... LIMIT 1 rather than a plain join on the name: lists.name is NOT
-- unique, and a duplicate name would emit two rows for one deck key, which
-- makes ON CONFLICT DO UPDATE fail outright with "cannot affect row a second
-- time". Oldest-first is the deterministic pick, and the dry run above prints
-- how many lists share each name so an ambiguous one is visible before writing.
INSERT INTO tinder_decks (key, display_name, target_list_id, recycle_mode, dataset_key, active)
SELECT d.key, d.display_name, l.id, d.recycle_mode, d.dataset_key, true
FROM input_decks d
JOIN LATERAL (
  SELECT id FROM lists WHERE name = d.target_list_name ORDER BY created_at ASC, id ASC LIMIT 1
) l ON true
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
run_psql "$WORKDIR/apply.sql"

# Verify, and fail LOUDLY on a shortfall. A deck whose target list does not
# resolve is skipped by the INSERT rather than erroring, so without this the
# script can report success having written half the data. That is not
# hypothetical: a codepage bug in the PowerShell twin did exactly that once,
# dropping the two decks whose list names carry Danish letters.
EXPECTED_ENTRIES=$(jq -s 'map(.entries | length) | add' $DECK_FILES)
printf "SELECT (SELECT count(*) FROM tinder_decks WHERE dataset_key IS NOT NULL) || ' ' || (SELECT count(*) FROM tinder_entries e JOIN tinder_decks d ON d.id = e.deck_id WHERE d.dataset_key IS NOT NULL);
" > "$WORKDIR/counts.sql"
COUNTS=$(run_psql "$WORKDIR/counts.sql" -tA | tr -d '
')
GOT_DECKS=$(echo "$COUNTS" | awk '{print $1}')
GOT_ENTRIES=$(echo "$COUNTS" | awk '{print $2}')
echo ""
echo "Seeded decks: $GOT_DECKS (expected at least 4)"
echo "Seeded entries: $GOT_ENTRIES (expected at least $EXPECTED_ENTRIES)"
if [ "$GOT_DECKS" -lt 4 ] || [ "$GOT_ENTRIES" -lt "$EXPECTED_ENTRIES" ]; then
  echo ""
  echo "SHORTFALL: fewer rows landed than the files hold."
  echo "The usual cause is a deck whose target list does not exist by that exact"
  echo "name. Re-run the dry run and check the list_status column."
  exit 1
fi

echo "Done."
