Param(
  [Parameter(Mandatory=$false)]
  [ValidateSet("client","server","both")]
  [string]$App = "both"
)

Write-Host "=== MSI Installer Diagnostic Tool ===" -ForegroundColor Green
Write-Host ""

# Find latest build directory
$latestRun = Get-ChildItem ".\dist" -Directory | Where-Object { $_.Name -like "run-*" } | Sort-Object CreationTime -Descending | Select-Object -First 1

if (-not $latestRun) {
  throw "No build directory found in .\dist. Run .\build-installers.ps1 first."
}

Write-Host "Build directory: $($latestRun.Name)" -ForegroundColor Cyan
Write-Host ""

$apps = @()
if ($App -eq "both") {
  $apps = @(@{Name="TodoList Server"; Type="server"}, @{Name="TodoList Client"; Type="client"})
} elseif ($App -eq "server") {
  $apps = @(@{Name="TodoList Server"; Type="server"})
} else {
  $apps = @(@{Name="TodoList Client"; Type="client"})
}

foreach ($appInfo in $apps) {
  $appName = $appInfo.Name
  $appType = $appInfo.Type
  
  Write-Host "========================================" -ForegroundColor White
  Write-Host "Analyzing: $appName" -ForegroundColor Green
  Write-Host "========================================" -ForegroundColor White
  
  $msiPath = Join-Path $latestRun.FullName "$appName-1.0.0.msi"
  
  if (-not (Test-Path $msiPath)) {
    Write-Host "[FAIL] MSI not found: $msiPath" -ForegroundColor Red
    continue
  }
  
  Write-Host "[OK] MSI found: $msiPath" -ForegroundColor Green
  $msiInfo = Get-Item $msiPath
  Write-Host "  Size: $('{0:N2}' -f ($msiInfo.Length / 1MB)) MB" -ForegroundColor Cyan
  Write-Host "  Created: $($msiInfo.CreationTime)" -ForegroundColor Cyan
  Write-Host ""
  
  # Check the jpackage input directory structure
  $inputDir = Join-Path ".\dist\_jpackage" $appType
  Write-Host "Checking jpackage input directory:" -ForegroundColor Cyan
  Write-Host "  Path: $inputDir" -ForegroundColor Gray
  
  if (Test-Path $inputDir) {
    Write-Host "  [OK] Input directory exists" -ForegroundColor Green
    
    # Count JARs
    $jars = Get-ChildItem $inputDir -Filter "*.jar"
    Write-Host "  JARs found: $($jars.Count)" -ForegroundColor Cyan
    
    # List main and dependency JARs
    Write-Host "`n  JAR files:" -ForegroundColor Cyan
    $mainJar = $null
    foreach ($jar in $jars | Sort-Object Name) {
      $size = '{0:N2}' -f ($jar.Length / 1MB)
      if ($jar.Name -like "todolist-$appType-*") {
        Write-Host "    [MAIN] $($jar.Name) ($size MB)" -ForegroundColor Yellow
        $mainJar = $jar.Name
      } elseif ($jar.Name -like "todolist-shared-*") {
        Write-Host "    [SHARED] $($jar.Name) ($size MB)" -ForegroundColor Magenta
      } elseif ($jar.Name -like "javafx-*") {
        Write-Host "    [JAVAFX] $($jar.Name) ($size MB)" -ForegroundColor Cyan
      } else {
        Write-Host "    [DEP] $($jar.Name) ($size MB)" -ForegroundColor Gray
      }
    }
    
    if (-not $mainJar) {
      Write-Host "  [WARN] Main JAR not found for $appType" -ForegroundColor Yellow
    }
    
    # Check for JavaFX dependencies
    $javafxJars = $jars | Where-Object { $_.Name -like "javafx-*" }
    if ($javafxJars.Count -gt 0) {
      Write-Host "`n  [OK] Found $($javafxJars.Count) JavaFX JARs" -ForegroundColor Green
    } else {
      Write-Host "`n  [FAIL] No JavaFX JARs found!" -ForegroundColor Red
    }
  } else {
    Write-Host "  [FAIL] Input directory not found" -ForegroundColor Red
  }
  
  # Check custom runtime
  Write-Host "`nChecking custom runtime:" -ForegroundColor Cyan
  $runtimeDir = ".\dist\custom-runtime"
  if (Test-Path $runtimeDir) {
    Write-Host "  [OK] Runtime directory exists" -ForegroundColor Green
    
    $runtimeBin = Join-Path $runtimeDir "bin"
    if (Test-Path $runtimeBin) {
      Write-Host "  [OK] Runtime bin directory exists" -ForegroundColor Green
      
      $javaw = Join-Path $runtimeBin "javaw.exe"
      $java = Join-Path $runtimeBin "java.exe"
      
      if (Test-Path $javaw) {
        Write-Host "    [OK] javaw.exe found" -ForegroundColor Green
      } else {
        Write-Host "    [FAIL] javaw.exe missing" -ForegroundColor Red
      }
      
      if (Test-Path $java) {
        Write-Host "    [OK] java.exe found" -ForegroundColor Green
      } else {
        Write-Host "    [FAIL] java.exe missing" -ForegroundColor Red
      }
    }
    
    # Check for JavaFX modules in runtime
    $runtimeLib = Join-Path $runtimeDir "lib"
    if (Test-Path $runtimeLib) {
      $javafxLibs = Get-ChildItem $runtimeLib -Filter "javafx*.dll" -ErrorAction SilentlyContinue
      if ($javafxLibs.Count -gt 0) {
        Write-Host "  [OK] Found $($javafxLibs.Count) JavaFX native libraries in runtime" -ForegroundColor Green
      } else {
        Write-Host "  [WARN] No JavaFX native libraries found in runtime/lib" -ForegroundColor Yellow
        Write-Host "    This might mean JavaFX is on classpath instead of module path" -ForegroundColor Gray
      }
    }
  } else {
    Write-Host "  [FAIL] Runtime directory not found: $runtimeDir" -ForegroundColor Red
  }
  
  Write-Host ""
}

