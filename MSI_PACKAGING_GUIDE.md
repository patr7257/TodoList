# MSI Installer Creation Guide

## Quick Reference for Creating Windows MSI Installer

### Prerequisites
- Java 21+ JDK installed
- jpackage (included in JDK 16+)
- WiX Toolset 3.11+ (for MSI creation on Windows)

---

## Step 1: Build Fat JARs

### Option A: Using Maven Shade Plugin (Recommended)

Add to `server/pom.xml`:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>dk.dtu.ServerMain</mainClass>
                            </transformer>
                        </transformers>
                        <finalName>todolist-server-all</finalName>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Build:
```powershell
mvn clean package
```

---

## Step 2: Create MSI with jpackage

### Basic MSI
```powershell
jpackage `
  --input server/target `
  --name "TodoList Server" `
  --main-jar todolist-server-all.jar `
  --main-class dk.dtu.ServerMain `
  --type msi `
  --app-version 1.0.0 `
  --vendor "Your Name"
```

### Full-Featured MSI
```powershell
jpackage `
  --input server/target `
  --name "TodoList Server" `
  --main-jar todolist-server-all.jar `
  --main-class dk.dtu.ServerMain `
  --type msi `
  --app-version 1.0.0 `
  --vendor "Your Name" `
  --description "TodoList Management Server" `
  --copyright "Copyright 2026" `
  --icon resources/icon.ico `
  --win-dir-chooser `
  --win-menu `
  --win-menu-group "TodoList" `
  --win-shortcut `
  --win-per-user-install `
  --java-options "-Dtodolist.data.dir=%APPDATA%\TodoList" `
  --java-options "-Dtodolist.bind.host=0.0.0.0" `
  --java-options "-Dtodolist.port=9001" `
  --java-options "-Xmx512m"
```

### Client MSI
```powershell
jpackage `
  --input client/target `
  --name "TodoList Client" `
  --main-jar todolist-client-all.jar `
  --main-class dk.dtu.ClientApp `
  --type msi `
  --app-version 1.0.0 `
  --vendor "Your Name" `
  --description "TodoList Management Client" `
  --win-dir-chooser `
  --win-menu `
  --win-shortcut `
  --java-options "-Dtodolist.server.ip=127.0.0.1" `
  --java-options "-Dtodolist.port=9001"
```

---

## Step 3: Data Directory Configuration

### For Single-User Installations
```powershell
--java-options "-Dtodolist.data.dir=%APPDATA%\TodoList"
```
Results in: `C:\Users\<username>\AppData\Roaming\TodoList\`

### For Shared/Multi-User Installations
```powershell
--java-options "-Dtodolist.data.dir=%PROGRAMDATA%\TodoList"
```
Results in: `C:\ProgramData\TodoList\`

### For Portable Installation
```powershell
--java-options "-Dtodolist.data.dir=%INSTALL_DIR%\data"
```
Results in: Data stored within application directory

---

## Step 4: Advanced Options

### Custom Icons
1. Create `icon.ico` (256x256 recommended)
2. Add to jpackage: `--icon path/to/icon.ico`

### License Agreement
```powershell
--license-file LICENSE.txt
```

### File Associations (Optional)
```powershell
--file-associations file-associations.properties
```

file-associations.properties:
```properties
mime-type=application/x-todolist
extension=tdl
description=TodoList Session File
```

---

## Complete Build Script

### build-installer.ps1
```powershell
# Build the project
Write-Host "Building project..." -ForegroundColor Green
mvn clean package -DskipTests

# Check if build succeeded
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

# Create server installer
Write-Host "Creating server MSI..." -ForegroundColor Green
jpackage `
  --input server/target `
  --name "TodoList Server" `
  --main-jar todolist-server-1.0.0.jar `
  --main-class dk.dtu.ServerMain `
  --type msi `
  --app-version 1.0.0 `
  --vendor "Your Name" `
  --description "TodoList Management Server" `
  --win-dir-chooser `
  --win-menu `
  --win-menu-group "TodoList" `
  --win-shortcut `
  --java-options "-Dtodolist.data.dir=%APPDATA%\TodoList" `
  --java-options "-Xmx512m"

