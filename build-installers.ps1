Param(
  [string]$Version = "1.0.0",
  [ValidateSet("msi","exe")][string]$Type = "msi"
)

$ErrorActionPreference = "Stop"

function Require-Command([string]$cmd) {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
    throw "Missing required command: $cmd"
  }
}

Require-Command "mvn"
Require-Command "jpackage"

Write-Host "Building Maven modules..." -ForegroundColor Green
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
  throw "Maven build failed"
}

$dist = Join-Path $PSScriptRoot "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$serverTarget = Join-Path $PSScriptRoot "server\target"
$clientTarget = Join-Path $PSScriptRoot "client\target"

$serverJar = "todolist-server-ui.jar"
$clientJar = "todolist-client-ui.jar"

Write-Host "Packaging Server UI ($Type)..." -ForegroundColor Green
jpackage `
  --dest $dist `
  --input $serverTarget `
  --name "TodoList Server" `
  --main-jar $serverJar `
  --main-class dk.dtu.ServerApp `
  --type $Type `
  --app-version $Version `
  --vendor "Patrick" `
  --description "TodoList Management Server" `
  --win-menu `
  --win-shortcut `
  --java-options "-Dtodolist.data.dir=%APPDATA%\\TodoList" `
  --java-options "-Dtodolist.port=9001"

Write-Host "Packaging Client UI ($Type)..." -ForegroundColor Green
jpackage `
  --dest $dist `
  --input $clientTarget `
  --name "TodoList Client" `
  --main-jar $clientJar `
  --main-class dk.dtu.ClientApp `
  --type $Type `
  --app-version $Version `
  --vendor "Patrick" `
  --description "TodoList Management Client" `
  --win-menu `
  --win-shortcut `
  --java-options "-Dtodolist.server.ip=127.0.0.1" `
  --java-options "-Dtodolist.port=9001"

Write-Host "Done. Output is in: $dist" -ForegroundColor Yellow
Write-Host "NOTE: If you choose MSI, Windows may require WiX Toolset installed." -ForegroundColor Yellow
