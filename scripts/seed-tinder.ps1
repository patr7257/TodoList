# Seeds the four TodoTinder decks (scripts/data/tinder-*.json) into Postgres.
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
# Usage (from anywhere):
#   .\scripts\seed-tinder.ps1
#
# Prompts for DATABASE_URL if it is not already in the environment (never pass
# it as an argument), shows a DRY RUN of what would be inserted / updated /
# skipped, then asks for confirmation before writing anything. Never deletes an
# existing entry.
#
# Requires the PostgreSQL client (psql) on PATH.

$ErrorActionPreference = 'Continue'

Set-Location (Join-Path $PSScriptRoot '..')

$deckFiles = @(
    'scripts/data/tinder-aktiviteter.json',
    'scripts/data/tinder-rejsemaal.json',
    'scripts/data/tinder-indkoeb.json',
    'scripts/data/tinder-datenights.json'
)

$psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psqlCmd) {
    Write-Host 'psql was not found on PATH.' -ForegroundColor Red
    Write-Host 'Install the PostgreSQL client tools (they ship psql) and try again.'
    exit 1
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

$tempFiles = @()

try {
    # --- Check the tinder_* tables exist before doing anything else ---
    $checkSql = "SELECT (to_regclass('public.tinder_decks') IS NOT NULL AND to_regclass('public.tinder_entries') IS NOT NULL)::text;"
    $checkOut = & psql $env:DATABASE_URL -tAc $checkSql 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Could not connect to the database or run the check query.' -ForegroundColor Red
        Write-Host ($checkOut -join "`n")
        exit 1
    }
    $checkVal = ($checkOut -join '').Trim()
    if ($checkVal -ne 't') {
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
"@

    $applyQuery = @"
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
"@

    $dryRunFile = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "todolist-tinder-dryrun-$([guid]::NewGuid().ToString('N')).sql")
    $applyFile = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "todolist-tinder-apply-$([guid]::NewGuid().ToString('N')).sql")
    $tempFiles += $dryRunFile, $applyFile

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($dryRunFile, ($setupSql + "`n" + $dryRunQuery), $utf8NoBom)
    [System.IO.File]::WriteAllText($applyFile, ($setupSql + "`n" + $applyQuery), $utf8NoBom)

    Write-Host ''
    Write-Host 'DRY RUN: nothing is written yet.' -ForegroundColor Cyan
    Write-Host ''
    & psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f $dryRunFile
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
    & psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f $applyFile
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Seeding failed. See the psql output above.' -ForegroundColor Red
        exit 1
    }
    Write-Host 'Done.' -ForegroundColor Green
} finally {
    foreach ($t in $tempFiles) {
        if (Test-Path $t) { Remove-Item $t -ErrorAction SilentlyContinue }
    }
    # Never leave a pasted secret behind in this shell.
    if ($setByUs) { Remove-Item Env:\DATABASE_URL -ErrorAction SilentlyContinue }
}
