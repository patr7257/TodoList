# Throwaway local Postgres for TodoList API development and smoke testing.
#
# Why this exists: `mvn -pl api exec:java` does NOT start an embedded Postgres.
# The embedded Postgres in this repo is a TEST-scope dependency used only by the
# api module's integration tests. With no DATABASE_URL the API starts but every
# data route answers 503, so any real local run needs a real database. Pointing
# a local API at production Neon is not an option either: startup runs Flyway,
# which would apply unmerged migrations to production.
#
# Usage (from anywhere):
#   .\scripts\dev-db.ps1              start (or reuse) the container, print DATABASE_URL
#   .\scripts\dev-db.ps1 -Fixture     also load scripts/dev-db-fixture.sql
#   .\scripts\dev-db.ps1 -Reset       delete the volume and start clean
#   .\scripts\dev-db.ps1 -Stop        stop and remove the container
#
# -Fixture requires the API to have started once already, because Flyway (inside
# the API) creates the schema. The script says so if the tables are missing.
#
# Requires Docker Desktop to be RUNNING.

[CmdletBinding()]
param(
    [switch] $Stop,
    [switch] $Reset,
    [switch] $Fixture,
    [int] $Port = 5433
)

# Keep Continue, not Stop: native CLIs (docker) write harmless banners to stderr
# and with EAP=Stop a banner would kill the script. Native failures are detected
# via $LASTEXITCODE instead.
$ErrorActionPreference = 'Continue'

Set-Location (Join-Path $PSScriptRoot '..')

$container = 'todolist-dev-db'
$volume = 'todolist-dev-db-data'
$image = 'postgres:16'
$dbUser = 'postgres'
$dbPass = 'todo'
$dbName = 'todo'
$dbUrl = "postgres://${dbUser}:${dbPass}@localhost:${Port}/${dbName}"

function Test-DockerRunning {
    docker version --format '{{.Server.Version}}' > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host ''
        Write-Host 'Docker is not running.' -ForegroundColor Red
        Write-Host 'Start Docker Desktop, wait until it says "Engine running", then run this script again.'
        return $false
    }
    return $true
}

if (-not (Test-DockerRunning)) { exit 1 }

if ($Stop) {
    Write-Host "Stopping and removing container $container ..."
    cmd /c "docker rm -f $container >nul 2>&1"
    Write-Host 'Done. The data volume is kept; pass -Reset next start to wipe it.'
    Write-Host "Remember to clear the env var in any shell that set it: Remove-Item Env:\DATABASE_URL"
    exit 0
}

if ($Reset) {
    Write-Host 'Reset requested: removing container and data volume ...'
    cmd /c "docker rm -f $container >nul 2>&1"
    cmd /c "docker volume rm $volume >nul 2>&1"
}

# Is it already up?
$running = docker ps --filter "name=^/$container$" --format '{{.Names}}'
if ($running -eq $container) {
    Write-Host "Container $container is already running."
} else {
    # Exists but stopped?
    $existing = docker ps -a --filter "name=^/$container$" --format '{{.Names}}'
    if ($existing -eq $container) {
        Write-Host "Starting existing container $container ..."
        docker start $container > $null
        if ($LASTEXITCODE -ne 0) { Write-Host 'docker start failed.' -ForegroundColor Red; exit 1 }
    } else {
        Write-Host "Creating container $container on port $Port ..."
        docker run -d --name $container `
            -e "POSTGRES_PASSWORD=$dbPass" `
            -e "POSTGRES_DB=$dbName" `
            -v "${volume}:/var/lib/postgresql/data" `
            -p "${Port}:5432" `
            $image > $null
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'docker run failed. Is port ' -NoNewline -ForegroundColor Red
            Write-Host "$Port already taken? Try -Port 5434." -ForegroundColor Red
            exit 1
        }
    }
}

# One bounded readiness loop, no blind sleeps.
Write-Host 'Waiting for Postgres to accept connections ...'
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    cmd /c "docker exec $container pg_isready -U $dbUser -d $dbName >nul 2>&1"
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 700
}
if (-not $ready) {
    Write-Host "Postgres did not become ready. Inspect it with: docker logs $container" -ForegroundColor Red
    exit 1
}
Write-Host 'Postgres is ready.'

if ($Fixture) {
    $hasSchema = $false
    cmd /c "docker exec $container psql -U $dbUser -d $dbName -c ""SELECT 1 FROM users LIMIT 1"" >nul 2>&1"
    if ($LASTEXITCODE -eq 0) { $hasSchema = $true }

    if (-not $hasSchema) {
        Write-Host ''
        Write-Host 'The schema does not exist yet, so the fixture cannot load.' -ForegroundColor Yellow
        Write-Host 'Flyway runs inside the API, so start the API once first:'
        Write-Host ''
        Write-Host "  `$env:DATABASE_URL='$dbUrl'; `$env:TODO_SESSION_SECRET='dev-secret'; mvn -pl api exec:java"
        Write-Host ''
        Write-Host 'Stop it once it logs that it is listening, then re-run this script with -Fixture.'
    } else {
        Write-Host 'Loading scripts/dev-db-fixture.sql ...'
        Get-Content -Raw 'scripts/dev-db-fixture.sql' | docker exec -i $container psql -U $dbUser -d $dbName -v ON_ERROR_STOP=1
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'Fixture load failed.' -ForegroundColor Red
            exit 1
        }
        Write-Host 'Fixture loaded.'
    }
}

Write-Host ''
Write-Host 'Local database ready. Paste this into the terminal that runs the API:' -ForegroundColor Green
Write-Host ''
Write-Host "`$env:DATABASE_URL='$dbUrl'; `$env:TODO_SESSION_SECRET='dev-secret'; mvn -pl api exec:java"
Write-Host ''
Write-Host 'Then in another terminal, for the desktop client:'
Write-Host ''
Write-Host '  mvn -pl client javafx:run'
Write-Host ''
Write-Host 'Create a login account against this database with:  .\scripts\seed-user.ps1'
Write-Host "(enter $dbUrl at its prompt)"
Write-Host ''
Write-Host 'Tear down when finished:  .\scripts\dev-db.ps1 -Stop'
