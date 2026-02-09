Param(
  [string]$Version = "1.0.0",
  [ValidateSet("msi","exe")][string]$Type = "msi",
  [switch]$WinConsole,
  [switch]$Debug
)

$ErrorActionPreference = "Stop"

function Require-Command([string]$cmd) {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
    throw "Missing required command: $cmd"
  }
}

function Verify-RuntimeImage([string]$runtimePath) {
  Write-Host "Verifying runtime image at: $runtimePath" -ForegroundColor Cyan
  
  $javawExe = Join-Path $runtimePath "bin\javaw.exe"
  $javaExe = Join-Path $runtimePath "bin\java.exe"
  
  if (-not (Test-Path $javawExe)) {
    throw "Runtime verification FAILED: Missing javaw.exe at $javawExe"
  }
  if (-not (Test-Path $javaExe)) {
    throw "Runtime verification FAILED: Missing java.exe at $javaExe"
  }
  
  Write-Host "  [OK] Found java.exe" -ForegroundColor Green
  Write-Host "  [OK] Found javaw.exe" -ForegroundColor Green
  
  # Verify JavaFX modules are present
  $jmodsDir = Join-Path $runtimePath "..\jmods"
  if (Test-Path $jmodsDir) {
    $javafxModules = @("javafx.controls", "javafx.graphics", "javafx.fxml", "javafx.base")
    foreach ($mod in $javafxModules) {
      $modFile = Join-Path $jmodsDir "$mod.jmod"
      if (Test-Path $modFile) {
        Write-Host "  [OK] Found JavaFX module: $mod" -ForegroundColor Green
      }
    }
  }
  
  Write-Host "Runtime image verification PASSED" -ForegroundColor Green
  return $true
}

Require-Command "mvn"
Require-Command "jpackage"
Require-Command "jlink"

Write-Host "Building Maven modules..." -ForegroundColor Green
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
  throw "Maven build failed"
}

$dist = Join-Path $PSScriptRoot "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

# Use a unique output directory per run to avoid failures when previous installer files are locked
$destDir = Join-Path $dist ("run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $destDir | Out-Null

$serverTarget = Join-Path $PSScriptRoot "server\target"
$clientTarget = Join-Path $PSScriptRoot "client\target"
$sharedTarget = Join-Path $PSScriptRoot "shared\target"

# Stage app + runtime dependencies for jpackage (jpackage does NOT resolve Maven deps automatically)
$stageRoot = Join-Path $dist "_jpackage"
if (Test-Path $stageRoot) {
  Remove-Item -Recurse -Force $stageRoot
}

$serverInput = Join-Path $stageRoot "server"
$clientInput = Join-Path $stageRoot "client"
New-Item -ItemType Directory -Force -Path $serverInput | Out-Null
New-Item -ItemType Directory -Force -Path $clientInput | Out-Null

$serverJar = "todolist-server-$Version.jar"
$clientJar = "todolist-client-$Version.jar"
$sharedJar = "todolist-shared-$Version.jar"

$serverJarPath = Join-Path $serverTarget $serverJar
$clientJarPath = Join-Path $clientTarget $clientJar
$sharedJarPath = Join-Path $sharedTarget $sharedJar

if (-not (Test-Path $serverJarPath)) { throw "Missing server jar: $serverJarPath" }
if (-not (Test-Path $clientJarPath)) { throw "Missing client jar: $clientJarPath" }
if (-not (Test-Path $sharedJarPath)) { throw "Missing shared jar: $sharedJarPath" }

Copy-Item -Force $serverJarPath (Join-Path $serverInput $serverJar)
Copy-Item -Force $clientJarPath (Join-Path $clientInput $clientJar)

# Ensure shared module is on the runtime classpath for both apps
Copy-Item -Force $sharedJarPath (Join-Path $serverInput $sharedJar)
Copy-Item -Force $sharedJarPath (Join-Path $clientInput $sharedJar)

Write-Host "Staging runtime dependencies for server..." -ForegroundColor Green
mvn -q -pl server -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$serverInput"

Write-Host "Staging runtime dependencies for client..." -ForegroundColor Green
mvn -q -pl client -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$clientInput"

# ============================================================================
# CREATE CUSTOM RUNTIME IMAGE WITH JAVAFX MODULES
# ============================================================================
Write-Host "`nCreating custom JRE runtime image with JavaFX..." -ForegroundColor Green

$runtimeDir = Join-Path $dist "custom-runtime"
if (Test-Path $runtimeDir) {
  Remove-Item -Recurse -Force $runtimeDir
}

# Get JAVA_HOME
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
  # Try to find java.exe and derive JAVA_HOME
  $javaExePath = (Get-Command java -ErrorAction SilentlyContinue).Source
  if ($javaExePath) {
    $javaHome = Split-Path (Split-Path $javaExePath -Parent) -Parent
  } else {
    throw "JAVA_HOME not set and java.exe not found in PATH. Please set JAVA_HOME to your JDK 21 installation."
  }
}

