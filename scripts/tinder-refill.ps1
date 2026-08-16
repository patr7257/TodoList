# Posts a batch of TodoTinder cards to the authenticated refill import endpoint
# (issue #59). This is the ONE line a drained deck's refill prompt hands over:
#   cd "C:\Users\pr\repos\1-Personal\TodoList"; .\scripts\tinder-refill.ps1
#
# It exists so the prompt never has to contain a token. A Claude session writes
# the batch to a JSON file and stops there; this script prompts for the session
# token, shows what would be sent, asks before sending, and never leaves the
# token behind in the shell.
#
# The file it reads is exactly what the endpoint takes:
#   {"source":"claude-refill","entries":[{"text":"...","metadata":{}}]}

Set-Location (Join-Path $PSScriptRoot '..')

# EAP stays Continue on purpose: this script calls native-ish web cmdlets and a
# harmless banner on stderr must not kill the run mid-flight.
$ErrorActionPreference = 'Continue'

$defaultApi = 'https://api.todolist.patrickrobel.dk'
$apiBase = Read-Host "API base URL (Enter for $defaultApi)"
if ([string]::IsNullOrWhiteSpace($apiBase)) { $apiBase = $defaultApi }
$apiBase = $apiBase.TrimEnd('/')

$deck = Read-Host 'Deck key (for example: aktiviteter)'
if ([string]::IsNullOrWhiteSpace($deck)) { Write-Host 'No deck key given, nothing to do.' -ForegroundColor Yellow; return }

$defaultFile = 'refill.json'
$file = Read-Host "Path to the entries JSON file (Enter for $defaultFile)"
if ([string]::IsNullOrWhiteSpace($file)) { $file = $defaultFile }
if (-not (Test-Path $file)) { Write-Host "No such file: $file" -ForegroundColor Red; return }

$payload = Get-Content -Raw -Encoding UTF8 $file
try {
  $parsed = $payload | ConvertFrom-Json
} catch {
  Write-Host "That file is not valid JSON: $($_.Exception.Message)" -ForegroundColor Red
  return
}
if ($null -eq $parsed.entries) {
  Write-Host 'The file has no "entries" array. Expected {"source":"...","entries":[...]}.' -ForegroundColor Red
  return
}

$count = @($parsed.entries).Count
$url = "$apiBase/api/todo/tinder/decks/$deck/entries"

# Dry run first: show exactly what is about to happen, then ask.
Write-Host ''
Write-Host 'About to send:' -ForegroundColor Cyan
Write-Host "  endpoint : POST $url"
Write-Host "  file     : $file"
Write-Host "  entries  : $count"
Write-Host "  first    : $(@($parsed.entries)[0].text)"
Write-Host ''
Write-Host 'Cards already in the deck are skipped by the endpoint, not duplicated.' -ForegroundColor DarkGray

$go = Read-Host 'Send it? (y/N)'
if ($go -ne 'y' -and $go -ne 'Y') { Write-Host 'Cancelled, nothing was sent.' -ForegroundColor Yellow; return }

$secure = Read-Host 'TodoList session token (input hidden)' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
  if ([string]::IsNullOrWhiteSpace($token)) { Write-Host 'No token given, nothing was sent.' -ForegroundColor Yellow; return }

  $headers = @{ Authorization = "Bearer $token" }
  try {
    $result = Invoke-RestMethod -Method Post -Uri $url -Headers $headers `
      -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($payload))
  } catch {
    Write-Host "Import failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
      $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
      Write-Host $reader.ReadToEnd() -ForegroundColor Red
    }
    return
  }

  Write-Host ''
  Write-Host "Deck '$($result.deck)': received $($result.received), inserted $($result.inserted), skipped $($result.skipped). The deck now holds $($result.total) cards." -ForegroundColor Green
} finally {
  # Never leave a pasted secret behind, in this shell or in memory we control.
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  Remove-Variable token -ErrorAction SilentlyContinue
}
