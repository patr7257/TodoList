# Seeds the four TodoTinder decks (scripts/data/tinder-*.json) into Postgres.
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
# The four target lists (Aktiviteter, Rejsemål, Indkøb, Date nights) must
# already exist by NAME. A deck whose list is missing is reported in the dry run
# and skipped rather than being pointed at nothing.
#
# Usage (from anywhere):
#   .\scripts\seed-tinder.ps1
#
# Prompts for DATABASE_URL if it is not already in the environment (never pass
# it as an argument), shows a DRY RUN of what would be inserted / updated /
# skipped, then asks for confirmation before writing anything. Never deletes an
# existing entry.
#
# Uses psql if it is installed, otherwise borrows one from a throwaway docker
# container, so nothing has to be installed on a machine that already runs the
# dev database in Docker.

$ErrorActionPreference = 'Continue'

Set-Location (Join-Path $PSScriptRoot '..')

$deckFiles = @(
    'scripts/data/tinder-aktiviteter.json',
    'scripts/data/tinder-rejsemaal.json',
    'scripts/data/tinder-indkoeb.json',
    'scripts/data/tinder-datenights.json'
)

# How to reach psql. A local client is preferred, but this machine deliberately
# runs Postgres only in Docker (see dev-db.ps1) and has no PostgreSQL client
# installed, so requiring one on PATH would make this script unrunnable exactly
# where it is meant to be run. The fallback borrows psql from a throwaway
# container instead, which needs no install and works against any target,
# including Neon.
$psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
$useDocker = $false
if (-not $psqlCmd) {
    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCmd) {
        Write-Host 'Neither psql nor docker was found on PATH.' -ForegroundColor Red
        Write-Host 'Install the PostgreSQL client tools, or start Docker Desktop, and try again.'
        exit 1
    }
    $useDocker = $true
    Write-Host 'psql is not installed locally, borrowing one from a docker container.' -ForegroundColor Yellow
}

$env:PGCLIENTENCODING = 'UTF8'

# Every deck name and most entry text contains æ, ø or å, so how the SQL reaches
# psql is not a detail. It is NEVER piped: PowerShell encodes a pipe into a
# native process using the console codepage, which varies by how the script was
# launched, and under the wrong one "Indkøb" arrives as mojibake, its target
# list lookup matches nothing, and the seed silently drops that deck while still
# printing success. That happened, and it is what the shortfall check at the end
# exists to catch. Instead the SQL goes through a UTF-8 file with no BOM, which
# no codepage can touch; the docker path mounts the directory holding it.
$sqlDir = Join-Path ([System.IO.Path]::GetTempPath()) "todolist-tinder-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $sqlDir | Out-Null

# Runs a SQL script (given as a string) and returns psql's output. $LASTEXITCODE
# is meaningful afterwards either way.
function Invoke-Psql {
    param([string] $Sql, [switch] $Quiet)
    $name = "q-$([guid]::NewGuid().ToString('N')).sql"
    $path = Join-Path $sqlDir $name
    [System.IO.File]::WriteAllText($path, $Sql, (New-Object System.Text.UTF8Encoding($false)))
    try {
        if ($useDocker) {
            # localhost inside a container is the CONTAINER, not this machine, so
            # a local dev URL has to be rewritten or the connection silently goes
            # nowhere. Neon and any other remote host are unaffected.
            $url = $env:DATABASE_URL -replace 'localhost', 'host.docker.internal' -replace '127\.0\.0\.1', 'host.docker.internal'
            if ($Quiet) {
                return (& docker run --rm -e PGCLIENTENCODING=UTF8 -v "${sqlDir}:/sql:ro" postgres:16 psql $url -v ON_ERROR_STOP=1 -tA -f "/sql/$name" 2>&1)
            }
            return (& docker run --rm -e PGCLIENTENCODING=UTF8 -v "${sqlDir}:/sql:ro" postgres:16 psql $url -v ON_ERROR_STOP=1 -f "/sql/$name" 2>&1)
        }
        if ($Quiet) {
            return (& psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -tA -f $path 2>&1)
        }
        return (& psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f $path 2>&1)
    } finally {
        Remove-Item $path -ErrorAction SilentlyContinue
    }
}

foreach ($f in $deckFiles) {
    if (-not (Test-Path $f)) {
        Write-Host "Missing dataset file: $f" -ForegroundColor Red
        exit 1
    }
}

$setByUs = $false
if (-not $env:DATABASE_URL) {
    $env:DATABASE_URL = Read-Host 'DATABASE_URL (Postgres connection string)'
    $setByUs = $true
}

