# Diagnose installed TodoList applications
# Run this script to check if the installers bundled everything correctly

$ErrorActionPreference = "Continue"

Write-Host "=== TodoList Installation Diagnostics ===" -ForegroundColor Cyan
Write-Host ""

function Test-Installation {
    param(
        [string]$AppName,
        [string]$InstallPath
    )
    
    Write-Host "Checking $AppName..." -ForegroundColor Yellow
    
    if (-not (Test-Path $InstallPath)) {
        Write-Host "  [ERROR] Not installed at: $InstallPath" -ForegroundColor Red
        return
    }
    
    Write-Host "  [OK] Found installation directory" -ForegroundColor Green
    
    # Check for runtime
    $runtimePath = Join-Path $InstallPath "runtime"
    if (Test-Path $runtimePath) {
        Write-Host "  [OK] Runtime directory exists" -ForegroundColor Green
        
        $javaExe = Join-Path $runtimePath "bin\java.exe"
        if (Test-Path $javaExe) {
            Write-Host "  [OK] java.exe found" -ForegroundColor Green
        } else {
            Write-Host "  [ERROR] java.exe NOT FOUND!" -ForegroundColor Red
        }
    } else {
        Write-Host "  [ERROR] Runtime directory MISSING!" -ForegroundColor Red
        Write-Host "          This means the installer didn't bundle a JRE" -ForegroundColor Red
    }
    
    # Check for app directory
    $appPath = Join-Path $InstallPath "app"
    if (Test-Path $appPath) {
        Write-Host "  [OK] App directory exists" -ForegroundColor Green
        
        $jars = Get-ChildItem -Path $appPath -Filter "*.jar" | Select-Object -First 5
        Write-Host "  [INFO] JARs found in app directory:" -ForegroundColor Cyan
        foreach ($jar in $jars) {
            Write-Host "         - $($jar.Name)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  [ERROR] App directory MISSING!" -ForegroundColor Red
    }
    
    # Check for executable
    $exePath = Join-Path $InstallPath "$AppName.exe"
    if (Test-Path $exePath) {
        Write-Host "  [OK] Executable found: $AppName.exe" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] Executable NOT FOUND!" -ForegroundColor Red
    }
    
    Write-Host ""
}

# Check Server
Test-Installation -AppName "TodoList Server" -InstallPath "C:\Program Files\TodoList Server"

# Check Client
Test-Installation -AppName "TodoList Client" -InstallPath "C:\Program Files\TodoList Client"

Write-Host "=== Next Steps ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. If runtime is MISSING:" -ForegroundColor Yellow
Write-Host "   The GitHub Actions workflow needs fixing (missing --runtime-image)" -ForegroundColor White
Write-Host ""
Write-Host "2. If app directory is MISSING:" -ForegroundColor Yellow
Write-Host "   The dependency staging step failed" -ForegroundColor White
Write-Host ""
Write-Host "3. To see actual runtime errors:" -ForegroundColor Yellow
Write-Host "   Run the executable from PowerShell:" -ForegroundColor White
Write-Host "   cd 'C:\Program Files\TodoList Server'" -ForegroundColor Gray
Write-Host "   .'TodoList Server.exe'" -ForegroundColor Gray
Write-Host ""
Write-Host "4. Check Windows Event Viewer:" -ForegroundColor Yellow
Write-Host "   Win+X → Event Viewer → Windows Logs → Application" -ForegroundColor White
Write-Host ""