# Examine the build script configuration
Write-Host "========================================" -ForegroundColor White
Write-Host "Build Script Analysis" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor White

Write-Host "Checking build-installers.ps1 for jpackage arguments..." -ForegroundColor Cyan
$buildScript = Get-Content ".\build-installers.ps1" -Raw

# Extract jpackage server arguments
if ($buildScript -match '(?s)\$serverArgs = @\((.*?)\)') {
  Write-Host "`nServer jpackage arguments:" -ForegroundColor Yellow
  $matches[1] -split "`n" | Where-Object { $_ -match '\S' } | ForEach-Object {
    Write-Host "  $_" -ForegroundColor Gray
  }
}

# Extract jpackage client arguments
if ($buildScript -match '(?s)\$clientArgs = @\((.*?)\)') {
  Write-Host "`nClient jpackage arguments:" -ForegroundColor Yellow
  $matches[1] -split "`n" | Where-Object { $_ -match '\S' } | ForEach-Object {
    Write-Host "  $_" -ForegroundColor Gray
  }
}

Write-Host "`n========================================" -ForegroundColor White
Write-Host "Diagnostic Summary" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor White
Write-Host "All checks completed." -ForegroundColor Green
Write-Host "`nNext steps:" -ForegroundColor Cyan
Write-Host "  1. If MSI structure looks good, try installing with:" -ForegroundColor White
Write-Host "     .\install-and-test.ps1 -App server" -ForegroundColor Yellow
Write-Host "     (Run PowerShell as Administrator)" -ForegroundColor Gray
Write-Host "  2. Check installation log for errors" -ForegroundColor White
Write-Host "  3. Check application logs in: %LOCALAPPDATA%\TodoList\logs\" -ForegroundColor White
Write-Host ""