Write-Host "Using JAVA_HOME: $javaHome" -ForegroundColor Cyan

$modulePath = Join-Path $javaHome "jmods"
if (-not (Test-Path $modulePath)) {
  throw "JDK jmods directory not found at: $modulePath. Please ensure you have JDK 21 installed (not just JRE)"
}

# Find JavaFX module path from Maven dependencies
# Maven downloads JavaFX as separate platform-specific JARs
$javafxJars = Get-ChildItem -Path $clientInput -Filter "javafx-*.jar"
if ($javafxJars.Count -eq 0) {
  throw "No JavaFX JARs found in client dependencies. Maven dependency resolution may have failed."
}

Write-Host "Found JavaFX dependencies:" -ForegroundColor Cyan
$javafxJars | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor Gray }

# Build module path including JavaFX JARs
$javafxModulePath = $clientInput
$combinedModulePath = "$modulePath;$javafxModulePath"

# Core Java modules required for the app
$javaModules = @(
  "java.base",
  "java.desktop",
  "java.logging",
  "java.naming",
  "java.sql",
  "java.xml",
  "java.management",
  "java.instrument",
  "java.prefs",
  "java.scripting",
  "jdk.unsupported",
  "jdk.crypto.ec"
)

# JavaFX modules (these will be resolved from the JavaFX JARs)
$javafxModules = @(
  "javafx.controls",
  "javafx.fxml",
  "javafx.graphics",
  "javafx.base"
)

$allModules = $javaModules + $javafxModules
$modulesStr = ($allModules -join ",")

Write-Host "`nRunning jlink to create custom runtime with JavaFX..." -ForegroundColor Cyan
Write-Host "Module path: $combinedModulePath" -ForegroundColor Gray
Write-Host "Modules: $modulesStr" -ForegroundColor Gray
Write-Host "Output: $runtimeDir" -ForegroundColor Gray

# Create the runtime image
jlink `
  --module-path $combinedModulePath `
  --add-modules $modulesStr `
  --output $runtimeDir `
  --strip-debug `
  --no-header-files `
  --no-man-pages `
  --compress=zip-6

if ($LASTEXITCODE -ne 0) {
  Write-Host "jlink failed. Attempting fallback approach..." -ForegroundColor Yellow
  
  # Fallback: create runtime without JavaFX modules (JavaFX will be on classpath)
  Write-Host "Creating runtime with Java modules only (JavaFX will be on classpath)..." -ForegroundColor Cyan
  $modulesStr = ($javaModules -join ",")
  
  jlink `
    --module-path $modulePath `
    --add-modules $modulesStr `
    --output $runtimeDir `
    --strip-debug `
    --no-header-files `
    --no-man-pages `
    --compress=zip-6
  
  if ($LASTEXITCODE -ne 0) {
    throw "jlink failed to create runtime image even with fallback"
  }
  
  Write-Host "Runtime created successfully (JavaFX on classpath mode)" -ForegroundColor Green
} else {
  Write-Host "Runtime created successfully (JavaFX modules included)" -ForegroundColor Green
}

# Verify the runtime image
Verify-RuntimeImage $runtimeDir

Write-Host "`nCustom runtime created successfully at: $runtimeDir" -ForegroundColor Green

# ============================================================================
# CONFIGURE JPACKAGE ARGUMENTS
# ============================================================================

$winConsoleArgs = @()
if ($WinConsole) {
  $winConsoleArgs += "--win-console"
}

