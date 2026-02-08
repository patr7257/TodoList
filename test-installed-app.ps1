Param(
  [Parameter(Mandatory=$true)]
  [ValidateSet("client","server")]
  [string]$App
)

$ErrorActionPreference = "Stop"

$appName = if ($App -eq "client") { "TodoList Client" } else { "TodoList Server" }
$exePath = "C:\Program Files\$appName\$appName.exe"

Write-Host "Testing installed app: $appName" -ForegroundColor Green
Write-Host "EXE path: $exePath" -ForegroundColor Cyan

if (-not (Test-Path $exePath)) {
  Write-Host "ERROR: Application not found at: $exePath" -ForegroundColor Red
  Write-Host "Please install the application first." -ForegroundColor Yellow
  exit 1
}

Write-Host "`n=== Checking runtime structure ===" -ForegroundColor Green
$installDir = Split-Path $exePath -Parent
$runtimeBin = Join-Path $installDir "runtime\bin"

if (Test-Path $runtimeBin) {
  Write-Host "Runtime bin directory exists: $runtimeBin" -ForegroundColor Green
  
  $javawExe = Join-Path $runtimeBin "javaw.exe"
  $javaExe = Join-Path $runtimeBin "java.exe"
  
  if (Test-Path $javawExe) {
    Write-Host "  ✓ Found javaw.exe" -ForegroundColor Green
  } else {
    Write-Host "  ✗ Missing javaw.exe" -ForegroundColor Red
  }
  
  if (Test-Path $javaExe) {
    Write-Host "  ✓ Found java.exe" -ForegroundColor Green
  } else {
    Write-Host "  ✗ Missing java.exe" -ForegroundColor Red
  }
  
  Write-Host "`nRuntime bin contents:" -ForegroundColor Cyan
  Get-ChildItem $runtimeBin | Select-Object -First 20 | ForEach-Object {
    Write-Host "  - $($_.Name)" -ForegroundColor Gray
  }
} else {
  Write-Host "Runtime bin directory NOT found: $runtimeBin" -ForegroundColor Red
}

Write-Host "`n=== Attempting to launch ===" -ForegroundColor Green
Write-Host "Starting process: $exePath" -ForegroundColor Cyan

$proc = Start-Process -FilePath $exePath -PassThru -Wait

Write-Host "Process exited with code: $($proc.ExitCode)" -ForegroundColor $(if ($proc.ExitCode -eq 0) { "Green" } else { "Red" })

if ($proc.ExitCode -ne 0) {
  Write-Host "`n=== Checking for log files ===" -ForegroundColor Yellow
  $logsDir = Join-Path $env:LOCALAPPDATA "TodoList\logs"
  
  if (Test-Path $logsDir) {
    Write-Host "Logs directory: $logsDir" -ForegroundColor Cyan
    $logFiles = Get-ChildItem $logsDir -Filter "*.log" -ErrorAction SilentlyContinue
    
    if ($logFiles) {
      Write-Host "Found log files:" -ForegroundColor Green
      $logFiles | ForEach-Object {
        Write-Host "  - $($_.Name) ($(Get-Date $_.LastWriteTime -Format 'yyyy-MM-dd HH:mm:ss'))" -ForegroundColor White
      }
      
      Write-Host "`nMost recent log file content (last 50 lines):" -ForegroundColor Cyan
      $latestLog = $logFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1
      Get-Content $latestLog.FullName -Tail 50 -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host $_ -ForegroundColor Gray
      }
    } else {
      Write-Host "No log files found in: $logsDir" -ForegroundColor Yellow
    }
  } else {
    Write-Host "Logs directory not found: $logsDir" -ForegroundColor Yellow
    Write-Host "Run the build script with -Debug or -WinConsole to enable logging." -ForegroundColor Cyan
  }
}

Write-Host "`n=== Process check ===" -ForegroundColor Green
$javaProcs = Get-Process -Name "java","javaw","TodoList*" -ErrorAction SilentlyContinue
if ($javaProcs) {
  Write-Host "Found running Java/TodoList processes:" -ForegroundColor Green
  $javaProcs | ForEach-Object {
    Write-Host "  - $($_.ProcessName) (PID: $($_.Id))" -ForegroundColor White
  }
} else {
  Write-Host "No Java/TodoList processes currently running." -ForegroundColor Yellow
}
