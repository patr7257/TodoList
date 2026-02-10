# Test the installed TodoList Server and capture error output

$ErrorActionPreference = "Continue"

Write-Host "=== Testing Installed TodoList Server ===" -ForegroundColor Cyan
Write-Host ""

$installPath = "C:\Program Files\TodoList Server"
$exePath = Join-Path $installPath "TodoList Server.exe"

if (-not (Test-Path $exePath)) {
    Write-Host "[ERROR] TodoList Server not found at: $exePath" -ForegroundColor Red
    Write-Host "Please install it first." -ForegroundColor Yellow
    exit 1
}

Write-Host "[OK] Found executable" -ForegroundColor Green
Write-Host "Path: $exePath" -ForegroundColor Gray
Write-Host ""

# Check runtime
$runtimeJava = Join-Path $installPath "runtime\bin\java.exe"
if (Test-Path $runtimeJava) {
    Write-Host "[OK] runtime\bin\java.exe exists" -ForegroundColor Green
    Write-Host "Testing java.exe..." -ForegroundColor Cyan
    & $runtimeJava -version
} else {
    Write-Host "[ERROR] runtime\bin\java.exe NOT FOUND!" -ForegroundColor Red
    Write-Host "This means jpackage didn't bundle the runtime correctly." -ForegroundColor Yellow
    
    # Check if runtime directory has anything
    $runtimeDir = Join-Path $installPath "runtime"
    if (Test-Path $runtimeDir) {
        Write-Host "`nRuntime directory exists. Contents:" -ForegroundColor Cyan
        Get-ChildItem $runtimeDir | ForEach-Object {
            Write-Host "  - $($_.Name)" -ForegroundColor Gray
        }
    }
}

Write-Host ""
Write-Host "=== Attempting to run TodoList Server ===" -ForegroundColor Cyan
Write-Host "With --win-console enabled, you should see a console window with errors..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop after seeing the error." -ForegroundColor Yellow
Write-Host ""

# Change to install directory and run
Push-Location $installPath
try {
    & ".\TodoList Server.exe"
} catch {
    Write-Host "`n[ERROR] Exception caught:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "=== Check Windows Event Viewer for more details ===" -ForegroundColor Cyan
Write-Host "Win+X → Event Viewer → Windows Logs → Application" -ForegroundColor White
