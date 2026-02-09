Param(
  [Parameter(Mandatory=$true)]
  [ValidateSet("client","server","both")]
  [string]$App = "server",
  [switch]$UninstallOnly
)

$ErrorActionPreference = "Stop"

# Check for admin privileges
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
  Write-Host "========================================" -ForegroundColor Red
  Write-Host "ADMIN PRIVILEGES REQUIRED" -ForegroundColor Red
  Write-Host "========================================" -ForegroundColor Red
  Write-Host "MSI installers require administrator privileges." -ForegroundColor Yellow
  Write-Host "Please run this script as Administrator:" -ForegroundColor Yellow
  Write-Host "  1. Right-click on PowerShell" -ForegroundColor Cyan
  Write-Host "  2. Select 'Run as Administrator'" -ForegroundColor Cyan
  Write-Host "  3. Navigate to: $PSScriptRoot" -ForegroundColor Cyan
  Write-Host "  4. Run: .\install-and-test.ps1 -App $App" -ForegroundColor Cyan
  Write-Host "" -ForegroundColor White
  Write-Host "Or use this command to restart as admin:" -ForegroundColor Yellow
  Write-Host "  Start-Process powershell -Verb RunAs -ArgumentList '-NoExit', '-Command', 'cd `"$PSScriptRoot`"; .\install-and-test.ps1 -App $App'" -ForegroundColor White
  exit 1
}

Write-Host "Running as Administrator: OK" -ForegroundColor Green
Write-Host ""

function Uninstall-TodoListApp([string]$appName) {
  Write-Host "`n=== Checking for existing $appName installation ===" -ForegroundColor Cyan
  
  # Method 1: Try using msiexec with product code
  $installedApp = Get-WmiObject -Class Win32_Product -ErrorAction SilentlyContinue | Where-Object { $_.Name -eq $appName }
  
  if ($installedApp) {
    Write-Host "Found existing installation: $appName" -ForegroundColor Yellow
    Write-Host "Uninstalling..." -ForegroundColor Yellow
    $installedApp.Uninstall() | Out-Null
    Write-Host "Uninstall completed" -ForegroundColor Green
    Start-Sleep -Seconds 2
  } else {
    Write-Host "No existing installation found for: $appName" -ForegroundColor Green
  }
  
  # Method 2: Check if installation directory exists and remove it
  $installDir = "C:\Program Files\$appName"
  if (Test-Path $installDir) {
    Write-Host "Installation directory still exists: $installDir" -ForegroundColor Yellow
    Write-Host "Removing manually..." -ForegroundColor Yellow
    try {
      Remove-Item -Path $installDir -Recurse -Force -ErrorAction Stop
      Write-Host "Removed installation directory" -ForegroundColor Green
    } catch {
      Write-Host "Could not remove installation directory: $_" -ForegroundColor Red
      Write-Host "You may need to remove it manually or reboot" -ForegroundColor Yellow
    }
  }
}

function Install-TodoListApp([string]$msiPath, [string]$appName) {
  Write-Host "`n=== Installing $appName ===" -ForegroundColor Green
  Write-Host "MSI: $msiPath" -ForegroundColor Cyan
  
  if (-not (Test-Path $msiPath)) {
    throw "MSI file not found: $msiPath"
  }
  
  # Install with logging
  $logFile = Join-Path $env:TEMP "$appName-install-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
  Write-Host "Installation log: $logFile" -ForegroundColor Cyan
  
  $msiArgs = "/i `"$msiPath`" /qn /norestart /L*v `"$logFile`""
  Write-Host "Running: msiexec.exe $msiArgs" -ForegroundColor Gray
  
  $process = Start-Process "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru
  
  if ($process.ExitCode -eq 0) {
    Write-Host "Installation successful!" -ForegroundColor Green
  } elseif ($process.ExitCode -eq 3010) {
    Write-Host "Installation successful (reboot required)" -ForegroundColor Yellow
  } else {
    Write-Host "Installation failed with exit code: $($process.ExitCode)" -ForegroundColor Red
    Write-Host "Check installation log: $logFile" -ForegroundColor Yellow
    throw "Installation failed"
  }
  
  # Wait for files to be written
  Start-Sleep -Seconds 3
  
  # Verify installation
  $exePath = "C:\Program Files\$appName\$appName.exe"
  if (Test-Path $exePath) {
    Write-Host "Verified: $exePath exists" -ForegroundColor Green
  } else {
    Write-Host "WARNING: Expected executable not found: $exePath" -ForegroundColor Red
  }
  
  return $logFile
}