# Logging options for debugging
$loggingDir = "%LOCALAPPDATA%\TodoList\logs"
$debugJavaOpts = @()
if ($Debug -or $WinConsole) {
  $debugJavaOpts += "-Xlog:all=info:file=$loggingDir\jvm-%p.log:time,level,tags"
  Write-Host "Debug mode enabled - JVM logs will be written to: $loggingDir" -ForegroundColor Yellow
}

# JavaFX module arguments for jpackage
# Since JavaFX JARs are in the input directory, we need to tell jpackage to use them as modules
$javafxModuleArgs = @(
  "--add-modules", "javafx.controls,javafx.fxml"
)

# ============================================================================
# PACKAGE SERVER
# ============================================================================
Write-Host "`nPackaging Server UI ($Type)..." -ForegroundColor Green

$serverArgs = @(
  "--dest", $destDir,
  "--input", $serverInput,
  "--name", "TodoList Server",
  "--main-jar", $serverJar,
  "--main-class", "dk.dtu.ServerApp",
  "--type", $Type,
  "--app-version", $Version,
  "--vendor", "Patrick",
  "--description", "TodoList Management Server",
  "--runtime-image", $runtimeDir,
  "--win-menu",
  "--win-shortcut",
  "--java-options", "-Dtodolist.port=9001"
)

if ($WinConsole) {
  $serverArgs += "--win-console"
}

if ($Debug -or $WinConsole) {
  $serverArgs += "--java-options"
  $serverArgs += "-Xlog:all=info:file=$loggingDir\server-%p.log:time,level,tags"
}

jpackage @serverArgs

if ($LASTEXITCODE -ne 0) {
  throw "jpackage failed for Server"
}

# ============================================================================
# PACKAGE CLIENT
# ============================================================================
Write-Host "`nPackaging Client UI ($Type)..." -ForegroundColor Green

$clientArgs = @(
  "--dest", $destDir,
  "--input", $clientInput,
  "--name", "TodoList Client",
  "--main-jar", $clientJar,
  "--main-class", "dk.dtu.ClientApp",
  "--type", $Type,
  "--app-version", $Version,
  "--vendor", "Patrick",
  "--description", "TodoList Management Client",
  "--runtime-image", $runtimeDir,
  "--win-menu",
  "--win-shortcut",
  "--java-options", "-Dtodolist.server.ip=127.0.0.1",
  "--java-options", "-Dtodolist.port=9001"
)

if ($WinConsole) {
  $clientArgs += "--win-console"
}

if ($Debug -or $WinConsole) {
  $clientArgs += "--java-options"
  $clientArgs += "-Xlog:all=info:file=$loggingDir\client-%p.log:time,level,tags"
}

jpackage @clientArgs

if ($LASTEXITCODE -ne 0) {
  throw "jpackage failed for Client"
}

if ($LASTEXITCODE -ne 0) {
  throw "jpackage failed for Client"
}

# ============================================================================
# SUCCESS
# ============================================================================
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "Output directory: $destDir" -ForegroundColor Yellow
Write-Host "Runtime image: $runtimeDir" -ForegroundColor Yellow

if ($Debug -or $WinConsole) {
  Write-Host "`nDebug mode active. After installation, check logs at:" -ForegroundColor Cyan
  Write-Host "  %LOCALAPPDATA%\TodoList\logs\" -ForegroundColor Cyan
}

Write-Host "`nInstaller files:" -ForegroundColor Cyan
Get-ChildItem -Path $destDir -Filter "*.$Type" | ForEach-Object {
  Write-Host "  - $($_.Name)" -ForegroundColor White
}

if ($Type -eq "msi") {
  Write-Host "`nNOTE: MSI installers require WiX Toolset to be installed." -ForegroundColor Yellow
}

Write-Host "`nTo test the installers:" -ForegroundColor Cyan
Write-Host "  1. Uninstall any previous versions from Windows Settings" -ForegroundColor White
Write-Host "  2. Run the installer as Administrator" -ForegroundColor White
Write-Host "  3. Launch from Start Menu shortcuts" -ForegroundColor White
Write-Host "  4. Check %LOCALAPPDATA%\TodoList\logs\ for startup issues" -ForegroundColor White
