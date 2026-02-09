# Release Preparation - Critical Fixes Applied

## ✅ Issues Fixed

### 1. **Data Directory Path Bug** (CRITICAL FIX)
**Problem:**  
The build script was passing `%APPDATA%\TodoList` as a Java system property, but Java doesn't expand Windows environment variables. This caused the server to fail creating the data directory with errors like:
```
Failed to create data directory: C:\Program Files\TodoList Server\%APPDATA%
Failed to save session: %APPDATA%\TodoList\session.json (path not found)
```

**Solution:**  
Removed the incorrect `--java-options` line from `build-installers.ps1` (line 260). The application now uses its default behavior from `Config.java`:
```java
System.getProperty("user.home") + File.separator + ".todolist-data"
```

**Result:**  
- Data will be stored in: `C:\Users\<username>\.todolist-data\`
- Works correctly without Java/IDE
- Cross-platform compatible
- Session data persists properly between runs

### 2. **Server POM Configuration** (Already Correct ✓)
The `server/pom.xml` already has the correct configuration:
```xml
<artifactId>todolist-server</artifactId>
<name>TodoList Server</name>
```
No duplicate `todolist-shared` artifactId issue exists.

## 📋 Next Steps to Create Release

### 1. Rebuild Installers
```powershell
.\build-installers.ps1 -WinConsole -Debug
```

### 2. Test the Installers
1. **Uninstall** any previous versions from Windows Settings
2. **Install** the new MSI as Administrator
3. **Launch** TodoList Server from Start Menu
4. **Verify** the server starts without data directory errors
5. **Check** that data is created in `C:\Users\<your-username>\.todolist-data\`
6. **Launch** TodoList Client and connect to server
7. **Test** creating/editing tasks to verify persistence works

### 3. Verify Logs (Debug Mode)
Check logs at: `%LOCALAPPDATA%\TodoList\logs\`
- `server-*.log` - Server startup and operations
- `client-*.log` - Client connection and operations

### 4. Where Data is Stored

**Server Data:**
- Location: `C:\Users\<username>\.todolist-data\`
- File: `session.json` (contains users, lists, tasks)
- Created automatically on first run
- If missing: loads preset database (3 demo users: Alice, Bob, Charlize + 30 tasks)

**Log Files (Debug/Console mode only):**
- Location: `%LOCALAPPDATA%\TodoList\logs\`
- Files: `server-*.log`, `client-*.log`, `jvm-*.log`

## 🎯 How the Application Works Now

### Server Startup Flow:
1. Reads data directory from system property OR uses default (`~/.todolist-data`)
2. Creates directory if it doesn't exist
3. Attempts to load `session.json`
4. If no session found: loads preset database with sample data
5. Starts listening on configured IP/port (default: 0.0.0.0:9001)

### Client Connection:
1. Connects to server IP:port (default: 127.0.0.1:9001)
2. Retrieves lists and tasks from server
3. All changes are saved automatically by server

## 🔧 Advanced Configuration (Optional)

Users can override defaults via system properties when launching:

```powershell
# Custom data directory
"TodoList Server.exe" -Dtodolist.data.dir=D:\MyTodoData

# Custom port
"TodoList Server.exe" -Dtodolist.port=8080

# Custom bind address
"TodoList Server.exe" -Dtodolist.bind.host=192.168.1.100
```

## 📦 Build Output Structure

```
dist/
├── run-YYYYMMDD-HHMMSS/
│   ├── TodoList Server-1.0.0.msi
│   └── TodoList Client-1.0.0.msi
├── custom-runtime/           # Custom JRE with JavaFX
│   └── bin/
│       ├── java.exe
│       └── javaw.exe
└── _jpackage/                # Staging directory
    ├── server/
    │   ├── todolist-server-1.0.0.jar
    │   ├── todolist-shared-1.0.0.jar
    │   └── [dependencies]
    └── client/
        ├── todolist-client-1.0.0.jar
        ├── todolist-shared-1.0.0.jar
        └── [dependencies]
```

## ✨ GitHub Release Checklist

- [ ] Rebuild installers with fixes applied
- [ ] Test server starts without errors
- [ ] Test client connects successfully
- [ ] Test data persistence (create task, restart server, verify task exists)
- [ ] Test on clean Windows machine without Java installed
- [ ] Create GitHub release tag (e.g., `v1.0.0`)
- [ ] Upload MSI installers to release
- [ ] Add release notes describing features
- [ ] Include installation instructions

## 📝 Sample Release Notes Template

```markdown
## TodoList Management System v1.0.0

### Features
- Multi-user task management with lists and priorities
- Client-server architecture for data synchronization
- Dark mode support
- Real-time notifications
- Data persistence with automatic save

### Installation
1. Download the MSI installers below
2. Install TodoList Server (required)
3. Install TodoList Client
4. Launch server first, then client
5. No Java installation required!

### System Requirements
- Windows 10/11 (64-bit)
- 200 MB disk space
- Network connectivity (for server-client communication)

### Files
- `TodoList Server-1.0.0.msi` - Server application
- `TodoList Client-1.0.0.msi` - Client application

### Data Location
- Server data: `C:\Users\<username>\.todolist-data\`
- Logs: `%LOCALAPPDATA%\TodoList\logs\` (debug builds only)
```

---

**Status:** ✅ Ready to rebuild and test for release!