function Test-TodoListApp([string]$appName) {
  Write-Host "`n=== Testing $appName ===" -ForegroundColor Green
  
  $exePath = "C:\Program Files\$appName\$appName.exe"
  
  if (-not (Test-Path $exePath)) {
    Write-Host "ERROR: Application not found at: $exePath" -ForegroundColor Red
    return $false
  }
  
  # Check runtime structure
  Write-Host "`nChecking runtime structure..." -ForegroundColor Cyan
  $installDir = Split-Path $exePath -Parent
  $runtimeBin = Join-Path $installDir "runtime\bin"
  
  if (Test-Path $runtimeBin) {
    Write-Host "  [OK] Runtime bin directory exists" -ForegroundColor Green
    
    $javawExe = Join-Path $runtimeBin "javaw.exe"
    $javaExe = Join-Path $runtimeBin "java.exe"
    
    if (Test-Path $javawExe) {
      Write-Host "  [OK] Found javaw.exe" -ForegroundColor Green
    } else {
      Write-Host "  [FAIL] Missing javaw.exe" -ForegroundColor Red
    }
    
    if (Test-Path $javaExe) {
      Write-Host "  [OK] Found java.exe" -ForegroundColor Green
    } else {
      Write-Host "  [FAIL] Missing java.exe" -ForegroundColor Red
    }
  } else {
    Write-Host "  [FAIL] Runtime bin directory NOT found: $runtimeBin" -ForegroundColor Red
  }
  
  # Create logs directory if it doesn't exist
  $logsDir = Join-Path $env:LOCALAPPDATA "TodoList\logs"
  if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
    Write-Host "Created logs directory: $logsDir" -ForegroundColor Cyan
  }
  
  # Launch the app with a timeout
  Write-Host "`nLaunching $appName..." -ForegroundColor Cyan
  Write-Host "  Executable: $exePath" -ForegroundColor Gray
  Write-Host "  Logs directory: $logsDir" -ForegroundColor Gray
  Write-Host "  ** A console window should appear with the application **" -ForegroundColor Yellow
  Write-Host "  ** Check the console for any error messages **" -ForegroundColor Yellow
  Write-Host "`nPress Ctrl+C to stop the application, or close the window when done testing..." -ForegroundColor Cyan
  
  try {
    # Start the process and wait for it to exit
    $proc = Start-Process -FilePath $exePath -PassThru
    
    # Wait a bit for startup
    Start-Sleep -Seconds 5
    
    # Check if process is still running
    if (-not $proc.HasExited) {
      Write-Host "`n[OK] Application started successfully (PID: $($proc.Id))" -ForegroundColor Green
      Write-Host "Waiting for application to close..." -ForegroundColor Cyan
      $proc.WaitForExit()
    } else {
      Write-Host "`n[FAIL] Application exited immediately with code: $($proc.ExitCode)" -ForegroundColor Red
    }
    
    $exitCode = $proc.ExitCode
  } catch {
    Write-Host "Error launching application: $_" -ForegroundColor Red
    $exitCode = -1
  }
  
  # Check for log files
  Write-Host "`n=== Checking log files ===" -ForegroundColor Cyan
  
  if (Test-Path $logsDir) {
    $logFiles = Get-ChildItem $logsDir -Filter "*.log" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
    
    if ($logFiles) {
      Write-Host "Found log files:" -ForegroundColor Green
      $logFiles | ForEach-Object {
        Write-Host "  - $($_.Name) ($(Get-Date $_.LastWriteTime -Format 'yyyy-MM-dd HH:mm:ss'), $($_.Length) bytes)" -ForegroundColor White
      }
      
      Write-Host "`n=== Latest log file content (last 100 lines) ===" -ForegroundColor Cyan
      $latestLog = $logFiles | Select-Object -First 1
      Write-Host "File: $($latestLog.FullName)" -ForegroundColor Gray
      Write-Host "---" -ForegroundColor Gray
      Get-Content $latestLog.FullName -Tail 100 -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_ -match "error|exception|fail|warn") {
          Write-Host $_ -ForegroundColor Red
        } else {
          Write-Host $_ -ForegroundColor Gray
        }
      }
    } else {
      Write-Host "No log files found in: $logsDir" -ForegroundColor Yellow
      Write-Host "This might indicate the application failed before logging started" -ForegroundColor Yellow
    }
  } else {
    Write-Host "Logs directory not found: $logsDir" -ForegroundColor Yellow
  }
  
  Write-Host "`n=== Test Results ===" -ForegroundColor White
  Write-Host "Application: $appName" -ForegroundColor Cyan
  Write-Host "Exit Code: $exitCode" -ForegroundColor $(if ($exitCode -eq 0) { "Green" } else { "Red" })
  
  return ($exitCode -eq 0)
}

# Main script

# Find latest build directory
$latestRun = Get-ChildItem ".\dist" -Directory | Where-Object { $_.Name -like "run-*" } | Sort-Object CreationTime -Descending | Select-Object -First 1

if (-not $latestRun) {
  throw "No build directory found in .\dist. Run .\build-installers.ps1 first."
}

Write-Host "Using build directory: $($latestRun.Name)" -ForegroundColor Cyan

$apps = @()
if ($App -eq "both") {
  $apps = @("TodoList Server", "TodoList Client")
} elseif ($App -eq "server") {
  $apps = @("TodoList Server")
} else {
  $apps = @("TodoList Client")
}

foreach ($appName in $apps) {
  # Uninstall existing version
  Uninstall-TodoListApp $appName
  
  if ($UninstallOnly) {
    continue
  }
  
  # Install new version
  $msiPath = Join-Path $latestRun.FullName "$appName-1.0.0.msi"
  $installLog = Install-TodoListApp $msiPath $appName
  
  # Test the application
  $testResult = Test-TodoListApp $appName
  
  if ($testResult) {
    Write-Host "`n[SUCCESS] $appName test passed!" -ForegroundColor Green
  } else {
    Write-Host "`n[FAIL] $appName test failed!" -ForegroundColor Red
    Write-Host "Installation log: $installLog" -ForegroundColor Yellow
  }
}

Write-Host "`n========================================" -ForegroundColor White
Write-Host "Testing complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor White
