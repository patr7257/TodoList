# Seeds (or updates) a TodoList login account in the shared Neon Postgres, using
# the API's own scrypt hashing so the account can sign in on web and desktop.
# Run from anywhere: cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\seed-user.ps1
# Prompts for the Neon DATABASE_URL (unpooled) if it is not already in the env,
# then for email / name / password. Re-running with the same email resets it.

Set-Location (Join-Path $PSScriptRoot '..')

$jar = 'api/target/todolist-api.jar'
if (-not (Test-Path $jar)) {
  Write-Host 'Building the api jar (first run only)...' -ForegroundColor Cyan
  mvn -q -pl api -am -DskipTests package
  if ($LASTEXITCODE -ne 0) { Write-Host 'Maven build failed; fix that first.' -ForegroundColor Red; return }
}

$setByUs = $false
if (-not $env:DATABASE_URL) {
  $env:DATABASE_URL = Read-Host 'Neon DATABASE_URL (unpooled)'
  $setByUs = $true
}

try {
  & java -cp $jar dk.dtu.api.tools.SeedUser
} finally {
  # Never leave a pasted secret behind in this shell.
  if ($setByUs) { Remove-Item Env:\DATABASE_URL -ErrorAction SilentlyContinue }
}