# Create client installer
Write-Host "Creating client MSI..." -ForegroundColor Green
jpackage `
  --input client/target `
  --name "TodoList Client" `
  --main-jar todolist-client-1.0.0.jar `
  --main-class dk.dtu.ClientApp `
  --type msi `
  --app-version 1.0.0 `
  --vendor "Your Name" `
  --description "TodoList Management Client" `
  --win-dir-chooser `
  --win-menu `
  --win-shortcut

Write-Host "MSI installers created successfully!" -ForegroundColor Green
Write-Host "Server: TodoList Server-1.0.0.msi" -ForegroundColor Yellow
Write-Host "Client: TodoList Client-1.0.0.msi" -ForegroundColor Yellow
```

Run:
```powershell
.\build-installer.ps1
```

---

## Alternative: GraalVM Native Image

For smaller executables without JVM:

### 1. Install GraalVM
Download from: https://www.graalvm.org/

### 2. Build Native Image
```powershell
# Server
native-image `
  --no-fallback `
  -jar server/target/todolist-server-all.jar `
  -H:Name=TodoListServer `
  -H:+ReportExceptionStackTraces

# Client (requires JavaFX native-image plugin)
native-image `
  --no-fallback `
  -jar client/target/todolist-client-all.jar `
  -H:Name=TodoListClient
```

### 3. Package with Inno Setup or NSIS
```iss
[Setup]
AppName=TodoList Server
AppVersion=1.0.0
DefaultDirName={pf}\TodoList
OutputBaseFilename=TodoListSetup

[Files]
Source: "TodoListServer.exe"; DestDir: "{app}"
Source: "data\*"; DestDir: "{userappdata}\TodoList"

[Icons]
Name: "{group}\TodoList Server"; Filename: "{app}\TodoListServer.exe"
```

---

## Testing the Installer

### 1. Test on Clean VM
- Create Windows 10/11 VM
- Install your MSI
- Run without Java installed
- Verify all features work

### 2. Test Upgrade
- Install version 1.0.0
- Create some data
- Install version 1.0.1
- Verify data persists

### 3. Test Uninstall
- Check "Remove user data" option works
- Verify clean uninstall

---

## Common Issues & Solutions

### Issue: "JVM not found"
**Solution**: Include runtime with jpackage (it does this automatically)

### Issue: "Data directory not writable"
**Solution**: Use `%APPDATA%` instead of `%PROGRAMFILES%`

### Issue: "JavaFX not found"
**Solution**: Ensure JavaFX modules are in the input directory

### Issue: "MSI not signing"
**Solution**: Add code signing certificate:
```powershell
--win-sign-tool signtool `
--win-sign-keystore path/to/cert.pfx
```

---

## Distribution Checklist

- [ ] Build tested on clean system
- [ ] Icons and branding added
- [ ] Version number updated
- [ ] License file included
- [ ] README/Help documentation
- [ ] Data directory configurable
- [ ] Uninstaller tested
- [ ] Upgrade path tested
- [ ] Code signed (optional but recommended)
- [ ] VirusTotal scan clean

---

## Size Comparison

| Method | Size | Java Required | Startup Time |
|--------|------|---------------|--------------|
| JAR + JRE | ~50-80 MB | No (bundled) | ~2-3s |
| jpackage MSI | ~50-80 MB | No (bundled) | ~2-3s |
| GraalVM Native | ~20-40 MB | No | ~0.1-0.5s |

---

## Next Steps

1. **Add build-installer.ps1** script to your project
2. **Test locally** with jpackage
3. **Set up CI/CD** (GitHub Actions, Azure DevOps) for automated builds
4. **Add code signing** for production releases
5. **Create auto-updater** (optional) for seamless updates

Your project is ready for MSI packaging! 🚀
