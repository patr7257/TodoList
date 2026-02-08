Write-Host "=== TodoList Build Environment Verification ===" -ForegroundColor Green
Write-Host ""

$allGood = $true

# Check Java
Write-Host "Checking Java..." -ForegroundColor Cyan
try {
  $javaVersion = java -version 2>&1 | Select-Object -First 1
  Write-Host "  Java: $javaVersion" -ForegroundColor White
  
  if ($javaVersion -match 'version "21') {
    Write-Host "  [OK] Java 21 detected" -ForegroundColor Green
  } else {
    Write-Host "  [FAIL] Java 21 required, but found: $javaVersion" -ForegroundColor Red
    $allGood = $false
  }
} catch {
  Write-Host "  [FAIL] Java not found in PATH" -ForegroundColor Red
  $allGood = $false
}

# Check JAVA_HOME
Write-Host "`nChecking JAVA_HOME..." -ForegroundColor Cyan
if ($env:JAVA_HOME) {
  Write-Host "  JAVA_HOME: $env:JAVA_HOME" -ForegroundColor White
  
  if (Test-Path $env:JAVA_HOME) {
    Write-Host "  [OK] JAVA_HOME directory exists" -ForegroundColor Green
    
    $jmodsPath = Join-Path $env:JAVA_HOME "jmods"
    if (Test-Path $jmodsPath) {
      $jmodCount = (Get-ChildItem $jmodsPath -Filter "*.jmod" -ErrorAction SilentlyContinue).Count
      Write-Host "  [OK] jmods directory found (${jmodCount} modules)" -ForegroundColor Green
    } else {
      Write-Host "  [FAIL] jmods directory not found at: $jmodsPath" -ForegroundColor Red
      Write-Host "    This indicates JRE is installed instead of JDK" -ForegroundColor Yellow
      $allGood = $false
    }
  } else {
    Write-Host "  [FAIL] JAVA_HOME points to non-existent directory" -ForegroundColor Red
    $allGood = $false
  }
} else {
  Write-Host "  [WARN] JAVA_HOME not set (will try to auto-detect)" -ForegroundColor Yellow
  
  # Try to find JAVA_HOME
  $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
  if ($javaExe) {
    $detectedHome = Split-Path (Split-Path $javaExe -Parent) -Parent
    Write-Host "  Detected JAVA_HOME: $detectedHome" -ForegroundColor Cyan
    
    $jmodsPath = Join-Path $detectedHome "jmods"
    if (Test-Path $jmodsPath) {
      Write-Host "  [OK] jmods found in detected location" -ForegroundColor Green
      Write-Host "  Consider setting: `$env:JAVA_HOME = `"$detectedHome`"" -ForegroundColor Cyan
    } else {
      Write-Host "  [FAIL] jmods not found in detected location" -ForegroundColor Red
      $allGood = $false
    }
  }
}

# Check Maven
Write-Host "`nChecking Maven..." -ForegroundColor Cyan
try {
  $mvnVersion = mvn -version 2>&1 | Select-Object -First 1
  Write-Host "  Maven: $mvnVersion" -ForegroundColor White
  Write-Host "  [OK] Maven found" -ForegroundColor Green
} catch {
  Write-Host "  [FAIL] Maven not found in PATH" -ForegroundColor Red
  Write-Host "    Download from: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
  $allGood = $false
}

# Check jpackage
Write-Host "`nChecking jpackage..." -ForegroundColor Cyan
try {
  $jpackageVersion = jpackage --version 2>&1
  Write-Host "  jpackage: $jpackageVersion" -ForegroundColor White
  Write-Host "  [OK] jpackage found" -ForegroundColor Green
} catch {
  Write-Host "  [FAIL] jpackage not found in PATH" -ForegroundColor Red
  Write-Host "    jpackage comes with JDK 14+, ensure JDK bin is in PATH" -ForegroundColor Yellow
  $allGood = $false
}

# Check jlink
Write-Host "`nChecking jlink..." -ForegroundColor Cyan
try {
  $jlinkVersion = jlink --version 2>&1
  Write-Host "  jlink: $jlinkVersion" -ForegroundColor White
  Write-Host "  [OK] jlink found" -ForegroundColor Green
} catch {
  Write-Host "  [FAIL] jlink not found in PATH" -ForegroundColor Red
  Write-Host "    jlink comes with JDK, ensure JDK bin is in PATH" -ForegroundColor Yellow
  $allGood = $false
}

# Check WiX (optional, for MSI)
Write-Host "`nChecking WiX Toolset (for MSI installers)..." -ForegroundColor Cyan
$wixPath = Get-Command candle.exe -ErrorAction SilentlyContinue
if ($wixPath) {
  Write-Host "  [OK] WiX Toolset found" -ForegroundColor Green
} else {
  Write-Host "  [WARN] WiX Toolset not found (optional, only needed for MSI installers)" -ForegroundColor Yellow
  Write-Host "    Download from: https://wixtoolset.org/" -ForegroundColor Cyan
  Write-Host "    You can still build EXE installers with: -Type exe" -ForegroundColor Cyan
}

# Check Maven project
Write-Host "`nChecking Maven project..." -ForegroundColor Cyan
$pomPath = Join-Path $PSScriptRoot "pom.xml"
if (Test-Path $pomPath) {
  Write-Host "  [OK] Root pom.xml found" -ForegroundColor Green
  
  $clientPom = Join-Path $PSScriptRoot "client\pom.xml"
  $serverPom = Join-Path $PSScriptRoot "server\pom.xml"
  $sharedPom = Join-Path $PSScriptRoot "shared\pom.xml"
  
  if ((Test-Path $clientPom) -and (Test-Path $serverPom) -and (Test-Path $sharedPom)) {
    Write-Host "  [OK] All module pom.xml files found" -ForegroundColor Green
  } else {
    Write-Host "  [FAIL] Some module pom.xml files missing" -ForegroundColor Red
    $allGood = $false
  }
} else {
  Write-Host "  [FAIL] pom.xml not found in current directory" -ForegroundColor Red
  Write-Host "    Run this script from the project root directory" -ForegroundColor Yellow
  $allGood = $false
}

# Summary
Write-Host "`n========================================" -ForegroundColor White
if ($allGood) {
  Write-Host "[SUCCESS] All checks passed! Ready to build." -ForegroundColor Green
  Write-Host "`nTo build installers, run:" -ForegroundColor Cyan
  Write-Host "  .\build-installers.ps1" -ForegroundColor White
  Write-Host "`nFor debugging builds:" -ForegroundColor Cyan
  Write-Host "  .\build-installers.ps1 -WinConsole -Debug" -ForegroundColor White
  Write-Host "  .\build-installers.ps1 -Type exe -Debug" -ForegroundColor White
} else {
  Write-Host "[FAILED] Some checks failed. Please fix the issues above." -ForegroundColor Red
  Write-Host "`nQuick fixes:" -ForegroundColor Cyan
  Write-Host "  1. Install JDK 21 from: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor White
  Write-Host "  2. Set JAVA_HOME: `$env:JAVA_HOME = 'C:\Path\To\JDK-21'" -ForegroundColor White
  Write-Host "  3. Add to PATH: `$env:PATH += ';' + `$env:JAVA_HOME + '\bin'" -ForegroundColor White
}
Write-Host "========================================" -ForegroundColor White
