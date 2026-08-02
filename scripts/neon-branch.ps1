# Create or delete a disposable Neon branch of the TodoList production database.
#
# Why this exists: some things can only be verified against real data, above all
# the lists.owner free-text to owner_id backfill (does every existing owner
# string actually match exactly one user?). A Neon branch is a copy-on-write
# clone: cheap, isolated, and deletable, so migrations and backfills can be
# rehearsed on production-shaped data without touching production.
#
# Usage (from anywhere):
#   .\scripts\neon-branch.ps1                       create branch (default name below)
#   .\scripts\neon-branch.ps1 -Name my-test         create with a chosen name
#   .\scripts\neon-branch.ps1 -Delete               delete the default-named branch
#   .\scripts\neon-branch.ps1 -Delete -Name my-test delete a specific branch
#   .\scripts\neon-branch.ps1 -List                 list existing branches
#
# It prompts once for a Neon API key (create one at
# `https://console.neon.tech/app/settings/api-keys`). The key is never written to
# disk and never left in your shell.
#
# NEVER point a local API at the production branch: API startup runs Flyway, so
# it would apply unmerged migrations to production.

[CmdletBinding()]
param(
    [string] $Name = 'todolist-smoke',
    [switch] $Delete,
    [switch] $List
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Set-Location (Join-Path $PSScriptRoot '..')

$apiRoot = 'https://console.neon.tech/api/v2'

$secure = Read-Host -Prompt 'Neon API key (hidden, not stored)' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$key = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
if ([string]::IsNullOrWhiteSpace($key)) {
    Write-Host 'No API key entered. Nothing done.' -ForegroundColor Yellow
    exit 1
}
$headers = @{ Authorization = "Bearer $key"; Accept = 'application/json' }

function Invoke-Neon {
    param([string] $Method, [string] $Path, $Body)
    $uri = "$apiRoot$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
    }
    $json = $Body | ConvertTo-Json -Depth 8
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $json -ContentType 'application/json'
}

try {
    $projects = (Invoke-Neon -Method GET -Path '/projects').projects
} catch {
    Write-Host 'Could not list Neon projects. Is the API key valid?' -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}

if (-not $projects -or $projects.Count -eq 0) {
    Write-Host 'No Neon projects on this account.' -ForegroundColor Red
    exit 1
}

if ($projects.Count -eq 1) {
    $project = $projects[0]
} else {
    Write-Host ''
    Write-Host 'Neon projects:'
    for ($i = 0; $i -lt $projects.Count; $i++) {
        Write-Host ("  [{0}] {1}  ({2})" -f $i, $projects[$i].name, $projects[$i].id)
    }
    $pick = Read-Host -Prompt 'Which project number holds the TodoList database?'
    $idx = 0
    if (-not [int]::TryParse($pick, [ref] $idx)) {
        Write-Host 'Not a number. Nothing done.' -ForegroundColor Yellow
        exit 1
    }
    if ($idx -lt 0 -or $idx -ge $projects.Count) {
        Write-Host 'Out of range. Nothing done.' -ForegroundColor Yellow
        exit 1
    }
    $project = $projects[$idx]
}
Write-Host ("Project: {0} ({1})" -f $project.name, $project.id)

$branches = (Invoke-Neon -Method GET -Path "/projects/$($project.id)/branches").branches

if ($List) {
    Write-Host ''
    Write-Host 'Branches:'
    foreach ($b in $branches) {
        $flag = ''
        if ($b.default -eq $true) { $flag = '  (default / production)' }
        Write-Host ("  {0}{1}   id={2}  created={3}" -f $b.name, $flag, $b.id, $b.created_at)
    }
    exit 0
}

$existing = $branches | Where-Object { $_.name -eq $Name }

if ($Delete) {
    if (-not $existing) {
        Write-Host "No branch named '$Name'. Nothing to delete."
        exit 0
    }
    if ($existing.default -eq $true) {
        Write-Host "Refusing to delete '$Name': it is the DEFAULT (production) branch." -ForegroundColor Red
        exit 1
    }
    Write-Host ''
    Write-Host "About to DELETE Neon branch '$Name' (id $($existing.id)) in project $($project.name)."
    $confirm = Read-Host -Prompt "Type the branch name to confirm"
    if ($confirm -ne $Name) {
        Write-Host 'Name did not match. Nothing deleted.' -ForegroundColor Yellow
        exit 1
    }
    Invoke-Neon -Method DELETE -Path "/projects/$($project.id)/branches/$($existing.id)" | Out-Null
    Write-Host "Deleted branch '$Name'." -ForegroundColor Green
    exit 0
}

if ($existing) {
    Write-Host "Branch '$Name' already exists (id $($existing.id)); reusing it."
    $branchId = $existing.id
} else {
    $default = $branches | Where-Object { $_.default -eq $true }
    if (-not $default) {
        Write-Host 'Could not find the default branch to clone from.' -ForegroundColor Red
        exit 1
    }
    Write-Host "Creating branch '$Name' from default branch '$($default.name)' ..."
    $body = @{
        branch    = @{ name = $Name; parent_id = $default.id }
        endpoints = @(@{ type = 'read_write' })
    }
    $created = Invoke-Neon -Method POST -Path "/projects/$($project.id)/branches" -Body $body
    $branchId = $created.branch.id
    Write-Host "Created branch id $branchId." -ForegroundColor Green
}

# Unpooled connection string: Dokploy and these scripts use the direct endpoint,
# not the pooler.
$dbs = (Invoke-Neon -Method GET -Path "/projects/$($project.id)/branches/$branchId/databases").databases
$roles = (Invoke-Neon -Method GET -Path "/projects/$($project.id)/branches/$branchId/roles").roles
$dbName = $dbs[0].name
$roleName = $roles[0].name

$uri = (Invoke-Neon -Method GET -Path ("/projects/{0}/connection_uri?branch_id={1}&database_name={2}&role_name={3}&pooled=false" -f $project.id, $branchId, $dbName, $roleName)).uri

Write-Host ''
Write-Host 'Branch connection string (UNPOOLED). Paste into the terminal that runs the API:' -ForegroundColor Green
Write-Host ''
Write-Host "`$env:DATABASE_URL='$uri'; `$env:TODO_SESSION_SECRET='smoke-secret'; mvn -pl api exec:java"
Write-Host ''
Write-Host 'This is a CLONE. Writes here never reach production. Delete it when finished:'
Write-Host "  .\scripts\neon-branch.ps1 -Delete -Name $Name"
Write-Host ''

# Never leave the key behind in this session.
Remove-Variable key -ErrorAction SilentlyContinue
$headers = $null