try {
    # --- Check the tinder_* tables exist before doing anything else ---
    # Casting the boolean to text yields 'true' / 'false', NOT 't' / 'f'; the
    # short forms are psql's ALIGNED display, not the value. Comparing against
    # 't' made this check fail even on a database that had the tables.
    $checkSql = "SELECT (to_regclass('public.tinder_decks') IS NOT NULL AND to_regclass('public.tinder_entries') IS NOT NULL)::text;"
    $checkOut = Invoke-Psql -Sql $checkSql -Quiet
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Could not connect to the database or run the check query.' -ForegroundColor Red
        Write-Host ($checkOut -join "`n")
        exit 1
    }
    $checkVal = ($checkOut -join '').Trim()
    if ($checkVal -ne 'true') {
        Write-Host ''
        Write-Host 'The tinder_decks / tinder_entries tables do not exist in this database yet.' -ForegroundColor Yellow
        Write-Host 'They come from the V8 migration (issue #56). Start the API once against this'
        Write-Host 'DATABASE_URL after that migration has merged, so Flyway creates the schema,'
        Write-Host 'then re-run this script.'
        exit 1
    }

    # --- Build the VALUES lists from the JSON files ---
    function SqlEscape([string] $s) {
        if ($null -eq $s) { return '' }
        return $s -replace "'", "''"
    }

    $deckLines = New-Object System.Collections.Generic.List[string]
    $entryLines = New-Object System.Collections.Generic.List[string]

    foreach ($f in $deckFiles) {
        $raw = Get-Content -Raw -Encoding UTF8 $f
        $json = $raw | ConvertFrom-Json
        $deck = $json.deck

        $deckLines.Add("('$(SqlEscape $deck.key)', '$(SqlEscape $deck.displayName)', '$(SqlEscape $deck.targetList)', '$(SqlEscape $deck.recycleMode)', '$(SqlEscape $deck.datasetKey)')")

        foreach ($e in $json.entries) {
            $metaJson = $e.metadata | ConvertTo-Json -Compress -Depth 6
            $entryLines.Add("('$(SqlEscape $deck.key)', '$(SqlEscape $e.text)', '$(SqlEscape $metaJson)'::jsonb)")
        }
    }

    $deckValuesSql = ($deckLines -join ",`n")
    $entryValuesSql = ($entryLines -join ",`n")

    $setupSql = @"
CREATE TEMP TABLE input_decks (key text, display_name text, target_list_name text, recycle_mode text, dataset_key text);
INSERT INTO input_decks (key, display_name, target_list_name, recycle_mode, dataset_key) VALUES
$deckValuesSql;

CREATE TEMP TABLE input_entries (deck_key text, entry_text text, metadata jsonb);
INSERT INTO input_entries (deck_key, entry_text, metadata) VALUES
$entryValuesSql;
"@

    $dryRunQuery = @"
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
"@

    $applyQuery = @"
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
"@

    Write-Host ''
    Write-Host 'DRY RUN: nothing is written yet.' -ForegroundColor Cyan
    Write-Host ''
    Invoke-Psql -Sql ($setupSql + "`n" + $dryRunQuery) | Write-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Dry run query failed. See the psql output above.' -ForegroundColor Red
        exit 1
    }

    Write-Host ''
    $confirm = Read-Host 'Proceed with these writes? [y/N]'
    if ($confirm -notmatch '^(y|yes)$') {
        Write-Host 'Nothing written.' -ForegroundColor Yellow
        exit 0
    }

    Write-Host ''
    Write-Host 'Writing ...' -ForegroundColor Cyan
    Invoke-Psql -Sql ($setupSql + "`n" + $applyQuery) | Write-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Seeding failed. See the psql output above.' -ForegroundColor Red
        exit 1
    }

    # Verify, and fail LOUDLY on a shortfall. A deck whose target list does not
    # resolve is skipped by the INSERT rather than erroring, so without this the
    # script can report success having written half the data. That is not
    # hypothetical: it is exactly what a codepage bug in the pipe did here once,
    # dropping the two decks whose list names carry Danish letters while still
    # printing "Done".
    $expectedDecks = $deckLines.Count
    $expectedEntries = $entryLines.Count
    $countSql = "SELECT (SELECT count(*) FROM tinder_decks WHERE dataset_key IS NOT NULL)::text || ' ' || (SELECT count(*) FROM tinder_entries e JOIN tinder_decks d ON d.id = e.deck_id WHERE d.dataset_key IS NOT NULL)::text;"
    $counts = (Invoke-Psql -Sql $countSql -Quiet) -join ''
    $parts = $counts.Trim() -split '\s+'
    if ($parts.Count -eq 2) {
        $gotDecks = [int] $parts[0]
        $gotEntries = [int] $parts[1]
        Write-Host ''
        Write-Host "Seeded decks: $gotDecks (expected at least $expectedDecks)"
        Write-Host "Seeded entries: $gotEntries (expected at least $expectedEntries)"
        if ($gotDecks -lt $expectedDecks -or $gotEntries -lt $expectedEntries) {
            Write-Host ''
            Write-Host 'SHORTFALL: fewer rows landed than the files hold.' -ForegroundColor Red
            Write-Host 'The usual cause is a deck whose target list does not exist by that exact'
            Write-Host 'name. Re-run the dry run and check the list_status column.'
            exit 1
        }
    }

    Write-Host 'Done.' -ForegroundColor Green
} finally {
    if (Test-Path $sqlDir) { Remove-Item $sqlDir -Recurse -Force -ErrorAction SilentlyContinue }
    # Never leave a pasted secret behind in this shell.
    if ($setByUs) { Remove-Item Env:\DATABASE_URL -ErrorAction SilentlyContinue }
}
